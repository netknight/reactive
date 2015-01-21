package services

import actors.TrackActor
import akka.actor.Props
import akka.util.Timeout
import models.{IncomingTracks, TrackSaved}
import play.libs.Akka
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try


object AkkaService {

  val trackActor = Akka.system.actorOf(Props[TrackActor])
  implicit val timeout = Timeout(5 seconds)

  def pushTrack(tracks: IncomingTracks) = {
    val result = Await.result(trackActor ? tracks, timeout.duration).asInstanceOf[TrackSaved]
    result
  }
}
