package crd

import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.client.{CustomResource, Version}
import io.fabric8.kubernetes.model.annotation.Group

@Group("kafka.monitoring")
@Version("v1")
class KMT extends CustomResource[KMTSpec, String] with Namespaced {
  override def initStatus(): String = "started"
}
