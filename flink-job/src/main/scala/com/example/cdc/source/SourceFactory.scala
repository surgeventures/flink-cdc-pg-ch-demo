package com.example.cdc.source

import com.ververica.cdc.connectors.postgres.PostgreSQLSource
import com.ververica.cdc.debezium.JsonDebeziumDeserializationSchema
import com.example.cdc.config.DatabaseConfig
import java.util.Properties

object SourceFactory {
  def createSource(config: DatabaseConfig) = {
    val props = new Properties()
    props.setProperty("metrics.registry.enable", "false")
    props.setProperty("metrics.jmx.enable", "false")
    props.setProperty("database.server.name", config.serverName)
    props.setProperty("decimal.handling.mode", "string")

    PostgreSQLSource
      .builder[String]()
      .hostname(config.hostname)
      .port(config.port)
      .database(config.database)
      .schemaList(config.schema)
      .tableList(s"${config.schema}.${config.tableName}")
      .username(config.username)
      .password(config.password)
      .decodingPluginName("pgoutput")
      .deserializer(new JsonDebeziumDeserializationSchema())
      .debeziumProperties(props)
      .build()
  }
}
