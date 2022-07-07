package controller;

import com.google.common.base.Joiner;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.libs.json.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KafkaBrokerConfigMonitor implements LifecycleAware {
    private final Logger logger = LoggerFactory.getLogger(KafkaBrokerConfigMonitor.class);
    private final Map<String, TreeCache> clusterToZNodeMap = new HashMap<>();
    private final String kafkaBasePath;

    public KafkaBrokerConfigMonitor(CuratorFramework curator, String kafkaBasePath, List<String> kafkaClusters) {
        this.kafkaBasePath = kafkaBasePath;
        kafkaClusters.forEach(
                cluster -> clusterToZNodeMap.put(cluster, new TreeCache(curator, kafkaBasePath +"/" + cluster))
        );
    }

    public Map<String, String> getClusterIps() {
        HashMap<String, List<String>> map = new HashMap<>();
        clusterToZNodeMap.forEach((clusterPrefix, treeCache) -> {
            Map<String, ChildData> brokerNodeAndDataMap =
                    treeCache.getCurrentChildren(kafkaBasePath + "/" + clusterPrefix);
            if (brokerNodeAndDataMap != null && !brokerNodeAndDataMap.isEmpty()) {
                brokerNodeAndDataMap.values().forEach(childData -> {
                    try {
                        byte[] brokerData = childData.getData();
                        String data = new String(brokerData);

                        logger.info("ChildPath: " + childData.getPath());
                        logger.error("Broker data: " + data);
                        parseAndPopulateMap(map, data);
                    } catch (Exception ex) {
                        logger.error("Cluster IP Exception ex: " + ex);
                    }
                });
            }
        });
        return map.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Joiner.on(",").join(e.getValue())
                ));
    }

    private static void parseAndPopulateMap(HashMap<String, List<String>> map, String data) {
        JsObject jsValue = (JsObject) Json.parse(data);
        JsArray nodeAddresses = (JsArray) jsValue.underlying().get("nodeAddresses").get();

        String port = ((JsNumber) jsValue.underlying().get("port").get()).value().toString();

        JsString cluster = (JsString) ((JsObject) jsValue.underlying().get("properties").get())
                .underlying().get("node.cluster").get();

        map.putIfAbsent(cluster.value(), new ArrayList<>());
        List<String> ipsForCluster = map.get(cluster.value());

        nodeAddresses.value()
                .toStream()
                .map(x -> ((JsString) x).value() + ":" + port)
                .foreach(ipsForCluster::add);
    }

    @Override
    public void start() throws OperatorException {
        try {
            for (TreeCache treeCache : clusterToZNodeMap.values()) {
                treeCache.start();
            }
        } catch (Exception e) {
            logger.error("Monitor Failed to Start",  e);
            e.printStackTrace();
        }
    }

    @Override
    public void stop() throws OperatorException {
        for (TreeCache treeCache : clusterToZNodeMap.values()) {
            treeCache.close();
        }
    }
}
