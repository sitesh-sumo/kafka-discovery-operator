package controller

import crd.{KMT, KMTSpec}
import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.dsl.Resource
import io.javaoperatorsdk.operator.api.reconciler.{Context, Reconciler, UpdateControl}
import org.apache.commons.lang3.tuple.Pair
import org.slf4j.LoggerFactory

import java.util
import java.util.function.UnaryOperator

class KMTReconciler extends Reconciler[KMT] {

  private val log = LoggerFactory.getLogger(classOf[KMTReconciler])
  private var monitor: KafkaBrokerConfigMonitor = null
  private var client: KubernetesClient = null
  private var configUpdater: ConfigUpdater = null

  def this(kubernetesClient: KubernetesClient, brokerConfigMonitor: KafkaBrokerConfigMonitor, configUpdater: ConfigUpdater) {
    this()
    this.client = kubernetesClient
    this.monitor = brokerConfigMonitor
    this.configUpdater = configUpdater
  }

  override def reconcile(resource: KMT, context: Context[KMT]): UpdateControl[KMT] = {
    log.info("Starting reconcile -----------")
    try {
      val spec: KMTSpec = resource.getSpec
      val configMapResource: Resource[ConfigMap] = getConfigMap(spec)
      val clusterIps: util.Map[String, String] = monitor.getClusterIps
      log.info("Cluster IPs: " + clusterIps)
      val editOp: UnaryOperator[ConfigMap] = (configMap: ConfigMap) => {
        val applicationConf: String = configMap.getData.get(spec.getConfigKey)
        log.info("Current conf = " + applicationConf)
        val updateResult: Pair[String, Boolean] = configUpdater.updated(applicationConf, clusterIps)
        if (updateResult.getRight) {
          log.info("Updating conf " + spec.getConfigKey)
          configMap.getData.put(spec.getConfigKey, updateResult.getLeft)
          log.info("Restarting target Deployment: ")
          //                    restartDeployment(spec);
          log.info("Updating to = " + configMap.getData.get(spec.getConfigKey))
        }
        else log.info("No change to applicationConf = " + configMap.getData.get(spec.getConfigKey))
        configMap
      }
      // As, the library is providing resources of ConfigMap, not ConfigMap alone.
      // So, we have to edit this way.
      val updatedConf: ConfigMap = configMapResource.edit(editOp)
      log.info("Updated applicationConf = " + updatedConf.getData.get(spec.getConfigKey))
    } catch {
      case e: Exception =>
        e.printStackTrace()
        resource.setStatus(e.getMessage)
    }
    resource.setStatus("ok")
    UpdateControl.updateStatus(resource)
  }

  private def getConfigMap(spec: KMTSpec) = client.configMaps.inNamespace(spec.getNamespace).withName(spec.getConfigName)
}
