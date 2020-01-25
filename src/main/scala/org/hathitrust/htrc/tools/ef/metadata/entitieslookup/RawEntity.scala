package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

object EntityTypes {
  val WorldCat = 1
  val Viaf = 2
  val Loc = 3
}

final case class RawEntity(`type`: Int, label: String, rdfType: Option[String], queryType: Option[String])