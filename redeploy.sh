kubectl delete configmap desired-config-map -n sumo-kafka-discovery-operator
sleep 1
kubectl delete deployment sumo-kafka-discovery-operator -n sumo-kafka-discovery-operator
sleep 3
docker rmi sumo-kafka-discovery-operator:latest
./gradlew {clean,jibDockerBuild}
kubectl apply -f k8s/operator.yaml
