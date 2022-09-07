val scala211 = "2.11.12"
val scala212 = "2.12.15"
val scala213 = "2.13.8"
val scala3 = "3.2.0"
ThisBuild / scalaVersion := scala213
Global / semanticdbEnabled := true
Global / semanticdbVersion := "4.5.0"

lazy val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

lazy val root = (project in file("."))
  .aggregate(expecty.projectRefs: _*)
  .settings(
    name := "Expecty Root",
    Compile / sources := Nil,
    Test / sources := Nil,
    publish / skip := true,
    commands += Command.command("release") { state =>
      "clean" ::
        "publishSigned" ::
        state
    },
    sonatypeProfileName := "com.eed3si9n",
  )

lazy val expecty = (projectMatrix in file("."))
  .settings(
    name := "Expecty",
    scalacOptions ++= Seq("-Yrangepos", "-feature", "-deprecation"),
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _            => Nil
    }),
    libraryDependencies += verify % Test,
    testFrameworks += new TestFramework("verify.runner.Framework"),
  )
  .jvmPlatform(
    scalaVersions = Seq(scala213, scala212, scala211, scala3),
    settings = Seq(
      libraryDependencies += "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
      Test / unmanagedSourceDirectories ++= {
        Seq((LocalRootProject / baseDirectory).value / "jvm" / "src" / "test" / "scala")
      },
    )
  )
  .jsPlatform(scalaVersions = Seq(scala213, scala212, scala211, scala3))
  .nativePlatform(scalaVersions = Seq(scala211, scala212, scala213))

lazy val expecty3 = expecty
  .jvm(scala3)
  .disablePlugins(ScalafmtPlugin)

lazy val expectyJS3 = expecty
  .js(scala3)
  .disablePlugins(ScalafmtPlugin)
