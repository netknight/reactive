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
      IncomingTrack("1", List(Track(new DateTime(), 0.0, 0.0), Track(new DateTime(), 1.1, 1.1))),
      IncomingTrack("1", List(Track(new DateTime(), 0.0, 0.0), Track(new DateTime(), 1.1, 1.1)))
    ))
    Ok(Json.toJson(tl))
  }

  def list = Action {
    val tracks = Tracks.database.withSession { implicit session =>
      IncomingTracks(Tracks.list.groupBy(_.vehicleId).map(tr =>
        IncomingTrack(tr._1.toString, tr._2.map(t =>
          Track(t.date, t.latitude, t.longitude)
        ))
      ).toList)
    }
    Ok(Json.toJson(tracks))

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