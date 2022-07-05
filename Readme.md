
# Kafka Discover Operator

Monitors a target config map and updates the values from zookeeper. 
Primary use-case is to ensure open source kakfa monitoring tools can work 
with kafka-switch.

## Deployment

Steps for deploying to minikube

1. Start the minikube.
2. Create the CRD and the resource for the operator to monitor.
    ```shell
    kubectl apply -f k8s/crd.yaml
    kubectl apply -f k8s/kmt.yaml
    ```

3. Create the target config map
    ```shell
    kubectl apply -f k8s/nite-config-map.yaml 
   ```

4. Build the docker image
   ```shell
   eval $(minikube docker-env)
   ./gradlew {clean,jibDockerBuild}
   ```
   

5. Finally, launch the operator 
    ```shell
    kubectl apply -f k8s/operator.yaml
    ```
