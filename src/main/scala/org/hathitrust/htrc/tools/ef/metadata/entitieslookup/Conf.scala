package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

import java.io.File

import org.rogach.scallop.{Scallop, ScallopConf, ScallopHelpFormatter, ScallopOption, SimpleOption}

class Conf(args: Seq[String]) extends ScallopConf(args) {
  appendDefaultToDescription = true
  helpFormatter = new ScallopHelpFormatter {
    override def getOptionsHelp(s: Scallop): String = {
      super.getOptionsHelp(s.copy(opts = s.opts.map {
        case opt: SimpleOption if !opt.required =>
          opt.copy(descr = "(Optional) " + opt.descr)
        case other => other
      }))
    }
  }

  private val (appTitle, appVersion, appVendor) = {
    val p = getClass.getPackage
    val nameOpt = Option(p).flatMap(p => Option(p.getImplementationTitle))
    val versionOpt = Option(p).flatMap(p => Option(p.getImplementationVersion))
    val vendorOpt = Option(p).flatMap(p => Option(p.getImplementationVendor))
    (nameOpt, versionOpt, vendorOpt)
  }

  version(appTitle.flatMap(
    name => appVersion.flatMap(
      version => appVendor.map(
        vendor => s"$name $version\n$vendor"))).getOrElse(Main.appName))

  val parallelism: ScallopOption[Int] = opt[Int]("parallelism",
    descr = "The number of parallel connections to make",
    required = false,
    default = Some(Runtime.getRuntime.availableProcessors()),
    argName = "N",
    validate = 0 <
  )

  val outputPath: ScallopOption[File] = opt[File]("output",
    descr = "Write the output to FILE",
    required = true,
    argName = "FILE"
  )

  val inputPath: ScallopOption[File] = trailArg[File]("input",
    descr = "The path to the folder containing the entities to look up"
  )

  validateFileIsFile(inputPath)
  verify()
}
