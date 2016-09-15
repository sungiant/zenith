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

object Version {
  val cats = "0.6.1"
  val nscala_time = "1.6.0"
  val circe = "0.5.0-M2"
  val zenith = "0.3.0"
}

object Dependencies {
  val cats = "org.typelevel" %% "cats" % Version.cats
  val nscala_time = "com.github.nscala-time" %% "nscala-time" % Version.nscala_time
  val circe_core = "io.circe" %% "circe-core" % Version.circe
  val circe_generic = "io.circe" %% "circe-generic" % Version.circe
  val circe_jawn = "io.circe" %% "circe-jawn" % Version.circe
  val zenith = "io.github.sungiant" %% "zenith" % Version.zenith
  val zenith_netty = "io.github.sungiant" %% "zenith-netty" % Version.zenith
  val zenith_default = "io.github.sungiant" %% "zenith-default" % Version.zenith
}

object Resolvers {
  val sonatype = "Sonatype" at "https://oss.sonatype.org/content/repositories/releases/"
  val typesafe = "Typesafe" at "http://repo.typesafe.com/typesafe/releases/"
}

trait SbtCommonConfig {
  lazy val compilerOptions =
    "-deprecation" ::
    "-encoding" :: "UTF-8" ::
    "-feature" ::
    "-unchecked" ::
    "-language:_" ::
    "-Yno-adapted-args" ::
    "-Yrangepos" ::
    "-Ywarn-dead-code" ::
    "-Ywarn-numeric-widen" ::
    "-Xfuture" :: Nil

  lazy val buildSettings =
    (organization := "io.github.sungiant") ::
    (scalaVersion := "2.11.8") :: Nil

  lazy val commonSettings =
    (resolvers += Resolvers.sonatype) ::
    (resolvers += Resolvers.typesafe) ::
    (libraryDependencies += Dependencies.cats) ::
    (libraryDependencies += Dependencies.nscala_time) ::
    (scalacOptions ++= compilerOptions) ::
    (parallelExecution in ThisBuild := false) :: Nil
}

trait SbtDemoBuild { this: SbtCommonConfig =>
  lazy val demo_common = project
    .in (file ("source/demo.common"))
    .settings (moduleName := "demo-common")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (libraryDependencies += Dependencies.circe_core)
    .settings (libraryDependencies += Dependencies.circe_generic)
    .settings (libraryDependencies += Dependencies.circe_jawn)

  lazy val demo_server = project
    .in (file ("source/demo.server"))
    .settings (moduleName := "demo-server")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .settings (libraryDependencies += Dependencies.zenith)
    .settings (libraryDependencies += Dependencies.zenith_netty)
    .settings (libraryDependencies += Dependencies.zenith_default)
    .dependsOn (demo_common % "test->test;compile->compile")

  lazy val demo_bot = project
    .in (file ("source/demo.bot"))
    .settings (moduleName := "demo-bot")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (connectInput in run := true)
    .settings (fork in run := true)
    .settings (libraryDependencies += Dependencies.zenith)
    .settings (libraryDependencies += Dependencies.zenith_netty)
    .settings (libraryDependencies += Dependencies.zenith_default)
    .dependsOn (demo_common % "test->test;compile->compile")
}

object SbtBuild extends Build with SbtCommonConfig with SbtDemoBuild {
  lazy val root = project
    .in (file ("."))
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .aggregate (demo_common, demo_server, demo_bot)
}
