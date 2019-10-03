package org.hathitrust.htrc.tools.ef.metadata

import play.api.libs.json.{Json, OWrites}

package object entitieslookup {

  implicit val entityResultWrites: OWrites[EntityResult] = Json.writes[EntityResult]

}
