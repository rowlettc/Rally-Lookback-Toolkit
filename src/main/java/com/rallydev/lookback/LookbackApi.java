package com.rallydev.lookback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

/**
 * LookbackApi objects provide an API for communicating with Rally's Lookback API service.
 * Create a LookbackApi object with the default constructor and then set your authentication
 * credentials and workspace:
 *
 *      LookbackApi api = new LookbackApi()
 *                          .setCredentials("myRallyUsername", "myRallyPassword")
 *                          .setWorkspace("myRallyWorkspace");
 *
 * Request a LookbackQuery from your LookbackApi object, it can be used to configure and execute
 * whatever query you wish to make:
 *
 *      LookbackQuery query = api.newSnapshotQuery();
 */
public class LookbackApi {

    protected URL serverUrl;
    protected String versionMajor;
    protected String versionMinor;
    protected String workspace;
    protected String username;
    protected String password;
    protected URL proxyUrl;
    protected String proxyUserName;
    protected String proxyPassword;
    protected HttpClient client;


    /**
     * Create LookbackApi objects for communicating with Rally's Lookback API.
     */
    public LookbackApi() {
        this(new DefaultHttpClient());
        Runnable shutdown = new Runnable() {
            @Override
            public void run() {
                client.getConnectionManager().shutdown();
            }
        };
        Thread shutdownHookThread = new Thread(shutdown);
        shutdownHookThread.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
    }

    public LookbackApi(HttpClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client must not be null");
        }
        this.client = client;
        try {
            serverUrl = new URL("https://rally1.rallydev.com");
        } catch (MalformedURLException mue) {
            throw new RuntimeException(mue);
        }
        versionMajor = "2";
        versionMinor = "0";
    }

    /**
     * Set your Rally credentials for use in LookbackQuery's.
     * @param username - Your Rally Username
     * @param password - Your Rally Password
     * @return LookbackApi - Enables method chaining
     */
    public LookbackApi setCredentials(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    /**
     * Set your credentials for your proxy server.  You must also set the proxy url or
     * these will be ignored.
     * At this time, only basic auth is supported.
     * @param username
     * @param password
     */
    public LookbackApi setProxyCredentials(String username, String password) {
        this.proxyUserName = username;
        this.proxyPassword = password;
        return this;
    }


    public boolean hasCredentials() {
        return username != null && password != null;
    }

    public boolean hasProxyCredentials() {
        return proxyUserName != null && proxyPassword != null;
    }

    public boolean hasProxyServer() {
        return this.proxyUrl != null;
    }

    public boolean hasServer() {
        return this.serverUrl != null;
    }

    protected Credentials getProxyCredentials() {
        return new UsernamePasswordCredentials(proxyUserName, proxyPassword);
    }

    protected Credentials getCredentials() {
        return new UsernamePasswordCredentials(username, password);
    }

    protected void applyCredentials() {
        if (! (client instanceof DefaultHttpClient)) {
            // i can only manipulate a DefaultHttpClient
            return;
        }
        DefaultHttpClient dhc = (DefaultHttpClient) client;

        if (hasProxyServer()) {
            HttpHost proxyHost = new HttpHost(proxyUrl.getHost(), proxyUrl.getPort(), proxyUrl.getProtocol());
            client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxyHost);
        }
        if (hasProxyServer() && hasProxyCredentials()) {
            AuthScope proxyAuthScope = new AuthScope(this.proxyUrl.getHost(), this.proxyUrl.getPort(), AuthScope.ANY_REALM);
            dhc.getCredentialsProvider().setCredentials(proxyAuthScope, getProxyCredentials());
        }

        if (hasCredentials() && hasServer()) {
            AuthScope authScope = new AuthScope(this.serverUrl.getHost(), this.serverUrl.getPort(), AuthScope.ANY_REALM);
            dhc.getCredentialsProvider().setCredentials(authScope, getCredentials());
        }
    }


    /**
     * Set the Rally server you wish to communicate with, by default the
     * server is set to https://rally1.rallydev.com
     * @param server - The Rally server you wish to use, must include the protocol
     * @throw RuntimeException if the server string cannot be parsed or some other URL exception occurs
     */
    public LookbackApi setServer(String server) {
        try {
            this.serverUrl = new URL(server);
        } catch (MalformedURLException mue) {
            throw new RuntimeException(mue);
        }
        return this;
    }

    /**
     * Set the proxy server you wish to communicate through.
     * @param server - The proxy server you wish to use, must include the protocol (e.g. http://myproxy:8080 )
     * @throw RuntimeException if the server string cannot be parsed or some other URL exception occurs
     */
    public LookbackApi setProxyServer(String server) {
        try {
            this.proxyUrl = new URL(server);
        } catch (MalformedURLException mue) {
            // i didn't want to do this, but i want setProxyServer to mirror setServer
            // and setServer never threw a MUE so I can only throw RE to maintain
            // the interface.
            throw new RuntimeException(mue);
        }
        return this;
    }
    /**
     * Set the Rally workspace you wish to make queries against, must be a workspace
     * for which you have read permissions.
     * @param workspace - The Rally workspace you wish to query
     * @return LookbackApi - Enables method chaining
     */
    public LookbackApi setWorkspace(String workspace) {
        this.workspace = workspace;
        return this;
    }

    /**
     * Set the version of Lookback API you wish to use, by default the version is 2.0.
     * @param major
     * @param minor
     * @return LookbackApi - Enables method chaining
     */
    public LookbackApi setVersion(String major, String minor) {
        this.versionMajor = major;
        this.versionMinor = minor;
        return this;
    }

    /**
     * Create a new LookbackQuery object for configuring a query.
     * @return LookbackQuery - new query object
     */
    public LookbackQuery newSnapshotQuery() {
        return new LookbackQuery(this);
    }

    /**
     * Use a LookbackResult to create a new LookbackQuery for the next page of results.
     * @param resultSet - The LookbackResult representing the previous page of results
     * @return LookbackQuery - Query object for the next page of data
     */
    public LookbackQuery getQueryForNextPage(LookbackResult resultSet) {
        return new LookbackQuery(resultSet, this);
    }

    LookbackResult executeQuery(LookbackQuery query) throws IOException {
        String requestJson = query.getRequestJson();
        HttpResponse response = executeRequest(requestJson);
        LookbackResult result = buildLookbackResult(response);
        return result.validate(query);
    }

    private HttpResponse executeRequest(String requestJson) throws IOException {
        HttpUriRequest request = createRequest(requestJson);

        applyCredentials();
        return client.execute(request);
    }

    private LookbackResult buildLookbackResult(HttpResponse response) throws IOException {
        HttpEntity responseBody = validateResponse(response);
        String json = getResponseJson(responseBody);
        return serializeLookbackResultFromJson(json);
    }

    private HttpUriRequest createRequest(String requestJson) throws IOException {
        HttpPost post = new HttpPost(buildUrl());
        post.setEntity(new StringEntity(requestJson, "UTF-8"));
        return post;
    }

    private HttpEntity validateResponse(HttpResponse response) {
        if (authorizationFailed(response)) {
            throw new LookbackException("Authorization failed, check username and password");
        }
        HttpEntity responseBody = response.getEntity();
        if (responseBody == null) {
            throw new LookbackException("No data received from server");
        }
        return responseBody;
    }

    private String getResponseJson(HttpEntity responseBody) throws IOException {
        InputStream responseStream = responseBody.getContent();
        try {
            return readFromStream(responseStream);
        } finally {
            responseStream.close();
        }
    }

    private LookbackResult serializeLookbackResultFromJson(String json) {
        Gson serializer = new GsonBuilder().serializeNulls().create();
        return serializer.fromJson(json, LookbackResult.class);
    }

    private boolean authorizationFailed(HttpResponse response) {
        return response.getStatusLine().getStatusCode() == 401;
    }

    private String buildUrl() {
        if (workspace == null) {
            throw new LookbackException("Workspace is required to execute query");
        }

        return String.format(
                "%s/analytics/%s/service/rally/workspace/%s/artifact/snapshot/query.js",
                serverUrl.toExternalForm(), buildApiVersion(), workspace);
    }

    private String readFromStream(InputStream stream) {
        Scanner scanner = new Scanner(stream);
        scanner.useDelimiter("\\A");
        if (scanner.hasNext()) {
            return scanner.next();
        } else {
            return "";
        }
    }

    private String buildApiVersion() {
        return "v" + versionMajor + "." + versionMinor;
    }

}
