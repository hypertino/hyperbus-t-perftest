fork := true

connectInput in run := true

crossScalaVersions := Seq("2.12.1", "2.11.8")

scalaVersion in Global := "2.12.1"

organization := "com.hypertino"

name := "hyperbus-t-perftest"

version := "0.1-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.hypertino"   %% "hyperbus" % "0.2-SNAPSHOT",
  "com.hypertino"   %% "hyperbus-t-zeromq" % "0.2-SNAPSHOT",
  "com.hypertino"   %% "service-config" % "0.2-SNAPSHOT",
  "com.hypertino"   %% "service-control" % "0.3-SNAPSHOT",
  "com.hypertino"   %% "hyperbus-model" % "0.2-SNAPSHOT",
  "ch.qos.logback" % "logback-classic" % "1.1.8",
  "org.scalamock"   %% "scalamock-scalatest-support" % "3.5.0" % "test",
  compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
)

resolvers ++= Seq(
  Resolver.sonatypeRepo("public")
)
