package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, JsonFraming}
import akka.util.ByteString
import com.gilt.gfc.time.Timer
import dispatch._
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.EntityTypes._
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.Helper.{authOclc, lookupEntity}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import io.netty.util.{HashedWheelTimer, Timer => NettyTimer}

import scala.concurrent.ExecutionContext
import scala.io.Source
import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try, Using}

object Main {
  val appName: String = "entities-lookup"
  val logger: Logger = LoggerFactory.getLogger(appName)

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args.toIndexedSeq)
    val inputPath = conf.inputPath().toString
    val outputPath = conf.outputPath()
    val parallelism = conf.parallelism()
    val entityCache = conf.entityCacheFile.map(EntityCache.fromFile).getOrElse(EntityCache.empty)

    logger.info(f"Loaded ${entityCache.size}%,d resolved entities into cache")

    Option(outputPath.getParentFile).foreach(_.mkdirs())

    implicit val system: ActorSystem = ActorSystem()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val http: Http = Http.withConfiguration { _
      .setFollowRedirect(true)
      .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:72.0) Gecko/20100101 Firefox/72.0")
    }
    implicit val timer: NettyTimer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 8192)

    logger.info("Starting...")

    // record start time
    val t0 = System.nanoTime()

    for (tokenOpt <- authOclc()) {
      tokenOpt.foreach(token => logger.info(s"Authenticated to OCLC - token expires in ${Timer.pretty(token.expires_in*(1e9.toLong))}"))
      val done = FileIO.fromPath(Paths.get(inputPath))
        .via(JsonFraming.objectScanner(Int.MaxValue))
        .map(_.utf8String)
        .map { s =>
          val entityTry = Try(Json.parse(s).as[RawEntity])
          entityTry match {
            case Failure(e) => logger.error(s, e)
            case _ =>
          }
          entityTry
        }
        .collect {
          case Success(entity) => entity
        }
        .filterNot {
          case RawEntity(WorldCat, url, _, _) if url.startsWith("_") => true
          case _ => false
        }
        .mapAsyncUnordered(parallelism) { entity =>
          entityCache.get(entity)
            .map(entity -> Right(_))
            .map(Future.successful)
            .getOrElse(lookupEntity(entity, tokenOpt).map(entity -> _))
        }
        .map {
          case (RawEntity(t, l, r, q), Right(value)) => EntityResult(t, l, r, q, value = value)
          case (RawEntity(t, l, r, q), Left(e)) => EntityResult(t, l, r, q, error = Some(e.getMessage))
        }
        .map(er => ByteString(Json.toJsObject(er).toString() + "\n"))
        .runWith(FileIO.toPath(outputPath.toPath))

      done
        .andThen {
          case _ => http.shutdown()
        }
        .andThen {
          case _ => system.terminate()
        }

      system.registerOnTermination {
        // record elapsed time and report it
        val t1 = System.nanoTime()
        val elapsed = t1 - t0

        logger.info(f"All done in ${Timer.pretty(elapsed)}")
        val totalCacheAccesses = entityCache.cacheHits + entityCache.cacheMisses
        val percHits = entityCache.cacheHits * 100 / totalCacheAccesses
        val percMisses = 100 - percHits
        logger.info(f"Cache stats - hits: ${entityCache.cacheHits}%,d ($percHits%%), misses: ${entityCache.cacheMisses}%,d ($percMisses%%)")
        System.exit(0)
      }
    }
  }

}
