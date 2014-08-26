package com.rallydev.lookback;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Iterator;

public class LookbackApiTest extends LookbackIntegrationTest {

    private String workspace = "888";

    private LookbackApi api;

    @Before
    public void setUp() {
        api = new LookbackApi()
                .setServer("http://localhost:" + listeningPort())
                .setCredentials(username, password)
                .setWorkspace(workspace);
    }


    @Test
    public void makeQuery() throws Exception {
        LookbackResult result = api.newSnapshotQuery()
                .addFindClause("_TypeHierarchy", -51038)
                .addFindClause("Children", null)
                .addFindClause("_ItemHierarchy", new BigInteger("5103028089"))
                .execute();

        Assert.assertEquals(2, requests.size());
        Assert.assertEquals("there should be 2 results", 2, result.Results.size());
    }


    @Test
    public void makeInlineQuery() {
        Iterator resultIterator = new LookbackApi()
                .setWorkspace(workspace)
                .setServer("http://localhost:" + listeningPort())
                .setCredentials(username, password)
                .newSnapshotQuery()
                .addFindClause("_TypeHierarchy", -51038)
                .addFindClause("Children", null)
                .addFindClause("_ItemHierarchy", new BigInteger("5103028089"))
                .execute()
                .getResultsIterator();
    }
}
