lazy val commonSettings = Seq(
  organization := "com.mwt",
  version := "1.0.0",
  scalaVersion := "2.12.3",
  description := "Follower Maze Protocol Server"
)

lazy val mazeserver = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    assemblyJarName in assembly := "server.jar"
  ).
  settings(
   mainClass in assembly := Some("com.mwt.maze.server"),
   ).
  settings(
    libraryDependencies ++= Seq(
	    "com.typesafe.akka" %% "akka-actor" % "2.5.9",
      "com.typesafe.akka" %% "akka-testkit" % "2.5.9" % Test,
      "org.scalactic" %% "scalactic" % "3.0.4",
      "org.scalatest" %% "scalatest" % "3.0.4" % "test",
    )
  )
