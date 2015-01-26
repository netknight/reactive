package models

import org.joda.time.DateTime
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scala.util.control.Exception._

object JsonFormats {

  implicit val jodaISODateFormat = Format[org.joda.time.DateTime] (

    Reads { (json: JsValue) =>
      lazy val error = JsError(Seq(JsPath() -> Seq(ValidationError("validate.error.expected.date.isoformat", "ISO8601"))))
      json match {
        case JsString(s) => allCatch[DateTime] opt DateTime.parse(s) match {
          case Some(d) => JsSuccess(d)
          case None => error
        }
        case _ => error
      }
    },
    Writes { (d: org.joda.time.DateTime) =>
      JsString(d.toString)
    }
  )

  implicit val trackPointFormat = Json.format[TrackPoint]
  implicit val incomingTrackFormat = Json.format[IncomingTrack]
  implicit val incomingTracksFormat = Json.format[IncomingTracks]
  implicit val extendedTrackPointFormat = Json.format[ExtendedTrackPoint]
  implicit val vehicleStatusFormat = Json.format[VehicleStatus]
  implicit val webEventFormat = Json.format[WebEvent]
  implicit val webEventsFormat = Json.format[WebEvents]

}
