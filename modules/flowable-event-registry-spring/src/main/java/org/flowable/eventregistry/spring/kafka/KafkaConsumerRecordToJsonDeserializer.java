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
package org.flowable.eventregistry.spring.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.eventregistry.api.InboundEventDeserializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class KafkaConsumerRecordToJsonDeserializer implements InboundEventDeserializer<JsonNode> {

    protected ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public JsonNode deserialize(Object rawEvent) {
        try {
            return objectMapper.readTree(convertEventToString(rawEvent));
        } catch (Exception e) {
            throw new FlowableException("Could not deserialize event to json", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public String convertEventToString(Object rawEvent) throws Exception {
        ConsumerRecord<Object, Object> consumerRecord = (ConsumerRecord<Object, Object>) rawEvent;
        return consumerRecord.value().toString();
    }
}
