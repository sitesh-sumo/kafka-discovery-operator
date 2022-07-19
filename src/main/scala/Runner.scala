import controller.{ConfigUpdater, KMTReconciler, KafkaBrokerConfigMonitor}
import io.fabric8.kubernetes.client.{Config, ConfigBuilder, DefaultKubernetesClient, KubernetesClient}
import io.javaoperatorsdk.operator.Operator
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import org.slf4j.LoggerFactory
import org.takes.facets.fork.{FkRegex, TkFork}
import org.takes.http.{Exit, FtBasic}
import watcher.ZookeeperWatcher

import java.io.IOException

object Runner {
  private val log = LoggerFactory.getLogger("Runner")

  def main(args: Array[String]): Unit = {
    log.info("Starting Sumo-kafka-discovery-operator ----")
    try {
      val config: Config = new ConfigBuilder().build
      val client: KubernetesClient = new DefaultKubernetesClient(config)
      val operator: Operator = new Operator(client)

      val zkConnectionString: String = System.getenv("zkConnect")
      log.info("zkConnect = " + zkConnectionString)

      val kafkaClusters: List[String] = System.getenv("kafkaClusters").split(",").toList
      val kafkaBasePath: String = System.getenv("kafkaBasePath")

      val curator: CuratorFramework = CuratorFrameworkFactory.newClient(zkConnectionString, new ExponentialBackoffRetry(1000, 3))
      curator.start()

      val monitor: KafkaBrokerConfigMonitor = new KafkaBrokerConfigMonitor(curator, kafkaBasePath, kafkaClusters)
      monitor.start()

      val configUpdater: ConfigUpdater = new ConfigUpdater("kafka-lag-exporter.clusters", "name", "bootstrap-brokers")

      val controller: KMTReconciler = new KMTReconciler(client, monitor, configUpdater)
      operator.register(controller)
      operator.start()

      val zookeeperWatcher: ZookeeperWatcher = new ZookeeperWatcher(curator, client)
      zookeeperWatcher.start()

    } catch {
      case e: Exception => {
        log.error("Oops, something went wrong", e)
        e.printStackTrace()
      }
    }

    try new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080).start(Exit.NEVER)
    catch {
      case e: IOException =>
        log.error("IO Exception ", e)
        e.printStackTrace()
    }
    log.info("Finished Sumo-kafka-discovery-operator ----")
  }
}
