package actors

import akka.pattern.ask
import akka.actor.{ActorRef, Props, ActorLogging, Actor}
import akka.event.LoggingReceive
import akka.util.Timeout
import models._
import org.joda.time.DateTime

import scala.util.{Success, Try}

class TrackMgrActor(vehicleMgrActor: ActorRef) extends Actor with ActorLogging {


  import context._
  val trackSaverActor = actorOf(Props(new TrackSaverActor(self)).withDispatcher("saver-dispatcher"), name = "trackSaver") // Creating actor with constructor parameter

  override def receive = LoggingReceive {
    case event: IncomingTracks => trackSaverActor forward event
    case event: TrackSaved =>
      event.payload match {
        case Success(tracks) =>
          tracks.devices.foreach(deviceTrack => vehicleMgrActor ! deviceTrack)
        case _ => // Not needed, but scala warnings fired without this match case
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
    val ref = actorOf(Props(new VehicleActor(self, vehicle.get)), name = f"vehicle$vehicleId")
    vehicleActors += ((vehicleId, ref))
    ref
  }

  private def findActorForVehicle(vehicleId: Int): ActorRef = {
    vehicleActors.getOrElse(vehicleId, createVehicleActor(vehicleId))
  }

  private def processGetTracks(event: GetAllTracks): Unit = {
    import scala.concurrent.{Await,Future}
    import scala.concurrent.duration._

    implicit val timeout = Timeout(4 seconds)

    var results: List[Future[IncomingTrack]] = List()
    vehicleActors.values.foreach(actor => {
      val res = (actor ? event).mapTo[IncomingTrack]
      results = results ::: List(res)
    })
    sender ! IncomingTracks(Await.result(Future.sequence(results), timeout.duration))
  }

  override def receive = LoggingReceive {
    case event: IncomingTrack => findActorForVehicle(Vehicles.getVehicleId(event.deviceId)) ! event
    case event: GetCurrentStatus => findActorForVehicle(event.vehicleId) forward event
    case event: GetEvents => findActorForVehicle(event.vehicleId) forward event
    case event: GetAllTracks => processGetTracks(event)
  }
}

class VehicleActor(val mgrActor: ActorRef, val vehicle: Vehicle) extends Actor with ActorLogging {

  import context._

  private var track: List[TrackPoint] = List()
  private var events: List[Event] = List()

  val vehicleSpeedingActor = actorOf(Props(new SpeedingActor(vehicle.id)), name = "vehicleSpeeding")
  val movementDetectorActor = actorOf(Props(new MovementDetectorActor(vehicle.id)), name = "movementDetector")
  val vehicleStatusActor = actorOf(Props(new VehicleStatusActor(vehicle.id, List(movementDetectorActor, vehicleSpeedingActor))), name = "vehicleStatus")

  implicit object DateTimeOrdering extends Ordering[DateTime] {
    def compare(d1: DateTime, d2: DateTime) = d1.compareTo(d2)
  }

  private def processIncomingTrack(event: IncomingTrack): Unit = {
    if (event.track.isEmpty) return
    vehicleStatusActor ! event
    track = (track ::: event.track).sortBy(_.date)
  }

  private def addEvent(event: Event): Unit = {
    // TODO: Persist event
    events = events ::: List(event) // Last event is a position to recalculate in case of failure
  }

  override def receive = LoggingReceive {
    case event: IncomingTrack => processIncomingTrack(event)
    case event: GetCurrentStatus => vehicleStatusActor forward event
    case event: Event => addEvent(event)
    case event: GetEvents => sender ! VehicleEvents(vehicle.id, events)
    case event: GetAllTracks => sender ! IncomingTrack(vehicle.id.toString, track)
  }

  override def preStart(): Unit = {
    log.info("Initializing local track cache...")
    Tracks.database.withSession { implicit session =>
      track = Tracks.list(vehicle.id).map(tp => TrackPoint(tp.date, tp.latitude, tp.longitude))
    }
  }
}

class VehicleStatusActor(vehicleId: Int, subscribers: List[ActorRef]) extends Actor with ActorLogging {

  private var lastPoint: Option[ExtendedTrackPoint] = None

  private def buildMissingTrackInfo(point: TrackPoint): ExtendedTrackPoint = {
    import scala.math._
    lastPoint match {
      case Some(x) =>
        val mileage = sqrt(pow(x.point.latitude - point.latitude, 2) + pow(x.point.longitude - point.longitude, 2))
        val millis = (point.date.getMillis - x.point.date.getMillis).toInt
        val speed = if (millis > 0) (mileage / millis * 1000 * 60 * 60).round.toInt else 0
        val angle = 0 // TODO: Write some formula
        lastPoint = Some(ExtendedTrackPoint(point, angle ,mileage, speed))
      case None =>
        val mileage = 0
        val millis = 0
        val speed = 0
        val angle = 0
        lastPoint = Some(ExtendedTrackPoint(point, angle ,mileage, speed))
    }
    lastPoint.get
  }

  private def processEvent(event: IncomingTrack, sender: ActorRef): Unit = {
    val processingInfo = ExtendedTrack(event.track.map(t => buildMissingTrackInfo(t)))
    subscribers.foreach(actor => actor.tell(processingInfo, sender)) // Resend calculated data ahead with reply to VehicleActor
    lastPoint = Some(processingInfo.track.last)
  }

  override def receive = LoggingReceive {
    case event: IncomingTrack => processEvent(event, sender())
    case event: GetCurrentStatus => sender ! VehicleStatus(vehicleId, lastPoint)
  }
}

class SpeedingActor(vehicleId: Int) extends Actor with ActorLogging {

  import context._

  private val speedLimit = 90

  def speeding: Receive = {
    case event: ExtendedTrack =>
      val goodTrack = event.track.dropWhile(_.speed > speedLimit)
      if (goodTrack.size > 0) {
        sender ! SpeedingEndEvent(vehicleId, goodTrack.head.point, goodTrack.head.speed)
        unbecome()
        self.tell(ExtendedTrack(goodTrack), sender())
      }
  }

  override def receive = LoggingReceive {
    // Will generate event for every speeding, need to use behavior to implement events SpeedingStart and SpeedingFinish
    // Is behavior persisted during actor restart/recreate?
    case event: ExtendedTrack =>
      val speedingTrack = event.track.dropWhile(_.speed <= speedLimit)
      if (speedingTrack.size > 0) {
        sender ! SpeedingBeginEvent(vehicleId, speedingTrack.head.point, speedingTrack.head.speed)
        become(speeding)
        self.tell(ExtendedTrack(speedingTrack), sender())
      }
  }
}

class MovementDetectorActor(vehicleId: Int) extends Actor with ActorLogging {

  import context._

  def moving: Receive = {
    case event: ExtendedTrack =>
      // skip all moving points and become stopped if found a point
      val stoppedTrack = event.track.dropWhile(_.speed > 0)
      if (stoppedTrack.size > 0) {
        sender ! StoppedEvent(vehicleId, stoppedTrack.head.point)
        unbecome()
        self.tell(ExtendedTrack(stoppedTrack), sender())
      }
  }

  override def receive = LoggingReceive {
    case event: ExtendedTrack =>
      // skip all non-moving points and become moving if found a point
      val movedTrack = event.track.dropWhile(_.speed <= 0)
      if (movedTrack.size > 0) {
        sender ! MovedEvent(vehicleId, movedTrack.head.point)
        become(moving)
        self.tell(ExtendedTrack(movedTrack), sender())
      }
  }

}