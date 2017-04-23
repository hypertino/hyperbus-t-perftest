package com.hypertino.hperftest

import java.util.concurrent.Executors

import com.hypertino.hyperbus.transport.api.{ServiceResolver, TransportManager}
import com.hypertino.service.config.ConfigLoader
import com.hypertino.service.control.ConsoleModule
import com.hypertino.service.control.api.ServiceController
import com.typesafe.config.Config
import monix.execution.ExecutionModel.BatchedExecution
import monix.execution.Scheduler

object EntryPoint extends ConsoleModule {
//  lazy val scheduler = {
//    val javaService = Executors.newFixedThreadPool(64)
//    Scheduler(javaService, BatchedExecution(4000))
//  }
  lazy val scheduler = monix.execution.Scheduler.Implicits.global

  bind [Config] to ConfigLoader()
  bind [Scheduler] to scheduler
  bind [ServiceResolver] to new FakeServiceResolver
  bind [TransportManager] to injected[TransportManager]
  bind [ServiceController] to injected[MyServiceController]
  bind [PerfService] to injected[PerfService]

  def main(args: Array[String]): Unit = {
    inject[ServiceController].run()
  }
}
