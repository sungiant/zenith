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

object Version {
  val nscala_time =     "1.6.0"
  val specs2 =          "2.4.15"
  val netty =           "3.10.3.Final"
  val paradise =        "2.1.0"

  val cats =            "0.7.2"
  val circe =           "0.5.4"
  val kind_projector =  "0.9.2"
  val simulacrum =      "0.10.0"
}

object Dependencies {
  val cats =            "org.typelevel" %% "cats" % Version.cats
  val nscala_time =     "com.github.nscala-time" %% "nscala-time" % Version.nscala_time
  val specs2 =          "org.specs2" %% "specs2-core" % Version.specs2 % "test"
  val simulacrum =      "com.github.mpilquist" %% "simulacrum" % Version.simulacrum
  val paradise =        "org.scalamacros" %% "paradise" % Version.paradise cross CrossVersion.full
  val kind_projector =  "org.spire-math" %% "kind-projector" % Version.kind_projector cross CrossVersion.binary
  val netty =           "io.netty" % "netty" % Version.netty
  val circe_core =      "io.circe" %% "circe-core" % Version.circe
  val circe_generic =   "io.circe" %% "circe-generic" % Version.circe
  val circe_jawn =      "io.circe" %% "circe-jawn" % Version.circe
}

object Resolvers {
  val sonatype =        "Sonatype" at "https://oss.sonatype.org/content/repositories/releases/"
  val sonatype_public = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
  val typesafe =        "Typesafe" at "http://repo.typesafe.com/typesafe/releases/"
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
    (scalaVersion := "2.11.8") ::
    (crossScalaVersions := "2.10.5" :: "2.11.8" :: Nil) :: Nil

  lazy val commonSettings =
    (resolvers += Resolvers.sonatype) ::
    (resolvers += Resolvers.sonatype_public) ::
    (resolvers += Resolvers.typesafe) ::
    (libraryDependencies += Dependencies.cats) ::
    (libraryDependencies += Dependencies.specs2) ::
    (libraryDependencies += Dependencies.nscala_time) ::
    (libraryDependencies += Dependencies.simulacrum) ::
    (addCompilerPlugin (Dependencies.paradise)) ::
    (addCompilerPlugin (Dependencies.kind_projector)) ::
    (scalacOptions ++= compilerOptions) ::
    (parallelExecution in ThisBuild := false) :: Nil

  lazy val noPublishSettings =
    (publish := ()) ::
    (publishLocal := ()) :: Nil

  lazy val publishSettings =
    (releaseCrossBuild := true) ::
    (releasePublishArtifactsAction := PgpKeys.publishSigned.value) ::
    (homepage := Some (url ("https://github.com/sungiant/zenith"))) ::
    (licenses := Seq ("MIT" -> url ("https://raw.githubusercontent.com/sungiant/zenith/master/LICENSE"))) ::
    (publishMavenStyle := true) ::
    (publishArtifact in Test := false) ::
    (pomIncludeRepository := { _ => false }) ::
    (publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value) Some ("snapshots" at nexus + "content/repositories/snapshots")
      else Some ("releases"  at nexus + "service/local/staging/deploy/maven2")
    }) ::
    (credentials ++= (for {
        username <- Option (System.getenv ().get ("SONATYPE_USERNAME"))
        password <- Option (System.getenv ().get ("SONATYPE_PASSWORD"))
      } yield Credentials ("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)
    ).toSeq) ::
    (autoAPIMappings := true) ::
    (scmInfo := Some (ScmInfo (url ("https://github.com/sungiant/zenith"), "scm:git:git@github.com:sungiant/zenith.git"))) ::
    (pomExtra := (
      <developers>
        <developer>
          <id>sungiant</id>
          <name>Ash Pook</name>
          <url>https://github.com/sungiant</url>
        </developer>
      </developers>
      )) :: Nil
}

trait SbtZenithBuild { this: SbtCommonConfig =>
  lazy val zenith = project
    .in (file ("source/zenith"))
    .settings (moduleName := "zenith")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .settings (autoCompilerPlugins := true)

  lazy val zenith_netty = project
    .in (file ("source/zenith.netty"))
    .settings (moduleName := "zenith-netty")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .settings (libraryDependencies += Dependencies.netty)
    .dependsOn (zenith % "test->test;compile->compile")

  lazy val zenith_default = project
    .in (file ("source/zenith.default"))
    .settings (moduleName := "zenith-default")
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (publishSettings: _*)
    .settings (libraryDependencies += Dependencies.circe_core)
    .settings (libraryDependencies += Dependencies.circe_generic)
    .settings (libraryDependencies += Dependencies.circe_jawn)
    .settings (autoCompilerPlugins := true)
    .dependsOn (zenith % "test->test;compile->compile")
}

object SbtBuild extends Build with SbtCommonConfig with SbtZenithBuild {
  lazy val root = project
    .in (file ("."))
    .settings (buildSettings: _*)
    .settings (commonSettings: _*)
    .settings (noPublishSettings: _*)
    .aggregate (zenith, zenith_netty, zenith_default)
}
