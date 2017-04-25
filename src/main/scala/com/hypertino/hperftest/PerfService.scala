package com.hypertino.hperftest

import com.hypertino.hyperbus.Hyperbus
import com.hypertino.hyperbus.model.{Body, DefinedResponse, MessagingContext, Method, Ok, Request}
import com.hypertino.hyperbus.model.annotations.{body, request}
import com.hypertino.hyperbus.transport.api.TransportManager
import com.hypertino.service.control.api.{Console, Service}
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.atomic.{AtomicBoolean, AtomicInt, AtomicLong}
import monix.execution.{Cancelable, Scheduler}

import scala.annotation.tailrec
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Random, Success}

@body("perf-test-data")
case class PerfTestData(i1: Int, s1: String) extends Body

@request(Method.POST, "hb://com.hypertino.perftest")
case class PerfPostData(body: PerfTestData) extends Request[PerfTestData]
  with DefinedResponse[Ok[PerfTestData]]

class PerfService(console: Console, transportManager: TransportManager, implicit private val scheduler: Scheduler) extends Service{
  private val hyperbus = new Hyperbus(transportManager)
  private val cores: Int = Runtime.getRuntime.availableProcessors

  private var subscriptions: List[Cancelable] = List.empty
  private var client = false
  private val random = new Random(100500)

  private val sentCount = AtomicInt(0)
  private val receivedCount = AtomicInt(0)
  private val confirmedCount = AtomicInt(0)
  private val failedCount = AtomicInt(0)
  private val latency = AtomicLong(0)

  private val testData = PerfTestData(random.nextInt, randomString(20))

  private val reporter: Option[Cancelable] = startReporter()
  @volatile private var shutdown = false

  //runServer()
  //Thread.sleep(1000)
  //runClient(16)

  def runServer(parallelism: Int): Unit = {
    console.writeln("Starting server...")

    subscriptions = (0 until parallelism).map { _ ⇒
      hyperbus.commands[PerfPostData].subscribe { implicit post ⇒
        post.reply {
          receivedCount.increment()
          Success {
            val b = post.request.body
            val newData = b.copy(
              b.i1 + 1,
              b.s1
            )
            Ok(newData)
          }
        }
        Continue
      }
    }.toList
  }

  def runClient(parallelism: Int, batchSize: Int): Unit = {
    console.writeln("Starting client...")
    client = true
    0 until parallelism map { _ ⇒
      Task.fork {
        def loop(): Task[Unit] = {
          implicit val mcx = MessagingContext.empty
          val nextBatch = 0 until batchSize map { _ ⇒
            if (!shutdown) {
              sentCount.increment()
              val d = PerfPostData(testData)
              val start = System.nanoTime()
              hyperbus.ask(d).map { case result: Ok[PerfTestData@unchecked] ⇒
                if (result.body.i1 == (testData.i1 + 1)) {
                  val end =
                    confirmedCount.increment()
                }
                else {
                  failedCount.increment()
                }
              } onErrorRecover {
                case NonFatal(e) ⇒
                  println(e)
                  failedCount.increment()
              } doOnFinish (_ ⇒ Task.now {
                latency.add((System.nanoTime() - start) / 1000000000)
              })
            }
            else {
              Task.unit
            }
          }
          val taskNext = Task.gatherUnordered(nextBatch)
          taskNext.flatMap(_ ⇒ loop())
        }

        if (shutdown) {
          Task.unit
        } else {
          loop()
        }
      }.runAsync
    }
  }

  private def randomString(len: Int): String = {
    val sb = new StringBuilder
    0 until len foreach { _ ⇒
      sb.append(random.nextPrintableChar())
    }
    sb.toString()
  }

  def startReporter(): Option[Cancelable] = {
    val cancelable = new Cancelable {
      override def cancel(): Unit = {
        shutdown = true
      }
    }
    Task.fork {
      Task.eval {
        console.writeln("Please type: server or client or quit")
        var last = System.nanoTime()
        var lastConf = 0
        val lastLatency = 0
        while (!shutdown) {
          if (client || subscriptions.nonEmpty) try {
            val conf = confirmedCount.get
            val latencyNow = latency.get
            val now = System.nanoTime()
            val deltaTime = (now - last) / 1000000000.0
            val rps = if (deltaTime > 0) (conf-lastConf) / deltaTime else 0
            val ltncy = if (conf > 0) (latencyNow - lastLatency) / conf * 1.0 else 0
            last = now
            lastConf = conf
            console.writeln(s"Sent: ${sentCount.get} received: ${receivedCount.get} confirmed: $conf failed: ${failedCount.get} rps: $rps latency: $ltncy")
            Thread.sleep(5000)
          } catch {
            case NonFatal(e) ⇒ // ignore
          }
        }
      }
    }.runAsync
    Some(cancelable)
  }

  override def stopService(controlBreak: Boolean): Unit = {
    console.writeln("Stopping...")
    subscriptions.foreach(_.cancel())
    //client.foreach(_.cancel())
    hyperbus.shutdown(30.seconds).runAsync
    reporter.foreach(_.cancel())
  }
}
