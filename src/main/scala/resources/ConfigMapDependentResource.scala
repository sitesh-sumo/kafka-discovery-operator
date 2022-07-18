package resources

import crd.KMT
import io.fabric8.kubernetes.api.model.{ConfigMap, ConfigMapBuilder}
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.processing.dependent.Creator
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource
import org.slf4j.LoggerFactory
import watcher.DefaultWatcher

class ConfigMapDependentResource extends KubernetesDependentResource[ConfigMap, KMT](classOf[ConfigMap]) with Creator[ConfigMap, KMT] with DefaultWatcher {

  private val log = LoggerFactory.getLogger(classOf[ConfigMapDependentResource])

  override protected def desired(primary: KMT, context: Context[KMT]): ConfigMap = {
    val name: String = "desired-config-map"
    var configMap: ConfigMap = null
    try configMap = new ConfigMapBuilder().withNewMetadata.withNamespace(getKubernetesClient.getNamespace).withName(name).endMetadata.addToData("broker-ip", "").build
    catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
    log.info("Inserted ConfigMap at {} data {}", configMap.getMetadata.getSelfLink, configMap.getData)
    configMap
  }

  override def create(target: ConfigMap, primary: KMT, context: Context[KMT]): ConfigMap = {
    log.info("^^ Creating NOW")
    try super.create(target, primary, context)
    catch {
      case e: Exception =>
        log.error("Error Creating ", e)
        e.printStackTrace()
        null
    }
  }
}
