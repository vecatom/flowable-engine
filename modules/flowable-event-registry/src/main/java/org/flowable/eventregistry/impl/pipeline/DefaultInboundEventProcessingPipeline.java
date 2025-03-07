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
package org.flowable.eventregistry.impl.pipeline;

import java.util.Collection;
import java.util.Map;

import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.eventregistry.api.EventRegistryEvent;
import org.flowable.eventregistry.api.EventRepositoryService;
import org.flowable.eventregistry.api.InboundEventContextExtractor;
import org.flowable.eventregistry.api.InboundEventDeserializer;
import org.flowable.eventregistry.api.InboundEventHeaderInstanceExtractor;
import org.flowable.eventregistry.api.InboundEventKeyDetector;
import org.flowable.eventregistry.api.InboundEventPayloadExtractor;
import org.flowable.eventregistry.api.InboundEventProcessingPipeline;
import org.flowable.eventregistry.api.InboundEventTenantDetector;
import org.flowable.eventregistry.api.InboundEventTransformer;
import org.flowable.eventregistry.api.runtime.EventHeaderInstance;
import org.flowable.eventregistry.api.runtime.EventInstance;
import org.flowable.eventregistry.api.runtime.EventPayloadInstance;
import org.flowable.eventregistry.impl.runtime.EventInstanceImpl;
import org.flowable.eventregistry.model.EventModel;

/**
 * @author Joram Barrez
 * @author Filip Hrisafov
 */
public class DefaultInboundEventProcessingPipeline<T> implements InboundEventProcessingPipeline {

    protected EventRepositoryService eventRepositoryService;
    protected InboundEventContextExtractor inboundEventContextExtractor;
    protected InboundEventDeserializer<T> inboundEventDeserializer;
    protected InboundEventKeyDetector<T> inboundEventKeyDetector;
    protected InboundEventTenantDetector<T> inboundEventTenantDetector;
    protected InboundEventPayloadExtractor<T> inboundEventPayloadExtractor;
    protected InboundEventHeaderInstanceExtractor inboundEventHeaderInstanceExtractor;
    protected InboundEventTransformer inboundEventTransformer;

    public DefaultInboundEventProcessingPipeline(EventRepositoryService eventRepositoryService,
            InboundEventContextExtractor inboundEventContextExtractor,
            InboundEventDeserializer<T> inboundEventDeserializer,
            InboundEventKeyDetector<T> inboundEventKeyDetector,
            InboundEventTenantDetector<T> inboundEventTenantDetector,
            InboundEventPayloadExtractor<T> inboundEventPayloadExtractor,
            InboundEventHeaderInstanceExtractor inboundEventHeaderInstanceExtractor,
            InboundEventTransformer inboundEventTransformer) {
        
        this.inboundEventContextExtractor = inboundEventContextExtractor;
        this.eventRepositoryService = eventRepositoryService;
        this.inboundEventDeserializer = inboundEventDeserializer;
        this.inboundEventKeyDetector = inboundEventKeyDetector;
        this.inboundEventTenantDetector = inboundEventTenantDetector;
        this.inboundEventPayloadExtractor = inboundEventPayloadExtractor;
        this.inboundEventHeaderInstanceExtractor = inboundEventHeaderInstanceExtractor;
        this.inboundEventTransformer = inboundEventTransformer;
    }

    @Override
    public Collection<EventRegistryEvent> run(String channelKey, Object rawEvent) {
        T event = deserialize(rawEvent);
        
        String eventKey = detectEventDefinitionKey(event);

        boolean multiTenant = false;
        String tenantId = AbstractEngineConfiguration.NO_TENANT_ID;
        if (inboundEventTenantDetector != null) {
            tenantId = inboundEventTenantDetector.detectTenantId(event);
            multiTenant = true;
        }

        EventModel eventModel = multiTenant ? eventRepositoryService.getEventModelByKey(eventKey, tenantId) : eventRepositoryService.getEventModelByKey(eventKey);
        
        Map<String, Object> contextInfo = null;
        if (inboundEventContextExtractor != null) {
            contextInfo = inboundEventContextExtractor.extractContextInfo(rawEvent, eventModel);
        }
        
        EventInstanceImpl eventInstance = new EventInstanceImpl(
            eventModel.getKey(),
            extractHeaders(eventModel, contextInfo),
            extractPayload(eventModel, event),
            tenantId
        );

        return transform(eventInstance);
    }

    public T deserialize(Object rawEvent) {
        return inboundEventDeserializer.deserialize(rawEvent);
    }

    public String detectEventDefinitionKey(T event) {
        return inboundEventKeyDetector.detectEventDefinitionKey(event);
    }

    public Collection<EventPayloadInstance> extractPayload(EventModel eventDefinition, T event) {
        return inboundEventPayloadExtractor.extractPayload(eventDefinition, event);
    }
    
    public Collection<EventHeaderInstance> extractHeaders(EventModel eventDefinition, Map<String, Object> contextInfo) {
        Collection<EventHeaderInstance> eventHeaderInstances = null;
        if (inboundEventHeaderInstanceExtractor != null) {
            eventHeaderInstances = inboundEventHeaderInstanceExtractor.extractHeaderInstances(eventDefinition, contextInfo);
        }
        
        return eventHeaderInstances;
    }

    public Collection<EventRegistryEvent> transform(EventInstance eventInstance) {
        return inboundEventTransformer.transform(eventInstance);
    }
    
    public InboundEventDeserializer<T> getInboundEventDeserializer() {
        return inboundEventDeserializer;
    }
    
    public void setInboundEventDeserializer(InboundEventDeserializer<T> inboundEventDeserializer) {
        this.inboundEventDeserializer = inboundEventDeserializer;
    }
    
    public InboundEventKeyDetector<T> getInboundEventKeyDetector() {
        return inboundEventKeyDetector;
    }
    
    public void setInboundEventKeyDetector(InboundEventKeyDetector<T> inboundEventKeyDetector) {
        this.inboundEventKeyDetector = inboundEventKeyDetector;
    }
    
    public InboundEventTenantDetector<T> getInboundEventTenantDetector() {
        return inboundEventTenantDetector;
    }
    
    public void setInboundEventTenantDetector(InboundEventTenantDetector<T> inboundEventTenantDetector) {
        this.inboundEventTenantDetector = inboundEventTenantDetector;
    }
    
    public InboundEventPayloadExtractor<T> getInboundEventPayloadExtractor() {
        return inboundEventPayloadExtractor;
    }
    
    public void setInboundEventPayloadExtractor(InboundEventPayloadExtractor<T> inboundEventPayloadExtractor) {
        this.inboundEventPayloadExtractor = inboundEventPayloadExtractor;
    }
    
    public InboundEventHeaderInstanceExtractor getInboundEventHeaderInstanceExtractor() {
        return inboundEventHeaderInstanceExtractor;
    }

    public void setInboundEventHeaderInstanceExtractor(InboundEventHeaderInstanceExtractor inboundEventHeaderInstanceExtractor) {
        this.inboundEventHeaderInstanceExtractor = inboundEventHeaderInstanceExtractor;
    }

    public InboundEventTransformer getInboundEventTransformer() {
        return inboundEventTransformer;
    }
    
    public void setInboundEventTransformer(InboundEventTransformer inboundEventTransformer) {
        this.inboundEventTransformer = inboundEventTransformer;
    }
}
