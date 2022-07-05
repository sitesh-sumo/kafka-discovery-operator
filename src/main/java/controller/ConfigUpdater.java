package controller;

import com.google.common.base.Joiner;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.parser.ConfigDocumentFactory;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ConfigUpdater {
    private final String clusterListPath;
    private final String disabledClusterListPath;
    private final String nameKey;
    private final String bootStrapServerKey;

    public ConfigUpdater(String clusterListPath, String nameKey, String bootStrapServerKey) {
        this.clusterListPath = clusterListPath;
        this.nameKey = nameKey;
        this.bootStrapServerKey = bootStrapServerKey;
        this.disabledClusterListPath = "disabled." + clusterListPath;
    }

    private final Logger log = LoggerFactory.getLogger(ConfigUpdater.class);

    public Pair<String, Boolean> updated(String current, Map<String, String> clusterToBrokerIps) {
        try {
            Config config = ConfigFactory.parseString(current);

            Map<String, Config> disabledClusterListMap = getClusterListMap(config, disabledClusterListPath);
            log.info("Disabled clusters: " + Joiner.on(" , ").withKeyValueSeparator(" = ").join(disabledClusterListMap));

            Map<String, Config> enabledClusterListMap = getClusterListMap(config, clusterListPath);
            log.info("Enabled clusters: " + Joiner.on(" , ").withKeyValueSeparator(" = ").join(disabledClusterListMap));

            Map<String, ConfigObject> updatedEnabledConfigList =
                    getUpdatedEnabledConfigList(clusterToBrokerIps, enabledClusterListMap, disabledClusterListMap);

            Map<String, ConfigObject> updatedDisabledConfigList =
                    getUpdatedDisabledConfigList(clusterToBrokerIps, enabledClusterListMap, disabledClusterListMap);


            String finalConfig =
                    ConfigDocumentFactory.parseString(current)
                    .withValue(clusterListPath, ConfigValueFactory.fromIterable(updatedEnabledConfigList.values()))
                    .withValue(disabledClusterListPath, ConfigValueFactory.fromIterable(updatedDisabledConfigList.values()))
                    .render();

            log.info("Final Updated Config: " + finalConfig);
            return new ImmutablePair<>(finalConfig, !current.equals(finalConfig));
        } catch (Exception e) {
            log.error("Parsing failed", e);
            return new ImmutablePair<>(current, false);
        }
    }

    private Map<String, Config> getClusterListMap(Config config, String path) {
        Map<String, Config> clusterListMap = new HashMap<>();
        try {
            config.getConfigList(path)
                    .stream()
                    .forEach(conf -> clusterListMap.put(conf.getString(nameKey), conf));
        } catch (Exception e) {
            log.error("Config error for path " + path, e);
        }
        return clusterListMap;
    }

    private Map<String, ConfigObject> getUpdatedEnabledConfigList(Map<String, String> clusterToIps,
                                                            Map<String, Config> prevEnabled,
                                                            Map<String, Config> prevDisabled) {
        Map<String, ConfigObject>  result = new HashMap<>();
        prevEnabled.forEach((cluster, config) -> updateAndPutIfEnabled(clusterToIps, cluster, result, config));
        prevDisabled.forEach((cluster, config) -> updateAndPutIfEnabled(clusterToIps, cluster, result, config));
        return result;
    }

    private Map<String, ConfigObject> getUpdatedDisabledConfigList(Map<String, String> clusterToIps,
                                                            Map<String, Config> prevEnabled,
                                                            Map<String, Config> prevDisabled) {
        Map<String, ConfigObject>  result = new HashMap<>();
        prevEnabled.forEach((cluster, config) -> updateAndPutIfDisabled(clusterToIps, cluster, result, config));
        prevDisabled.forEach((cluster, config) -> updateAndPutIfDisabled(clusterToIps, cluster, result, config));
        return result;
    }

    private void updateAndPutIfEnabled(Map<String, String> clusterToIps, String cluster, Map<String, ConfigObject> result, Config config) {
        if (clusterToIps.containsKey(cluster) && !clusterToIps.get(cluster).isEmpty()) {
            result.put(cluster,
                    config.withValue(bootStrapServerKey, ConfigValueFactory.fromAnyRef(clusterToIps.get(cluster))).root()
            );
        }
    }

    private void updateAndPutIfDisabled(Map<String, String> clusterToIps, String cluster, Map<String, ConfigObject> result, Config config) {
        if (!clusterToIps.containsKey(cluster) || clusterToIps.get(cluster).isEmpty()) {
            result.put(cluster,
                    config.withValue(bootStrapServerKey, ConfigValueFactory.fromAnyRef("")).root()
            );
        }
    }


//    public static void main(String[] args) {
//        HashMap<String, String> map = new HashMap<>();
//        map.put("kafka-enriched-aa", "yes");
//        new ConfigUpdater(
//               "kafka-lag-exporter.clusters",
//               "name",
//               "bootstrap-brokers"
//       ).updated(disabled, map).getLeft();
//    }

    public static final  String input = "    kafka-lag-exporter {\n" +
            "      port = 8000\n" +
            "      poll-interval = 30 seconds\n" +
            "      lookup-table-size = 10\n" +
            "      client-group-id = \"kafka-lag-exporter\"\n" +
            "      kafka-client-timeout = 60 seconds\n" +
            "      clusters = [\n" +
            "        {\n" +
            "          name = \"kafka-enriched-aa\"\n" +
            "          bootstrap-brokers = \"10.0.44.42:9092, 10.0.79.7:9092, 10.0.43.19:9092\"\n" +
            "          consumer-properties = {\n" +
            "          }\n" +
            "          admin-client-properties = {\n" +
            "          }\n" +
            "          labels = {\n" +
            "          }\n" +
            "        }\n" +
            "      ]\n" +
            "      watchers = {\n" +
            "        strimzi = \"true\"\n" +
            "      }\n" +
            "      metric-whitelist = [\n" +
            "        \".*\"\n" +
            "      ]\n" +
            "    }\n" +
            "\n" +
            "    akka {\n" +
            "      loggers = [\"akka.event.slf4j.Slf4jLogger\"]\n" +
            "      loglevel = \"DEBUG\"\n" +
            "      logging-filter = \"akka.event.slf4j.Slf4jLoggingFilter\"\n" +
            "    }\n";

    public static  final String disabled = "kafka-lag-exporter {\n" +
            "      port = 8000\n" +
            "      poll-interval = 30 seconds\n" +
            "      lookup-table-size = 10\n" +
            "      client-group-id = \"kafka-lag-exporter\"\n" +
            "      kafka-client-timeout = 60 seconds\n" +
            "      clusters = []\n" +
            "      watchers = {\n" +
            "        strimzi = \"true\"\n" +
            "      }\n" +
            "      metric-whitelist = [\n" +
            "        \".*\"\n" +
            "      ]\n" +
            "    }\n" +
            "\n" +
            "    akka {\n" +
            "      loggers = [\"akka.event.slf4j.Slf4jLogger\"]\n" +
            "      loglevel = \"DEBUG\"\n" +
            "      logging-filter = \"akka.event.slf4j.Slf4jLoggingFilter\"\n" +
            "    }\n" +
            "    disabled : {\n" +
            "      kafka-lag-exporter : {\n" +
            "        clusters : [\n" +
            "                      {\n" +
            "                          \"admin-client-properties\" : {},\n" +
            "                          \"bootstrap-brokers\" : \"\",\n" +
            "                          \"consumer-properties\" : {},\n" +
            "                          \"labels\" : {},\n" +
            "                          \"name\" : \"kafka-enriched-aa\"\n" +
            "                      }\n" +
            "\n" +
            "                  ]\n" +
            "      }\n" +
            "    }";
}
