showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.13.10",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions"
  ),
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "HTRC Nexus Repository" at "https://nexus.htrc.illinois.edu/repository/maven-public"
  ),
  externalResolvers := Resolver.combineDefaultResolvers(resolvers.value.toVector, mavenCentral = false),
  Compile / packageBin / packageOptions += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  )
)

lazy val ammoniteSettings = Seq(
  libraryDependencies +=
    {
      val version = scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case _ ⇒  "2.5.5"
      }
      "com.lihaoyi" % "ammonite" % version % Test cross CrossVersion.full
    },
  Test / sourceGenerators += Def.task {
    val file = (Test / sourceManaged).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
    Seq(file)
  }.taskValue,
  connectInput := true,
  outputStrategy := Some(StdoutOutput)
)

lazy val `entities-lookup` = (project in file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt, JavaAppPackaging)
  .settings(commonSettings)
  .settings(ammoniteSettings)
  .settings(
    name := "entities-lookup",
    description := "Used to perform lookup (resolve) entities via external sources like VIAF, LOC, and WorldCat",
    licenses += "Apache2" -> url("http://www.apache.org/licenses/LICENSE-2.0"),
    libraryDependencies ++= Seq(
      "org.rogach"                    %% "scallop"                  % "4.1.0",
      "org.hathitrust.htrc"           %% "scala-utils"              % "2.13",
      "org.dispatchhttp"              %% "dispatch-core"            % "1.2.0",
      "com.typesafe.play"             %% "play-json"                % "2.9.3",
      "com.typesafe.akka"             %% "akka-stream"              % "2.7.0",
      "com.lightbend.akka"            %% "akka-stream-alpakka-json-streaming" % "5.0.0",
      "com.github.nscala-time"        %% "nscala-time"              % "2.32.0",
      "ch.qos.logback"                %  "logback-classic"          % "1.4.5",
      "org.scalacheck"                %% "scalacheck"               % "1.17.0"      % Test,
      "org.scalatest"                 %% "scalatest"                % "3.2.15"      % Test
    )
  )
