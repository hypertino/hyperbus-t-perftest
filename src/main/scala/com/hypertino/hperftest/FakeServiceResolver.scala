package com.hypertino.hperftest

import com.hypertino.hyperbus.transport.api.{ServiceEndpoint, ServiceResolver}
import monix.eval.Task

class FakeServiceResolver extends ServiceResolver {
  override def lookupService(serviceName: String): Task[ServiceEndpoint] = Task.now {
    new ServiceEndpoint {
      override def hostname: String = "127.0.0.1"
      override def port: Option[Int] = None
    }
  }
}
