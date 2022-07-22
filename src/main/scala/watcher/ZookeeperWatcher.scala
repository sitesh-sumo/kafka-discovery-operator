package watcher

import io.fabric8.kubernetes.api.model.{ConfigMap, ConfigMapBuilder}
import io.fabric8.kubernetes.client.{Config, ConfigBuilder, DefaultKubernetesClient, KubernetesClient}
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import org.apache.curator.framework.recipes.cache.TreeCacheEvent.Type
import org.apache.curator.framework.recipes.cache.{TreeCache, TreeCacheEvent}
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.control.Breaks.{break, breakable}

case class Cluster(var name: String, var brokerIps: List[String])

case class ChildCluster(var brokerIps: Option[List[String]])

case class ConfigData(var clusters: Option[List[Cluster]])

class ZookeeperWatcher extends DefaultWatcher {

  private val log = LoggerFactory.getLogger(classOf[ZookeeperWatcher])
  private val zkPath = "/service"
  private val configName = "desired-config-map"
  var defaultRetries = 5
  var defaultTimeToWaitMillis = 1000

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

  def createChildCluster(configMapData: ConfigData, clusterName: String) = {
    val cluster = Cluster(clusterName, new ListBuffer[String]().toList)
    val clusters = new ListBuffer[Cluster]

    if (configMapData.clusters.isDefined) {
      configMapData.clusters.get.foreach(c => clusters.addOne(c));
    }
    clusters.addOne(cluster)
    configMapData.clusters = Some(clusters.toList)
  }

  def createChildConfigMap(name: String) = {
    val configMaps = kubernetesClient.configMaps().inNamespace(kubernetesClient.getNamespace)
    try {
      val initConfigMapData = "{bootstrap.servers=\nzookeeper.connect=\n}"
      val newConfigMap = new ConfigMapBuilder().withNewMetadata.withNamespace(kubernetesClient.getNamespace).withName(name).endMetadata.addToData("properties", initConfigMapData).build
      configMaps.createOrReplace(newConfigMap)
    }
    catch {
      case e: Exception =>
        throw new RuntimeException(e)
    }
  }

  //todo - fetch the right IP
  def getZookeeperIp() = "172.17.0.13:2181"

  def addBrokerIpToChildConfigMap(name: String, brokerIp: String) = {
    val currentConfigMap = kubernetesClient.configMaps().inNamespace(kubernetesClient.getNamespace).withName(name).get

    val brokerIps = ListBuffer[String]()
    val bootstrapServers = currentConfigMap.getData.get("properties").split("\n")(0).split("=")
    if(bootstrapServers.size > 1)
      bootstrapServers(1).split(",").foreach(server => brokerIps.addOne(server))
    brokerIps.addOne(brokerIp)

    val newData = new mutable.HashMap[String, String]()
    val zookeeperIp = getZookeeperIp()
    val placeholder = s"bootstrap.servers=${brokerIps.toList.mkString(",")}\nzookeeper.connect=${zookeeperIp}\n"
    newData.put("properties", placeholder)
    updateConfigMap(name, currentConfigMap, newData)

    log.info("Updated Child CM")
  }

  def getClusterIndex(configMapData: ConfigData, clusterName: String): Int = {
    if (configMapData.clusters.isDefined) {
      val currCluster = configMapData.clusters.get.filter(ele => ele.name.equals(clusterName))
      if (currCluster.isEmpty)
        return -1
      configMapData.clusters.get.indexOf(currCluster.head)
    } else {
      -1
    }
  }

  def processTreeCacheEvent(event: TreeCacheEvent): Unit = {
    event.getType match {
      case Type.NODE_ADDED =>
        log.info("Node Added " + event.getData.getPath)
        val nodePath: Array[String] = event.getData.getPath.split("/").filter(ele => ele.nonEmpty)
        val clusterName = nodePath(1)
        val brokerIp = nodePath(2)

        var retries = 0
        breakable {
          while (retries < defaultRetries) {
            log.info("retrying..{}", retries)
            val currentConfigMap: ConfigMap = kubernetesClient.configMaps.inNamespace(kubernetesClient.getNamespace).withName(configName).get
            try {
              val brokerIps = new ListBuffer[String]
              var jValue: JValue = null
              var configMapData: ConfigData = null;
              var currentClusterIdx: Int = -1
              implicit val formats: DefaultFormats.type = DefaultFormats
              if (currentConfigMap.getData.get("configMapData") != null) {
                jValue = parse(currentConfigMap.getData.get("configMapData"))
                configMapData = jValue.extract[ConfigData]
                currentClusterIdx = getClusterIndex(configMapData, clusterName)
                if (currentClusterIdx == -1) {
                  createChildCluster(configMapData, clusterName)
                  createChildConfigMap(clusterName);
                  currentClusterIdx = getClusterIndex(configMapData, clusterName)
                }
                addBrokerIpToChildConfigMap(clusterName, brokerIp)
                log.info("Current Cluster Index {}", currentClusterIdx)
                if (configMapData.clusters.isDefined) {
                  configMapData.clusters.get(currentClusterIdx).brokerIps.foreach((ele) => brokerIps.addOne(ele))
                }
              }

              brokerIps += brokerIp
              if (configMapData.clusters.isDefined) {
                configMapData.clusters.get(currentClusterIdx).brokerIps = brokerIps.toList
              }

              val newData = new mutable.HashMap[String, String]()
              newData.put("configMapData", write(configMapData))
              updateConfigMap(configName, currentConfigMap, newData)

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
            retries += 1
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

  def updateConfigMap(configMapName: String, currentConfigMap: ConfigMap, newData: mutable.HashMap[String, String]): Unit = {
    try {
      log.info("Updating configMap")
      val newConfigMap: ConfigMap = new ConfigMap(currentConfigMap.getApiVersion, currentConfigMap.getBinaryData, currentConfigMap.getData, currentConfigMap.getImmutable, currentConfigMap.getKind, currentConfigMap.getMetadata)
      newConfigMap.setData(newData.asJava)

      val config = new ConfigBuilder().build()
      val client = new DefaultKubernetesClient(config)
      client.configMaps.inNamespace(client.getNamespace).withName(configMapName).lockResourceVersion(currentConfigMap.getMetadata.getResourceVersion).replace(newConfigMap)
    } catch {
      case e: Exception =>
        log.error("Failed to create Zookeeper Watcher", e)
        e.printStackTrace()
    }
  }
}
