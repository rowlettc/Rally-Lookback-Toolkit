package com.rallydev.lookback;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.connect.SocketConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class LookbackIntegrationTest {

    protected String username = "test1";
    protected String password = "pass1";
    protected String workspace = "999";
    protected int serverPort = 80;
    protected String serverHost = "wherever.rallydev.com";

    protected int listeningPort = 10001;


    private SocketConnection connection;
    private ContainerServer server;

    protected List<Request> requests = new ArrayList<Request>();

    protected int listeningPort() {
        return listeningPort;
    }

    @After
    public void shutdown() throws Exception {
        connection.close();
        server.stop();

    }

    @Before
    public void setupSimple() throws Exception {
        this.server = new ContainerServer(defaultContainer());
        this.connection = new SocketConnection(server);
        InetSocketAddress address = new InetSocketAddress(listeningPort());
        connection.connect(address);
    }

    protected Container assertsHeader(final Container c, final String header, final String value) {
        return new Container() {
            public void handle(Request request, Response response) {
                Assert.assertEquals(value, request.getValue(header));
                c.handle(request, response);
            }
        };
    }

    protected Container respondWith(final Container c, final int code) {
        return new Container() {
            public void handle(Request request, Response response) {
                response.setCode(code);
                c.handle(request, response);
            }
        };
    }

    protected Container addsHeader(final Container c, final String header, final String value) {
        return new Container() {
            public void handle(Request request, Response response) {
                response.setValue(header, value);
                c.handle(request, response);
            }
        };
    }

    protected Container body(final String text) {
        return new Container() {
            public void handle(Request request, Response response) {
                try {
                    PrintStream body = response.getPrintStream();
                    body.println(text);
                    body.close();
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
            }
        };
    }

    protected Container capturesRequest(final Container c) {
        return new Container() {
            public void handle(Request request, Response response) {
                requests.add(request);
                c.handle(request, response);
            }
        };
    }

    protected Container noBody() {
        return new Container() {
            public void handle(Request request, Response response) {
                try {
                    response.getPrintStream().close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        };
    }

    protected Container requestLbapiAuth() {
        Container ret = noBody();
        ret = addsHeader(ret, "WWW-Authenticate", "Basic realm=\"myRealm\"");
        ret = respondWith(ret, 401);
        ret = capturesRequest(ret);
        return ret;
    }

    protected Container returnLbapiResults() {
        String json = null;

        try {
            InputStream is = getClass().getResource("/1.json").openStream();
            Assert.assertNotNull(is);
            json = IOUtils.toString(is);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
        Container ret = body(json);
        ret = assertsHeader(ret, "Authorization", "Basic dGVzdDE6cGFzczE=");
        ret = capturesRequest(ret);
        return ret;
    }

    protected Container defaultContainer() {
        return new Container() {
            Stack<Container> containers = null;

            {
                containers = defaultContainers();
            }

            Container delegate = containers.pop();

            public void handle(Request request, Response response) {
                noisyContainer(delegate).handle(request, response);
                delegate = containers.pop();
            }
        };
    }

    protected Stack<Container> defaultContainers() {
        Stack<Container> containers = new Stack<Container>();
        containers.push(returnLbapiResults());
        containers.push(requestLbapiAuth());
        return containers;
    }


    protected Container noisyContainer(final Container c) {
        return new Container() {
            public void handle(Request request, Response response) {
                try {
                    c.handle(request, response);
                } catch (AssertionError ae) {
                    try {
                        response.getPrintStream().close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    ae.printStackTrace();
                }
            }
        };
    }
}
