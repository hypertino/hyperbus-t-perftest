package com.hypertino.hperftest

import com.hypertino.hyperbus.transport.api.TransportManager
import com.hypertino.service.config.ConfigLoader
import com.hypertino.service.control.ConsoleModule
import com.hypertino.service.control.api.{Service, ServiceController}
import com.typesafe.config.Config
import monix.execution.Scheduler

object EntryPoint extends ConsoleModule {
  bind [Config] to ConfigLoader()
  bind [Scheduler] to monix.execution.Scheduler.Implicits.global
  bind [TransportManager] to injected[TransportManager]
  bind [ServiceController] to injected[MyServiceController]
  bind [PerfService] to injected[PerfService]

  def main(args: Array[String]): Unit = {
    inject[ServiceController].run()
  }
}
