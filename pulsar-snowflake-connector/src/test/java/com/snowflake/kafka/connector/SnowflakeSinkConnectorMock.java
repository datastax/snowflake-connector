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

import static org.mockito.Mockito.when;

import com.snowflake.kafka.connector.internal.KCLogger;
import com.snowflake.kafka.connector.internal.SnowflakeConnectionService;
import com.snowflake.kafka.connector.internal.telemetry.SnowflakeTelemetryService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.mockito.Mockito;

public class SnowflakeSinkConnectorMock extends SnowflakeSinkConnector {

  private KCLogger DYNAMIC_LOGGER;
  final SnowflakeSinkConnector wrapped;
  final SnowflakeConnectionService connMock;
  final SnowflakeTelemetryService telemetryMock;

  public SnowflakeSinkConnectorMock() {
    DYNAMIC_LOGGER = new KCLogger(this.getClass().getName());
    this.DYNAMIC_LOGGER.info("[sf_mock] Creating SnowflakeSinkConnectorMock");
    wrapped = new SnowflakeSinkConnector();
    telemetryMock = Mockito.mock(SnowflakeTelemetryService.class);
    connMock = Mockito.mock(SnowflakeConnectionService.class);
    when(connMock.getTelemetryClient()).thenReturn(telemetryMock);
    when(connMock.isClosed()).thenReturn(false);
    when(connMock.getConnectorName()).thenReturn("TEST_CONNECTOR_SINK");
  }

  @Override
  public Class<? extends Task> taskClass() {
    return SnowflakeSinkTaskMock.class;
  }

  @Override
  public List<Map<String, String>> taskConfigs(int i) {
    return wrapped.taskConfigs(i);
  }

  @Override
  public void stop() {
    wrapped.stop();
  }

  @Override
  public ConfigDef config() {
    return null;
  }

  /*
  Doing the same setup as SnowflakeConnector but mocking services that access snowflake
  */
  @SneakyThrows
  @Override
  public void start(Map<String, String> parsedConfig) {
    Utils.checkConnectorVersion();
    this.DYNAMIC_LOGGER.info("SnowflakeSinkConnector:start");

    FieldUtils.writeDeclaredField(wrapped, "setupComplete", false, true);
    FieldUtils.writeDeclaredField(wrapped, "connectorStartTime", System.currentTimeMillis(), true);

    FieldUtils.writeDeclaredField(wrapped, "setupComplete", false, true);

    Map<String, String> config = new HashMap<>(parsedConfig);
    SnowflakeSinkConnectorConfig.setDefaultValues(config);
    FieldUtils.writeDeclaredField(wrapped, "config", config, true);

    Utils.validateConfig(config);
    // modify invalid connector name
    Utils.convertAppName(config);
    // enable proxy
    Utils.enableJVMProxy(config);

    // create a persisted connection, and validate snowflake connection
    // config as a side effect
    FieldUtils.writeDeclaredField(wrapped, "conn", connMock, true);
    FieldUtils.writeDeclaredField(wrapped, "telemetryClient", telemetryMock, true);

    FieldUtils.writeDeclaredField(wrapped, "setupComplete", true, true);
  }

  @Override
  public String version() {
    return wrapped.version();
  }
}
