package com.webtrends.harness.component.akkahttp

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.pattern.ask
import akka.stream.scaladsl.Source
import akka.util.{ByteString, Timeout}
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class TestWebsocket extends AkkaHttpWebsocket {
  override def path = "greeter"

  override def handleText(text: String): TextMessage = {
    TextMessage(Source.single("Hello " + text + "!"))
  }
}

class AkkaHttpWebsocketTest extends WordSpecLike
  with ScalatestRouteTest
  with MustMatchers {

  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val twsActor = system.actorOf(Props[TestWebsocket])
  val webSocketService = Await.result((twsActor ? GetRoute()).mapTo[Route], Duration("5 seconds"))

  "AkkaHttpWebsocket" should {
    "be able to take websocket input" in {
      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()
      // WS creates a WebSocket request for testing
      WS("/greeter", wsClient.flow) ~> webSocketService ~>
        check {
          // check response for WS Upgrade headers
          isWebSocketUpgrade mustEqual true

          // manually run a WS conversation
          wsClient.sendMessage("Peter")
          wsClient.expectMessage("Hello Peter!")

          wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
          wsClient.expectNoMessage(100.millis)

          wsClient.sendMessage("John")
          wsClient.expectMessage("Hello John!")

          wsClient.sendCompletion()
          wsClient.expectCompletion()
        }
    }
  }
}
