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
package org.flowable.eventregistry.integrationtest;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;

import javax.jms.Message;
import javax.jms.TextMessage;

import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.api.runtime.CaseInstance;
import org.flowable.cmmn.engine.CmmnEngine;
import org.flowable.cmmn.engine.test.CmmnDeployment;
import org.flowable.common.engine.impl.interceptor.EngineConfigurationConstants;
import org.flowable.eventregistry.api.EventDeployment;
import org.flowable.eventregistry.api.EventRepositoryService;
import org.flowable.eventregistry.impl.EventRegistryEngineConfiguration;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.TestPropertySource;

@JmsEventTest
@TestPropertySource(properties = {
        "application.test.jms-queue=test-queue"
})
public class CaseWithEventRegistryTest {
    
    @Autowired
    protected CmmnEngine cmmnEngine;
    
    @Autowired
    protected CmmnRuntimeService cmmnRuntimeService;
    
    @Autowired
    protected CmmnTaskService cmmnTaskService;
    
    @Autowired
    protected JmsTemplate jmsTemplate;

    @Test
    @CmmnDeployment(resources = { "org/flowable/eventregistry/integrationtest/startCaseWithEvent.cmmn",
            "org/flowable/eventregistry/integrationtest/one.event",
            "org/flowable/eventregistry/integrationtest/one.channel"})
    public void testStartCaseWithEvent() {
        try {
            assertThat(cmmnRuntimeService.createCaseInstanceQuery().caseDefinitionKey("testCaseStartEvent").count()).isEqualTo(0);
            
            jmsTemplate.convertAndSend("test-queue", "{"
                + "    \"payload1\": \"kermit\","
                + "    \"payload2\": 123"
                + "}", messageProcessor -> {
                    
                messageProcessor.setStringProperty("headerProperty1", "headertest");
                return messageProcessor;
            });

            await("receive events")
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(cmmnRuntimeService.createCaseInstanceQuery().caseDefinitionKey("testCaseStartEvent").count()).isEqualTo(1));
            
            CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceQuery().caseDefinitionKey("testCaseStartEvent").singleResult();
            assertThat(cmmnRuntimeService.getVariable(caseInstance.getId(), "headerVar1")).isEqualTo("headertest");
            assertThat(cmmnRuntimeService.getVariable(caseInstance.getId(), "variable1")).isEqualTo("kermit");
            assertThat(cmmnRuntimeService.getVariable(caseInstance.getId(), "variable2")).isEqualTo(123);
            
            jmsTemplate.convertAndSend("test-queue", "{"
                    + "    \"payload1\": \"fozzie\","
                    + "    \"payload2\": 456"
                    + "}");

            await("receive events")
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> assertThat(cmmnRuntimeService.createCaseInstanceQuery().caseDefinitionKey("testCaseStartEvent").count()).isEqualTo(2));
            
            CaseInstance caseInstance2 = cmmnRuntimeService.createCaseInstanceQuery().caseDefinitionKey("testCaseStartEvent")
                    .variableValueEquals("variable1", "fozzie").singleResult();
            assertThat(cmmnRuntimeService.getVariable(caseInstance2.getId(), "variable1")).isEqualTo("fozzie");
            assertThat(cmmnRuntimeService.getVariable(caseInstance2.getId(), "variable2")).isEqualTo(456);

        } finally {
            List<EventDeployment> eventDeployments = getEventRepositoryService().createDeploymentQuery().list();
            for (EventDeployment eventDeployment : eventDeployments) {
                getEventRepositoryService().deleteDeployment(eventDeployment.getId());
            }
        }
    }
    
    @Test
    @CmmnDeployment(resources = { "org/flowable/eventregistry/integrationtest/testSendEventTask.cmmn",
            "org/flowable/eventregistry/integrationtest/one.event",
            "org/flowable/eventregistry/integrationtest/one-outbound.channel"})
    public void testSendEventTask() throws Exception {
        try {
            CaseInstance caseInstance = cmmnRuntimeService.createCaseInstanceBuilder().caseDefinitionKey("testSendEvent")
                    .variable("myHeaderValue", "Some header value")
                    .variable("myVariable", "Some value")
                    .start();
            
            Task task = cmmnTaskService.createTaskQuery().caseInstanceId(caseInstance.getId()).singleResult();
            assertThat(task).isNotNull();
            
            Message message = jmsTemplate.receive("test-outbound-queue");
            assertThat(message.getStringProperty("headerProperty1")).isEqualTo("test");
            assertThat(message.getStringProperty("headerProperty2")).isEqualTo("Some header value");
            TextMessage textMessage = (TextMessage) message;
            assertThatJson(textMessage.getText())
                .isEqualTo("{"
                    + "  payload1: 'Some value'"
                    + "}");

        } finally {
            List<EventDeployment> eventDeployments = getEventRepositoryService().createDeploymentQuery().list();
            for (EventDeployment eventDeployment : eventDeployments) {
                getEventRepositoryService().deleteDeployment(eventDeployment.getId());
            }
        }
    }
    
    protected EventRepositoryService getEventRepositoryService() {
        EventRegistryEngineConfiguration eventRegistryEngineConfiguration = (EventRegistryEngineConfiguration) 
                cmmnEngine.getCmmnEngineConfiguration().getEngineConfigurations().get(EngineConfigurationConstants.KEY_EVENT_REGISTRY_CONFIG);
        return eventRegistryEngineConfiguration.getEventRepositoryService();
    }
}
