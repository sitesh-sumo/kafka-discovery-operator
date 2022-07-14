package controller;

import crd.KMT;
import crd.KMTSpec;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import resources.ConfigMapDependentResource;

import java.util.Map;
import java.util.function.UnaryOperator;

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
        } catch (Exception e) {
            e.printStackTrace();
            resource.setStatus(e.getMessage());
        }
        resource.setStatus("ok");
        return UpdateControl.updateStatus(resource);

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
}

