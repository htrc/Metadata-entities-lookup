showCurrentGitBranch

git.useGitDescribe := true

lazy val commonSettings = Seq(
  organization := "org.hathitrust.htrc",
  organizationName := "HathiTrust Research Center",
  organizationHomepage := Some(url("https://www.hathitrust.org/htrc")),
  scalaVersion := "2.12.10",
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-language:postfixOps",
    "-language:implicitConversions"
  ),
  externalResolvers := Seq(
    Resolver.defaultLocal,
    Resolver.mavenLocal,
    "HTRC Nexus Repository" at "https://nexus.htrc.illinois.edu/content/groups/public",
  ),
  packageOptions in (Compile, packageBin) += Package.ManifestAttributes(
    ("Git-Sha", git.gitHeadCommit.value.getOrElse("N/A")),
    ("Git-Branch", git.gitCurrentBranch.value),
    ("Git-Version", git.gitDescribedVersion.value.getOrElse("N/A")),
    ("Git-Dirty", git.gitUncommittedChanges.value.toString),
    ("Build-Date", new java.util.Date().toString)
  ),
//  wartremoverWarnings ++= Warts.unsafe.diff(Seq(
//    Wart.DefaultArguments,
//    Wart.NonUnitStatements,
//    Wart.Any
//  ))
)

lazy val ammoniteSettings = Seq(
  libraryDependencies +=
    {
      val version = scalaBinaryVersion.value match {
        case "2.10" => "1.0.3"
        case _ ⇒ "1.7.4"
      }
      "com.lihaoyi" % "ammonite" % version % Test cross CrossVersion.full
    },
  sourceGenerators in Test += Def.task {
    val file = (sourceManaged in Test).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.Main.main(args) }""")
    Seq(file)
  }.taskValue,
  fork in (Test, run) := false
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
      "org.rogach"                    %% "scallop"                  % "3.3.1",
      "org.hathitrust.htrc"           %% "scala-utils"              % "2.8-3-g437cdc0",
      "org.dispatchhttp"              %% "dispatch-core"            % "1.1.0",
      "com.typesafe.play"             %% "play-json"                % "2.7.3",
      "com.typesafe.akka"             %% "akka-stream"              % "2.5.25",
      "com.lightbend.akka"            %% "akka-stream-alpakka-csv"  % "1.1.1",
      "com.gilt"                      %% "gfc-time"                 % "0.0.7",
      "ch.qos.logback"                %  "logback-classic"          % "1.2.3",
      "org.scalacheck"                %% "scalacheck"               % "1.14.0"      % Test,
      "org.scalatest"                 %% "scalatest"                % "3.0.8"       % Test
    )
  )
