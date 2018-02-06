package com.mwt.mazeservice

import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.{ Actor, ActorRef, Props, ActorSystem}
import scala.concurrent.duration._

object PubSub { 
  case class Publish(topic: Long, payload: ByteString)

  case class OutgoingMessage(receivers: Set[Long], payload: ByteString)

  case class Subscribe(id: Long, topic: Long)

  case class UnsubscribeAll(id: Long)
}

// not thread-safe
// improve, immutable data structure 
class Subscriptions[K,V] {
  var subscriptions: Map[V, Set[K]] = Map()
  var references: Map[V, Int] = Map()

  var index: Map[K, Set[V]] = Map()
  
  def subscribe(id: K, topic: V): Unit = {
    val topics = index.get(id) match {
      case Some(topics) => topics + topic
      case None => Set(topic)
    }
    index = index + (id -> topics)

    val ids = subscriptions.get(topic) match {
      case Some(ids) => ids + id 
      case None => Set(id)
    }

    val refs = references.getOrElse(topic, 0)
    references = references + (topic -> (refs + 1)) 
    subscriptions = subscriptions + (topic -> ids)
  }

  def unsubscribeAll(id: K): Unit = {
    if(!(index contains id)) 
      return ()

    index(id) foreach { topic => 
      val ids = subscriptions(topic) - id
      if(references.getOrElse(topic, 1) - 1 < 1) 
        subscriptions = subscriptions - topic
      else
        subscriptions = subscriptions + (topic -> ids)
    }
    index = index - id
  }
}

/**
 * The dispatcher is a simple pubsub message bus for the clients. 
 * It has a state, but it does not persist its state.
 *
 * For extensibility we will use topics instead of users, 
 * maybe use a type parameter here.
 */
class PubSub(sink: ActorRef) extends Actor with ActorLogging {
  import PubSub._
  import Protocol._

  val subscriptions = new Subscriptions[Long, Long]
   implicit val timeout = Timeout(5 seconds) 

  def sentForTopic(topic: Long, payload: ByteString) = {
    val receivers = subscriptions.subscriptions.getOrElse(topic, Set())
    sink ! OutgoingMessage(receivers, payload)
  }

  def route(event: ParsedEvent) = event match {
    case ParsedEvent(data: ByteString, s: Long, Follow(from: UserId, to: UserId)) =>
      sentForTopic(from, data)
      sentForTopic(to, data)

    case ParsedEvent(data: ByteString, s: Long, Unfollow(from: UserId, to: UserId)) =>
      sentForTopic(from, data)
      sentForTopic(to, data)

    case ParsedEvent(data: ByteString, s: Long, StatusUpdate(from: UserId)) =>
      sentForTopic(from, data)

    case ParsedEvent(data: ByteString, s: Long, PrivateMsg(from: UserId, to: UserId)) =>
      sentForTopic(from, data)
      sentForTopic(to, data)

    case ParsedEvent(data: ByteString, s: Long, Broadcast()) =>
      sentForTopic(-1, data)
  }

  def receive = {
    case Subscribe(id: Long, topic: Long) => 
      subscriptions.subscribe(id, topic)
      log.info(s"client $id subscribed to topic $topic")

    case UnsubscribeAll(id: Long) => 
      subscriptions.unsubscribeAll(id)
      log.info(s"client $id unsubscribed from all topics")

    case ParsedEventBatch(batch: Seq[ParsedEvent]) => 
      batch
        .sortWith { case (a,b) => a.sequenceNr < b.sequenceNr }
        .foreach(route)
  }
}

