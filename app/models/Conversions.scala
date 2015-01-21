package models

import java.sql.Timestamp

import org.joda.time.{DateTime, Duration}

import scala.slick.driver.MySQLDriver.simple._

trait Conversions {
  
  implicit val durationColumnType = MappedColumnType.base[Duration, String](
  { d => val s = d.toString; s },
  { s => Duration.parse(s) }
  )

  implicit val datetimeColumnType = MappedColumnType.base[DateTime, Timestamp](
  { dt => new Timestamp(dt.toDate.getTime) },
  { sql => new DateTime(sql) }
  )
}
