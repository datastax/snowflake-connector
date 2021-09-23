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
package com.datastax.oss.snowflake;

import java.util.Map;
import org.apache.pulsar.io.core.SinkContext;
import org.apache.pulsar.io.kafka.connect.KafkaConnectSink;

public class SnowflakeSink extends KafkaConnectSink {
  private static final String DEFAULT_CLASS =
      "com.snowflake.kafka.connector.SnowflakeSinkConnector";
  private static final String DEFAULT_OFFSET_TOPIC = "snowflake-sink-offsets";

  public static void setConfigIfNull(Map<String, Object> config, String key, String value) {
    if (config.get(key) == null) {
      config.put(key, value);
    }
  }

  @Override
  public void open(Map<String, Object> config, SinkContext ctx) throws Exception {
    setConfigIfNull(config, "kafkaConnectorSinkClass", DEFAULT_CLASS);
    setConfigIfNull(config, "offsetStorageTopic", DEFAULT_OFFSET_TOPIC);
    super.open(config, ctx);
  }
}
