package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

import java.nio.file.Paths
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.alpakka.csv.scaladsl.CsvParsing
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import com.gilt.gfc.time.Timer
import dispatch._
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.EntityTypes._
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.Helper.lookupEntity
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import io.netty.util.{HashedWheelTimer, Timer => NettyTimer}

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

object Main {
  val appName: String = "entities-lookup"
  val logger: Logger = LoggerFactory.getLogger(appName)

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)
    val inputPath = conf.inputPath().toString
    val outputPath = conf.outputPath()
    val parallelism = conf.parallelism()

    outputPath.getParentFile.mkdirs()

    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext: ExecutionContext = system.dispatcher
    implicit val http: Http = Http.withConfiguration { _
      .setFollowRedirect(true)
    }
    implicit val timer: NettyTimer = new HashedWheelTimer()

    logger.info("Starting...")

    // record start time
    val t0 = System.nanoTime()

    val done = FileIO.fromPath(Paths.get(inputPath))
      .via(CsvParsing.lineScanner())
      .map(_.map(_.utf8String))
      .map(Entity(_))
      .filterNot {
        case Entity(WorldCat, url, _, _) if url.startsWith("_") => true
        case _ => false
      }
      .mapAsyncUnordered(parallelism)(entity => lookupEntity(entity).map(entity -> _))
      .map {
        case (Entity(t, l, r, _), Right(value)) => EntityResult(t, l, r, value = value)
        case (Entity(t, l, r, _), Left(e)) => EntityResult(t, l, r, error = Some(e.getMessage))
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
    }
  }

}
