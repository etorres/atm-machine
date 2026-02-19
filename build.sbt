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

lazy val withBaseSettings: Project => Project =
  _.settings(
    run / javaOptions += "--enable-native-access=ALL-UNNAMED",
    Test / javaOptions += "--enable-native-access=ALL-UNNAMED",
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
    Compile / doc / sources := Seq(),
    Test / envVars := Map(
      "SBT_TEST_ENV_VARS" -> "true",
    ),
    Test / testOptions += Tests.Argument(MUnitFramework, "--exclude-tags=online"),
    Test / logBuffered := false,
  )

lazy val withMunitCatsEffect: Project => Project =
  withBaseSettings.compose(
    _.settings(
      libraryDependencies ++= Seq(
        "com.47deg" %% "scalacheck-toolbox-datetime" % "0.7.0" % Test,
        "io.chrisdavenport" %% "cats-scalacheck" % "0.3.2" % Test,
        "org.typelevel" %% "munit-cats-effect" % "2.1.0" % Test,
        "org.typelevel" %% "scalacheck-effect-munit" % "2.1.0-RC1" % Test,
        "org.typelevel" %% "scalacheck-effect" % "2.1.0-RC1" % Test,
      ),
    ),
  )

lazy val cashDomain =
  (project in file("modules/cash/cash-domain"))
    .configure(withMunitCatsEffect)
    .settings(
      name := "cash-domain",
      libraryDependencies ++= Seq(
        "com.google.ortools" % "ortools-java" % "9.15.6755",
        "io.github.iltotore" %% "iron" % "3.3.0",
        "io.github.iltotore" %% "iron-cats" % "3.3.0" % Test,
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
        "org.typelevel" %% "cats-mtl" % "1.6.0",
        "org.typelevel" %% "kittens" % "3.5.0",
        "org.typelevel" %% "log4cats-core" % "2.7.1",
        "org.typelevel" %% "squants" % "1.8.3",
      ),
    )
    .dependsOn(testSupport % "compile->test")

lazy val atmDomain =
  (project in file("modules/atm/atm-domain"))
    .configure(withMunitCatsEffect)
    .settings(
      name := "atm-domain",
      libraryDependencies ++= Seq(
        "io.github.iltotore" %% "iron" % "3.3.0",
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
        "org.typelevel" %% "cats-effect-std" % "3.6.3",
        "org.typelevel" %% "cats-mtl" % "1.6.0",
        "org.typelevel" %% "cats-time" % "0.6.0",
        "org.typelevel" %% "kittens" % "3.5.0",
        "org.typelevel" %% "squants" % "1.8.3",
      ),
    )
    .dependsOn(cashDomain % "compile->compile;test->test")
    .dependsOn(testSupport % "compile->test")

lazy val atmApplication =
  (project in file("modules/atm/atm-application"))
    .configure(withMunitCatsEffect)
    .settings(
      name := "atm-application",
      libraryDependencies ++= Seq(
        "io.github.iltotore" %% "iron" % "3.3.0",
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
        "org.typelevel" %% "cats-effect-std" % "3.6.3",
        "org.typelevel" %% "cats-mtl" % "1.6.0",
        "org.typelevel" %% "log4cats-core" % "2.7.1",
        "org.typelevel" %% "squants" % "1.8.3",
      ),
    )
    .dependsOn(atmDomain % "compile->compile;test->test")
    .dependsOn(testSupport % "compile->test")

lazy val atmInfrastructure =
  (project in file("modules/atm/atm-infrastructure"))
    .configure(withMunitCatsEffect)
    .settings(
      name := "atm-infrastructure",
      libraryDependencies ++= Seq(
        "co.fs2" %% "fs2-io" % "3.12.2" % Test,
        "com.h2database" % "h2" % "2.4.240",
        "io.circe" %% "circe-core" % "0.14.15",
        "io.circe" %% "circe-parser" % "0.14.15",
        "io.github.iltotore" %% "iron" % "3.3.0",
        "io.github.iltotore" %% "iron-circe" % "3.3.0",
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
        "org.typelevel" %% "cats-effect-std" % "3.6.3",
        "org.typelevel" %% "cats-free" % "2.13.0",
        "org.typelevel" %% "cats-mtl" % "1.6.0",
        "org.typelevel" %% "squants" % "1.8.3",
        "org.tpolecat" %% "doobie-core" % "1.0.0-RC11",
        "org.tpolecat" %% "doobie-free" % "1.0.0-RC11",
        "org.tpolecat" %% "doobie-h2" % "1.0.0-RC11",
        "org.tpolecat" %% "typename" % "1.1.0",
      ),
    )
    .dependsOn(atmApplication % "compile->compile;test->test")
    .dependsOn(testSupport % "compile->test")

lazy val testSupport =
  (project in file("modules/test/test-support"))
    .configure(withBaseSettings)
    .settings(
      name := "test-support",
      libraryDependencies ++= Seq(
        "com.47deg" %% "scalacheck-toolbox-datetime" % "0.7.0",
        "io.github.iltotore" %% "iron" % "3.3.0",
        "org.scalacheck" %% "scalacheck" % "1.19.0",
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-collections-core" % "0.9.10",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
        "org.typelevel" %% "cats-effect-std" % "3.6.3",
        "org.typelevel" %% "cats-mtl" % "1.6.0",
      ),
    )

lazy val appLauncher =
  (project in file("modules/atm/app-launcher"))
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .configure(withBaseSettings)
    .settings(
      name := "app-launcher",
      libraryDependencies ++= Seq(
        "io.github.iltotore" %% "iron" % "3.3.0",
        "io.github.iltotore" %% "iron-decline" % "3.3.0",
        "com.lmax" % "disruptor" % "3.4.4" % Runtime,
        "com.monovore" %% "decline" % "2.6.0",
        "com.monovore" %% "decline-effect" % "2.6.0",
        "org.apache.logging.log4j" % "log4j-core" % "2.25.3" % Runtime,
        "org.apache.logging.log4j" % "log4j-layout-template-json" % "2.25.3" % Runtime,
        "org.apache.logging.log4j" % "log4j-slf4j2-impl" % "2.25.3" % Runtime,
        "org.typelevel" %% "cats-core" % "2.13.0",
        "org.typelevel" %% "cats-effect" % "3.6.3",
        "org.typelevel" %% "cats-effect-kernel" % "3.6.3",
        "org.typelevel" %% "cats-effect-std" % "3.6.3",
        "org.typelevel" %% "log4cats-core" % "2.7.1",
        "org.typelevel" %% "log4cats-slf4j" % "2.7.1",
        "org.typelevel" %% "squants" % "1.8.3",
      ),
      Compile / mainClass := Some("es.eriktorr.AppLauncher"),
      Universal / javaOptions ++= Seq(
        "-Djava.library.path=./lib",
        "-Xmx2G",
      ),
    )
    .dependsOn(atmInfrastructure)

lazy val root =
  (project in file("."))
    .aggregate(
      cashDomain,
      atmDomain,
      atmApplication,
      atmInfrastructure,
      testSupport,
      appLauncher,
    )
    .configure(withBaseSettings)
    .settings(
      name := "atm-machine",
      publish := {},
      publishLocal := {},
    )
