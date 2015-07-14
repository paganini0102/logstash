package org.apache.mesos.logstash.executor;

import com.spotify.docker.client.DefaultDockerClient;
import org.apache.mesos.MesosExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.logstash.executor.docker.DockerClient;
import org.apache.mesos.logstash.executor.docker.DockerStreamer;
import org.apache.mesos.logstash.executor.state.DockerInfoCache;
import org.apache.mesos.logstash.executor.logging.FileLogSteamWriter;
import org.apache.mesos.logstash.executor.docker.DockerLogSteamManager;
import org.apache.mesos.logstash.executor.state.GlobalStateInfo;

import java.net.*;
import java.util.Enumeration;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.HOURS;

public class Application implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(Application.class.toString());

    public static void main(String[] args) {
        new Application().run();
    }

    public void run() {
        DockerClient dockerClient = createDockerClient();
        DockerLogSteamManager streamManager = new DockerLogSteamManager(new DockerStreamer(new FileLogSteamWriter(), dockerClient));
        DockerInfoCache dockerInfoCache = new DockerInfoCache();

        ConfigManager controller = createController(dockerClient, streamManager,dockerInfoCache);
        GlobalStateInfo globalStateInfo = new GlobalStateInfo(dockerClient, streamManager, dockerInfoCache);

        LogstashExecutor executor = new LogstashExecutor(controller, globalStateInfo);
        MesosExecutorDriver driver = new MesosExecutorDriver(executor);

        dockerClient.startMonitoringContainerState(); // we start after the controller is initiated because it's sets a frameworkListener

        LOGGER.info("Mesos Logstash Executor Started");
        Protos.Status status = driver.run();
        LOGGER.info("Mesos Logstash Executor Stopped");

        if (status.equals(Protos.Status.DRIVER_STOPPED)) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }

    private ConfigManager createController(DockerClient dockerClient, DockerLogSteamManager streamManager, DockerInfoCache dockerInfoCache) {
        LogstashService logstashService = new LogstashService();

        return new ConfigManager(dockerClient, logstashService, streamManager, dockerInfoCache);
    }

    private DockerClient createDockerClient() {
        return new DockerClient(DefaultDockerClient.builder()
                .readTimeoutMillis(HOURS.toMillis(1))
                .uri(URI.create(getHostAddress()))
                .build());
    }

    private String getHostAddress() {
        String hostAddress = null;
        try {
            Enumeration<InetAddress> inetAddresses = NetworkInterface.getByName("eth0").getInetAddresses();
            while (inetAddresses.hasMoreElements()) {
                InetAddress a = inetAddresses.nextElement();
                if (a instanceof Inet6Address) {
                    continue;
                }

                hostAddress = String.format("http:/%s:2376", a.toString());
                LOGGER.info("Host address is: " + hostAddress);
            }
        } catch (SocketException se) {
            se.printStackTrace();
        }

        return hostAddress;
    }
}
