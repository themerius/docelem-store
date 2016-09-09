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

import eu.themerius.docelemstore.utils.Stats.time

import com.typesafe.config.ConfigFactory


object AccumuloXmiScanner {

  val conf = ConfigFactory.load
  val brokerUri = conf.getString("docelem-store.broker.uri")
  val brokerUsr = conf.getString("docelem-store.broker.usr")
  val brokerPwd = conf.getString("docelem-store.broker.pwd")
  val brokerQueue = conf.getString("docelem-store.broker.queue")
  val brokerBilling = conf.getString("docelem-store.broker.billing")

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
  lazy val sessions = List.fill(16) {
    connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
  }
  lazy val prods = sessions.map(_.createProducer(dest))


  def scanXMI(prefix: String) = time("scanXMI") {

    println(s"Starting scanning $prefix")
    println(s"Sending to $dest")

    val auths = new Authorizations()
    val scan = AccumuloConnectionFactory.get.createScanner(AccumuloConnectionFactory.ARTIFACTS, auths)
    scan.setRange(Range.prefix(prefix))
    scan.fetchColumn(new Text("raw_data"), new Text("textmining\u0000gzip_xmi\u00000"))

    val iterator = scan.iterator()
    var countVar = 0
    var latestFuture: Future[Unit] = null

    while (iterator.hasNext()) {

      val entry = iterator.next()
      val countVal = countVar

      latestFuture = Future {
        val message = sessions(countVal % 16).createTextMessage(new String(entry.getValue.get))
        message.setStringProperty("tracking-nr", entry.getKey.getRow.toString)
        message.setStringProperty("content-type", "gzip_xml")
        prods(countVal % 16).send(message)
      }

      countVar = countVar + 1

      if (countVar % 10000 == 0) {
        println(s"Scanned from $prefix the $countVar^th XMI.")
        // this will throttle the accumulo iterator a litte bit,
        // because the message library (or broker or network?) is slower...
        Await.ready(latestFuture, 1.minutes)
      }

    }

    println(s"Scanned from $prefix the LAST (=$countVar^th) XMI.")
    Await.ready(latestFuture, 5.minutes)
    println("Awaited last message to be SEND.")
    println("Success!")
    System.exit(0)

  }


}
