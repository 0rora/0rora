name := "luxe"
organization := "io.github.luxe-app"

version := "0.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  guice, evolutions, jdbc,
  "io.github.synesso" %% "scala-stellar-sdk" % "0.5.0",
  "com.nrinaudo" %% "kantan.csv-generic" % "0.4.0",
  "com.h2database" % "h2" % "1.4.192",
  "org.postgresql" % "postgresql" % "42.2.5",
  "org.scalikejdbc" %% "scalikejdbc" % "3.3.0",
  "org.scalikejdbc" %% "scalikejdbc-config" % "3.3.0",
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % "2.6.0-scalikejdbc-3.3",
  "org.webjars.npm" % "bulma" % "0.7.2",
  "org.webjars" % "font-awesome" % "5.6.3",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "io.github.lux-app.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "io.github.lux-app.binders._"
