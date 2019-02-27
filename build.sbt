name := "0rora"
organization := "io.github.0rora"
maintainer := "keybase.io/jem"

version := "0.1.1"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  guice, evolutions, jdbc, specs2 % Test,
  "io.github.synesso" %% "scala-stellar-sdk" % "0.5.2",
  "com.nrinaudo" %% "kantan.csv-generic" % "0.4.0",
  "com.h2database" % "h2" % "1.4.192",
  "org.postgresql" % "postgresql" % "42.2.5",
  "org.scalikejdbc" %% "scalikejdbc" % "3.3.0",
  "org.scalikejdbc" %% "scalikejdbc-config" % "3.3.0",
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % "2.6.0-scalikejdbc-3.3",
  "org.webjars.npm" % "bulma" % "0.7.2",
  "org.webjars" % "font-awesome" % "5.6.3",
  "org.scalikejdbc" %% "scalikejdbc-test" % "3.3.0" % Test,
  "org.specs2" %% "specs2-scalacheck" % "4.3.6" % Test
)

javaOptions in Test += "-Dconfig.file=conf/in-memory-db.conf"

coverageExcludedPackages := "controllers\\.javascript;router;views.html;controllers\\.Reverse.*"