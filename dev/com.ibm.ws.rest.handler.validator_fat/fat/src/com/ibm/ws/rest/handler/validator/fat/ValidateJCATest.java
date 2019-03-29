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
package com.ibm.ws.rest.handler.validator.fat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.ResourceAdapterArchive;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ShrinkHelper;

import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.FATServletClient;
import componenttest.topology.utils.HttpsRequest;

@RunWith(FATRunner.class)
public class ValidateJCATest extends FATServletClient {
    @Server("com.ibm.ws.rest.handler.validator.jca.fat")
    public static LibertyServer server;

    private static String VERSION_REGEX = "[0-9]+\\.[0-9]+.*";

    @BeforeClass
    public static void setUp() throws Exception {
        ResourceAdapterArchive rar = ShrinkWrap.create(ResourceAdapterArchive.class, "TestValidationAdapter.rar")
                        .addAsLibraries(ShrinkWrap.create(JavaArchive.class)
                                        .addPackage("org.test.validator.adapter"));
        ShrinkHelper.exportToServer(server, "dropins", rar);

        server.startServer();

        // Wait for the API to become available
        List<String> messages = new ArrayList<>();
        messages.add("CWWKS0008I"); // CWWKS0008I: The security service is ready.
        messages.add("CWWKS4105I"); // CWWKS4105I: LTPA configuration is ready after # seconds.
        messages.add("CWPKI0803A"); // CWPKI0803A: SSL certificate created in # seconds. SSL key file: ...
        messages.add("CWWKO0219I: .* defaultHttpEndpoint-ssl"); // CWWKO0219I: TCP Channel defaultHttpEndpoint-ssl has been started and is now listening for requests on host *  (IPv6) port 8020.
        messages.add("CWWKT0016I"); // CWWKT0016I: Web application available (default_host): http://9.10.111.222:8010/ibm/api/
        messages.add("J2CA7001I: .* TestValidationAdapter"); // J2CA7001I: Resource adapter TestValidationAdapter installed in # seconds.

        server.waitForStringsInLogUsingMark(messages);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        server.stopServer("J2CA0046E: .*eis/cf-port-not-in-range" // intentionally raised error to test exception path
        );
    }

    /**
     * Attempt to validate a connectionFactory that does not exist in server configuration.
     */
    @Test
    public void testConnectionFactoryNotFound() throws Exception {
        JsonObject json = new HttpsRequest(server, "/ibm/api/validator/connectionFactory/NotAConfiguredConnectionFactory").run(JsonObject.class);
        String err = "unexpected response: " + json;
        assertEquals(err, "NotAConfiguredConnectionFactory", json.getString("uid"));
        assertNull(err, json.get("id"));
        assertNull(err, json.get("jndiName"));
        assertFalse(err, json.getBoolean("successful"));
        assertNull(err, json.get("info"));
        assertNotNull(err, json = json.getJsonObject("failure"));
        assertTrue(err, json.getString("message").contains("Did not find any configured instances of connectionFactory matching the request"));
    }

    /**
     * Validate a connectionFactory that is configured at top level with an id attribute.
     */
    @Test
    public void testTopLevelConnectionFactoryWithID() throws Exception {
        JsonObject json = new HttpsRequest(server, "/ibm/api/validator/connectionFactory/cf1").run(JsonObject.class);
        String err = "Unexpected json response: " + json;
        assertEquals(err, "cf1", json.getString("uid"));
        assertEquals(err, "cf1", json.getString("id"));
        assertEquals(err, "eis/cf1", json.getString("jndiName"));
        assertTrue(err, json.getBoolean("successful"));
        assertNull(err, json.get("failure"));
        assertNotNull(err, json = json.getJsonObject("info"));
        assertEquals(err, "TestValidationAdapter", json.getString("resourceAdapterName"));
        assertEquals(err, "28.45.53", json.getString("resourceAdapterVersion"));
        assertEquals(err, "1.7", json.getString("resourceAdapterJCASupport"));
        assertEquals(err, "OpenLiberty", json.getString("resourceAdapterVendor"));
        assertEquals(err, "This tiny resource adapter doesn't do much at all.", json.getString("resourceAdapterDescription"));
        assertEquals(err, "TestValidationEIS", json.getString("eisProductName"));
        assertEquals(err, "33.56.65", json.getString("eisProductVersion"));
        assertEquals(err, "DefaultUserName", json.getString("user"));
    }

    /**
     * Validate a connectionFactory that is configured at top level without an id attribute,
     * where the validation attempt fails with an exception that has a chain of cause exceptions,
     * some of which have error codes.
     */
    @AllowedFFDC({ "java.lang.IllegalArgumentException", // intentionally raised by mock resource adapter to cover exception paths
                   "javax.resource.spi.ResourceAllocationException" // same error as above, wrapped as ResourceAllocationException
    })
    @Test
    public void testTopLevelConnectionFactoryWithoutIDWithChainedExceptions() throws Exception {
        JsonObject json = new HttpsRequest(server, "/ibm/api/validator/connectionFactory/connectionFactory[default-1]").run(JsonObject.class);
        String err = "Unexpected json response: " + json;
        assertEquals(err, "connectionFactory[default-1]", json.getString("uid"));
        assertNull(err, json.get("id"));
        assertEquals(err, "eis/cf-port-not-in-range", json.getString("jndiName"));
        assertFalse(err, json.getBoolean("successful"));
        assertNull(err, json.get("info"));

        // Liberty wraps the IllegalArgumentException with ResourceException
        assertNotNull(err, json = json.getJsonObject("failure"));
        assertNull(err, json.get("errorCode"));
        assertEquals(err, "javax.resource.spi.ResourceAllocationException", json.getString("class"));
        JsonArray stack = json.getJsonArray("stack");
        assertNotNull(err, stack);
        assertTrue(err, stack.size() > 10); // stack is actually much longer, but size could vary
        assertTrue(err, stack.getString(0).startsWith("com."));
        assertTrue(err, stack.getString(1).startsWith("com."));
        assertTrue(err, stack.getString(2).startsWith("com."));

        assertNotNull(err, json = json.getJsonObject("cause"));
        assertNull(err, json.get("errorCode"));
        assertEquals(err, "java.lang.IllegalArgumentException", json.getString("class"));
        assertEquals(err, "22", json.getString("message"));
        stack = json.getJsonArray("stack");
        assertNotNull(err, stack);
        assertTrue(err, stack.size() > 10); // stack is actually much longer, but size could vary
        assertTrue(err, stack.getString(0).startsWith("org.test.validator.adapter.ManagedConnectionFactoryImpl.createManagedConnection(ManagedConnectionFactoryImpl.java:"));
        assertTrue(err, stack.getString(1).startsWith("com."));
        assertTrue(err, stack.getString(2).startsWith("com."));

        assertNotNull(err, json = json.getJsonObject("cause"));
        assertEquals(err, "ERR_PORT_INV", json.getString("errorCode"));
        assertEquals(err, "org.test.validator.adapter.InvalidPortException", json.getString("class"));
        assertTrue(err, json.getString("message").startsWith("Port cannot be used."));
        stack = json.getJsonArray("stack");
        assertNotNull(err, stack);
        assertTrue(err, stack.size() > 10); // stack is actually much longer, but size could vary
        assertTrue(err, stack.getString(0).startsWith("org.test.validator.adapter.ManagedConnectionFactoryImpl.createManagedConnection(ManagedConnectionFactoryImpl.java:"));
        assertTrue(err, stack.getString(1).startsWith("com."));
        assertTrue(err, stack.getString(2).startsWith("com."));
        assertTrue(err, stack.getString(2).startsWith("com."));

        assertNotNull(err, json = json.getJsonObject("cause"));
        assertEquals(err, "ERR_PORT_OOR", json.getString("errorCode"));
        assertEquals(err, "javax.resource.spi.ResourceAllocationException", json.getString("class"));
        assertTrue(err, json.getString("message").startsWith("Port not in allowed range."));
        stack = json.getJsonArray("stack");
        assertNotNull(err, stack);
        assertTrue(err, stack.size() > 10); // stack is actually much longer, but size could vary
        assertTrue(err, stack.getString(0).startsWith("org.test.validator.adapter.ManagedConnectionFactoryImpl.createManagedConnection(ManagedConnectionFactoryImpl.java:"));
        assertTrue(err, stack.getString(1).startsWith("com."));
        assertTrue(err, stack.getString(2).startsWith("com."));

        assertNotNull(err, json = json.getJsonObject("cause"));
        assertNull(err, json.get("errorCode"));
        assertEquals(err, "javax.resource.ResourceException", json.getString("class"));
        assertEquals(err, "Port number is too low.", json.getString("message"));
        stack = json.getJsonArray("stack");
        assertNotNull(err, stack);
        assertTrue(err, stack.size() > 10); // stack is actually much longer, but size could vary
        assertTrue(err, stack.getString(0).startsWith("org.test.validator.adapter.ManagedConnectionFactoryImpl.createManagedConnection(ManagedConnectionFactoryImpl.java:"));
        assertTrue(err, stack.getString(1).startsWith("com."));
        assertTrue(err, stack.getString(2).startsWith("com."));

        assertNull(err, json.get("cause"));
    }
}
