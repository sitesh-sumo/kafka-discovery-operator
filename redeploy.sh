kubectl delete deployment sumo-kafka-discovery-operator -n sumo-kafka-discovery-operator
sleep 2
docker rmi sumo-kafka-discovery-operator:latest
./gradlew {clean,jibDockerBuild}
kubectl apply -f k8s/operator.yaml
