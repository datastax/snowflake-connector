/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.oss;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.datastax.oss.snowflake.SnowflakeSink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.snowflake.kafka.connector.SnowflakeSinkConnectorMock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.Data;
import org.apache.avro.reflect.AvroDefault;
import org.apache.avro.reflect.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.ProducerConsumerBase;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.client.api.schema.Field;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.client.api.schema.GenericRecord;
import org.apache.pulsar.client.api.schema.SchemaDefinition;
import org.apache.pulsar.client.impl.MessageIdImpl;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.client.impl.schema.JSONSchema;
import org.apache.pulsar.common.schema.SchemaType;
import org.apache.pulsar.functions.api.Record;
import org.apache.pulsar.functions.source.PulsarRecord;
import org.apache.pulsar.io.core.SinkContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Maps;

public class SnowflakeSinkTest extends ProducerConsumerBase {

  @Data
  static class StructWithAnnotations {
    int field1;
    @Nullable String field2;

    @AvroDefault("\"1000\"")
    Long field3;
  }

  @Data
  @Builder
  static class MockGenericObjectWrapper implements GenericRecord {

    private final Object nativeObject;
    private final SchemaType schemaType;
    private final byte[] schemaVersion;

    @Override
    public List<Field> getFields() {
      return null;
    }

    @Override
    public Object getField(String fieldName) {
      return null;
    }
  }

  private String offsetTopicName = "persistent://my-property/my-ns/kafka-connect-sink-offset";

  private Path file;
  private Map<String, Object> props;
  private SinkContext context;
  private PulsarClient client;

  @BeforeMethod
  @Override
  protected void setup() throws Exception {
    super.internalSetup();
    super.producerBaseSetup();

    props = Maps.newHashMap();
    props.put("topic", "test-topic");
    props.put("offsetStorageTopic", offsetTopicName);
    props.put("kafkaConnectorSinkClass", SnowflakeSinkConnectorMock.class.getCanonicalName());

    Map<String, String> kafkaConnectorProps = Maps.newHashMap();
    kafkaConnectorProps.put("name", "snowflakesink");
    kafkaConnectorProps.put("connector.class", SnowflakeSinkConnectorMock.class.getCanonicalName());
    kafkaConnectorProps.put("tasks.max", "1");
    kafkaConnectorProps.put("topics", "fake-topic");
    // cannot be less than 10
    kafkaConnectorProps.put("buffer.count.records", "10");
    kafkaConnectorProps.put("buffer.flush.time", "10");
    kafkaConnectorProps.put("buffer.size.bytes", "10");

    kafkaConnectorProps.put("snowflake.url.name", "fake.snowflakecomputing.com:443");
    kafkaConnectorProps.put("snowflake.user.name", "kafka_connector_user_1");
    kafkaConnectorProps.put("snowflake.private.key", "fake");
    kafkaConnectorProps.put("snowflake.database.name", "KAFKA_DB");
    kafkaConnectorProps.put("snowflake.schema.name", "KAFKA_SCHEMA");

    props.put("kafkaConnectorConfigProperties", kafkaConnectorProps);

    this.context = mock(SinkContext.class);
    this.client = PulsarClient.builder().serviceUrl(brokerUrl.toString()).build();
    when(context.getSubscriptionType()).thenReturn(SubscriptionType.Failover);
    when(context.getPulsarClient()).thenReturn(client);
  }

  @AfterMethod(alwaysRun = true)
  @Override
  protected void cleanup() throws Exception {
    if (file != null && Files.exists(file)) {
      Files.delete(file);
    }

    if (this.client != null) {
      client.close();
    }

    super.internalCleanup();
  }

  @Test
  public void smokeTest() throws Exception {
    JSONSchema<StructWithAnnotations> jsonSchema =
        JSONSchema.of(
            SchemaDefinition.<StructWithAnnotations>builder()
                .withPojo(StructWithAnnotations.class)
                .withAlwaysAllowNull(false)
                .build());
    StructWithAnnotations obj = new StructWithAnnotations();
    obj.setField1(10);
    obj.setField2("test");
    obj.setField3(100L);

    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonNode = mapper.valueToTree(obj);

    SnowflakeSink sink = new SnowflakeSink();
    // will throw if the config is not ok
    sink.open(props, context);

    final GenericRecord rec = getGenericRecord(jsonNode, jsonSchema);
    Message msg = mock(MessageImpl.class);
    when(msg.getValue()).thenReturn(rec);
    when(msg.getKey()).thenReturn("key");
    when(msg.hasKey()).thenReturn(true);
    when(msg.getMessageId()).thenReturn(new MessageIdImpl(1, 0, 0));

    final AtomicInteger status = new AtomicInteger(0);
    Record<GenericObject> record =
        PulsarRecord.<String>builder()
            .topicName("fake-topic")
            .message(msg)
            .schema(jsonSchema)
            .ackFunction(status::incrementAndGet)
            .failFunction(status::decrementAndGet)
            .build();

    // will throw if record is not converted properly
    // into what snowflake connector expects
    sink.write(record);
    sink.flush();
    sink.close();

    assertEquals(status.get(), 1);
  }

  private GenericRecord getGenericRecord(Object value, Schema schema) {
    final GenericRecord rec;
    if (value instanceof GenericRecord) {
      rec = (GenericRecord) value;
    } else {
      rec =
          MockGenericObjectWrapper.builder()
              .nativeObject(value)
              .schemaType(schema != null ? schema.getSchemaInfo().getType() : null)
              .schemaVersion(new byte[] {1})
              .build();
    }
    return rec;
  }
}
