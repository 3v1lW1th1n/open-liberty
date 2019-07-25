package com.ibm.ws.jdbc.fat.db2;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.testcontainers.containers.output.OutputFrame;

import com.ibm.websphere.simplicity.ShrinkHelper;
import com.ibm.websphere.simplicity.log.Log;

import componenttest.annotation.Server;
import componenttest.annotation.TestServlet;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import db2.web.DB2TestServlet;

@RunWith(FATRunner.class)
public class DB2Test extends FATServletClient {

    public static final String APP_NAME = "db2fat";
    public static final String SERVLET_NAME = "DB2TestServlet";

    @Server("com.ibm.ws.jdbc.fat.db2")
    @TestServlet(servlet = DB2TestServlet.class, path = APP_NAME + '/' + SERVLET_NAME)
    public static LibertyServer server;

    @ClassRule
    public static DB2Container<?> db2 = new DB2Container<>()
                    .acceptLicense()
                    .withPrivilegedMode(true)
                    .withExposedPorts(50000)
                    .withLogConsumer(DB2Test::log);

    @BeforeClass
    public static void setUp() throws Exception {
        ShrinkHelper.defaultApp(server, APP_NAME, "db2.web");

        server.addEnvVar("DB2_DBNAME", db2.getDatabaseName());
        server.addEnvVar("DB2_HOSTNAME", db2.getContainerIpAddress());
        server.addEnvVar("DB2_PORT", String.valueOf(db2.getMappedPort(50000)));
        server.addEnvVar("DB2_USER", db2.getUsername());
        server.addEnvVar("DB2_PASS", db2.getPassword());

        server.startServer();

        runTest(server, APP_NAME + '/' + SERVLET_NAME, "initDatabase");
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer();
    }

    private static void log(OutputFrame frame) {
        String msg = frame.getUtf8String();
        if (msg.endsWith("\n"))
            msg = msg.substring(0, msg.length() - 1);
        Log.info(DB2Test.class, "db2", msg);
    }
}
