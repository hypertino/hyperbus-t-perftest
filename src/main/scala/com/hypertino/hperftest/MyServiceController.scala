package com.hypertino.hperftest

import com.hypertino.service.control.ConsoleServiceController
import scaldi.Injector

class MyServiceController(implicit injector: Injector)
  extends ConsoleServiceController  {
  private val service = inject[PerfService]

  override def customCommand = {
    case "client" ⇒ service.runClient()
    case "server" ⇒ service.runServer()
  }
}