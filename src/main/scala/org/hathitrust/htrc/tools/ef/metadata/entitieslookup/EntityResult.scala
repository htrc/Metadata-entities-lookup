package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

final case class EntityResult(`type`: Int,
                              label: String,
                              rdfType: Option[String],
                              queryType: Option[String],
                              value: Option[String] = None,
                              error: Option[String] = None)
