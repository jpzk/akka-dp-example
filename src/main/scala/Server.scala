package com.mwt.mazeservice

import akka.routing._
import akka.actor.ActorLogging
import akka.actor.{ Actor, ActorRef, Props, ActorSystem}
import akka.io.{ IO, Tcp }
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import java.net.InetSocketAddress
import scala.util.{Try, Failure, Success}
import scala.concurrent.duration._

object Server extends App {
  implicit val system = ActorSystem("my-system")

  val registry = system.actorOf(Props(new Registry))
  val pubSub = system.actorOf(Props(new PubSub(registry)))
  val clientServer = system.actorOf(Props(new ClientServer(registry, pubSub)))

  val parser = system.actorOf(Props(new Parser(pubSub)))
  val server = system.actorOf(Props(new EventSourceServer(parser)))
}
