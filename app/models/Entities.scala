package models

import org.joda.time.DateTime
import scala.util.Try

// Basic entities

case class TrackPoint(date: DateTime, latitude: Double, longitude: Double)
case class IncomingTrack(deviceId: String, track: List[TrackPoint])
case class IncomingTracks(devices: List[IncomingTrack])
case class ExtendedTrackPoint(point: TrackPoint, angle: Int, mileage: Double, speed: Int)
case class VehicleStatus(vehicleId: Int, lastPoint: Option[ExtendedTrackPoint])
case class ExtendedTrack(track: List[ExtendedTrackPoint])

// Events & Messages
trait Event {
  def vehicleId: Int
  def point: TrackPoint
}

case class GetAllTracks()
case class TrackSaved(payload: Try[IncomingTracks])
case class GetCurrentStatus(vehicleId: Int)
case class GetEvents(vehicleId: Int)
case class VehicleEvents(vehicleId: Int, events: List[Event])
case class SpeedingBeginEvent(vehicleId: Int, point: TrackPoint, speed: Int) extends Event
case class SpeedingEndEvent(vehicleId: Int, point: TrackPoint, speed: Int) extends Event
case class MovedEvent(vehicleId: Int, point: TrackPoint) extends Event
case class StoppedEvent(vehicleId: Int, point: TrackPoint) extends Event

case class WebEvent(point: TrackPoint, value: String)
case class WebEvents(events: List[WebEvent])

object Convertor {
  def convert(events: VehicleEvents): WebEvents = {
    WebEvents(events.events.map(e => WebEvent(e.point, e match {
      case ev: SpeedingBeginEvent => ev.speed.toString
      case ev: SpeedingEndEvent => ev.speed.toString
      case _ => ""
    })))
  }
}