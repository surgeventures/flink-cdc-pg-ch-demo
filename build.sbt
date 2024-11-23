ThisBuild / scalaVersion := "2.12.17"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"

lazy val flinkVersion = "1.18.0"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-Ywarn-unused",
    "-encoding", "UTF-8"
  )
)

lazy val root = (project in file("."))
  .settings(
    name := "flink-cdc-clickhouse",
    publish / skip := true
  )
  .aggregate(flinkJob)

lazy val flinkJob = (project in file("flink-job"))
  .settings(
    commonSettings,
    name := "flink-job",
    
    libraryDependencies ++= Seq(
      // These are the core Flink dependencies
      "org.apache.flink" %% "flink-streaming-scala" % flinkVersion % "provided",
      "org.apache.flink" %% "flink-scala" % flinkVersion % "provided",
      "org.apache.flink" % "flink-connector-base" % flinkVersion % "provided",
      
      // CDC connector
      "com.ververica" % "flink-connector-postgres-cdc" % "2.4.0",
      
      // ClickHouse
      "com.clickhouse" % "clickhouse-jdbc" % "0.4.6" classifier "http",
      
      // JSON handling
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.14.2",
      
      // Config & Logging
      "com.typesafe" % "config" % "1.4.3",
      "ch.qos.logback" % "logback-classic" % "1.2.13",
      "org.slf4j" % "slf4j-api" % "1.7.36"
    ),

    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "application.conf" => MergeStrategy.concat
      case "reference.conf" => MergeStrategy.concat
      case x if x.endsWith("module-info.class") => MergeStrategy.discard
      case _ => MergeStrategy.first
    }
  )