package eu.themerius.docelemstore

import akka.actor.{ Actor }

class AccumuloFeeder extends Actor {

  def receive = {
    case Transform2DocElem(model, data) => {
      println("Got model with data:")
      model.deserialize(data)
      model.applyRules
    }
  }

}
