import scala.sys.process._

enablePlugins(GuardrailPlugin)

val Http4sVersion     = "0.21.0-M6"
val CirceVersion      = "0.12.3"
val Specs2Version     = "4.8.1"
val LogbackVersion    = "1.2.3"
val pureconfigVersion = "0.12.1"
val hydraJavaVersion  = "1.0.0"

resolvers ++= Seq(
  Resolver.jcenterRepo,
  Resolver.mavenLocal
)

lazy val root = (project in file("."))
  .settings(
    organization := "com.ukonnra",
    name := "chessur",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.13.1",
    libraryDependencies ++= Seq(
      "org.http4s"            %% "http4s-blaze-server"      % Http4sVersion,
      "org.http4s"            %% "http4s-blaze-client"      % Http4sVersion,
      "org.http4s"            %% "http4s-circe"             % Http4sVersion,
      "org.http4s"            %% "http4s-dsl"               % Http4sVersion,
      "io.circe"              %% "circe-generic"            % CirceVersion,
      "org.specs2"            %% "specs2-core"              % Specs2Version % "test",
      "ch.qos.logback"        % "logback-classic"           % LogbackVersion,
      "com.github.pureconfig" %% "pureconfig"               % pureconfigVersion,
      "com.github.ory"        % "hydra-client-resttemplate" % hydraJavaVersion
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.10.3"),
    addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1"),
    addCompilerPlugin(scalafixSemanticdb)
  )

scalacOptions ++= Seq(
  "-deprecation",
  "-language:postfixOps",
  "-Yrangepos",
  "-Ywarn-unused",
  "-target:jvm-11"
)
addCommandAlias("fix", "all compile:scalafix test:scalafix")
addCommandAlias(
  "fixCheck",
  "; compile:scalafix --check ; test:scalafix --check"
)

guardrailTasks in Compile := {
  if (!file("target/api.swagger.json").exists()) {
    url(
      "https://raw.githubusercontent.com/ory/hydra/master/docs/api.swagger.json"
    ) #> file("target/api.swagger.json") !
  }
  List(
    ScalaClient(
      file("target/api.swagger.json"),
      pkg = "com.ukonnra.chessur",
      framework = "http4s",
      tracing = true
    )
  )
}
unmanagedSourceDirectories in Compile += (sourceManaged in Compile).value
