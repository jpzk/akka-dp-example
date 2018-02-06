package com.mwt.mazeservice

import org.scalatest._
import scala.util.{Try, Success, Failure}
import akka.pattern.ask
import akka.io.{Tcp}
import akka.util.{Timeout, ByteString}
import akka.actor.Props
import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestActors, TestKit}
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpecLike }
import akka.testkit.TestActorRef
import scala.concurrent.duration._
import akka.testkit.TestProbe
import Tcp.{Received, PeerClosed}

class ClientServerSpec extends TestKit(ActorSystem("ClientServerSpec")) 
  with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll {

  import Registry._
  import PubSub._
  implicit val timeout = Timeout(5 seconds) 
  
  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ClientHandler actor" must {
    "accept id, register, subscribe, unregister, unsubscribe" in {
      val connection = TestProbe()
      val registry = TestProbe()
      val pubSub = TestProbe()

      val clientHandler = TestActorRef(
        new ClientHandler(connection.ref, registry.ref,pubSub.ref))

      clientHandler ! Received(ByteString("123"))
      registry.expectMsg(Register(123, connection.ref))
      pubSub.expectMsg(Subscribe(123, 123))
      pubSub.expectMsg(Subscribe(123, -1))

      clientHandler ! PeerClosed
      registry.expectMsg(Unregister(123))
      pubSub.expectMsg(UnsubscribeAll(123))
    }
  }
}
