package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

import play.api.libs.json.Json

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import scala.io.Source
import scala.util.Using


object EntityCache {
  def fromFile(file: File): EntityCache[RawEntity, Option[String]] = {
    val cache = Using.resource(Source.fromFile(file)) { entitiesSource =>
      val entityResults = entitiesSource.getLines().map(line => Json.parse(line).as[EntityResult])
      entityResults.map(er => er.entity -> er.value).toMap
    }
    new EntityCache(cache)
  }

  val empty = new EntityCache[RawEntity, Option[String]](Map.empty)
}

class EntityCache[K, V](protected val cache: Map[K, V]) {
  protected val _cacheHits = new AtomicLong(0)
  protected val _cacheMisses = new AtomicLong(0)

  def get(key: K): Option[V] = {
    cache.get(key) match {
      case value@Some(_) =>
        _cacheHits.incrementAndGet()
        value
      case None =>
        _cacheMisses.incrementAndGet()
        None
    }
  }

  def cacheHits: Long = _cacheHits.longValue()
  def cacheMisses: Long = _cacheMisses.longValue()
  def size: Int = cache.size
}
