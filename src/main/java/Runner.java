import controller.ConfigUpdater;
import controller.KMTReconciler;
import controller.KafkaBrokerConfigMonitor;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;
import watcher.ZookeeperWatcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;


public class Runner {
    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    public static void main(String[] args) {
        log.info("Starting Sumo-kafka-discovery-operator ----");
        try {
            Config config = new ConfigBuilder().build();
            KubernetesClient client = new DefaultKubernetesClient(config);
            Operator operator = new Operator(client);

            String zkConnectionString = System.getenv("zkConnect");
            log.info("zkConnect = " + zkConnectionString);

            List<String> kafkaClusters = Arrays.asList(System.getenv("kafkaClusters").split(","));
            String kafkaBasePath = System.getenv("kafkaBasePath");

            CuratorFramework curator =
                    CuratorFrameworkFactory.newClient(zkConnectionString,
                            new ExponentialBackoffRetry(1000, 3));
            curator.getConnectionStateListenable().addListener((c, newState)-> {
                log.info("state =" + newState);
            });
            curator.start();

            KafkaBrokerConfigMonitor monitor = new KafkaBrokerConfigMonitor(curator, kafkaBasePath, kafkaClusters);
            monitor.start();

            ConfigUpdater configUpdater =
                    new ConfigUpdater("kafka-lag-exporter.clusters", "name", "bootstrap-brokers");

            KMTReconciler controller = new KMTReconciler(client, monitor, configUpdater);
            operator.register(controller);
            operator.start();

            ZookeeperWatcher zookeeperWatcher = new ZookeeperWatcher(curator, client);
            zookeeperWatcher.start();


        } catch (Exception e) {
            log.info("Oops, something went wrong", e);
            e.printStackTrace();
        }

        try {
            new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080).start(Exit.NEVER);
        } catch (IOException e) {
            log.error("IO Exception ", e);
            e.printStackTrace();
        }
        log.info("Finished Sumo-kafka-discovery-operator ----");
    }
}