package com.mwt.mazeservice

import org.scalatest._
import scala.util.{Try, Success, Failure}
import akka.pattern.ask
import akka.util.{Timeout, ByteString}
import akka.actor.Props
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActors, TestKit}
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.testkit.TestActorRef

class SubscriptionsSpec extends FlatSpec with Matchers {
  val K = 1L
  val V = 1L
  val K2 = 2L

  "Subscriptions" should "should subscribe" in {
    val s = new Subscriptions[Long, Long]
    s.subscribe(K, V)
    
    (s.index contains K) shouldEqual true
    (s.subscriptions contains V) shouldEqual true
    (s.subscriptions(V) shouldEqual Set(K))
  }

  it should "should unsubscribe and cleanup" in {
    val s = new Subscriptions[Long, Long]
    s.subscribe(K, V)
    s.unsubscribeAll(K)

    (s.index contains K) shouldEqual false 
    (s.subscriptions contains V) shouldEqual false 
  }

  it should "topic take multi subscriptions" in {
    val s = new Subscriptions[Long, Long]
    s.subscribe(K, V)
    s.subscribe(K2, V)
    (s.subscriptions(V) shouldEqual Set(K,K2))
  }

  it should "untouch subscriptions when unsubscribing" in {
    val s = new Subscriptions[Long, Long]
    s.subscribe(K, V)
    s.subscribe(K2, V)
    s.unsubscribeAll(K2)
    (s.subscriptions(V) shouldEqual Set(K))
  }
}

class PubSubSpec extends TestKit(ActorSystem("PubSubSpec")) 
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  import Protocol._
  import PubSub._
  import scala.concurrent.duration._

  val Payload = ByteString.empty

  implicit val timeout = Timeout(5 seconds) 
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }
  
  "PubSub actor" must {
    "subscribe clients to a topic, publish" in {
      val pubsub = TestActorRef(new PubSub(testActor))
      (pubsub ! Subscribe(1L, 1L))
      (pubsub ! Subscribe(2L, 1L))

      val event = ParsedEvent(Payload, 0L, Follow(1L, 2L))
      pubsub ! ParsedEventBatch(Seq(event))
      expectMsg(OutgoingMessage(Set(1L,2L), Payload))
    }

    "subscribe. unsubscribe client to a topic, publish" in {
      val pubsub = TestActorRef(new PubSub(testActor))
      (pubsub ! Subscribe(1L, 1L))
      (pubsub ! Subscribe(1L, 2L))
      (pubsub ! UnsubscribeAll(1L))
 
      val event = ParsedEvent(Payload, 0L, Follow(1L, 2L))
      pubsub ! ParsedEventBatch(Seq(event))
     
      pubsub ! Publish(1L, Payload) 
      expectMsg(OutgoingMessage(Set(), Payload))

      pubsub ! Publish(2L, Payload)
      expectMsg(OutgoingMessage(Set(), Payload))
    }
  }
}
