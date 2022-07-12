import controller.ConfigUpdater;
import controller.KMTReconciler;
import controller.KafkaBrokerConfigMonitor;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.fabric8.kubernetes.client.ConfigBuilder;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.TkFork;
import org.takes.http.Exit;
import org.takes.http.FtBasic;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Runner {
    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    private static void processTreeCacheEvent(CuratorFramework curator, TreeCacheEvent event){
        log.info("Type " + event.getType());
        switch (event.getType()){
            case NODE_ADDED: {
                log.info("Node Added " + event.getData().getPath());
                break;
            }
            case NODE_REMOVED: {
                log.info("Node Removed " + event.getData().getPath());
                break;
            }
            case NODE_UPDATED: {
                log.info("Node Updated " + event.getData().getPath());
                break;
            }
        }
    }
    public static void main(String[] args) {
        System.out.println("Starting Sumo-kafka-discovery-operator ----");
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

            log.info("cache started");
            // todo - hardcoded for now, need to pass kafkaPath + clusterPath
            TreeCache cache = TreeCache.newBuilder(curator, "/service/kafka-ingest-enriched").setCacheData(false).build();
            cache.getListenable().addListener(Runner::processTreeCacheEvent);
            cache.start();

            KafkaBrokerConfigMonitor monitor = new KafkaBrokerConfigMonitor(curator, kafkaBasePath, kafkaClusters);
            monitor.start();

            ConfigUpdater configUpdater =
                    new ConfigUpdater("kafka-lag-exporter.clusters", "name", "bootstrap-brokers");

            KMTReconciler controller = new KMTReconciler(client, monitor, configUpdater);
            operator.register(controller);
            operator.start();
        } catch (Exception e) {
            System.out.println("Oops, something went wrong");
            e.printStackTrace();
        }

        try {
            new FtBasic(new TkFork(new FkRegex("/health", "ALL GOOD!")), 8080).start(Exit.NEVER);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Finished Sumo-kafka-discovery-operator ----");
    }
}