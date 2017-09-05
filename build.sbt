lazy val zenith = project
  .in (file ("source/zenith"))
  .settings (moduleName := "zenith")
  .settings (Configurations.buildSettings: _*)
  .settings (Configurations.commonSettings: _*)
  .settings (Configurations.publishSettings: _*)
  .settings (autoCompilerPlugins := true)

lazy val zenith_netty = project
  .in (file ("source/zenith.netty"))
  .settings (moduleName := "zenith-netty")
  .settings (Configurations.buildSettings: _*)
  .settings (Configurations.commonSettings: _*)
  .settings (Configurations.publishSettings: _*)
  .settings (libraryDependencies += Dependencies.netty)
  .dependsOn (zenith % "test->test;compile->compile")

lazy val zenith_default = project
  .in (file ("source/zenith.default"))
  .settings (moduleName := "zenith-default")
  .settings (Configurations.buildSettings: _*)
  .settings (Configurations.commonSettings: _*)
  .settings (Configurations.publishSettings: _*)
  .settings (libraryDependencies += Dependencies.circe_core)
  .settings (libraryDependencies += Dependencies.circe_generic)
  .settings (libraryDependencies += Dependencies.circe_jawn)
  .settings (autoCompilerPlugins := true)
  .dependsOn (zenith % "test->test;compile->compile")

lazy val root = project
  .in (file ("."))
  .settings (Configurations.buildSettings: _*)
  .settings (Configurations.commonSettings: _*)
  .settings (Configurations.noPublishSettings: _*)
  .aggregate (zenith, zenith_netty, zenith_default)
