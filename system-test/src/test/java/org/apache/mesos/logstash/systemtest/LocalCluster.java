package org.apache.mesos.logstash.systemtest;

import com.containersol.minimesos.cluster.MesosCluster;
import com.containersol.minimesos.config.ClusterConfig;
import com.containersol.minimesos.mesos.ClusterUtil;
import com.containersol.minimesos.mesos.MesosMaster;
import com.containersol.minimesos.mesos.MesosAgent;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import java.util.List;
import java.util.TreeMap;

@SuppressWarnings({"PMD.AvoidUsingHardCodedIP"})
public class LocalCluster {

    private static final String DOCKER_PORT = "2376";
    public final MesosCluster cluster = new MesosCluster(ClusterUtil.withAgent(3, zooKeeper -> new MesosAgent(null, zooKeeper) {
        @Override
        public TreeMap<String, String> getDefaultEnvVars() {
            final TreeMap<String, String> envVars = super.getDefaultEnvVars();
            envVars.put("MESOS_RESOURCES", "ports(*):[9299-9299,9300-9300]");
            return envVars;
        }
    }).withMaster().withZooKeeper().build());

    public static void main(String[] args) throws Exception {
        new LocalCluster().run();
    }

    private void run() throws Exception {

        Runtime.getRuntime().addShutdownHook(new Thread(cluster::stop));
        cluster.start();

        MesosMaster master = cluster.getMasterContainer();

        DockerClientConfig.DockerClientConfigBuilder dockerConfigBuilder = DockerClientConfig
            .createDefaultConfigBuilder()
            .withUri("http://" + master.getIpAddress() + ":" + DOCKER_PORT);
        DockerClient clusterDockerClient = DockerClientBuilder
            .getInstance(dockerConfigBuilder.build()).build();

        DummyFrameworkContainer dummyFrameworkContainer = new DummyFrameworkContainer(
            clusterDockerClient, "dummy-framework");
        dummyFrameworkContainer.start(ClusterConfig.DEFAULT_TIMEOUT_SECS);

        String mesosZk = master.getFormattedZKAddress();

        System.setProperty("mesos.zk", mesosZk);
        System.setProperty("mesos.logstash.logstash.heap.size", "128");
        System.setProperty("mesos.logstash.executor.heap.size", "64");

        System.out.println("");
        System.out.println("Cluster Started.");
        System.out.println("MASTER URL: " + master.getFormattedZKAddress());
        System.out.println("");

        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(5000);
            printRunningContainers(clusterDockerClient);
        }
    }

    private void printRunningContainers(DockerClient dockerClient) {
        List<Container> containers = dockerClient.listContainersCmd().exec();
        for (Container container : containers) {
            System.out.println(container.getImage());
        }
    }

}
