package actor

import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import actors.{VehicleStatusActor, MovementDetectorActor, SpeedingActor}
import akka.actor.{Props, ActorSystem}
import models._
import org.joda.time.DateTime
import org.scalatest.{Matchers, BeforeAndAfterAll, FeatureSpecLike}
import play.api.test.FakeApplication
import services.AkkaService

import scala.concurrent.duration._

class ActorSpec(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with FeatureSpecLike with Matchers with BeforeAndAfterAll {

  lazy val dt = new DateTime
  lazy val vehicleId = 99

  def this() = this(ActorSystem("ActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  private def buildTrack(speed: Int*): ExtendedTrack = {
    ExtendedTrack(speed.map(s => ExtendedTrackPoint(TrackPoint(dt, 0.0, 0.0), 0, 0.0, s)).toList)
  }

  feature("SpeedingActor") {
    val actor = system.actorOf(Props(new SpeedingActor(vehicleId)))
    scenario("No speeding: (0, 1, 89) km/h values") {
      actor ! buildTrack(0, 1, 89)
      expectNoMsg(1 second)
    }
    scenario("Speeding: (0, 89, 100, 101, 99, 89") {
      val track = buildTrack(0, 89, 100, 101, 99, 89)
      actor ! track
      expectMsg(SpeedingBeginEvent(vehicleId, track.track.head.point, 100))
      expectMsg(SpeedingEndEvent(vehicleId, track.track.head.point, 89))
    }
  }

  feature("MovingActor") {
    val actor = system.actorOf(Props(new MovementDetectorActor(vehicleId)))
    scenario("No movement") {
      actor ! buildTrack(0, 0, 0)
      expectNoMsg(1 second)
    }
    scenario("Start and stop: 0, 0, 1, 99, 0") {
      val track = buildTrack(0, 0, 1, 99, 0)
      actor ! track
      expectMsg(MovedEvent(vehicleId, track.track.head.point))
      expectMsg(StoppedEvent(vehicleId, track.track.head.point))
    }
  }

  feature("VehicleStatusActor") {
    val probe1 =  TestProbe()
    val probe2 = TestProbe()
    val actor = system.actorOf(Props(new VehicleStatusActor(1, List(probe1.ref, probe2.ref))))
    scenario("First point & multiple subscribers") {
      val tp = TrackPoint(dt, 0.0, 0.0)
      actor ! IncomingTrack("1", List(tp))
      probe1.expectMsg(ExtendedTrack(List(ExtendedTrackPoint(tp, 0, 0.0, 0))))
      probe2.expectMsg(ExtendedTrack(List(ExtendedTrackPoint(tp, 0, 0.0, 0))))
    }
    scenario("Track calculations") {
      val tp1 = TrackPoint(dt, 1.0, 1.0)
      val tp2 = TrackPoint(dt.plusMinutes(10), 10.0, 10.0)
      actor ! IncomingTrack("1", List(tp1, tp2))
      probe1.expectMsg(ExtendedTrack(List(ExtendedTrackPoint(tp1, 0, 1.4142135623730951, 0), ExtendedTrackPoint(tp2, 0, 12.727922061357855, 76))))
    }
  }

  feature("Full stack") {
    val tp1 = TrackPoint(dt, 1.0, 1.0)
    val tp2 = TrackPoint(dt.plusMinutes(10), 14.0, 14.0)
    scenario("speeding") {
      import play.api.test.Helpers.running
      running(FakeApplication()) {
        val trackSaved = AkkaService.pushTrack(IncomingTracks(List(IncomingTrack("1", List(tp1, tp2)))))
        trackSaved.payload.isSuccess shouldEqual true
        val status = AkkaService.getStatus(1)
        status.vehicleId shouldEqual 1
        status.lastPoint should not be empty
        val events = AkkaService.getEvents(1)
        events.events.size shouldEqual 2
        events.events shouldEqual List(SpeedingBeginEvent(1, tp2, 110), MovedEvent(1, tp2))
      }
    }
  }

}
