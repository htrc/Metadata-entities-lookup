package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

import akka.stream.scaladsl.{Sink, Source}
import dispatch._
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.EntityTypes._
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.utils.dispatch.response
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsObject

import scala.concurrent.ExecutionContext

object Helper {
  @transient lazy val logger: Logger = LoggerFactory.getLogger(Main.appName)

  private val VIAF_SEARCH_URL: String = "http://www.viaf.org/viaf/AutoSuggest"
  private val LOC_SEARCH_URL: String = "http://id.loc.gov/search/"

  val http: Http = Http.withConfiguration { _
    .setFollowRedirect(true)
  }

  sys.addShutdownHook {
    println("Helper shutdown hook invoked")
    http.shutdown()
  }

  def lookupWorldCat(worldCatId: String)(implicit ec: ExecutionContext): Future[Either[Throwable, Option[String]]] = {
    http(url(s"$worldCatId.jsonld") OK response.as.Json)
      .either
      .right
      .map { json =>
        (json \ "@graph").as[List[JsObject]]
          .find(o => (o \ "@id").as[String] == worldCatId)
          .flatMap(o => (o \ "exampleOfWork").asOpt[String])
      }
  }

  def lookupViaf(label: String, queryType: String)(implicit ec: ExecutionContext): Future[Either[Throwable, Option[String]]] = {
    import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.utils.akka.SparkActorSystem._

    val cleanedLabel = label
      .trim
      .replaceAllLiterally(""""""", """'""")  // replace double quotation marks with single quotation marks
      .replaceAllLiterally("""\""", """\\""") // replace \ with \\
      .replaceAll("""[.,-]+$""", "")

    lazy val variantLabels = cleanedLabel #::
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
        http(svc.addQueryParameter("query", query) OK response.as.Json)
          .either
          .right
          .map { json =>
              val results = (json \ "result").asOpt[List[JsObject]]
              results
                .flatMap(_.find(o => (o \ "nametype").as[String] == queryType && (o \ "viafid").isDefined))
                .map(o => (o \ "viafid").as[String])
          }
      }
      .collect {
        case r @ Right(Some(_)) => r
        case l @ Left(_) => l
      }
      .take(1)
      .runWith(Sink.headOption)(materializer)
      .map {
        case Some(result) => result
        case None => Right(None)
      }
  }

  def lookupLoc(label: String, rdfType: String, queryType: String)(implicit ec: ExecutionContext): Future[Either[Throwable, Option[String]]] = {
    val cleanedLabel = label
      .trim
      .replaceAllLiterally(""""""", """\"""")
      .replaceAll("""[.,-]+$""", "")

    val svc = url(LOC_SEARCH_URL) <<? List(
      "q" -> s"""aLabel:"$cleanedLabel"""",
      "q" -> s"cs:http://id.loc.gov/authorities/$queryType",
      "q" -> s"rdftype:$rdfType",
      "format" -> "atom"
    )

    http(svc OK dispatch.as.xml.Elem)
      .either
      .right
      .map { xml =>
          (xml \ "feed" \ "entry")
            .headOption
            .map(entry => (entry \ "id").text.replaceFirst("""^info:lc""", "http://id.loc.gov"))
      }
  }

  def lookupEntity(entity: Entity)(implicit ec: ExecutionContext): Future[Either[Throwable, Option[String]]] = entity match {
    case Entity(WorldCat, worldCatId, _, _) => lookupWorldCat(worldCatId)
    case Entity(Viaf, label, _, queryType) => lookupViaf(label, queryType)
    case Entity(Loc, label, rdfType, queryType) => lookupLoc(label, rdfType, queryType)
    case _ => throw new IllegalArgumentException("entity")
  }

}