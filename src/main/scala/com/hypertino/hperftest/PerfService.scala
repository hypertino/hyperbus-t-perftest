package com.hypertino.hperftest

import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{Body, DefinedResponse, MessagingContext, Method, Ok, Request}
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.transport.api.TransportManager
import com.hypertino.service.control.api.{Console, Service}
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.atomic.AtomicInt
import monix.execution.{Cancelable, Scheduler}

import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}

@body("perf-test-data")
case class PerfTestData(i1: Int, i2: Int, s1: String, s2: String, d: Double, seq: Seq[String]) extends Body

@request(Method.POST, "hb://com.hypertino.perftest")
case class PerfPostData(body: PerfTestData) extends Request[PerfTestData]
  with DefinedResponse[Ok[PerfTestData]]

class PerfService(console: Console, transportManager: TransportManager, implicit private val scheduler: Scheduler) extends Service{
  private val hyperbus = new Hyperbus(transportManager)
  private val cores: Int = Runtime.getRuntime.availableProcessors

  private var subscription: Option[Cancelable] = None
  private var client: Option[Cancelable] = None
  private val random = new Random(100500)

  private val sentCount = AtomicInt(0)
  private val receivedCount = AtomicInt(0)
  private val confirmedCount = AtomicInt(0)
  private val failedCount = AtomicInt(0)

  private var reporter: Option[Cancelable] = startReporter()

  private val testData = PerfTestData(random.nextInt, random.nextInt, randomString(20), randomString(10), random.nextDouble(), Seq(
    randomString(20), randomString(10),
    randomString(20), randomString(10),
    randomString(20), randomString(10)
  ))

  def runServer(): Unit = {
    console.writeln("Starting server...")

    subscription = Some {
      hyperbus.commands[PerfPostData].subscribe { implicit post ⇒
        receivedCount.increment()
        post.reply {
          Success {
            val b = post.request.body
            val newData = b.copy(
              b.i1 + 1,
              b.i2 - 1,
              b.s1 + "hey",
              b.s2,
              b.d + 0.3,
              b.seq :+ "yey"
            )
            Ok(newData)
          }
        }
        Continue
      }
    }
  }

  def runClient(): Unit = {
    console.writeln("Starting client...")
    @volatile var isCanceled = false
    val cancelable = new Cancelable {
      override def cancel(): Unit = {
        isCanceled = true
      }
    }
    Task.fork {
      Task.eval {
        while(!isCanceled) {
          implicit val mcx = MessagingContext.empty

          sentCount.increment()
          hyperbus.ask(PerfPostData(testData)).runOnComplete {
            case Success(result: Ok[PerfTestData]) ⇒
              if (result.body.i1 == (testData.i1-1)) {
                confirmedCount.increment()
              }
              else {
                failedCount.increment()
              }
            case Failure(e) ⇒
              failedCount.increment()
          }
        }
      }
    }.runAsync
    client = Some(cancelable)
  }

  private def randomString(len: Int): String = {
    val sb = new StringBuilder
    0 until len foreach { _ ⇒
      sb.append(random.nextPrintableChar())
    }
    sb.toString()
  }

  def startReporter(): Option[Cancelable] = {
    @volatile var isCanceled = false
    val cancelable = new Cancelable {
      override def cancel(): Unit = {
        isCanceled = true
      }
    }
    Task.fork {
      Task.eval {
        console.writeln("Please type: server or client or quit")

        while (!isCanceled) {
          if (client.isDefined || subscription.isDefined) {
            console.writeln(s"Sent: ${sentCount.get} received: ${receivedCount.get} confirmed: ${confirmedCount.get} failed: ${failedCount.get}")
            Thread.sleep(1000)
          }
        }
      }
    }.runAsync
    Some(cancelable)
  }

  override def stopService(controlBreak: Boolean): Unit = {
    console.writeln("Stopping...")
    subscription.foreach(_.cancel())
    client.foreach(_.cancel())
    reporter.foreach(_.cancel())
    hyperbus.shutdown(10.seconds)
  }
}
