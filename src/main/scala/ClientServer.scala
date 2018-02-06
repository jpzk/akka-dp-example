package com.mwt.mazeservice

import akka.actor.ActorLogging
import akka.actor.{ Actor, ActorRef, Props, ActorSystem}
import akka.io.{ IO, Tcp }
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import java.net.InetSocketAddress
import scala.util.{Try, Failure, Success}
import scala.concurrent.duration._

class ClientHandler(connection: ActorRef, registry: ActorRef, pubSub: ActorRef) 
  extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds) 

  import Registry._
  import PubSub._
  import Tcp.{Received, PeerClosed}

  var id: Option[Long] = None
  var registrySender: ActorRef = null

  def receive = {
    case Received(data) => Try(data.utf8String.trim().toLong) match {
      case Success(reportedId) => 
        id = Some(reportedId)
        registry ! Register(reportedId, connection)
        pubSub ! Subscribe(reportedId, reportedId)
        pubSub ! Subscribe(reportedId, -1) // broadcast 
      case Failure(e) => 
        log.error(e.getMessage, e)
        None
    }

    case PeerClosed => 
      if(id.isDefined) {
        registry ! Unregister(id.get)
        pubSub ! UnsubscribeAll(id.get) 
      }
      context stop self
  }
}

class ClientServer(registry: ActorRef, pubSub: ActorRef) 
  extends Actor with ActorLogging {
  
  import Tcp._
  import context.system

  IO(Tcp) ! Bind(self, new InetSocketAddress("localhost", 9099))

  def receive = {
    case b @ Bound(localAddress) => context.parent ! b
    case CommandFailed(_: Bind) => context stop self
    case c @ Connected(remote, local) =>
      log.info(s"New connection: $remote")
      val connection = sender()
      val handler = context.actorOf(Props(
        new ClientHandler(connection, registry, pubSub)))
      connection ! Register(handler)
  }
}

object Registry {
  case class Register(id: Long, handler: ActorRef)
  case class Unregister(id: Long)
}

class Registry extends Actor with ActorLogging {
  import PubSub._
  import Registry._

  val CTRLF = ByteString("\n")

  implicit val timeout = Timeout(5 seconds) 
  var conns: Map[Long, ActorRef] = Map[Long, ActorRef]()

  def receive = {
    case Register(id: Long, ref: ActorRef) =>
      conns += (id -> ref)
      log.info(s"Connection for client $id registered")

    case Unregister(id: Long) => 
      conns -= id 
      log.info(s"Connection for client $id unregistred")

    case OutgoingMessage(receivers, payload) => 
      receivers.foreach { r => 
        conns.get(r) match {
          case Some(c) => 
            c ! Tcp.Write(payload ++ CTRLF)
          case None => 
            log.warning("Connection already closed")
        }
      }
  }
}

