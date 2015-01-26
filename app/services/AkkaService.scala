package services

import actors.{TrackMgrActor, VehicleMgrActor}
import akka.actor.Props
import akka.util.Timeout
import models._
import play.libs.Akka
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._

object AkkaService {

  val vehicleMgrActor = Akka.system.actorOf(Props[VehicleMgrActor], name = "vehicleMgr")
  val trackMgrActor = Akka.system.actorOf(Props(new TrackMgrActor(vehicleMgrActor)), name = "trackMgr")
  implicit val timeout = Timeout(5 seconds)

  def pushTrack(tracks: IncomingTracks): TrackSaved = {
    Await.result(trackMgrActor ? tracks, timeout.duration).asInstanceOf[TrackSaved]
  }

  def getEvents(vehicleId: Int): VehicleEvents = {
    Await.result(vehicleMgrActor ? GetEvents(vehicleId), timeout.duration).asInstanceOf[VehicleEvents]
  }

  def getStatus(vehicleId: Int): VehicleStatus = {
    Await.result(vehicleMgrActor ? GetCurrentStatus(vehicleId), timeout.duration).asInstanceOf[VehicleStatus]
  }
}
