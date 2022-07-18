package crd

import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.client.{CustomResource}
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Version

@Group("kafka.monitoring")
@Version("v1")
class KMT extends CustomResource[KMTSpec, String] with Namespaced {
  override def initStatus(): String = "started"
}
