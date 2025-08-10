package io.sentrius.sso.sshproxy.service;


import java.util.Collections;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.util.Config;

public class K8sServiceCreator {

    public static void exposePort(String namespace, String podName, int targetPort) throws Exception {
        ApiClient client = Config.defaultClient(); // works in-cluster or out
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        String serviceName = "ssh-service-" + podName;

        V1Service service = new V1Service()
            .metadata(new V1ObjectMeta()
                .name(serviceName)
                .namespace(namespace)
                .labels(Collections.singletonMap("sentrius-host", podName)))
            .spec(new V1ServiceSpec()
                .type("ClusterIP") // or "NodePort" if needed externally
                .selector(Collections.singletonMap("app", "sentrius")) // match your pod's label selector
                .ports(Collections.singletonList(new V1ServicePort()
                    .protocol("TCP")
                    .port(targetPort) // port exposed by the service
                    .targetPort(new IntOrString(targetPort)))));

        try {
            api.createNamespacedService(namespace, service).execute();
            System.out.printf("Created service `%s` exposing port %d%n", serviceName, targetPort);
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                System.out.println("Service already exists. Updating instead...");
                api.replaceNamespacedService(serviceName, namespace, service).execute();
            } else {
                throw e;
            }
        }
    }
}