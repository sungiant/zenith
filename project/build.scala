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
  with SbtCommonConfig with SbtZenithBuild with SbtDemoBuild {
  lazy val default = project
    .in (file ("."))
    .settings (initialCommands in console :=
      """
        | println ("Zenith REPL")
        | import scala.concurrent._
        | import scala.concurrent.duration._
        | import ExecutionContext.Implicits.global
        | import java.util.UUID
        | import org.joda.time._
        | import cats._
        | import cats.std.all._
        | import zenith._
        | import zenith.netty._
      """.stripMargin)
    .settings (commonSettings: _*)
    .aggregate (zenith, zenith_netty, demo, demo_server, demo_bot)
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
  lazy val commonSettings = Seq (
    organization := "zenith",
    scalaVersion := "2.11.7",
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

trait SbtZenithBuild { this: SbtCommonConfig =>
  lazy val zenith = project
    .in (file ("source/zenith"))
    .settings (moduleName := "zenith")
    .settings (commonSettings: _*)
    .settings (libraryDependencies += "com.github.mpilquist" %% "simulacrum" % "0.3.0")
    .settings (libraryDependencies += "io.circe" %% "circe-core" % "0.1.1")
    .settings (libraryDependencies += "io.circe" %% "circe-generic" % "0.1.1")
    .settings (libraryDependencies += "io.circe" %% "circe-jawn" % "0.1.1")
    .settings (autoCompilerPlugins := true)
    .settings (addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full))

  lazy val zenith_netty = project
    .in (file ("source/zenith.netty"))
    .settings (moduleName := "zenith-netty")
    .settings (commonSettings: _*)
    .settings (libraryDependencies += "io.netty" % "netty" % "3.10.3.Final")
    .dependsOn (zenith % "test->test;compile->compile")
}

trait SbtDemoBuild { this: SbtCommonConfig with SbtZenithBuild =>
  lazy val demo = project
    .in (file ("source/demo"))
    .settings (moduleName := "demo")
    .settings (commonSettings: _*)
    .dependsOn (zenith % "test->test;compile->compile")

  lazy val demo_server = project
    .in (file ("source/demo.server"))
    .settings (moduleName := "demo-server")
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .dependsOn (demo % "test->test;compile->compile")
    .dependsOn (zenith % "test->test;compile->compile")
    .dependsOn (zenith_netty % "test->test;compile->compile")

  lazy val demo_bot = project
    .in (file ("source/demo.bot"))
    .settings (moduleName := "demo-bot")
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .dependsOn (demo % "test->test;compile->compile")
    .dependsOn (zenith % "test->test;compile->compile")
    .dependsOn (zenith_netty % "test->test;compile->compile")
}
