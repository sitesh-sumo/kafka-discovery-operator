package controller

import io.javaoperatorsdk.operator.processing.LifecycleAware
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.{ChildData, TreeCache}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsArray, JsNumber, JsObject, JsString, JsValue, Json}

import java.util


class KafkaBrokerConfigMonitor extends LifecycleAware {

  private val logger: Logger = LoggerFactory.getLogger(classOf[KafkaBrokerConfigMonitor])
  private val clusterToZNodeMap: util.Map[String, TreeCache] = new util.HashMap[String, TreeCache]
  private var kafkaBasePath: String = null

  def this(curator: CuratorFramework, kafkaBasePath: String, kafkaClusters: util.List[String]) {
    this()
    this.kafkaBasePath = kafkaBasePath
    kafkaClusters.forEach((cluster: String) => clusterToZNodeMap.put(cluster, new TreeCache(curator, kafkaBasePath + "/" + cluster)))
  }

  override def start(): Unit = {
    try {
      clusterToZNodeMap.values().forEach((treeCache) => treeCache.start())
    }
    catch {
      case e: Exception =>
        logger.error("Monitor Failed to Start", e)
        e.printStackTrace()
    }
  }

  override def stop(): Unit = {
    clusterToZNodeMap.values().forEach((treeCache) => treeCache.close())
  }

  private def parseAndPopulateMap(map: util.HashMap[String, util.List[String]], data: String): Unit = {
    val jsValue = Json.parse(data).asInstanceOf[JsObject]
    val nodeAddresses = jsValue.value("nodeAddresses").asInstanceOf[JsArray]
    val port = jsValue.value("port").asInstanceOf[JsNumber].value.toString
    val cluster = jsValue.value("properties").asInstanceOf[JsObject].value("node.cluster").asInstanceOf[JsString]
    map.putIfAbsent(cluster.value, new util.ArrayList[String])
    val ipsForCluster = map.get(cluster.value)
    nodeAddresses.value.toStream.map((x: JsValue) => x.asInstanceOf[JsString].value + ":" + port).foreach(ipsForCluster.add)
  }

  def getClusterIps: util.Map[String, String] = {
    val map = new util.HashMap[String, util.List[String]]
    clusterToZNodeMap.forEach((clusterPrefix: String, treeCache: TreeCache) => {
      val brokerNodeAndDataMap = treeCache.getCurrentChildren(kafkaBasePath + "/" + clusterPrefix)
      if (brokerNodeAndDataMap != null && !brokerNodeAndDataMap.isEmpty) brokerNodeAndDataMap.values.forEach((childData: ChildData) => {
        try {
          val brokerData = childData.getData
          val data = new String(brokerData)
          logger.info("ChildPath: " + childData.getPath)
          logger.error("Broker data: " + data)
          parseAndPopulateMap(map, data)
        } catch {
          case ex: Exception =>
            logger.error("Cluster IP Exception ex: " + ex)
        }
      })

    })
    // return map.entrySet.stream.collect(Collectors.toMap(util.Map.Entry.getKey, (e: util.Map.Entry[String, util.List[String]]) => Joiner.on(",").join(e.getValue)))
    // alternative for above
    val resultMap = new util.HashMap[String, String]()
    map.entrySet().forEach((entry) => {
      resultMap.put(entry.getKey, entry.getValue.toArray().mkString(","))
    })
    resultMap
  }
}
