name := "0rora"
organization := "io.github.0rora"
maintainer := "keybase.io/jem"

lazy val root = (project in file(".")).enablePlugins(PlayScala, DockerPlugin, GitVersioning)

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  guice,  
  
  // stellar
  "io.github.synesso" %% "scala-stellar-sdk" % "0.6.0",
  
  // sources
  "com.nrinaudo" %% "kantan.csv-generic" % "0.4.0",
  
  // database
  evolutions, jdbc,
  "org.postgresql" % "postgresql" % "42.2.5",
  "org.scalikejdbc" %% "scalikejdbc" % "3.3.0",
  "org.scalikejdbc" %% "scalikejdbc-config" % "3.3.0",
  "org.scalikejdbc" %% "scalikejdbc-play-initializer" % "2.6.0-scalikejdbc-3.3",

  // js & css
  "org.webjars.npm" % "bulma" % "0.7.2",
  "org.webjars" % "font-awesome" % "5.6.3",

  // auth
  "org.pac4j" %% "play-pac4j" % "7.0.1",
  "org.pac4j" % "pac4j-http" % "3.6.1",
  "org.pac4j" % "pac4j-sql" % "3.6.1",
  "org.apache.shiro" % "shiro-core" % "1.4.0",

  // test
  specs2 % Test,
  "org.specs2" %% "specs2-scalacheck" % "4.3.6" % Test,
  "org.scalikejdbc" %% "scalikejdbc-test" % "3.3.0" % Test,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.21" % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.0" % Test,
  "com.whisk" %% "docker-testkit-specs2" % "0.9.8" % Test,
  "com.whisk" %% "docker-testkit-impl-docker-java" % "0.9.8" % Test,
  "com.h2database" % "h2" % "1.4.192" % Test,
)

scalacOptions ++= Seq(
  "-Yrangepos",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-language:postfixOps",
  "-encoding",
  "UTF-8",
  "-target:jvm-1.8")

coverageExcludedPackages := "controllers\\.javascript;router;views.html;controllers\\.Reverse.*"
