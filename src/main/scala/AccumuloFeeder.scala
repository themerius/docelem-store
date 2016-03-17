package eu.themerius.docelemstore

import akka.actor.{ ActorRef, ActorSystem, Props, Actor, Inbox }

import scala.concurrent.duration._
import scala.xml.XML
import scala.xml.NodeSeq
import scala.util.hashing.MurmurHash3

// Better to use Converters. See: http://stackoverflow.com/questions/8301947
import scala.collection.JavaConverters._

import org.apache.accumulo.core.data.Mutation
import org.apache.accumulo.core.security.ColumnVisibility

import org.apache.accumulo.core.security.Authorizations
import org.apache.accumulo.core.data.Range
import org.apache.hadoop.io.Text

class AccumuloFeeder extends Actor {

  def receive = {
    case Transform2DocElem(model, data) => {
      println("Got model with data:")
      model.deserialize(data)
      model.applyRules
    }
  }

}
