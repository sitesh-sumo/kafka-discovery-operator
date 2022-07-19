package watcher

import io.fabric8.kubernetes.api.model.ConfigMap
import io.fabric8.kubernetes.client.{Config, ConfigBuilder, DefaultKubernetesClient, KubernetesClient}
import org.apache.curator.framework.recipes.cache.TreeCacheEvent.Type
import org.apache.curator.framework.recipes.cache.{TreeCache, TreeCacheEvent}
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.control.Breaks.{break, breakable}

class ZookeeperWatcher extends DefaultWatcher {

  private val log = LoggerFactory.getLogger(classOf[ZookeeperWatcher])
  private val zkPath = "/service/kafka-ingest-enriched"
  private val configName = "desired-config-map"
  var defaultRetries = 5;
  var defaultTimeToWaitMillis = 1000;

  private var kubernetesClient: KubernetesClient = null
  private var curator: CuratorFramework = null

  def this(curatorFramework: CuratorFramework, client: KubernetesClient) = {
    this()
    curator = curatorFramework
    kubernetesClient = client
  }

  def start(): Unit = {
    try {
      val cache = TreeCache.newBuilder(curator, zkPath).setCacheData(false).build
      cache.getListenable.addListener((_: CuratorFramework, event: TreeCacheEvent) => this.processTreeCacheEvent(event))
      cache.start
    } catch {
      case e: Exception =>
        log.error("Failed to attached Listener", e)
        e.printStackTrace()
    }
  }

  def processTreeCacheEvent(event: TreeCacheEvent): Unit = {
    event.getType match {
      case Type.NODE_ADDED =>
        log.info("Node Added " + event.getData.getPath)
        var retries = 0
        breakable {
          while (retries < defaultRetries) {
            log.info("retrying..{}", retries)
            val currentConfigMap: ConfigMap = kubernetesClient.configMaps.inNamespace(kubernetesClient.getNamespace).withName(configName).get
            try {
              var brokerIps = new ListBuffer[String]
              //todo - check if buggy ?
              if (!(currentConfigMap.getData.get("broker-ip") == ""))
                brokerIps += currentConfigMap.getData.get("broker-ip").split(",").mkString(",")
              val currConfigMap: ConfigMap = kubernetesClient.configMaps.inNamespace(kubernetesClient.getNamespace).withName(configName).get()
              val newData = new mutable.HashMap[String, String]()
              brokerIps += event.getData.getPath
              val newConfigMap: ConfigMap = new ConfigMap(currConfigMap.getApiVersion, currConfigMap.getBinaryData, currConfigMap.getData, currConfigMap.getImmutable, currConfigMap.getKind, currConfigMap.getMetadata)
              newData.put("broker-ip", brokerIps.toList.mkString(","))
              newConfigMap.setData(newData.asJava)
              updateConfigMap(currConfigMap, newConfigMap)
              break
            } catch {
              case ex: Exception =>
                log.error("Update Config Failed! ", ex)
                ex.printStackTrace()
                try {
                  log.info("Waiting & Retrying...")
                  Thread.sleep(defaultTimeToWaitMillis)
                } catch {
                  case e: InterruptedException =>
                    log.error("Failed to sleep", e)
                    e.printStackTrace()
                }
            }
            retries -= 1
          }
        }


      case Type.NODE_REMOVED =>
        log.info("Node Removed " + event.getData.getPath)


      case Type.NODE_UPDATED =>
        log.info("Node Updated " + event.getData.getPath)

      case _ => {}
    }
  }

  override def getClusterIps(): List[String] = {
    val config: Config = new ConfigBuilder().build()
    val client: KubernetesClient = new DefaultKubernetesClient(config)
    val zkConnectionString: String = System.getenv("zkConnect")
    val curator: CuratorFramework = CuratorFrameworkFactory.newClient(zkConnectionString, new ExponentialBackoffRetry(1000, 3))
    curator.start()

    val currentConfigMap: ConfigMap = client.configMaps.inNamespace(client.getNamespace).withName("desired-config-map").get
    currentConfigMap.getData.get("broker-ip").split(",").toList
  }

  def updateConfigMap(oldConfigMap: ConfigMap, newConfigMap: ConfigMap): Unit = {
    try {
      log.info("Updating configMap")
      val config = new ConfigBuilder().build()
      val client = new DefaultKubernetesClient(config)
      client.configMaps.inNamespace(client.getNamespace).withName(configName).lockResourceVersion(oldConfigMap.getMetadata.getResourceVersion).replace(newConfigMap)
    } catch {
      case e: Exception =>
        log.error("Failed to create Zookeeper Watcher", e)
        e.printStackTrace()
    }
  }
}
