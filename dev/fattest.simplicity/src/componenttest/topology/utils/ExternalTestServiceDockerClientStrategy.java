/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package componenttest.topology.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy;
import org.testcontainers.dockerclient.InvalidConfigurationException;

import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.ibm.websphere.simplicity.log.Log;

import componenttest.custom.junit.runner.FATRunner;

/**
 * This class is discovered by Testcontainers via META-INF/services/org.testcontainers.dockerclient.DockerClientProviderStrategy
 * Nothing else should have to directly reference this class, which is important because we will rely on
 * the FAT that wants to use Testcontainers to provide the org.testcontainers.* classes imported by this class.
 * <p>
 * If you are in the IBM network and want to use an external Docker host, run the FAT with
 *
 * <pre>
 * <code>-Dfat.test.use.remote.docker=true</code>
 * </pre>
 */
public class ExternalTestServiceDockerClientStrategy extends DockerClientProviderStrategy {

    private static final Class<?> c = ExternalTestServiceDockerClientStrategy.class;
    private static final boolean USE_REMOTE_DOCKER = Boolean.getBoolean("fat.test.use.remote.docker");

    private EnvironmentAndSystemPropertyClientProviderStrategy strat = null;

    /**
     * By default, Testcontainrs will cache the DockerClient strategy in <code>~/.testcontainers.properties</code>.
     * It is not necessary to call this method whenever Testcontainers is used, but if you want to be able to
     * automatically switch between using your local Docker install, or a remote Docker host, call this method
     * in FATSuite beforeClass setup.
     */
    public static void clearTestcontainersConfig() throws Exception {
        File testcontainersConfigFile = new File(System.getProperty("user.home"), ".testcontainers.properties");
        Log.info(c, "clearTestcontainersConfig", "Removing testcontainers property file at: " + testcontainersConfigFile.getAbsolutePath());
        try {
            Files.deleteIfExists(testcontainersConfigFile.toPath());
        } catch (IOException e) {
            Log.error(c, "clearTestcontainersConfig", e);
        }
    }

    @Override
    public void test() throws InvalidConfigurationException {
        try {
            ExternalTestService.getService("docker-engine", new AvailableDockerHostFilter());
        } catch (Exception e) {
            throw new InvalidConfigurationException("Unable to localte any healthy docker-engine instances", e);
        }
    }

    private class AvailableDockerHostFilter implements ExternalTestServiceFilter {
        @Override
        public boolean isMatched(ExternalTestService dockerService) {
            String m = "tryDockerHost";
            String dockerHostURL = "tcp://" + dockerService.getAddress() + ":" + dockerService.getPort();
            Log.info(c, m, "Checking if Docker host " + dockerHostURL + " is available and healthy...");

            System.setProperty("DOCKER_HOST", dockerHostURL);
            File certDir = new File("docker-certificates");
            certDir.mkdirs();
            writeFile(new File(certDir, "ca.pem"), dockerService.getProperties().get("ca.pem"));
            writeFile(new File(certDir, "cert.pem"), dockerService.getProperties().get("cert.pem"));
            writeFile(new File(certDir, "key.pem"), dockerService.getProperties().get("key.pem"));
            System.setProperty("DOCKER_TLS_VERIFY", "1");
            System.setProperty("DOCKER_CERT_PATH", certDir.getAbsolutePath());

            config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            strat = new EnvironmentAndSystemPropertyClientProviderStrategy();
            try {
                strat.test(); // this blows up if service is not reachable
            } catch (InvalidConfigurationException e) {
                Log.error(c, m, e, "ExternalService " + dockerService.getAddress() + ':' + dockerService.getPort() + " with props=" +
                                   dockerService.getProperties() + " failed with " + strat.getDescription());
                throw e;
            }
            client = strat.getClient();
            Log.info(c, m, "Docker host " + dockerHostURL + " is healthy.");
            return true;
        }
    }

    private static void writeFile(File outFile, String content) {
        try {
            Files.deleteIfExists(outFile.toPath());
            Files.write(outFile.toPath(), content.getBytes());
        } catch (IOException e) {
            Log.error(c, "writeFile", e);
            throw new RuntimeException(e);
        }
        Log.info(c, "writeFile", "Wrote property to: " + outFile.getAbsolutePath());
    }

    @Override
    public String getDescription() {
        return "Uses a local Docker install or a remote docker-host service via ExternalTestService. Current config:\n" +
               strat == null ? null : strat.getDescription();
    }

    @Override
    protected int getPriority() {
        return isFirstPriority() ? 900 : 0;
    }

    @Override
    protected boolean isPersistable() {
        return true;
    }

    @Override
    protected boolean isApplicable() {
        // this strategy is always applicable, but sometimes it is max priority and sometimes it is min priority
        return true;
    }

    private static boolean isFirstPriority() {
        return System.getProperty("os.name", "unknown").toLowerCase().contains("windows") || // we are on windows (no docker support)
               !FATRunner.FAT_TEST_LOCALRUN || // this is a remote runs
               USE_REMOTE_DOCKER; // or if remote docker hosts are specifically requested
    }

}
