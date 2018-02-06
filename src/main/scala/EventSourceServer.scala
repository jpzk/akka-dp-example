package com.mwt.mazeservice

import akka.actor.ActorLogging
import akka.actor.{ Actor, ActorRef, Props, ActorSystem}
import akka.io.{ IO, Tcp }
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import java.net.InetSocketAddress
import scala.util.{Try, Failure, Success}
import scala.concurrent.duration._

class EventSourceServer(sink: ActorRef) extends Actor with ActorLogging {
  import Tcp._
  import Protocol._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 9090))

  def receive = {
    case b @ Bound(localAddress) => 
      context.parent ! b

    case CommandFailed(_: Bind) => context stop self
    
    case c @ Connected(remote, local) =>
      log.info(s"New connection: $remote")
      val connection = sender()
      val handler = context.actorOf(Props(new SourceHandler(connection, sink)))
      connection ! Register(handler)
  }
}

class SourceHandler(connection: ActorRef, sink: ActorRef) extends Actor with ActorLogging {
  import Tcp._
  import scala.concurrent.duration._

  def receive = {
    case Received(data) => sink ! data
    case PeerClosed => context stop self
  }
}

