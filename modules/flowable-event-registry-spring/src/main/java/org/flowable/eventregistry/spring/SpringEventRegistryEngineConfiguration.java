/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.flowable.eventregistry.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.sql.DataSource;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.impl.cfg.SpringBeanFactoryProxyMap;
import org.flowable.common.engine.impl.interceptor.CommandConfig;
import org.flowable.common.engine.impl.interceptor.CommandInterceptor;
import org.flowable.common.spring.AutoDeploymentStrategy;
import org.flowable.common.spring.SpringEngineConfiguration;
import org.flowable.common.spring.SpringTransactionContextFactory;
import org.flowable.common.spring.SpringTransactionInterceptor;
import org.flowable.eventregistry.api.ChannelProcessingPipelineManager;
import org.flowable.eventregistry.impl.EventRegistryEngine;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.flowable.eventregistry.impl.EventRegistryEngines;
import org.flowable.eventregistry.impl.cfg.StandaloneEventRegistryEngineConfiguration;
import org.flowable.eventregistry.spring.autodeployment.DefaultAutoDeploymentStrategy;
import org.flowable.eventregistry.spring.autodeployment.ResourceParentFolderAutoDeploymentStrategy;
import org.flowable.eventregistry.spring.autodeployment.SingleResourceAutoDeploymentStrategy;
import org.flowable.eventregistry.spring.jms.JmsMessageInboundEventContextExtractor;
import org.flowable.eventregistry.spring.jms.JmsMessageToJsonDeserializer;
import org.flowable.eventregistry.spring.kafka.KafkaConsumerRecordInboundEventContextExtractor;
import org.flowable.eventregistry.spring.kafka.KafkaConsumerRecordToJsonDeserializer;
import org.flowable.eventregistry.spring.management.DefaultSpringEventRegistryChangeDetectionExecutor;
import org.flowable.eventregistry.spring.rabbit.RabbitMessageInboundEventContextExtractor;
import org.flowable.eventregistry.spring.rabbit.RabbitMessageToJsonDeserializer;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * @author Tijs Rademakers
 * @author David Syer
 * @author Joram Barrez
 */
public class SpringEventRegistryEngineConfiguration extends EventRegistryEngineConfiguration implements SpringEngineConfiguration {

    protected PlatformTransactionManager transactionManager;
    protected String deploymentName = "SpringAutoDeployment";
    protected Resource[] deploymentResources = new Resource[0];
    protected String deploymentMode = "default";
    protected ApplicationContext applicationContext;
    protected Integer transactionSynchronizationAdapterOrder;
    protected Collection<AutoDeploymentStrategy<EventRegistryEngine>> deploymentStrategies = new ArrayList<>();

    protected volatile boolean running = false;
    protected List<String> enginesBuild = new ArrayList<>();
    protected final Object lifeCycleMonitor = new Object();

    public SpringEventRegistryEngineConfiguration() {
        this.transactionsExternallyManaged = true;
        this.enableEventRegistryChangeDetectionAfterEngineCreate = false;
        deploymentStrategies.add(new DefaultAutoDeploymentStrategy());
        deploymentStrategies.add(new SingleResourceAutoDeploymentStrategy());
        deploymentStrategies.add(new ResourceParentFolderAutoDeploymentStrategy());
        
        addInboundContextExtractor(ChannelProcessingPipelineManager.CHANNEL_JMS_TYPE, new JmsMessageInboundEventContextExtractor());
        addInboundContextExtractor(ChannelProcessingPipelineManager.CHANNEL_RABBIT_TYPE, new RabbitMessageInboundEventContextExtractor());
        addInboundContextExtractor(ChannelProcessingPipelineManager.CHANNEL_KAFKA_TYPE, new KafkaConsumerRecordInboundEventContextExtractor());
        
        addChannelEventDeserializer(ChannelProcessingPipelineManager.CHANNEL_JMS_TYPE, 
                ChannelProcessingPipelineManager.DESERIALIZER_JSON_TYPE, new JmsMessageToJsonDeserializer());
        addChannelEventDeserializer(ChannelProcessingPipelineManager.CHANNEL_RABBIT_TYPE, 
                ChannelProcessingPipelineManager.DESERIALIZER_JSON_TYPE, new RabbitMessageToJsonDeserializer());
        addChannelEventDeserializer(ChannelProcessingPipelineManager.CHANNEL_KAFKA_TYPE, 
                ChannelProcessingPipelineManager.DESERIALIZER_JSON_TYPE, new KafkaConsumerRecordToJsonDeserializer());
    }

    @Override
    public EventRegistryEngine buildEventRegistryEngine() {
        EventRegistryEngine eventRegistryEngine = super.buildEventRegistryEngine();
        EventRegistryEngines.setInitialized(true);
        enginesBuild.add(eventRegistryEngine.getName());
        return eventRegistryEngine;
    }

    @Override
    public void initBeans() {
        if (beans == null) {
            beans = new SpringBeanFactoryProxyMap(applicationContext);
        }
    }

    public void setTransactionSynchronizationAdapterOrder(Integer transactionSynchronizationAdapterOrder) {
        this.transactionSynchronizationAdapterOrder = transactionSynchronizationAdapterOrder;
    }

    @Override
    public void initDefaultCommandConfig() {
        if (defaultCommandConfig == null) {
            defaultCommandConfig = new CommandConfig().setContextReusePossible(true);
        }
    }

    @Override
    public CommandInterceptor createTransactionInterceptor() {
        if (transactionManager == null) {
            throw new FlowableException("transactionManager is required property for SpringEventRegistryEngineConfiguration, use " + StandaloneEventRegistryEngineConfiguration.class.getName() + " otherwise");
        }

        return new SpringTransactionInterceptor(transactionManager);
    }

    @Override
    public void initTransactionContextFactory() {
        if (transactionContextFactory == null && transactionManager != null) {
            transactionContextFactory = new SpringTransactionContextFactory(transactionManager, transactionSynchronizationAdapterOrder);
        }
    }

    protected void autoDeployResources(EventRegistryEngine eventRegistryEngine) {
        if (deploymentResources != null && deploymentResources.length > 0) {
            final AutoDeploymentStrategy<EventRegistryEngine> strategy = getAutoDeploymentStrategy(deploymentMode);
            strategy.deployResources(deploymentName, deploymentResources, eventRegistryEngine);
        }
    }

    @Override
    public EventRegistryEngineConfiguration setDataSource(DataSource dataSource) {
        if (dataSource instanceof TransactionAwareDataSourceProxy) {
            return (EventRegistryEngineConfiguration) super.setDataSource(dataSource);
        } else {
            // Wrap datasource in Transaction-aware proxy
            DataSource proxiedDataSource = new TransactionAwareDataSourceProxy(dataSource);
            return (EventRegistryEngineConfiguration) super.setDataSource(proxiedDataSource);
        }
    }

    @Override
    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public String getDeploymentName() {
        return deploymentName;
    }

    @Override
    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }

    @Override
    public Resource[] getDeploymentResources() {
        return deploymentResources;
    }

    @Override
    public void setDeploymentResources(Resource[] deploymentResources) {
        this.deploymentResources = deploymentResources;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public String getDeploymentMode() {
        return deploymentMode;
    }

    @Override
    public void setDeploymentMode(String deploymentMode) {
        this.deploymentMode = deploymentMode;
    }

    /**
     * Gets the {@link AutoDeploymentStrategy} for the provided mode. This method may be overridden to implement custom deployment strategies if required, but implementors should take care not to
     * return <code>null</code>.
     * 
     * @param mode
     *            the mode to get the strategy for
     * @return the deployment strategy to use for the mode. Never <code>null</code>
     */
    protected AutoDeploymentStrategy<EventRegistryEngine> getAutoDeploymentStrategy(final String mode) {
        AutoDeploymentStrategy<EventRegistryEngine> result = new DefaultAutoDeploymentStrategy();
        for (final AutoDeploymentStrategy<EventRegistryEngine> strategy : deploymentStrategies) {
            if (strategy.handlesMode(mode)) {
                result = strategy;
                break;
            }
        }
        return result;
    }

    public Collection<AutoDeploymentStrategy<EventRegistryEngine>> getDeploymentStrategies() {
        return deploymentStrategies;
    }

    public void setDeploymentStrategies(Collection<AutoDeploymentStrategy<EventRegistryEngine>> deploymentStrategies) {
        this.deploymentStrategies = deploymentStrategies;
    }

    @Override
    public void start() {
        synchronized (lifeCycleMonitor) {
            if (!isRunning()) {
                enginesBuild.forEach(name -> {
                    EventRegistryEngine eventRegistryEngine = EventRegistryEngines.getEventRegistryEngine(name);
                    eventRegistryEngine.handleDeployedChannelDefinitions();

                    createAndInitEventRegistryChangeDetectionExecutor();

                    autoDeployResources(eventRegistryEngine);
                });
                running = true;
            }
        }
    }

    @Override
    public void initChangeDetectionExecutor() {
        if (eventRegistryChangeDetectionExecutor == null) {
            eventRegistryChangeDetectionExecutor = new DefaultSpringEventRegistryChangeDetectionExecutor(
                eventRegistryChangeDetectionInitialDelayInMs, eventRegistryChangeDetectionDelayInMs);
        }
    }

    protected void createAndInitEventRegistryChangeDetectionExecutor() {
        if (enableEventRegistryChangeDetection && eventRegistryChangeDetectionExecutor != null) {
            eventRegistryChangeDetectionExecutor.setEventRegistryChangeDetectionManager(eventRegistryChangeDetectionManager);
            eventRegistryChangeDetectionExecutor.initialize();
        }
    }

    @Override
    public void stop() {
        synchronized (lifeCycleMonitor) {
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return SpringEngineConfiguration.super.getPhase() - SpringEngineConfiguration.PHASE_DELTA * 2;
    }
}
