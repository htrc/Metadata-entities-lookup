package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

object EntityTypes {
  val WorldCat = 1
  val Viaf = 2
  val Loc = 3
}

case class Entity(`type`: Int, label: String, rdfType: String, queryType: String)