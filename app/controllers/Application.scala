package controllers

import models._
import org.joda.time.DateTime
import play.api._
import play.api.libs.json.Json
import play.api.mvc._
import services.AkkaService

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Success}

object Application extends Controller {

  import models.JsonFormats._

  def index = Action {
    val tl = IncomingTracks(List(
      IncomingTrack("1", List(TrackPoint(new DateTime(), 0.0, 0.0), TrackPoint(new DateTime(), 1.1, 1.1))),
      IncomingTrack("1", List(TrackPoint(new DateTime(), 0.0, 0.0), TrackPoint(new DateTime(), 1.1, 1.1)))
    ))
    Ok(Json.toJson(tl))
  }

  def list = Action {
    // TODO: Rewrite to ask from Actor
    val tracks = Tracks.database.withSession { implicit session =>
      IncomingTracks(Tracks.list.groupBy(_.vehicleId).map(tr =>
        IncomingTrack(tr._1.toString, tr._2.map(t =>
          TrackPoint(t.date, t.latitude, t.longitude)
        ))
      ).toList)
    }
    Ok(Json.toJson(tracks))
  }

  def status(vehicleId: Int) = Action.async { implicit request =>
    Future.successful(Ok(Json.toJson(AkkaService.getStatus(vehicleId))))
  }

  def events(vehicleId: Int) = Action.async { implicit request =>
    Future.successful(Ok(Json.toJson(Convertor.convert(AkkaService.getEvents(vehicleId)))))
  }

  def track = Action.async(parse.json) { implicit request =>
    request.body.validate[IncomingTracks].asEither match {
      case Left(error) =>
        Logger.error(s"failed to  parse/validate request ${request.body} from ${request.remoteAddress}:\n${error.mkString("\n")}")
        Future.successful(BadRequest(""))
      case Right(req) =>
        val result = AkkaService.pushTrack(req).payload
        result match {
          case Success(v) => Future.successful(Created(""))
          case Failure(v) => Future.successful(InternalServerError(v.getMessage))
        }
    }
  }
}