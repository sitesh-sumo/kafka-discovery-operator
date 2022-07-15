package watcher;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZookeeperWatcher implements Watcher {

    private static final Logger log = LoggerFactory.getLogger(ZookeeperWatcher.class);

    KubernetesClient kubernetesClient;

    public ZookeeperWatcher(KubernetesClient client){
        kubernetesClient = client;
    }

    public void processTreeCacheEvent(TreeCacheEvent event){
        log.info("Type " + event.getType());
        switch (event.getType()){
            case NODE_ADDED: {
                log.info("Node Added " + event.getData().getPath());
                ConfigMap currentConfigMap = kubernetesClient.configMaps().inNamespace(kubernetesClient.getNamespace()).withName("desired-config-map").get();
                try {
                    List<String> brokerIps = new ArrayList<>();
                    if(!currentConfigMap.getData().get("broker-ip").equals("")){
                        brokerIps = new ArrayList<>(List.of(currentConfigMap.getData().get("broker-ip").split(",")));
                    }
                    ConfigMap currConfigMap = kubernetesClient.configMaps().inNamespace(kubernetesClient.getNamespace()).withName("desired-config-map").get();
                    ConfigMap newConfigMap = new ConfigMap(currConfigMap.getApiVersion(), currConfigMap.getBinaryData(), currConfigMap.getData(), currConfigMap.getImmutable(), currConfigMap.getKind(), currConfigMap.getMetadata());
                    Map<String, String> newData = new HashMap<>();
                    brokerIps.add(event.getData().getPath());
                    newData.put("broker-ip", String.join(",", brokerIps));
                    newConfigMap.setData(newData);
                    updateConfigMap(currConfigMap, newConfigMap);
                    break;
                } catch (Exception ex){
                    log.error("Update Config Failed! ", ex);
                    ex.printStackTrace();
                    break;
                }
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
    public void updateConfigMap(ConfigMap oldConfigMap, ConfigMap newConfigMap){

        try {
            log.info("^^ updating configMap");
            Config config = new ConfigBuilder().build();
            KubernetesClient client = new DefaultKubernetesClient(config);
            client.configMaps().inNamespace(client.getNamespace()).withName("desired-config-map").lockResourceVersion(oldConfigMap.getMetadata().getResourceVersion()).replace(newConfigMap);
        } catch (Exception e){
            log.error("Failed to create Zookeeper Watcher", e);
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getClusterIps() throws Exception {
        Config config = new ConfigBuilder().build();
        KubernetesClient client = new DefaultKubernetesClient(config);
        String zkConnectionString = System.getenv("zkConnect");
        CuratorFramework curator = CuratorFrameworkFactory.newClient(zkConnectionString,
                new ExponentialBackoffRetry(1000, 3));
        curator.start();

        ConfigMap currentConfigMap = client.configMaps().inNamespace(client.getNamespace()).withName("desired-config-map").get();
        return List.of(currentConfigMap.getData().get("broker-ip").split(","));
    }
}
