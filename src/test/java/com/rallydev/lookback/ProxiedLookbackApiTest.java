package com.rallydev.lookback;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.simpleframework.http.core.Container;

import java.io.InputStream;
import java.util.Stack;

public class ProxiedLookbackApiTest extends LookbackIntegrationTest {

    private String proxyuser = "test";
    private String proxypass = "pass";
    private String proxyHost = "localhost";


    @Test
    public void worksWithCustomHttpClient() {
        // this test simply shows that you can pass in your own http client that you MANAGE
        // and the LookbackApi will use it instead of creating a DefaultHttpClient
        DefaultHttpClient c = new DefaultHttpClient();
        c.getCredentialsProvider().setCredentials(new AuthScope(proxyHost, listeningPort()), new UsernamePasswordCredentials(proxyuser, proxypass));
        c.getCredentialsProvider().setCredentials(new AuthScope(serverHost, serverPort), new UsernamePasswordCredentials(username, password));
        c.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, listeningPort()));

        // credentials are set above
        LookbackApi a = new LookbackApi(c)
                .setServer("http://" + serverHost)
                .setWorkspace(workspace);
        LookbackResult result = a.newSnapshotQuery()
                .addFindClause("_TypeHierarchy", -51038)
                .execute();

        Assert.assertEquals("there should be 2 results", 2, result.Results.size());
    }

    @Test
    public void worksThroughProxy() {
        LookbackApi api = new LookbackApi()
                .setServer("http://" + serverHost)
                .setProxyServer("http://" + proxyHost + ":" + listeningPort())
                .setCredentials(username, password)
                .setProxyCredentials(proxyuser, proxypass)
                .setWorkspace(workspace);
        LookbackResult result = api.newSnapshotQuery()
                .addFindClause("_TypeHierarchy", -51038)
                .execute();

        Assert.assertEquals("there should be 2 results", 2, result.Results.size());
    }


    protected Stack<Container> defaultContainers() {
        // these are executed in reverse order.
        // first, proxy auth is required, (407 is returned)
        // second, lbapi responds with 401
        // last, lbapi returns results.
        Stack<Container> containers = new Stack<Container>();
        containers.push(returnLbapiResults());
        containers.push(requestLbapiAuth());
        containers.push(requestProxyAuth());
        return containers;
    }


    protected Container requestProxyAuth() {
        Container ret = noBody();
        ret = capturesRequest(ret);
        ret = respondWith(ret, 407);
        ret = addsHeader(ret, "Proxy-Authenticate", "Basic realm=\"theproxy\"");
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
        ret = capturesRequest(ret);
        ret = assertsHeader(ret, "Proxy-Authorization", "Basic dGVzdDpwYXNz");
        ret = assertsHeader(ret, "Authorization", "Basic dGVzdDE6cGFzczE=");
        return ret;
    }

    protected Container requestLbapiAuth() {
        Container ret = noBody();
        ret = capturesRequest(ret);
        ret = assertsHeader(ret, "Proxy-Authorization", "Basic dGVzdDpwYXNz");
        ret = addsHeader(ret, "WWW-Authenticate", "Basic realm=\"myRealm\"");
        ret = respondWith(ret, 401);
        return ret;
    }

}
