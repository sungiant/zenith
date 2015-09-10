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

object SbtBuild extends Build
  with SbtCommonConfig with SbtDemoBuild {
  lazy val default = project
    .in (file ("."))
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .aggregate (demo, demo_server, demo_bot)
}

trait SbtCommonConfig {
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
    "-Xfuture",
    "-Xlint"
  )

  lazy val buildSettings = Seq(
    organization := "io.github.sungiant",
    scalaVersion := "2.11.7",
    crossScalaVersions := Seq ("2.10.5", "2.11.7")
  )
  lazy val commonSettings = Seq (
    resolvers ++= Seq (
      "Sonatype" at "https://oss.sonatype.org/content/repositories/releases/",
      "Typesafe" at "http://repo.typesafe.com/typesafe/releases/"),
    libraryDependencies ++= Seq (
      "org.spire-math" %% "cats" % "0.1.2",
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
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith" % "0.0.1")

  lazy val demo_server = project
    .in (file ("source/demo.server"))
    .settings (moduleName := "demo-server")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith" % "0.0.1")
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith-netty" % "0.0.1")
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith-context" % "0.0.1")
    .dependsOn (demo % "test->test;compile->compile")

  lazy val demo_bot = project
    .in (file ("source/demo.bot"))
    .settings (moduleName := "demo-bot")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith" % "0.0.1")
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith-netty" % "0.0.1")
    .settings (libraryDependencies += "io.github.sungiant" %% "zenith-context" % "0.0.1")
    .dependsOn (demo % "test->test;compile->compile")
}
