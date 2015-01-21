package actors

import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import models._

import scala.util.{Success, Try}

class TrackActor extends Actor with ActorLogging {
  import context._
  val vehicleMgrActor = actorOf(Props[VehicleMgrActor], name = "vehicleMgr") // TODO: Locate from context. Track actor does not create mgr actor
  val trackSaverActor = actorOf(Props(new TrackSaverActor(this.self)).withDispatcher("saver-dispatcher"), name = "trackSaver") // Creating actor with constructor parameter

  override def receive = LoggingReceive {
    case event: IncomingTracks => trackSaverActor forward event
    case event: TrackSaved =>
      event.payload match {
        case Success(tracks) =>
          tracks.devices.foreach(deviceTrack => vehicleMgrActor ! deviceTrack)
        case _ => // Must never happen because failed result must never be sent to this actor
      }
  }
}

class TrackSaverActor(parent: ActorRef) extends Actor with ActorLogging {

  private def saveTracks(tracks: IncomingTracks): TrackSaved = {
    val result = Try(Tracks.database.withSession { implicit session => // Try Monad helps us to wrap any of result or exception to avoid try .. catch blocks
      tracks.devices.foreach((track) => {
        Tracks.insert(Vehicles.getVehicleId(track.deviceId), track.track)
      })
      tracks
    })
    TrackSaved(result)
  }

  override def receive = LoggingReceive {
    case event: IncomingTracks =>
      log.debug("Track received: {}", event)
      println("Track received: " + event)
      val result = saveTracks(event)
      sender ! result
      if (result.payload.isSuccess) parent ! result // No need to send failed data for processing
  }
}

class VehicleMgrActor extends Actor with ActorLogging {
  import context._
  private val vehicleActors = new scala.collection.mutable.HashMap[Int, ActorRef]

  private def createVehicleActor(vehicleId: Int): ActorRef = {
    val vehicle = Vehicles.database.withSession { implicit session =>
      Vehicles.get(vehicleId)
    }
    if (!vehicle.isDefined) throw new IllegalArgumentException("Vehicle not found!") // Must never happen if vehicle existence is checked during track save
    val ref = actorOf(Props(new VehicleActor(this.self, vehicle.get)), name = f"vehicle$vehicleId")
    vehicleActors += ((vehicleId, ref))
    ref
  }

  private def findActorForVehicle(vehicleId: Int): ActorRef = {
    vehicleActors.getOrElse(vehicleId, createVehicleActor(vehicleId))
  }

  override def receive = LoggingReceive {
    case event: IncomingTrack => findActorForVehicle(Vehicles.getVehicleId(event.deviceId)) ! event
  }
}

class VehicleActor(val mgrActor: ActorRef, val vehicle: Vehicle) extends Actor with ActorLogging {

  private var lastTrack: Track = null

  import context._
  override def receive = LoggingReceive {
    case event: IncomingTrack =>
      log.debug("Track received: {}", event)
      lastTrack = event.track.last
      // Send ahead for processing
  }
}


