package com.mwt.mazeservice

import akka.util.ByteString
import org.scalatest._
import scala.util.{Try, Success, Failure}

class ProtocolSpec extends FlatSpec with Matchers {
  import Protocol._
  import Parser._

  "Parser" should "decode RawEvent" in {
    val input = """666|F|60|50"""
    val output = Parser.decodeRaw(input)
  }

  it should "decode Follow" in {
    val input = ByteString("""666|F|60|50""")
    parse(input) shouldEqual Success(ParsedEvent(input, 666, Follow(60L, 50L)))
  }
  
  it should "decode Unfollow" in {
    val input = ByteString("""1|U|12|9""")
    parse(input) shouldEqual Success(ParsedEvent(input, 1, Unfollow(12L, 9L)))
  }

  it should "decode Broadcast" in {
    val input = ByteString("""542532|B""")
    parse(input) shouldEqual Success(ParsedEvent(input, 542532, Broadcast()))
  }

  it should "decode PrivateMsg" in {
    val input = ByteString("""43|P|32|56""")
    parse(input) shouldEqual Success(ParsedEvent(input, 43, PrivateMsg(32L, 56L)))
  }

  it should "decode StatusUpdate" in {
    val input = ByteString("""634|S|32""")
    parse(input) shouldEqual Success(ParsedEvent(input, 634, StatusUpdate(32L)))
  }

  it should "throw on bad format" in {
    val input = ByteString("""666;F|60|50""")
    parse(input).isFailure shouldEqual true
  }

  it should "throw on empty input" in {
    val input = ByteString.empty
    parse(input).isFailure shouldEqual true
  }

}
