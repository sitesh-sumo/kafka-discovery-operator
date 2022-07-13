package resource;


import crd.KMT;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReconcileResult;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

//@KubernetesDependent(labelSelector = "managed")
//public class ConfigMapDependentResource extends CRUKubernetesDependentResource<ConfigMap, KMT> {
//
//    public ConfigMapDependentResource() {
//        super(ConfigMap.class);
//    }
//
//    private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);
//
//    private static String brokerIps = "192.168.0.0";

//    @Override
//    protected ConfigMap desired(KMT kmt, Context<KMT> context) {
//        log.info("DESIRED CALLED");
//        Map<String, String> data = new HashMap<>();
//        log.info("broker Ip desired {}", brokerIps);
//        data.put("brokerIps", brokerIps);
//        Map<String, String> labels = new HashMap<>();
//        labels.put("managed", "true");
//        return new ConfigMapBuilder()
//                .withMetadata(
//                        new ObjectMetaBuilder()
//                                .withName("managed-config-map")
//                                .withNamespace(kmt.getMetadata().getNamespace())
//                                .withLabels(labels)
//                                .build())
//                .withData(data)
//                .build();
//    }

//    @Override
//    public ConfigMap update(ConfigMap actual, ConfigMap target, KMT primary,
//                            Context<KMT> context) {
//        log.info("UPDATE CALLED");
//        log.info("actual {}", actual);
//        log.info("target {}", target);
//        var ns = actual.getMetadata().getNamespace();
//        log.info("broker Ip before update {}", brokerIps);
//        var newBrokerIps = actual.getData().get("brokerIps");
//        log.info("broker Ip after update {}", newBrokerIps);
//        brokerIps = newBrokerIps;
//        var res = super.update(actual, target, primary, context);
//        log.info("Restarting pods because broker IP has changed in {}", ns);
//        getKubernetesClient()
//                .pods()
//                .inNamespace(ns)
//                .withLabel("app", primary.getMetadata().getName())
//                .delete();
//        return res;
//    }
//}

//
@KubernetesDependent(labelSelector = "managed")
public class ConfigMapDependentResource extends KubernetesDependentResource<ConfigMap, KMT> implements Creator<ConfigMap, KMT> {

    private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);

    public ConfigMapDependentResource() {

        super(ConfigMap.class);
        log.info("CREATED MANAGED !!!");
        Map<String, String> data = new HashMap<>();
        data.put("brokerIps", "localhost");
        Map<String, String> labels = new HashMap<>();
        labels.put("managed", "true");


//        Resource<ConfigMap> currentConfigMapResource = (Resource<ConfigMap>) getKubernetesClient().configMaps().inNamespace(getKubernetesClient().getNamespace());
//
//        String name = "dep-config-map";
//        ConfigMap configMap = currentConfigMapResource.createOrReplace(new ConfigMapBuilder()
//                .withNewMetadata()
//                .withName(name)
//                .endMetadata()
//                .addToData("foo", "" + new Date())
//                .addToData("bar", "test")
//                .build());
//
//        log.info("Inserted ConfigMap at {} data {}", configMap.getMetadata().getSelfLink(), configMap.getData());
    }

    @Override
    protected ConfigMap desired(KMT primary, Context<KMT> context) {
//        return super.desired(primary, context);
        String name = "desired-config-map";
//        Resource<ConfigMap> currentConfigMapResource = (Resource<ConfigMap>) getKubernetesClient().configMaps().inNamespace(getKubernetesClient().getNamespace());
        log.info("^^ Namespace " + getKubernetesClient().getNamespace());
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withNamespace("sumo-kafka-discover-operator")
                .withName(name)
                .endMetadata()
                .addToData("foo1", "" + new Date())
                .addToData("bar1", "test1")
                .build();

        log.info("Inserted ConfigMap at {} data {}", configMap.getMetadata().getSelfLink(), configMap.getData());
        return configMap;
    }

    @Override
    public ConfigMap create(ConfigMap target, KMT primary, Context<KMT> context) {
        log.info("^^ Creating NOW");
        try {
            getKubernetesClient().configMaps().create(target);
            return super.create(target, primary, context);
        } catch (Exception e){
            log.error("Error Creating ", e);
            e.printStackTrace();
            return null;
        }
    }
}

//@KubernetesDependent(labelSelector = "managed")
//public class ConfigMapDependentResource implements DependentResource<ConfigMap, KMT>{
//
//    private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);
//
//    @Override
//    public ReconcileResult<ConfigMap> reconcile(KMT primary, Context<KMT> context) {
//        log.info("Reconciling ConfigMap!!!");
//        return ReconcileResult.noOperation(null) ;
//    }
//
//    @Override
//    public Class<ConfigMap> resourceType() {
//        return null;
//    }
//
//    @Override
//    public Optional<ConfigMap> getSecondaryResource(KMT primary) {
//        return Optional.empty();
//    }
//}