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
import sbtrelease.ReleasePlugin.autoImport._
import com.typesafe.sbt.SbtPgp.autoImport._

object SbtBuild extends Build
  with SbtCommonConfig with SbtZenithBuild {
  lazy val default = project
    .in (file ("."))
    .settings (initialCommands in console :=
      """
        | println ("Zenith REPL")
        | import java.util.UUID
        | import org.joda.time._
        | import cats._
        | import cats.std.all._
        | import zenith._
        | import zenith.netty._
        | import zenith.context._
      """.stripMargin)
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (noPublishSettings: _*)
    .dependsOn (zenith % "test->test;compile->compile")
    .dependsOn (zenith_netty % "test->test;compile->compile")
    .dependsOn (zenith_context % "test->test;compile->compile")
    .aggregate (zenith, zenith_netty, zenith_context)
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
    scalacOptions in (Compile, console) := compilerOptions,
    scalacOptions in (Compile, test) := compilerOptions,
    parallelExecution in ThisBuild := false
  )
  lazy val noPublishSettings = Seq(
    publish := (),
    publishLocal := (),
    publishArtifact := false
  )
  lazy val publishSettings = Seq (
    releaseCrossBuild := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    homepage := Some(url("https://github.com/sungiant/zenith")),
    licenses := Seq("MIT" -> url("https://raw.githubusercontent.com/sungiant/zenith/master/LICENSE")),
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= (
      for {
        username <- Option(System.getenv().get("SONATYPE_USERNAME"))
        password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
      } yield Credentials(
        "Sonatype Nexus Repository Manager",
        "oss.sonatype.org",
        username,
        password
      )
    ).toSeq,
    autoAPIMappings := true,
//    apiURL := Some(url("https://sungiant.github.io/zenith/api/")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/sungiant/zenith"),
        "scm:git:git@github.com:sungiant/zenith.git"
      )
    ),
    pomExtra := (
      <developers>
        <developer>
          <id>sungiant</id>
          <name>Ash Pook</name>
          <url>https://github.com/sungiant</url>
        </developer>
      </developers>
      )
  )
}

trait SbtZenithBuild { this: SbtCommonConfig =>
  lazy val zenith = project
    .in (file ("source/zenith"))
    .settings (moduleName := "zenith")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .settings (libraryDependencies += "com.github.mpilquist" %% "simulacrum" % "0.3.0")
    .settings (libraryDependencies += "io.circe" %% "circe-core" % "0.1.1")
    .settings (libraryDependencies += "io.circe" %% "circe-generic" % "0.1.1")
    .settings (libraryDependencies += "io.circe" %% "circe-jawn" % "0.1.1")
    .settings (libraryDependencies += "org.specs2" %% "specs2-core" % "2.4.15" % "test")
    .settings (autoCompilerPlugins := true)
    .settings (addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full))

  lazy val zenith_netty = project
    .in (file ("source/zenith.netty"))
    .settings (moduleName := "zenith-netty")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .settings (libraryDependencies += "io.netty" % "netty" % "3.10.3.Final")
    .dependsOn (zenith % "test->test;compile->compile")

  lazy val zenith_context = project
    .in (file ("source/zenith.context"))
    .settings (moduleName := "zenith-context")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .dependsOn (zenith % "test->test;compile->compile")
}
