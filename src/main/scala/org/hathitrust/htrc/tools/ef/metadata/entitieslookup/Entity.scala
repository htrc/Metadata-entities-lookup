package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

object EntityTypes {
  val WorldCat = 1
  val Viaf = 2
  val Loc = 3
}

object Entity {
  def apply(row: List[String]): Entity = row match {
    case sType :: sLabel :: sRdfType :: sQueryType :: Nil =>
      Entity(sType.toInt, sLabel, Some(sRdfType).filter(_.nonEmpty), Some(sQueryType).filter(_.nonEmpty))
    case _ => throw new IllegalArgumentException("row")
  }
}

final case class Entity(`type`: Int, label: String, rdfType: Option[String], queryType: Option[String])