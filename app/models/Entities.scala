package models

import akka.actor.ActorRef
import org.joda.time.DateTime

import scala.util.Try

// Basic entities

case class Track(date: DateTime, latitude: Double, longitude: Double)
case class IncomingTrack(deviceId: String, track: List[Track])
case class IncomingTracks(devices: List[IncomingTrack])

// Events & Messages
case class TrackSaved(payload: Try[IncomingTracks])