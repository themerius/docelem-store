package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.minicluster.MiniAccumuloCluster
import org.apache.accumulo.core.client.ZooKeeperInstance
import org.apache.accumulo.core.client.security.tokens.PasswordToken
import org.apache.accumulo.core.security.ColumnVisibility
import org.apache.accumulo.core.data.Value
import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.core.client.BatchWriterConfig
import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Range
import org.apache.accumulo.core.data.Key
import org.apache.accumulo.core.client.IteratorSetting
import org.apache.accumulo.core.iterators.user.IntersectingIterator
import org.apache.accumulo.core.client.Durability

import org.apache.hadoop.io.Text

import java.io.File
import java.lang.Iterable
import java.util.Collections

import scala.collection.JavaConverters._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.config.ConfigFactory

import eu.themerius.docelemstore.utils.Stats.time

// For writing into database
case class WriteDocelems(dedupes: Iterable[Mutation], versions: Iterable[Mutation], size: Int)
case class WriteAnnotations(annots: Iterable[Mutation], index: Iterable[Mutation], size: Int)

// For querying the database
case class FindDocelem(authority: String, typ: String, uid: String, replyTo: String, trackingNr: String, gate: ActorRef)
case class DiscoverDocelemsWithAnnotations(searchedLayers: Seq[String], annotUiids: Seq[String], replyTo: String, trackingNr: String, gate: ActorRef)
class AccumuloStorage extends Actor {

  println(s"Storage ${context.dispatcher}")

  // SETUP writers (they are threadsafe and could be shared by multiple actors)
  val config = new BatchWriterConfig
  config.setMaxMemory(100L * 1024L * 1024L); // bytes available to batchwriter for buffering mutations
  config.setMaxWriteThreads(10)
  config.setDurability(Durability.NONE) // no write ahead log

  var writerTimeMachine = AccumuloConnection.get.createBatchWriter("timemachine_v3", config)
  var writerDedupes = AccumuloConnection.get.createBatchWriter("dedupes_v3", config)
  var writerAnnotations = AccumuloConnection.get.createBatchWriter("annotations_v3", config)
  var writerAnnotationsIndex = AccumuloConnection.get.createBatchWriter("annotations_index_v3", config)

  var countD = 0
  var countA = 0

  def receive = {

    case WriteDocelems(dedupes, versions, size) => {
        writerDedupes.addMutations(dedupes)
        writerTimeMachine.addMutations(versions)

      if (countD % 1000 == 0) {
        println(s"Accumulo:WriteDocelems($countD)")
        time("Accumulo:CloseDocelemWriter") {
          writerDedupes.close
          writerTimeMachine.close
          writerTimeMachine = AccumuloConnection.get.createBatchWriter("timemachine_v3", config)
          writerDedupes = AccumuloConnection.get.createBatchWriter("dedupes_v3", config)
        }
      }
      countD = countD + 1
    }

    case WriteAnnotations(annots, index, size) => {
      writerAnnotations.addMutations(annots)
      writerAnnotationsIndex.addMutations(index)

      if (countA % 1000 == 0){
        println(s"Accumulo:WriteAnnotations($countA)")
        time("Accumulo:CloseAnnotsWriter") {
          writerAnnotations.close
          writerAnnotationsIndex.close
          writerAnnotations = AccumuloConnection.get.createBatchWriter("annotations_v3", config)
          writerAnnotationsIndex = AccumuloConnection.get.createBatchWriter("annotations_index_v3", config)
        }
      }
      countA = countA + 1
    }

    case "FLUSH" => time ("Accumulo:FLUSH") {
      writerDedupes.flush
      writerTimeMachine.flush
      writerAnnotations.flush
      writerAnnotationsIndex.flush
    }

    case FindDocelem(authority, typ, uid, replyTo, trackingNr, gate) => time (s"Accumulo:FindDocelem") {
      val found = scanSingleDocelem(authority, typ, uid)
      found.map {
        xml => {  // TODO is too ulgy!
          val annots = scanAnnotations(xml \\ "uiid" text, xml \ "@version" text)
          val hack = "<corpus>" + xml.toString + annots.mkString + "</corpus>"
          gate ! Reply(hack, replyTo, trackingNr)
        }
      }
    }

    case DiscoverDocelemsWithAnnotations(layers, annotations, replyTo, trackingNr, gate) => time (s"Accumulo:DiscoverDocelemsWithAnnotations") {
      val found = scanAnnotationsIndex(layers, annotations)
      val xml =
      <results>{
        found.map { uiid =>
          <uiid>{uiid}</uiid>
        }
      }</results>
      gate ! Reply(xml.toString, replyTo, trackingNr)
    }

  }

  def scanSingleDocelem(authority: String, typ: String, uid: String) = {
    val auths = new Authorizations()
    val scan = AccumuloConnection.get.createScanner("timemachine_v3", auths)
    scan.setRange(Range.exact(authority))
    scan.fetchColumn(new Text(typ), new Text(uid))

    for (entry <- scan.asScala) yield {
        val key = entry.getKey()
        val value = entry.getValue()

        val scanDedupes = AccumuloConnection.get.createScanner("dedupes_v3", auths)
        scanDedupes.setRange(Range.exact(value.toString))  // ^= hash
        scanDedupes.fetchColumn(new Text(authority), new Text(typ + "/" + uid))

        val content = for (entryDedupes <- scanDedupes.asScala) yield {
          entryDedupes.getValue().toString
        }

        if (content.size > 0)
          <docelem version={value.toString}>
            <uiid>{authority + "/" + typ + "/" + uid}</uiid>
            <model>{scala.xml.Unparsed(content.mkString(""))}</model>
          </docelem>
        else
          <docelem></docelem>
    }
  }

  def scanAnnotations(uiid: String, version: String) = {
    val auths = new Authorizations()
    val scan = AccumuloConnection.get.createScanner("annotations_v3", auths)
    scan.setRange(new Range(uiid, uiid + "/" + version))
    for (entry <- scan.asScala) yield {
      entry.getValue
    }
  }

  /*
   * This will only intersect the terms within the same row!
  */
  def scanAnnotationsIndex(layers: Seq[String], uiids: Seq[String]) = {
    val tableName = "annotations_index_v3"
    val authorizations = new Authorizations()
    val numQueryThreads = 3
    val bs = AccumuloConnection.get.createBatchScanner(tableName, authorizations, numQueryThreads)

    val terms = uiids.map(uiid => new Text(uiid)).toArray

    val priority = 20
    val name = "ii"
    val iteratorClass = classOf[IntersectingIterator]
    val ii = new IteratorSetting(priority, name, iteratorClass)
    IntersectingIterator.setColumnFamilies(ii, terms)  // side effect on ii!

    bs.addScanIterator(ii)

    if (layers.isEmpty) {
      bs.setRanges(Collections.singleton(new Range()))  // all ranges
    } else {
      bs.setRanges(layers.map(Range.exact(_)).asJava)
    }

    for (entry <- bs.asScala.take(100)) yield {
      entry.getKey.getColumnQualifier.toString
    }
  }

  def scanTopologyOfDocelems(authority: String, typ: String, uid: String) = {
    Nil
  }

}
