package examples

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions._

object ScalaExample {

  def main(args: Array[String]): Unit = {
    try1()
    try2()
  }

  def try1(): Unit = {

    val start = System.currentTimeMillis()

    val fb = Future(Shop.dealWithBottles)
    val fm = Future(Shop.takeMeat)
    val fv = Future(Shop.takeVegetables)
    val fw = Future(Shop.waitForLongQueue())

    val quest = for {
      bottles <- fb
      meat <- fm
      vegetables <- fv
      queue <- fw
      payment <- Future(Shop.pay(Seq(bottles, meat, vegetables), 10))
    } yield payment

    quest recover {
      case e => println("Show quest failed!" + e)
    }

    while (!quest.isCompleted) {
      Thread.sleep(100)
    }

    println("Finished in " + ((System.currentTimeMillis() - start) / 1000))
  }

  def try2(): Unit = {

    val start = System.currentTimeMillis()

    val fb = Future(Shop.dealWithBottles)
    val fm = Future(Shop.takeMeat)
    val fv = Future(Shop.takeVegetables)
    val fw = Future(Shop.waitForLongQueue())

    val quest = async {
      val receipt = Seq(
        await { fb  },
        await { fm },
        await { fv }
      )
      await { fw }
      await { Future(Shop.pay(receipt, 10)) }
    }

    quest recover {
      case e => println("Show quest failed!" + e)
    }

    while (!quest.isCompleted) {
      Thread.sleep(100)
    }

    println("Finished in " + ((System.currentTimeMillis() - start) / 1000))
  }

}
