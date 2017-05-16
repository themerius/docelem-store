package eu.themerius.docelemstore

import akka.actor.{ Actor }
import akka.event.Logging

import org.fusesource.stomp.jms._
import javax.jms._

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Range
import org.apache.hadoop.io.Text
import org.apache.accumulo.core.client.IteratorSetting
import org.apache.accumulo.core.iterators.user.IntersectingIterator

import scala.collection.JavaConverters._
import scala.xml.PrettyPrinter

import java.net.URI
import java.lang.Long
import java.util.ArrayList
import java.util.HashSet
import java.util.Collections

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import utils.Stats.time
import utils.PMIDLexicoder

import com.typesafe.config.ConfigFactory


object AccumuloDocElemStatistics {

  // TODO: create something like a document element / SDA logger
  // val sdalog = getSDALogger(sigmatics="some_unique_name", ttl=2.days)
  // sdalog.persist(SDA) / tmp(SDA, ttl) / ...

  val name = this.getClass.getSimpleName
  val date = ZonedDateTime.now.format(DateTimeFormatter.ISO_LOCAL_DATE)
  val sigmatics = s"${name}:${date}"

  println(sigmatics, "caption", "content", "en", "Accumulo document element statistics.")
  println(sigmatics, "table", "columns", "csv", "collection, docCount, elemCount, elemCountMedian, tagCount")
  println(sigmatics, "table", "element/PMID:collection", "str", "PubMed 2017")
  println(sigmatics, "table", "element/PMC:collection", "str", "PMC 2017")
  println(sigmatics, "table", "element/CTGOV:collection", "str", "ClinicalTrials.gov 2017")

  val conf = ConfigFactory.load
  val brokerUri = conf.getString("docelem-store.broker.uri")
  val brokerUsr = conf.getString("docelem-store.broker.usr")
  val brokerPwd = conf.getString("docelem-store.broker.pwd")
  val brokerQueue = conf.getString("docelem-store.broker.queue")
  val brokerBilling = conf.getString("docelem-store.broker.billing")

  val lc = new PMIDLexicoder

  // Connect to the broker
  val factory = new StompJmsConnectionFactory
  factory.setBrokerURI(brokerUri)

  val elistener = new ExceptionListener {
    override def onException(jmse: JMSException) {
      System.exit(1)
    }
  }

  val connection = factory.createConnection(brokerUsr, brokerPwd)
  connection.setExceptionListener(elistener);
  connection.setClientID(s"jms.topic.${java.util.UUID.randomUUID.toString}")
  connection.start

  var destStr = ""
  def setDestination(dest: String) = {
    this.destStr = dest
  }

  lazy val dest = new StompJmsDestination(this.destStr)
  lazy val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  lazy val prods = session.createProducer(dest)


  def collectStatistics = time("collectStatistics") {

    println(s"Starting scanning")

    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createBatchScanner(AccumuloConnectionFactory.ARTIFACTS, auths, 10)
    val ranges = List(
      new Range(new String(lc.encode("header/PMID:1")), new String(lc.encode("header/PMID:30000000"))),
      new Range(new String(lc.encode("header/PMCID:PMC1")), new String(lc.encode("header/PMCID:PMC30000000"))),
      new Range("header/NCTID:NCT", "header/NCTID:NCT30000000")
    )
    scan.setRanges(ranges.asJava)
    scan.fetchColumn(new Text("_"), new Text("header/id\u0000freetext\u00000"))

    var pmidCount = 0
    var pmcCount = 0
    var nctCount = 0

    try {

      val iterator = scan.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val value = new String(entry.getValue.get)
        value match {  // TODO: this should be part of syntax info...
          case v if (v.startsWith("PMC")) => pmcCount += 1
          case v if (v.startsWith("NCT")) => nctCount += 1
          case other => pmidCount += 1
        }
        if ( (pmidCount + pmcCount + nctCount) % 10000 == 0) {
          println("Intermediate results:")
          println(sigmatics, "table", "element/PMID:docCount", "int", pmidCount)
          println(sigmatics, "table", "element/PMC:docCount", "int", pmcCount)
          println(sigmatics, "table", "element/CTGOV:docCount", "int", nctCount)
        }
      }

    } finally {
      scan.close
    }

    println(sigmatics, "table", "element/PMID:docCount", "int", pmidCount)
    println(sigmatics, "table", "element/PMC:docCount", "int", pmcCount)
    println(sigmatics, "table", "element/CTGOV:docCount", "int", nctCount)

    println(s"Success!")

  }


  def median(s: Seq[Int]): Double = {
    if (s.nonEmpty) {
      val (lower, upper) = s.sortWith(_<_).splitAt(s.size / 2)
      if (s.size % 2 == 0) (lower.last + upper.head) / 2.0 else upper.head
    } else {
      Double.NaN
    }
  }

  def average(s: Seq[Int]): Double = {
    s.sum.toDouble / s.size
  }

  // to capture the distinct document element identifiers
  // var docelemsPMID = Set[String]()
  // var docelemsPMC = Set[String]()
  // var docelemsCTGOV = Set[String]()
  val docelemCountPerPMID = scala.collection.mutable.Map[String, Int]()
  val docelemCountPerPMC = scala.collection.mutable.Map[String, Int]()
  val docelemCountPerCTGOV = scala.collection.mutable.Map[String, Int]()

  def collectDocElemStatistics = {

    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.TOPOLOGY_INDEX, auths)
    // val range = new Range("header/PMID:", "header/PMID::")
    // scan.setRange(range)
    scan.fetchColumnFamily(new Text("contains"))

    try {

      var nofIter = 0
      val iterator = scan.iterator()
      val lastID = ""
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val topologyTag = entry.getKey.getRow.toString
        val docelemId = entry.getKey.getColumnQualifier.toString
        topologyTag match {
          case t if (t.startsWith("header/PMID")) => {
            //docelemsPMID = docelemsPMID + docelemId
            val currentCount = docelemCountPerPMID.getOrElse(topologyTag, 0)
            docelemCountPerPMID.update(topologyTag, currentCount + 1)
          }
          case t if (t.startsWith("header/PMCID")) => {
            //docelemsPMC = docelemsPMC + docelemId
            val currentCount = docelemCountPerPMC.getOrElse(topologyTag, 0)
            docelemCountPerPMC.update(topologyTag, currentCount + 1)
          }
          case t if (t.startsWith("header/NCT")) => {
            //docelemsCTGOV = docelemsCTGOV + docelemId
            val currentCount = docelemCountPerCTGOV.getOrElse(topologyTag, 0)
            docelemCountPerCTGOV.update(topologyTag, currentCount + 1)
          }
          case _ => println("Collection not from interest")
        }
        nofIter += 1
        if (nofIter % 1000000 == 0) {
          println(s"Intermediate results:")
          println(sigmatics, "table", "element/PMID:elemCountMedian", "double", median(docelemCountPerPMID.map(_._2).toSeq))
          println(sigmatics, "table", "element/PMC:elemCountMedian", "double", median(docelemCountPerPMC.map(_._2).toSeq))
          //println(sigmatics, "table", "element/CTGOV:elemCountMedian", "double", median(docelemCountPerCTGOV.map(_._2).toSeq))
        }
      }

    } finally {
      scan.close
    }

    // println(sigmatics, "table", "element/PMID:elemCount", "int", docelemsPMID.size)
    // println(sigmatics, "table", "element/PMC:elemCount", "int", docelemsPMC.size)
    // println(sigmatics, "table", "element/CTGOV:elemCount", "int", docelemsCTGOV.size)

    println(sigmatics, "table", "element/PMID:elemMin", "double", docelemCountPerPMID.map(_._2).toSeq.min)
    println(sigmatics, "table", "element/PMC:elemMin", "double", docelemCountPerPMC.map(_._2).toSeq.min)
    //println(sigmatics, "table", "element/CTGOV:elemMin", "double", docelemCountPerCTGOV.map(_._2).toSeq.min)

    // val pmcMin = docelemCountPerPMC.map(_._2).toSeq.min
    // println("ALL PMC MIN IDS")
    // println(docelemCountPerPMC.filter(_._2 == pmcMin))
    // println(docelemCountPerPMC.filter(_._2 == 39))

    println(sigmatics, "table", "element/PMID:elemMax", "double", docelemCountPerPMID.map(_._2).toSeq.max)
    println(sigmatics, "table", "element/PMC:elemMax", "double", docelemCountPerPMC.map(_._2).toSeq.max)
    //println(sigmatics, "table", "element/CTGOV:elemMax", "double", docelemCountPerCTGOV.map(_._2).toSeq.max)

    println(sigmatics, "table", "element/PMID:elemCountMedian", "double", median(docelemCountPerPMID.map(_._2).toSeq))
    println(sigmatics, "table", "element/PMC:elemCountMedian", "double", median(docelemCountPerPMC.map(_._2).toSeq))
    //println(sigmatics, "table", "element/CTGOV:elemCountMedian", "double", median(docelemCountPerCTGOV.map(_._2).toSeq))


    println(s"Success!")

  }

  def famousDocElems = {

    val docelemCountPerPMID = scala.collection.mutable.Map[String, Int]()
    val docelemCountPerPMC = scala.collection.mutable.Map[String, Int]()
    val docelemCountPerCTGOV = scala.collection.mutable.Map[String, Int]()

    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.TOPOLOGY_INDEX, auths)
    // val range = new Range("header/PMID:", "header/PMID::")
    // scan.setRange(range)
    scan.fetchColumnFamily(new Text("contains"))

    try {

      var nofIter = 0
      val iterator = scan.iterator()
      val lastID = ""
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val topologyTag = entry.getKey.getRow.toString
        val docelemId = entry.getKey.getColumnQualifier.toString
        topologyTag match {
          case t if (t.startsWith("header/PMID") && docelemId.startsWith("sentence/")) => {
            //docelemsPMID = docelemsPMID + docelemId
            val currentCount = docelemCountPerPMID.getOrElse(docelemId, 0)
            docelemCountPerPMID.update(docelemId, currentCount + 1)
          }
          case t if (t.startsWith("header/PMCID") && docelemId.startsWith("sentence/")) => {
            //docelemsPMC = docelemsPMC + docelemId
            val currentCount = docelemCountPerPMC.getOrElse(docelemId, 0)
            docelemCountPerPMC.update(docelemId, currentCount + 1)
          }
          case t if (t.startsWith("header/NCT") && docelemId.startsWith("sentence/")) => {
            //docelemsCTGOV = docelemsCTGOV + docelemId
            val currentCount = docelemCountPerCTGOV.getOrElse(docelemId, 0)
            docelemCountPerCTGOV.update(docelemId, currentCount + 1)
          }
          case _ => ()//println("Collection not from interest")
        }
        nofIter += 1
        if (nofIter % 1000000 == 0) {
          println(s"Intermediate results:")
          println("Lof max elems in pmid : " + docelemCountPerPMID.toSeq.sortBy(_._2).reverse.take(10))
          println("Lof max elems in pmc: " + docelemCountPerPMC.toSeq.sortBy(_._2).reverse.take(10))
        }
      }

    } finally {
      scan.close
    }

    println("Lof max elems in pmid : " + docelemCountPerPMID.toSeq.sortBy(_._2).reverse.take(10))
    println("Lof max elems in pmc: " + docelemCountPerPMC.toSeq.sortBy(_._2).reverse.take(10))

    println(s"Success!")

  }

  var genesPerSentenceWhenContainingTaggings = Map[String,Int]()


  def collectGeneNerTagStatistics(collection: String) = {

    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.ARTIFACTS, auths)
    scan.fetchColumnFamily(new Text("Homo_sapiens-12.0-21"))

    try {
      var nofIter = 0
      val iterator = scan.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val sentenceId = entry.getKey.getRow.toString
        val currentCount = genesPerSentenceWhenContainingTaggings.getOrElse(sentenceId, 0)
        genesPerSentenceWhenContainingTaggings = genesPerSentenceWhenContainingTaggings.updated(sentenceId, currentCount + 1)
        nofIter += 1
        if (nofIter % 10000 == 0) {
          println(s"Intermediate results (sentence count ${genesPerSentenceWhenContainingTaggings.size}):")
          val m = average(genesPerSentenceWhenContainingTaggings.map(_._2).toSeq)
          println(sigmatics, "table", s"element/${collection}:tagCount", "double", m)
        }
      }
    } finally {
      scan.close
    }

    //val scan = AccumuloConnectionFactory.get.createBatchScanner(AccumuloConnectionFactory.ARTIFACTS, auths, 10)
    // val ranges = docelemIds.map(Range.exact(_))
    // scan.setRanges(ranges.asJava)
    // scan.fetchColumnFamily(new Text("Homo_sapiens-12.0-21"))
    //
    // var count = 0
    //
    // try {
    //
    //   val iterator = scan.iterator()
    //   while (iterator.hasNext()) {
    //     val entry = iterator.next()
    //     count += 1
    //   }
    //
    // } finally {
    //   scan.close
    // }
    println(s"END (sentence count ${genesPerSentenceWhenContainingTaggings.size}):")
    val m = average(genesPerSentenceWhenContainingTaggings.map(_._2).toSeq)
    println(sigmatics, "table", s"element/${collection}:tagCount", "double", m)

    println(s"Success!")

  }

  // def collectGeneNerTagStatistics(collection: String) = {
  //
  //   val auths = new Authorizations()
  //   val scan = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.ARTIFACTS, auths)
  //   scan.fetchColumnFamily(new Text("Homo_sapiens-12.0-21"))
  //
  //   var count = 0
  //
  //   try {
  //
  //     val iterator = scan.iterator()
  //     while (iterator.hasNext()) {
  //       val entry = iterator.next()
  //       count += 1
  //     }
  //
  //   } finally {
  //     scan.close
  //   }
  //
  //   println(sigmatics, "table", s"element/${collection}:tagCount", "int", count)
  //
  //   println(s"Success!")
  //
  // }

}
