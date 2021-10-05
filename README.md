# Datastax Snowflake Sink Connector for Apache Pulsar

This connector pushes messages from Apache Pulsar topic into Snowflake DB.

This connector is Open Source Software, Apache 2 licensed and built using [Snowflake's Connector for Kafka](https://github.com/snowflakedb/snowflake-kafka-connector) ([documentation](https://docs.snowflake.com/en/user-guide/kafka-connector.html)).

## Installation

Please refer to Apache Pulsar IO [documentation](https://pulsar.apache.org/docs/en/io-use/).

Use `pulsar-snowflake-connector/target/pulsar-snowflake-connector-0.1.0-SNAPSHOT.nar` if the project is built from sources.

## Configuration

| Parameter Name  | Description | Default value |
|-----------------|-------------|---------------|
|batchSize|Size of messages in bytes the sink will attempt to batch messages together before flush.|16384|
|lingerTimeMs|Time interval in milliseconds the sink will attempt to batch messages together before flush.|2147483647|
|topic|The topic name that passed to kafka sink.|n/a|
|kafkaConnectorSinkClass|A kafka-connector sink class to use.|com.snowflake.kafka.connector.SnowflakeSinkConnector|
|offsetStorageTopic|Pulsar topic to store offsets at.|snowflake-sink-offsets|
|unwrapKeyValueIfAvailable|In case of `Record<KeyValue<>>` data use key from KeyValue<> instead of one from Record.|true|
|kafkaConnectorConfigProperties|Config properties to pass to the kafka connector.|n/a|

List of `kafkaConnectorConfigProperties` can be found at the [documentation](https://docs.snowflake.com/en/user-guide/kafka-connector-install.html#kafka-configuration-properties) for the Snowflake's Connector for Kafka.

### Known Issues

`snowflake.topic2table.map` parameter [is not supported](https://github.com/snowflakedb/snowflake-kafka-connector/issues/337).

Snowflake's Connector expects `topic:table[,topic:table]` format and does not handle Pulsar's topic URLs `persistent://tenant/namespace/topic-name`.

### Example

```yaml
processingGuarantees: "EFFECTIVELY_ONCE"
configs:
  topic: "snowflake-demo"
  offsetStorageTopic: "snowflake-sink-offsets-demo"
  batchSize: "100"
  lingerTimeMs: "600000"
  kafkaConnectorConfigProperties:
     name: "snowflakedemo"
     connector.class: "com.snowflake.kafka.connector.SnowflakeSinkConnector"
     tasks.max: "1"
     topics: "snowflake-demo"
     buffer.count.records: "100"
     buffer.flush.time: "600"
     buffer.size.bytes: "102400"
     snowflake.url.name: "tenant.snowflakecomputing.com:443"
     snowflake.user.name: "kafka_connector_user"
     snowflake.private.key: "very_secret_key"
     snowflake.database.name: "kafka_db"
     snowflake.schema.name: "kafka_schema"
     key.converter: "org.apache.kafka.connect.storage.StringConverter"
     value.converter: "com.snowflake.kafka.connector.records.SnowflakeJsonConverter"
```

## Building from source

If you want to develop and test this library you need to build it from sources.

Build Apache Pulsar Luna (version defined by pulsar.version in pom.xml) by running 

```shell
mvn clean install -DskipTests
```

Build this project as:

```shell
mvn clean install
```
