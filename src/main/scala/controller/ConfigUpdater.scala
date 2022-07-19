package controller

import com.google.common.base.Joiner
import com.typesafe.config.parser.ConfigDocumentFactory
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValueFactory}
import org.apache.commons.lang3.tuple.{ImmutablePair, Pair}
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class ConfigUpdater {
  private var clusterListPath: String = null
  private var disabledClusterListPath: String = null
  private var nameKey: String = null
  private var bootStrapServerKey: String = null

  def this(clusterListPath: String, nameKey: String, bootStrapServerKey: String) = {
    this()
    this.clusterListPath = clusterListPath
    this.nameKey = nameKey
    this.bootStrapServerKey = bootStrapServerKey
    this.disabledClusterListPath = "disabled." + clusterListPath
  }

  private val log = LoggerFactory.getLogger(classOf[ConfigUpdater])

  def updated(current: String, clusterToBrokerIps: mutable.Map[String, String]): Pair[String, Boolean] = try {
    val config: Config = ConfigFactory.parseString(current)
    val disabledClusterListMap: mutable.Map[String, Config] = getClusterListMap(config, disabledClusterListPath)
    log.info("Disabled clusters: " + Joiner.on(" , ").withKeyValueSeparator(" = ").join(disabledClusterListMap.asJava))
    val enabledClusterListMap: mutable.Map[String, Config] = getClusterListMap(config, clusterListPath)
    log.info("Enabled clusters: " + Joiner.on(" , ").withKeyValueSeparator(" = ").join(disabledClusterListMap.asJava))
    val updatedEnabledConfigList: mutable.Map[String, ConfigObject] = getUpdatedEnabledConfigList(clusterToBrokerIps, enabledClusterListMap, disabledClusterListMap)
    val updatedDisabledConfigList: mutable.Map[String, ConfigObject] = getUpdatedDisabledConfigList(clusterToBrokerIps, enabledClusterListMap, disabledClusterListMap)
    val finalConfig: String = ConfigDocumentFactory.parseString(current).withValue(clusterListPath, ConfigValueFactory.fromIterable(updatedEnabledConfigList.values.asJava)).withValue(disabledClusterListPath, ConfigValueFactory.fromIterable(updatedDisabledConfigList.values.asJava)).render
    log.info("Final Updated Config: " + finalConfig)
    new ImmutablePair[String, Boolean](finalConfig, !(current == finalConfig))
  } catch {
    case e: Exception =>
      log.error("Parsing failed", e)
      new ImmutablePair[String, Boolean](current, false)
  }

  private def getClusterListMap(config: Config, path: String) = {
    val clusterListMap = new mutable.HashMap[String, Config]
    try config.getConfigList(path).stream.forEach((conf: Config) => clusterListMap.put(conf.getString(nameKey), conf))
    catch {
      case e: Exception =>
        log.error("Config error for path " + path, e)
    }
    clusterListMap
  }

  private def getUpdatedEnabledConfigList(clusterToIps: mutable.Map[String, String], prevEnabled: mutable.Map[String, Config], prevDisabled: mutable.Map[String, Config]) = {
    val result = new mutable.HashMap[String, ConfigObject]
    for ((cluster, config) <- prevEnabled)
      updateAndPutIfEnabled(clusterToIps, cluster, result, config)

    for ((cluster, config) <- prevDisabled)
      updateAndPutIfEnabled(clusterToIps, cluster, result, config)

    result
  }

  private def getUpdatedDisabledConfigList(clusterToIps: mutable.Map[String, String], prevEnabled: mutable.Map[String, Config], prevDisabled: mutable.Map[String, Config]) = {
    val result = new mutable.HashMap[String, ConfigObject]

    for ((cluster, config) <- prevEnabled)
      updateAndPutIfDisabled(clusterToIps, cluster, result, config)

    for ((cluster, config) <- prevDisabled)
      updateAndPutIfDisabled(clusterToIps, cluster, result, config)

    result
  }

  private def updateAndPutIfEnabled(clusterToIps: mutable.Map[String, String], cluster: String, result: mutable.Map[String, ConfigObject], config: Config): Unit = {
    if (clusterToIps.contains(cluster) && clusterToIps.contains(cluster))
      result.put(cluster, config.withValue(bootStrapServerKey, ConfigValueFactory.fromAnyRef(clusterToIps.get(cluster))).root)
  }

  private def updateAndPutIfDisabled(clusterToIps: mutable.Map[String, String], cluster: String, result: mutable.Map[String, ConfigObject], config: Config): Unit = {
    if (!clusterToIps.contains(cluster) || !clusterToIps.contains(cluster))
      result.put(cluster, config.withValue(bootStrapServerKey, ConfigValueFactory.fromAnyRef("")).root)
  }
}
