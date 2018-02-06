package com.mwt.mazeservice

import scala.util.{Try, Success, Failure}
import akka.util.{ByteString, Timeout}
import akka.actor.{ Actor, ActorRef, Props, ActorLogging }
import akka.pattern.ask

object Protocol { 
 
  type UserId = Long

  case class RawEvent(sequenceNr: Long, eType: String, fields: Seq[String])

  case class ParsedEvent(data: ByteString, sequenceNr: Long, event: Event)

  case class ParsedEventBatch(batch: Seq[ParsedEvent])

  sealed trait Event

  case class Follow(from: UserId, to: UserId) extends Event

  case class Unfollow(from: UserId, to: UserId) extends Event

  case class Broadcast() extends Event

  case class PrivateMsg(from: UserId, to: UserId) extends Event

  case class StatusUpdate(from: UserId) extends Event
}

object Decoders {
  import Protocol._

  type DecoderType[T] = PartialFunction[Seq[String], Try[T]]

  val follow: DecoderType[Follow] = {
    case Seq(from, to) => Try(Follow(from.toLong, to.toLong))
  }
  val unfollow: DecoderType[Unfollow] = {
    case Seq(from, to) => Try(Unfollow(from.toLong, to.toLong))
  }
  val status: DecoderType[StatusUpdate] = {
    case Seq(from) => Try(StatusUpdate(from.toLong))
  }
  val privmsg: DecoderType[PrivateMsg] = {
    case Seq(from, to) => Try(PrivateMsg(from.toLong, to.toLong))
  }
  val broadcast: DecoderType[Broadcast] = {
    case Seq() => Success(Broadcast())
  }

  val DecoderFuncs = Map(
    "F" -> follow,
    "U" -> unfollow,
    "B" -> broadcast,
    "P" -> privmsg,
    "S" -> status
  )

  def decode(e: RawEvent): Try[Event] = {
    val t = e.eType
    if(!DecoderFuncs.contains(t)) 
      return Failure(new Exception(s"Event type $t does not exist"))
     
    val decoder = DecoderFuncs(t)
    if(decoder.isDefinedAt(e.fields))
      decoder(e.fields)
    else Failure(new Exception(s"Wrong number of attributes for type $t"))
  }
}

class Parser(sink: ActorRef) extends Actor with ActorLogging {
  import Parser._
  import Protocol._

  import scala.concurrent.duration._
  implicit val timeout = Timeout(5 seconds) 

  def receive = {
    case data: ByteString => 
      val batch = (data.utf8String.split("\n")).map(ByteString(_))
        .map { payload => (payload, parse(payload)) }
        .filter { case (payload, parsed) => parsed.isSuccess }
        .map { case (payload, parsed) => parsed.get }
      sink ! ParsedEventBatch(batch)
  }
}

object Parser {
  import Protocol._
  import Decoders._

  val MinimumAttributes = 2

  /**
   * Reading attribute values, splitting the raw event
   */
  def parts(s: String): Try[Seq[String]] = s.split("""\|""") match {
    case a if a.size >= MinimumAttributes => Success(a)
    case _ => 
      Failure(new Exception(s"Could not extract $s; check delimiter"))
  }

  /**
   * Getting the sequence number in long
   */
  def seqNr(s: String): Try[Long] = Try(s.toLong) match {
    case Failure(e) => 
      Failure(new Exception(s"Could not read sequence number, underlying $e"))
    case s => s
  }

  /**
   * Decode to raw event
   */
  def decodeRaw(s: String): Try[RawEvent] = for {
    ps <- parts(s)
    seq <- seqNr(ps(0))
  } yield RawEvent(seq, ps(1), ps.drop(2)) 

  /**
   * Parse to event 
   */
  def parse(s: ByteString): Try[ParsedEvent] = for {
    raw <- decodeRaw(s.utf8String.trim())
    event <- decode(raw)
  } yield ParsedEvent(s, raw.sequenceNr, event)
}
