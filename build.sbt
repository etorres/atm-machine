ThisBuild / organization := "es.eriktorr"
ThisBuild / version := "1.0.0"
ThisBuild / idePackagePrefix := Some("es.eriktorr")
Global / excludeLintKeys += idePackagePrefix

ThisBuild / scalaVersion := "3.8.1"

ThisBuild / semanticdbEnabled := true
ThisBuild / javacOptions ++= Seq("-source", "25", "-target", "25")

Global / cancelable := true
Global / fork := true
Global / onChangedBuildSource := ReloadOnSourceChanges

addCommandAlias(
  "check",
  "; undeclaredCompileDependenciesTest; unusedCompileDependenciesTest; scalafixAll; scalafmtSbtCheck; scalafmtCheckAll",
)

lazy val MUnitFramework = new TestFramework("munit.Framework")
lazy val warts = Warts.unsafe.filter(_ != Wart.DefaultArguments)

lazy val withBaseSettings: Project => Project = _.settings(
  Compile / doc / sources := Seq(),
  tpolecatDevModeOptions ++= Set(
    org.typelevel.scalacoptions.ScalacOptions
      .other("-java-output-version", List("25"), _ => true),
    org.typelevel.scalacoptions.ScalacOptions.other("-Werror", Nil, _ => true),
    org.typelevel.scalacoptions.ScalacOptions.warnOption("safe-init"),
    org.typelevel.scalacoptions.ScalacOptions.privateOption("explicit-nulls"),
  ),
  tpolecatExcludeOptions ++= Set(
    org.typelevel.scalacoptions.ScalacOptions.fatalWarnings,
  ),
  Compile / compile / wartremoverErrors ++= warts,
  Test / compile / wartremoverErrors ++= warts,
  libraryDependencies ++= Seq(
    "com.47deg" %% "scalacheck-toolbox-datetime" % "0.7.0" % Test,
    "io.chrisdavenport" %% "cats-scalacheck" % "0.3.2" % Test,
    "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
    "org.typelevel" %% "scalacheck-effect-munit" % "2.1.0-RC1" % Test,
    "org.typelevel" %% "scalacheck-effect" % "2.1.0-RC1" % Test,
  ),
  Test / envVars := Map(
    "SBT_TEST_ENV_VARS" -> "true",
  ),
  Test / testFrameworks += MUnitFramework,
  Test / testOptions += Tests.Argument(MUnitFramework, "--exclude-tags=online"),
  Test / logBuffered := false,
)

lazy val withCatsEffect: Project => Project = withBaseSettings.compose(
  _.settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-collections-core" % "0.9.10" % Test,
      "org.typelevel" %% "cats-core" % "2.13.0",
      "org.typelevel" %% "cats-effect" % "3.6.3",
      "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
      "org.typelevel" %% "cats-effect-std" % "3.6.3",
      "org.typelevel" %% "cats-free" % "2.13.0",
      "org.typelevel" %% "cats-mtl" % "1.6.0",
      "org.typelevel" %% "cats-time" % "0.6.0",
      "org.typelevel" %% "kittens" % "3.5.0",
    ),
  ),
)

lazy val root = (project in file("."))
  .configure(withCatsEffect)
  .settings(
    name := "atm-machine",
    run / javaOptions += "--enable-native-access=ALL-UNNAMED",
    Test / javaOptions += "--enable-native-access=ALL-UNNAMED",
    libraryDependencies ++= Seq(
      "com.google.ortools" % "ortools-java" % "9.15.6755",
      "com.h2database" % "h2" % "2.4.240",
      "com.monovore" %% "decline" % "2.5.0",
      "com.monovore" %% "decline-effect" % "2.5.0",
      "io.github.iltotore" %% "iron" % "3.2.3",
      "io.github.iltotore" %% "iron-cats" % "3.2.3" % Test,
      "org.tpolecat" %% "doobie-core" % "1.0.0-RC11",
      "org.tpolecat" %% "doobie-free" % "1.0.0-RC11",
      "org.tpolecat" %% "doobie-h2" % "1.0.0-RC11",
      "org.typelevel" %% "log4cats-core" % "2.7.1",
      "org.typelevel" %% "log4cats-noop" % "2.7.1" % Test,
      "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
      "org.typelevel" %% "squants" % "1.8.3",
      "org.tpolecat" %% "typename" % "1.1.0",
    ),
  )
