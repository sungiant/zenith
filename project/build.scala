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

object SbtBuild extends Build with SbtCommonConfig with SbtZenithBuild {
  lazy val default = project
    .in (file ("."))
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .aggregate (zenith, zenith_netty, zenith_plugins)
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
    //"-Xlint",
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
      "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/",
      "Typesafe" at "http://repo.typesafe.com/typesafe/releases/"),
    libraryDependencies ++= Seq (
      "org.typelevel" %% "cats" % "0.6.1",
      "com.github.nscala-time" %% "nscala-time" % "1.6.0",
      "org.specs2" %% "specs2-core" % "2.4.15" % "test"),
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
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },
    credentials ++= (for {
        username <- Option(System.getenv().get("SONATYPE_USERNAME"))
        password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
      } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password )
    ).toSeq,
    autoAPIMappings := true,
    scmInfo := Some(ScmInfo(url("https://github.com/sungiant/zenith"), "scm:git:git@github.com:sungiant/zenith.git")),
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
    .settings (libraryDependencies += "com.github.mpilquist" %% "simulacrum" % "0.7.0")
    .settings (autoCompilerPlugins := true)
    .settings (addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
    .settings (addCompilerPlugin("org.spire-math" % "kind-projector" % "0.8.0" cross CrossVersion.binary))

  lazy val zenith_netty = project
    .in (file ("source/zenith.netty"))
    .settings (moduleName := "zenith-netty")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .settings (libraryDependencies += "io.netty" % "netty" % "3.10.3.Final")
    .dependsOn (zenith % "test->test;compile->compile")

  lazy val zenith_plugins = project
    .in (file ("source/zenith.plugins"))
    .settings (moduleName := "zenith-plugins")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .settings (libraryDependencies += "io.circe" %% "circe-core" % "0.5.0-M2")
    .settings (libraryDependencies += "io.circe" %% "circe-generic" % "0.5.0-M2")
    .settings (libraryDependencies += "io.circe" %% "circe-jawn" % "0.5.0-M2")
    .settings (autoCompilerPlugins := true)
    .settings (addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))
    .dependsOn (zenith % "test->test;compile->compile")
}
