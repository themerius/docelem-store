package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }
import scala.concurrent.duration._

// For the ask pattern
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await

import java.nio.file.{ Files, Paths }
import scala.collection.JavaConverters._

import com.typesafe.config.ConfigFactory

import eu.themerius.docelemstore.utils.Stats.time

object DocElemStore extends App {

  // Workaround if no external config file is set
  if (System.getProperty("config.file") == "null") {
    System.clearProperty("config.file")
  }

  if (args.size == 4) {
    if(args(0).contains("scan-xmi")) {
      AccumuloXmiScanner.setDestination(args(3))
      AccumuloXmiScanner.scanXMI(args(1), args(2))
    } else {
      println("wrong arguments. Try something like 'scan-xmi header/PMID:1 header/PMID:1000000 jms.queue.XCAS2Lucene'")
      System.exit(1)
    }
  }

  // Load config
  val conf = ConfigFactory.load

  // Start Akka system
  val system = ActorSystem("docelem-store")

  // Start one Gate actor (you can start 1..n)
  val number = conf.getInt("docelem-store.gate.number") + 1
  for (i <- 1 until number) {
    val gate = system.actorOf(Props[Gate].withMailbox("akka.actor.blocking-mailbox"), s"gate-$i")
    if (conf.getBoolean("docelem-store.fillExamples")) {
      fillExamples(gate)
    }
    println(s"Starting gate-$i")
  }

  //AccumuloXmiScanner.setDestination(conf.getString("docelem-store.broker.queue"))
  //AccumuloXmiScanner.scanXMI("")

  def fillExamples(gate: ActorRef) = {

    val uri = getClass.getResource("/example.dlogs").toURI
    val path = if (uri.getScheme().equals("jar")) {
      val fs = initFileSystem(uri)
      fs.getPath("/example.dlogs")
    } else {
      Paths.get(uri)
    }

    val walk = Files.walk(path, 1)
    val it = walk.iterator
    while (it.hasNext) {
      val filePath = it.next
      if (filePath.toString.endsWith("dlog")) {
        val reader = Files.newBufferedReader(filePath)
        val model = new SimpleWalModel
        model.reader = reader
        gate ! CustomModel("wal", model)
      }
    }

  }

  def initFileSystem(uri: java.net.URI) = {

    val env = new java.util.HashMap[String, String]();
    env.put("create", "true");
    java.nio.file.FileSystems.newFileSystem(uri, env);

  }

}
