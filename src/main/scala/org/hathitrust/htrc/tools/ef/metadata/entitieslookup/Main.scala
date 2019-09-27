package org.hathitrust.htrc.tools.ef.metadata.entitieslookup

import java.util.concurrent.Executors

import com.gilt.gfc.time.Timer
import org.apache.spark.sql.types.{IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.{Encoder, Encoders, SparkSession}
import org.apache.spark.{SparkConf, SparkContext}
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.Helper._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.reflectiveCalls

object Main {
  val appName: String = "bibframe-entities"

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args)
    val inputPath = conf.inputPath().toString
    val outputPath = conf.outputPath().toString
    val numPartitions = conf.numPartitions.toOption

    conf.outputPath().mkdirs()

    // set up logging destination
    conf.sparkLog.foreach(System.setProperty("spark.logFile", _))
    System.setProperty("logLevel", conf.logLevel().toUpperCase)

    // set up Spark context
    val sparkConf = new SparkConf()
    sparkConf.setAppName(appName)
    sparkConf.setIfMissing("spark.master", "local[*]")

    val spark = SparkSession.builder()
      .config(sparkConf)
      .getOrCreate()

    implicit val sc: SparkContext = spark.sparkContext
    import spark.implicits._

    logger.info("Starting...")

    // record start time
    val t0 = System.nanoTime()

    val entitySchema = List(
      StructField("type", IntegerType, nullable = false),
      StructField("label", StringType, nullable = false),
      StructField("rdftype", StringType, nullable = true),
      StructField("querytype", StringType, nullable = true)
    )

    val csvLoader = spark.read.format("csv")
      .option("header", "false")
      .schema(StructType(entitySchema))

    implicit val entityEncoder: Encoder[Entity] = Encoders.product[Entity]
    val entitiesDS = csvLoader.load(inputPath).as[Entity]

    val entityIdsDS = entitiesDS
      .mapPartitions { entities =>
        implicit val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
        val resolvedEntities = Future.traverse(entities)(e => lookupEntity(e).map(e -> _))

        Await.result(resolvedEntities, Duration.Inf)
      }
      .map {
        case (e, Right(id)) => e.label -> id
        case (e, Left(err)) => e.label -> Some(s"ERROR: ${err.getMessage}")
      }

    entityIdsDS.write.format("csv")
      .option("header", "false")
      .save(outputPath + "/mapping")

//    if (xmlParseErrorAccumulator.nonEmpty || extractEntitiesErrorAccumulator.nonEmpty)
//      logger.info("Writing error report(s)...")
//
//    // save any errors to the output folder
//    if (xmlParseErrorAccumulator.nonEmpty)
//      xmlParseErrorAccumulator.saveErrors(new Path(outputPath, "xmlparse_errors.txt"), _)
//
//    if (extractEntitiesErrorAccumulator.nonEmpty)
//      extractEntitiesErrorAccumulator.saveErrors(new Path(outputPath, "extractentities_errors.txt"), _.toString)

    // record elapsed time and report it
    val t1 = System.nanoTime()
    val elapsed = t1 - t0

    logger.info(f"All done in ${Timer.pretty(elapsed)}")

    System.exit(0)
  }

}
