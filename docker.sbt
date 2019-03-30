import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

dockerCommands := Seq(
  Cmd("FROM", "openjdk:11-jre-slim"),
  Cmd("LABEL", s"""MAINTAINER="${maintainer.value}""""),
  Cmd("WORKDIR", "/opt/docker"),
  Cmd("ADD", "--chown=daemon:daemon opt /opt"),
  Cmd("USER", "daemon"),
  Cmd("EXPOSE", "9000"),
  ExecCmd("ENTRYPOINT", "/opt/docker/bin/0rora")
)

dockerUsername := Some("synesso")