package org.hathitrust.htrc.tools.ef.metadata.entitieslookup.utils.dispatch.response.as

import org.asynchttpclient.Response
import play.api.libs.json.JsValue

object Json extends (Response => JsValue) {
  def apply(r: Response): JsValue = play.api.libs.json.Json.parse(r.getResponseBodyAsStream)
}
