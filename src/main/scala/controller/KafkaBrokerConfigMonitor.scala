package controller

import io.javaoperatorsdk.operator.processing.LifecycleAware
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.cache.{ChildData, TreeCache}
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer


class KafkaBrokerConfigMonitor extends LifecycleAware {

  private val logger: Logger = LoggerFactory.getLogger(classOf[KafkaBrokerConfigMonitor])
  private val clusterToZNodeMap = new mutable.HashMap[String, TreeCache]
  private var kafkaBasePath: String = null

  def this(curator: CuratorFramework, kafkaBasePath: String, kafkaClusters: List[String]) = {
    this()
    this.kafkaBasePath = kafkaBasePath
    kafkaClusters.foreach((cluster: String) => clusterToZNodeMap += (cluster -> new TreeCache(curator, kafkaBasePath + "/" + cluster)))
  }

  override def start(): Unit = {
    try {
      for ((_, treeCache) <- clusterToZNodeMap)
        treeCache.start()
    }
    catch {
      case e: Exception =>
        logger.error("Monitor Failed to Start", e)
        e.printStackTrace()
    }
  }

  override def stop(): Unit = {
    for ((_, treeCache) <- clusterToZNodeMap)
      treeCache.close()
  }

  private def parseAndPopulateMap(map: mutable.HashMap[String, List[String]], data: String): Unit = {
    val jsValue = Json.parse(data).asInstanceOf[JsObject]
    val nodeAddresses = jsValue.value("nodeAddresses").asInstanceOf[JsArray]
    val port = jsValue.value("port").asInstanceOf[JsNumber].value.toString
    val cluster = jsValue.value("properties").asInstanceOf[JsObject].value("node.cluster").asInstanceOf[JsString]
    val ipsForClusterBuffer = new ListBuffer[String]()
    map.getOrElse(cluster.value, new ListBuffer[String].toList).foreach((ele) => ipsForClusterBuffer.addOne(ele))
    nodeAddresses.value.to(LazyList).map((x: JsValue) => x.asInstanceOf[JsString].value + ":" + port).foreach((ele) => {
      ipsForClusterBuffer += ele
    })
    map.put(cluster.value, ipsForClusterBuffer.toList)
  }

  def getClusterIps: mutable.Map[String, String] = {
    val map = new mutable.HashMap[String, List[String]]
    for ((clusterPrefix, treeCache) <- clusterToZNodeMap) {
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

    }
    // return map.entrySet.stream.collect(Collectors.toMap(util.Map.Entry.getKey, (e: util.Map.Entry[String, util.List[String]]) => Joiner.on(",").join(e.getValue)))
    // alternative for above
    val resultMap = new mutable.HashMap[String, String]()
    for ((key, value) <- map)
      resultMap.put(key, value.toArray.mkString(","))
    resultMap
  }
}
