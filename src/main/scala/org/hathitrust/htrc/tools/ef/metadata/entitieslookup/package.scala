package org.hathitrust.htrc.tools.ef.metadata

import play.api.libs.json.{Json, OWrites, Reads}

package object entitieslookup {

  implicit val entityReads: Reads[RawEntity] = Json.reads[RawEntity]
  implicit val entityResultReads: Reads[EntityResult] = Json.reads[EntityResult]
  implicit val entityResultWrites: OWrites[EntityResult] = Json.writes[EntityResult]

  implicit val tokenReads: Reads[Token] = Json.reads[Token]

}
