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
package com.snowflake.kafka.connector;

import static com.snowflake.kafka.connector.internal.streaming.IngestionMethodConfig.SNOWPIPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.snowflake.kafka.connector.internal.InternalUtilsAccessor;
import com.snowflake.kafka.connector.internal.KCLogger;
import com.snowflake.kafka.connector.internal.SnowflakeConnectionService;
import com.snowflake.kafka.connector.internal.SnowflakeConnectionServiceV1;
import com.snowflake.kafka.connector.internal.SnowflakeIngestionService;
import com.snowflake.kafka.connector.internal.SnowflakeIngestionServiceV1;
import com.snowflake.kafka.connector.internal.SnowflakeSinkService;
import com.snowflake.kafka.connector.internal.SnowflakeSinkServiceFactory;
import com.snowflake.kafka.connector.internal.telemetry.SnowflakeTelemetryService;
import com.snowflake.kafka.connector.internal.telemetry.SnowflakeTelemetryServiceV1;
import com.snowflake.kafka.connector.records.SnowflakeMetadataConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.mockito.Mockito;

public class SnowflakeSinkTaskMock extends SnowflakeSinkTask {

  private final KCLogger DYNAMIC_LOGGER;
  final SnowflakeSinkTask wrapped;
  final SnowflakeConnectionService connMock;
  final SnowflakeTelemetryService telemetryMock;
  final SnowflakeIngestionService ingestionMock;

  public SnowflakeSinkTaskMock() {
    DYNAMIC_LOGGER = new KCLogger(this.getClass().getName());
    this.DYNAMIC_LOGGER.info("[sf_mock] Creating SnowflakeSinkTaskMock");
    wrapped = new SnowflakeSinkTask();
    telemetryMock = Mockito.mock(SnowflakeTelemetryServiceV1.class);

    ingestionMock = Mockito.mock(SnowflakeIngestionServiceV1.class);
    when(ingestionMock.getStageName()).thenReturn("stage");
    when(ingestionMock.readIngestReport(any())).thenReturn(InternalUtilsAccessor.getEmptyIFSMap());

    connMock = Mockito.mock(SnowflakeConnectionServiceV1.class);
    when(connMock.getTelemetryClient()).thenReturn(telemetryMock);
    when(connMock.isClosed()).thenReturn(false);
    when(connMock.getConnectorName()).thenReturn("TEST_CONNECTOR_TASK");

    when(connMock.buildIngestService(any(), any())).thenReturn(ingestionMock);
    when(connMock.listStage(any(), any())).thenReturn(new ArrayList<>());

    when(connMock.pipeExist(any())).thenReturn(true);
    when(connMock.isPipeCompatible(any(), any(), any())).thenReturn(true);
    when(connMock.tableExist(any())).thenReturn(true);
    when(connMock.isTableCompatible(any())).thenReturn(true);
    when(connMock.stageExist(any())).thenReturn(true);
    when(connMock.isStageCompatible(any())).thenReturn(true);
  }

  @Override
  public String version() {
    return wrapped.version();
  }

  @Override
  public void open(final Collection<TopicPartition> partitions) {
    wrapped.open(partitions);
  }

  @Override
  public Map<TopicPartition, OffsetAndMetadata> preCommit(
      Map<TopicPartition, OffsetAndMetadata> offsets) throws RetriableException {
    return wrapped.preCommit(offsets);
  }

  @SneakyThrows
  @Override
  public void start(Map<String, String> parsedConfig) {
    long startTime = System.currentTimeMillis();

    String id = parsedConfig.getOrDefault(Utils.TASK_ID, "-1");
    FieldUtils.writeDeclaredField(wrapped, "taskConfigId", id, true);
    this.DYNAMIC_LOGGER.info("SnowflakeSinkTask[TaskConfigID:{}]:start", id);

    // generate topic to table map
    Map<String, String> topic2table = new HashMap<>();
    FieldUtils.writeDeclaredField(wrapped, "topic2table", topic2table, true);

    // enable jvm proxy
    Utils.enableJVMProxy(parsedConfig);

    // config buffer.count.records -- how many records to buffer
    final long bufferCountRecords =
        Long.parseLong(parsedConfig.get(SnowflakeSinkConnectorConfig.BUFFER_COUNT_RECORDS));
    // config buffer.size.bytes -- aggregate size in bytes of all records to
    // buffer
    final long bufferSizeBytes =
        Long.parseLong(parsedConfig.get(SnowflakeSinkConnectorConfig.BUFFER_SIZE_BYTES));
    final long bufferFlushTime =
        Long.parseLong(parsedConfig.get(SnowflakeSinkConnectorConfig.BUFFER_FLUSH_TIME_SEC));

    // Falling back to default behavior which is to ingest an empty json string if we get null
    // value. (Tombstone record)
    SnowflakeSinkConnectorConfig.BehaviorOnNullValues behavior =
        SnowflakeSinkConnectorConfig.BehaviorOnNullValues.DEFAULT;
    if (parsedConfig.containsKey(SnowflakeSinkConnectorConfig.BEHAVIOR_ON_NULL_VALUES_CONFIG)) {
      // we can always assume here that value passed in would be an allowed value, otherwise the
      // connector would never start or reach the sink task stage
      behavior =
          SnowflakeSinkConnectorConfig.BehaviorOnNullValues.valueOf(
              parsedConfig.get(SnowflakeSinkConnectorConfig.BEHAVIOR_ON_NULL_VALUES_CONFIG));
    }

    FieldUtils.writeDeclaredField(wrapped, "conn", connMock, true);
    FieldUtils.writeDeclaredField(wrapped, "ingestionMethodConfig", SNOWPIPE, true);

    SnowflakeMetadataConfig metadataConfig = new SnowflakeMetadataConfig(parsedConfig);

    SnowflakeSinkService sink =
        SnowflakeSinkServiceFactory.builder(connMock)
            .setFileSize(bufferSizeBytes)
            .setRecordNumber(bufferCountRecords)
            .setFlushTime(bufferFlushTime)
            .setTopic2TableMap(topic2table)
            .setMetadataConfig(metadataConfig)
            .setBehaviorOnNullValuesConfig(behavior)
            .build();

    FieldUtils.writeDeclaredField(wrapped, "sink", sink, true);

    this.DYNAMIC_LOGGER.info(
        "SnowflakeSinkTask[TaskConfigID:{}]:start. Time: {} seconds",
        id,
        (System.currentTimeMillis() - startTime) / 1000);
  }

  @Override
  public void put(Collection<SinkRecord> collection) {
    this.DYNAMIC_LOGGER.info("[sf_mock] put of {} items", collection.size());
    wrapped.put(collection);
  }

  @Override
  public void stop() {
    wrapped.stop();
  }
}
