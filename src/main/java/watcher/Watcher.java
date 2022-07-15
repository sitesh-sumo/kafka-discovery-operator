package watcher;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface Watcher {

    static final Logger log = LoggerFactory.getLogger(Watcher.class);

    public default List<String> getClusterIps() throws Exception {
        try {
            //get the original state IP
            return new ArrayList<>(Arrays.asList("127.0.0.1", "127.0.0.2"));

        } catch (Exception e) {
            log.error("Failed to create client: ", e);
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
