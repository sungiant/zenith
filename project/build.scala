/**
 *   __________            .__  __  .__
 *   \____    /____   ____ |__|/  |_|  |__
 *     /     // __ \ /    \|  \   __\  |  \
 *    /     /\  ___/|   |  \  ||  | |   Y  \
 *   /_______ \___  >___|  /__||__| |___|  /
 *           \/   \/     \/              \/
 */
import sbt.Keys._
import sbt._

object SbtBuild extends Build with SbtCommonConfig with SbtDemoBuild {
  lazy val root = project
    .in (file ("."))
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .aggregate (demo, demo_server, demo_bot)
}

trait SbtCommonConfig {

  lazy val circeVersion = "0.5.0-M2"
  lazy val zenithVersion = "0.1.2"

  lazy val compilerOptions = Seq (
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-language:_",
    "-Yno-adapted-args",
    "-Yrangepos",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xfuture"
  )

  lazy val buildSettings = Seq(
    organization := "io.github.sungiant",
    scalaVersion := "2.11.8",
    crossScalaVersions := Seq ("2.10.5", "2.11.8")
  )
  lazy val commonSettings = Seq (
    resolvers ++= Seq (
      "Sonatype" at "https://oss.sonatype.org/content/repositories/releases/",
      "Typesafe" at "http://repo.typesafe.com/typesafe/releases/"),
    libraryDependencies ++= Seq (
      "org.typelevel" %% "cats" % "0.6.1",
      "com.github.nscala-time" %% "nscala-time" % "1.6.0"),
    scalacOptions ++= compilerOptions,
    parallelExecution in ThisBuild := false
  )
}

trait SbtDemoBuild { this: SbtCommonConfig =>
  lazy val demo = project
    .in (file ("source/demo"))
    .settings (moduleName := "demo")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (libraryDependencies += "io.circe" %% "circe-core" % circeVersion)
    .settings (libraryDependencies += "io.circe" %% "circe-generic" % circeVersion)
    .settings (libraryDependencies += "io.circe" %% "circe-jawn" % circeVersion)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith" % zenithVersion)

  lazy val demo_server = project
    .in (file ("source/demo.server"))
    .settings (moduleName := "demo-server")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith" % zenithVersion)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith-netty" % zenithVersion)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith-plugins" % zenithVersion)
    .dependsOn (demo % "test->test;compile->compile")

  lazy val demo_bot = project
    .in (file ("source/demo.bot"))
    .settings (moduleName := "demo-bot")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith" % zenithVersion)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith-netty" % zenithVersion)
    .dependsOn (demo % "test->test;compile->compile")
}
