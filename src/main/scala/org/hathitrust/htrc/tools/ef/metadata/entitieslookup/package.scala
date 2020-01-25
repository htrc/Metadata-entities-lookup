package org.hathitrust.htrc.tools.ef.metadata

import play.api.libs.json.{Json, OWrites, Reads}

package object entitieslookup {

  implicit val entityReads: Reads[RawEntity] = Json.reads[RawEntity]
  implicit val entityResultWrites: OWrites[EntityResult] = Json.writes[EntityResult]

}
