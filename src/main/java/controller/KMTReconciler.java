package controller;

import crd.KMT;
import crd.KMTSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import org.apache.curator.framework.recipes.cache.TreeCacheEvent.*;
import resource.ConfigMapDependentResource;

@ControllerConfiguration(
    dependents = {@Dependent(type = ConfigMapDependentResource.class)}
)
public class KMTReconciler implements Reconciler<KMT> {
    private final Logger log = LoggerFactory.getLogger(KMTReconciler.class);
    private final KafkaBrokerConfigMonitor monitor;
    private final KubernetesClient client;
    private final ConfigUpdater configUpdater;

    public KMTReconciler(KubernetesClient kubernetesClient,
                         KafkaBrokerConfigMonitor brokerConfigMonitor,
                         ConfigUpdater configUpdater) {
        this.client = kubernetesClient;
        this.monitor = brokerConfigMonitor;
        this.configUpdater = configUpdater;
    }

    @Override
    public UpdateControl<KMT> reconcile(KMT resource, Context context) {
        log.info("Starting reconcile -----------");
        try {

            KMTSpec spec = resource.getSpec();
            Resource<ConfigMap> configMapResource = getConfigMap(spec);

            Map<String, String> clusterIps = monitor.getClusterIps();
            log.info("Cluster IPs: " + clusterIps);
            UnaryOperator<ConfigMap> editOp = configMap -> {
                String applicationConf = configMap.getData().get(spec.getConfigKey());
                log.info("Current conf = " + applicationConf);
                Pair<String, Boolean> updateResult = configUpdater.updated(applicationConf, clusterIps);

                if (updateResult.getRight()) {
                    log.info("Updating conf " + spec.getConfigKey());
                    configMap.getData().put(spec.getConfigKey(), updateResult.getLeft());

                    log.info("Restarting target Deployment: ");
//                    restartDeployment(spec);

                    log.info("Updating to = " + configMap.getData().get(spec.getConfigKey()));
                } else {
                    log.info("No change to applicationConf = " + configMap.getData().get(spec.getConfigKey()));
                }
                return configMap;
            };

            // As, the library is providing resources of ConfigMap, not ConfigMap alone.
            // So, we have to edit this way.
            ConfigMap updatedConf = configMapResource.edit(editOp);
            log.info("Updated applicationConf = " + updatedConf.getData().get(spec.getConfigKey()));

            log.info("Creating out new configMap");
            String name = "test-config-map";
            Resource<ConfigMap> currentConfigMapResource = getConfigMapByNameSpace(spec);
            ConfigMap configMap = currentConfigMapResource.createOrReplace(new ConfigMapBuilder()
                    .withNewMetadata()
                    .withName(name)
                    .endMetadata()
                    .addToData("foo", "" + new Date())
                    .addToData("bar", "test")
                    .build());
            log.info("Inserted ConfigMap at {} data {}", configMap.getMetadata().getSelfLink(), configMap.getData());

        } catch (Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
            resource.setStatus(e.getMessage());
        }
        resource.setStatus("ok");
        //increased from 30 - 120 for testing
//        return UpdateControl.updateStatus(resource).rescheduleAfter(2, TimeUnit.MINUTES);
        return UpdateControl.patchStatus(resource);
    }

    private void restartDeployment(KMTSpec spec) {
        client.apps().deployments()
                .inNamespace("nite-monitoring")
                .withName("nite-kafka-lag-exporter")
                .rolling()
                .restart();
    }

    private Resource<ConfigMap> getConfigMap(KMTSpec spec) {
        return client.configMaps()
                .inNamespace(spec.getNamespace())
                .withName(spec.getConfigName());
    }

    private Resource<ConfigMap> getConfigMapByNameSpace(KMTSpec spec) {
        return (Resource<ConfigMap>) client.configMaps()
                .inNamespace(spec.getNamespace());
    }
}

