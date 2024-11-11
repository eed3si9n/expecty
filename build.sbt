val scala212 = "2.12.20"
val scala213 = "2.13.15"
val scala3 = "3.3.4"
val scalaFull = Seq(scala213, scala212, scala3)
val verify = "1.0.0"

ThisBuild / scalaVersion := scala213
Global / semanticdbEnabled := true
Global / semanticdbVersion := "4.11.0"

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
    libraryDependencies += "com.eed3si9n.verify" %%% "verify" % verify % Test,
    testFrameworks += new TestFramework("verify.runner.Framework"),
  )
  .jvmPlatform(
    scalaVersions = scalaFull,
    settings = Seq(
      libraryDependencies += "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
      Test / unmanagedSourceDirectories ++= {
        Seq((LocalRootProject / baseDirectory).value / "jvm" / "src" / "test" / "scala")
      },
    )
  )
  .jsPlatform(scalaVersions = scalaFull)
  .nativePlatform(scalaVersions = scalaFull)

lazy val expecty3 = expecty
  .jvm(scala3)
  .disablePlugins(ScalafmtPlugin)

lazy val expectyJS3 = expecty
  .js(scala3)
  .disablePlugins(ScalafmtPlugin)
