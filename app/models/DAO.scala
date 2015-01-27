package models

import org.joda.time.DateTime
import play.api.Play.current
import play.api.db.DB
import play.api.db.slick.Config.driver.simple._
import play.api.db.slick.Session

import scala.slick.util.Logging

trait DAO {

}

trait WithId {
  def id: Int
}

case class VehicleTrack(override val id: Int, vehicleId: Int, date: DateTime, latitude: Double, longitude: Double) extends WithId
case class Vehicle(override val id: Int, number: String, vin: String) extends WithId

class TrackTable(tag: Tag) extends Table[VehicleTrack](tag, "TRACK") with Logging with Conversions {

  def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
  def vehicleId = column[Int]("VEHICLE_ID")
  def date = column[DateTime]("DATE")
  def latitude = column[Double]("LATITUDE")
  def longitude = column[Double]("LONGITUDE")

  def * = (id, vehicleId,date, longitude, latitude) <> (VehicleTrack.tupled, VehicleTrack.unapply)
}

object Tracks extends DAO {
  lazy val database = Database.forDataSource(DB.getDataSource())
  val tableQuery = TableQuery[TrackTable]

  def insert(vehicleId: Int, trackList: List[TrackPoint])(implicit session: Session) = {
    val tracks = trackList.map((t: TrackPoint) => VehicleTrack(0, vehicleId, t.date, t.latitude, t.longitude))
    tableQuery.insertAll(tracks: _*)
  }

  def list()(implicit session: Session) = {
    tableQuery.list
  }

  def list(id: Int)(implicit session: Session) = {
    tableQuery.filter(_.vehicleId === id).list
  }

  def clean()(implicit session: Session) = {
    tableQuery.delete
  }
}

class VehicleTable(tag: Tag) extends Table[Vehicle](tag, "VEHICLE") with Logging {
  def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
  def number = column[String]("NUMBER")
  def vin = column[String]("VIN")

  def * = (id, number, vin) <> (Vehicle.tupled, Vehicle.unapply)
}

object Vehicles extends DAO {
  lazy val database = Database.forDataSource(DB.getDataSource())
  val tableQuery = TableQuery[VehicleTable]

  def get(id: Int)(implicit session: Session): Option[Vehicle] = {
    tableQuery.findBy(_.id).applied(id).list.headOption
  }

  def getVehicleId(deviceId: String): Int = {
    Integer.parseInt(deviceId) // TODO: Rewrite to database value, must check is vehicle exists!
  }
}