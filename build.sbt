name := "luxe"
organization := "io.github.luxe-app"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "io.github.lux-app.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "io.github.lux-app.binders._"


imageNames in docker := Seq(
  ImageName(s"luxe-app/lux:${git.gitHeadCommit.value.get}"),
  ImageName(s"luxe-app/lux:latest")
)

/*
dockerfile in docker := {
  val artifact = (assemblyOutputPath in assembly).value
  val artifactTargetPath = "/app/server.jar"
  new Dockerfile {
    from("openjdk:8-jre-alpine")
    maintainer("Jem Mawson", "jem.mawson@gmail.com")
    add(artifact, artifactTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}*/
