package resource;


import crd.KMT;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@KubernetesDependent(labelSelector = "managed")
public class ConfigMapDependentResource extends KubernetesDependentResource<ConfigMap, KMT> {

    public ConfigMapDependentResource() {
        super(ConfigMap.class);
    }

    private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);

    private static String brokerIps = "192.168.0.0";

}
