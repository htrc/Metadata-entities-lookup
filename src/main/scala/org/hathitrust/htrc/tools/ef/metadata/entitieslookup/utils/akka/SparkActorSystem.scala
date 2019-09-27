package org.hathitrust.htrc.tools.ef.metadata.entitieslookup.utils.akka

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.hathitrust.htrc.tools.ef.metadata.entitieslookup.Helper.logger
import scala.concurrent.duration._
import scala.concurrent.Await

object SparkActorSystem {
  logger.info("Initializing SparkActorSystem...")
  lazy val actorSystem: ActorSystem = ActorSystem("SparkActorSystem")
  lazy val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

  sys.addShutdownHook {
    println("SparkActorSystem shutdown hook invoked")
    actorSystem.terminate()
    Await.ready(actorSystem.whenTerminated, 5 seconds)
  }
}
