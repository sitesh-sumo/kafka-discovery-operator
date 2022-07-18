package controller

import com.google.common.base.Joiner
import com.typesafe.config.parser.ConfigDocumentFactory
import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValueFactory}
import org.apache.commons.lang3.tuple.{ImmutablePair, Pair}
import org.slf4j.LoggerFactory

import java.util

class ConfigUpdater {
  private var clusterListPath: String = null
  private var disabledClusterListPath: String = null
  private var nameKey: String = null
  private var bootStrapServerKey: String = null

  def this(clusterListPath: String, nameKey: String, bootStrapServerKey: String) =  {
    this()
    this.clusterListPath = clusterListPath
    this.nameKey = nameKey
    this.bootStrapServerKey = bootStrapServerKey
    this.disabledClusterListPath = "disabled." + clusterListPath
  }

  private val log = LoggerFactory.getLogger(classOf[ConfigUpdater])

  def updated(current: String, clusterToBrokerIps: util.Map[String, String]): Pair[String, Boolean] = try {
    val config: Config = ConfigFactory.parseString(current)
    val disabledClusterListMap: util.Map[String, Config] = getClusterListMap(config, disabledClusterListPath)
    log.info("Disabled clusters: " + Joiner.on(" , ").withKeyValueSeparator(" = ").join(disabledClusterListMap))
    val enabledClusterListMap: util.Map[String, Config] = getClusterListMap(config, clusterListPath)
    log.info("Enabled clusters: " + Joiner.on(" , ").withKeyValueSeparator(" = ").join(disabledClusterListMap))
    val updatedEnabledConfigList: util.Map[String, ConfigObject] = getUpdatedEnabledConfigList(clusterToBrokerIps, enabledClusterListMap, disabledClusterListMap)
    val updatedDisabledConfigList: util.Map[String, ConfigObject] = getUpdatedDisabledConfigList(clusterToBrokerIps, enabledClusterListMap, disabledClusterListMap)
    val finalConfig: String = ConfigDocumentFactory.parseString(current).withValue(clusterListPath, ConfigValueFactory.fromIterable(updatedEnabledConfigList.values)).withValue(disabledClusterListPath, ConfigValueFactory.fromIterable(updatedDisabledConfigList.values)).render
    log.info("Final Updated Config: " + finalConfig)
    new ImmutablePair[String, Boolean](finalConfig, !(current == finalConfig))
  } catch {
    case e: Exception =>
      log.error("Parsing failed", e)
      new ImmutablePair[String, Boolean](current, false)
  }

  private def getClusterListMap(config: Config, path: String) = {
    val clusterListMap = new util.HashMap[String, Config]
    try config.getConfigList(path).stream.forEach((conf: Config) => clusterListMap.put(conf.getString(nameKey), conf))
    catch {
      case e: Exception =>
        log.error("Config error for path " + path, e)
    }
    clusterListMap
  }

  private def getUpdatedEnabledConfigList(clusterToIps: util.Map[String, String], prevEnabled: util.Map[String, Config], prevDisabled: util.Map[String, Config]) = {
    val result = new util.HashMap[String, ConfigObject]
    prevEnabled.forEach((cluster: String, config: Config) => updateAndPutIfEnabled(clusterToIps, cluster, result, config))
    prevDisabled.forEach((cluster: String, config: Config) => updateAndPutIfEnabled(clusterToIps, cluster, result, config))
    result
  }

  private def getUpdatedDisabledConfigList(clusterToIps: util.Map[String, String], prevEnabled: util.Map[String, Config], prevDisabled: util.Map[String, Config]) = {
    val result = new util.HashMap[String, ConfigObject]
    prevEnabled.forEach((cluster: String, config: Config) => updateAndPutIfDisabled(clusterToIps, cluster, result, config))
    prevDisabled.forEach((cluster: String, config: Config) => updateAndPutIfDisabled(clusterToIps, cluster, result, config))
    result
  }

  private def updateAndPutIfEnabled(clusterToIps: util.Map[String, String], cluster: String, result: util.Map[String, ConfigObject], config: Config): Unit = {
    if (clusterToIps.containsKey(cluster) && clusterToIps.get(cluster).nonEmpty)
      result.put(cluster, config.withValue(bootStrapServerKey, ConfigValueFactory.fromAnyRef(clusterToIps.get(cluster))).root)
  }

  private def updateAndPutIfDisabled(clusterToIps: util.Map[String, String], cluster: String, result: util.Map[String, ConfigObject], config: Config): Unit = {
    if (!clusterToIps.containsKey(cluster) || clusterToIps.get(cluster).isEmpty)
      result.put(cluster, config.withValue(bootStrapServerKey, ConfigValueFactory.fromAnyRef("")).root)
  }
}
