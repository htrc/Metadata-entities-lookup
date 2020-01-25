package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import dispatch._
import dispatch.retry.Success._
import io.netty.util.{Timer => NettyTimer}
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.EntityTypes._
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.utils.dispatch.response
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext

object Helper {
  val logger: Logger = LoggerFactory.getLogger(Main.appName)

  private val VIAF_SEARCH_URL: String = "http://www.viaf.org/viaf/AutoSuggest"
  private val LOC_SEARCH_URL: String = "http://id.loc.gov/search/"


  def lookupWorldCat(worldCatId: String)
                    (implicit http: Http, ec: ExecutionContext, timer: NettyTimer): Future[Either[Throwable, Option[String]]] = {
    val req = url(s"$worldCatId.jsonld")

    retry.Backoff(max = 2) { () =>
      if (logger.isTraceEnabled)
        logger.trace(req.toRequest.getUri.toString)

      http(req OK response.as.Json)
        .either
        .right
        .map { json =>
          (json \ "@graph").asOpt[List[JsObject]] match {
            case Some(graphNodes) =>
              graphNodes
                .find(o => (o \ "@id").as[String] == worldCatId)
                .flatMap(o => (o \ "exampleOfWork").asOpt[String])

            case None => None
          }
        }
    }
  }

  def lookupViaf(label: String, queryType: String)
                (implicit http: Http, ec: ExecutionContext, mat: Materializer, timer: NettyTimer): Future[Either[Throwable, Option[String]]] = {
    val cleanedLabel = label
      .trim
      .replaceAllLiterally(""""""", """'""")  // replace double quotation marks with single quotation marks
      .replaceAllLiterally("""\""", """\\""") // replace \ with \\
      .replaceAll("""[.,-]+$""", "")

    val variantLabels = cleanedLabel #::
      cleanedLabel.replaceAll(""" \([^)]+\)""", "").replaceAllLiterally("?", "") #::
      cleanedLabel.replaceAll(""" \([^)]+\)""", "").replaceAllLiterally("?", "").replaceAll("""-\d{4}$""", "") #::
      cleanedLabel.replaceAll(""" \([^)]+\)""", "").replaceAllLiterally("?", "").replaceAll(""", (?:\d{4}-)?\d{4}$""", "") #::
      cleanedLabel.replaceAll(""" \([^)]+\)""", "").replaceAll("""-\d{4}$""", "") #::
      cleanedLabel.replaceAll(""" \([^)]+\)""", "").replaceAll(""", (?:\d{4}-)?\d{4}$""", "") #::
      cleanedLabel.replaceAll(""" \([^)]+\)""", "") #::
      cleanedLabel.replaceAllLiterally("?", "").replaceAll("""-\d{4}$""", "") #::
      cleanedLabel.replaceAllLiterally("?", "").replaceAll(""", (?:\d{4}-)?\d{4}$""", "") #::
      cleanedLabel.replaceAll("""-\d{4}$""", "") #::
      cleanedLabel.replaceAll(""", (?:\d{4}-)?\d{4}$""", "") #::
      cleanedLabel.replaceAllLiterally("?", "") #::
      Stream.empty[String]

    val svc = url(VIAF_SEARCH_URL)

    Source(variantLabels)
      .mapAsync(1) { query =>
        val req = svc.addQueryParameter("query", query)

        retry.Backoff(max = 2) { () =>
          if (logger.isTraceEnabled)
            logger.trace(req.toRequest.getUri.toString)

          http(req OK response.as.Json)
            .either
            .right
            .map { json =>
              val results = (json \ "result").asOpt[List[JsObject]]
              results
                .flatMap(_.find(o => (o \ "nametype").as[String] == queryType && (o \ "viafid").isDefined))
                .map(o => (o \ "viafid").as[String])
            }
        }
      }
      .collect {
        case r @ Right(Some(_)) => r
        case l @ Left(_) => l
      }
      .take(1)
      .runWith(Sink.headOption)
      .map {
        case Some(result) => result
        case None => Right(None)
      }
  }

  def lookupLoc(label: String, rdfType: String, queryType: String)
               (implicit http: Http, ec: ExecutionContext, mat: Materializer, timer: NettyTimer): Future[Either[Throwable, Option[String]]] = {
    val cleanedLabel = label
      .trim
      .replaceAllLiterally(""""""", """\"""")
      .replaceAll("""[.,-]+$""", "")

    val svc = url(LOC_SEARCH_URL) <<? List(
      "q" -> s"""aLabel:"$cleanedLabel"""",
      "q" -> s"rdftype:$rdfType",
      "format" -> "atom"
    )

    val csValues = List(
      s"cs:http://id.loc.gov/authorities/$queryType",
      "cs:http://id.loc.gov/vocabulary/geographicAreas",
    )

    Source(csValues)
      .mapAsync(parallelism = 1) { cs =>
        val req = svc.addQueryParameter("q", cs)
        retry.Backoff(max = 2) { () =>
          if (logger.isTraceEnabled)
            logger.trace(req.toRequest.getUri.toString)

          http(req OK dispatch.as.xml.Elem)
            .either
            .right
            .map { xml =>
              (xml \ "entry")
                .headOption
                .map(entry => (entry \ "id").text.replaceFirst("""^info:lc""", "http://id.loc.gov"))
            }
        }
      }
      .collect {
        case r @ Right(Some(_)) => r
        case l @ Left(_) => l
      }
      .take(1)
      .runWith(Sink.headOption)
      .map {
        case Some(result) => result
        case None => Right(None)
      }
  }

  def lookupEntity(entity: RawEntity)
                  (implicit http: Http, ec: ExecutionContext, mat: Materializer, timer: NettyTimer): Future[Either[Throwable, Option[String]]] = {
    logger.debug("{}: Looking up {}", Thread.currentThread().getId, entity)

    entity match {
      case RawEntity(WorldCat, worldCatId, _, _) => lookupWorldCat(worldCatId)
      case RawEntity(Viaf, label, _, Some(queryType)) => lookupViaf(label, queryType)
      case RawEntity(Loc, label, Some(rdfType), Some(queryType)) => lookupLoc(label, rdfType, queryType)
      case _ => Future.successful(Left(new IllegalArgumentException("Invalid entity")))
    }
  }

}