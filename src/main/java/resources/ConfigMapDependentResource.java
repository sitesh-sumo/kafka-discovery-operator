package resources;

import crd.KMT;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class ConfigMapDependentResource extends KubernetesDependentResource<ConfigMap, KMT> implements Creator<ConfigMap, KMT> {

    private static final Logger log = LoggerFactory.getLogger(ConfigMapDependentResource.class);

    public ConfigMapDependentResource() {
        super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(KMT primary, Context<KMT> context) {
        String name = "desired-config-map";
        ConfigMap configMap = new ConfigMapBuilder()
                .withNewMetadata()
                .withNamespace(getKubernetesClient().getNamespace())
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
            return super.create(target, primary, context);
        } catch (Exception e) {
            log.error("Error Creating ", e);
            e.printStackTrace();
            return null;
        }
    }
}