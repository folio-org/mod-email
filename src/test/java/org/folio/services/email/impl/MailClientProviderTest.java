package org.folio.services.email.impl;

import static org.folio.util.StubUtils.buildSmtpConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.apache.commons.lang3.tuple.Pair;

@RunWith(VertxUnitRunner.class)
public class MailClientProviderTest {

  private static final String TENANT_ID = "test_tenant";

  private Vertx vertx;
  private MailClientProvider provider;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    provider = new MailClientProvider(vertx);
  }

  @After
  public void tearDown(TestContext context) {
    provider.cleanAll()
      .compose(ar -> vertx.close())
      .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void get_positive_createNewConfiguration(TestContext context) {
    var config = smtpConfiguration();

    var resultFuture = provider.get(TENANT_ID, config);
    resultFuture.onComplete(context.asyncAssertSuccess(Assert::assertNotNull));

    var configuration = provider.getConfiguration(TENANT_ID);
    assertEquals(smtpConfiguration(), configuration);
  }

  @Test
  public void get_positive_sameClientForIdenticalConfiguration(TestContext context) {
    provider.get(TENANT_ID, smtpConfiguration())
      .compose(firstClient -> provider.get(TENANT_ID, smtpConfiguration())
        .map(secondClient -> Pair.of(firstClient, secondClient)))
      .onComplete(context.asyncAssertSuccess(clientPair ->
        assertSame(clientPair.getLeft(), clientPair.getRight())
      ));
  }

  @Test
  public void get_positive_shouldInitClientForNewConfig(TestContext context) {
    provider.get(TENANT_ID, smtpConfiguration("password-1"))
      .compose(firstClient -> provider.get(TENANT_ID, smtpConfiguration("password-2"))
        .map(secondClient -> Pair.of(firstClient, secondClient)))
      .onComplete(context.asyncAssertSuccess(clientPair ->
        assertNotSame(clientPair.getLeft(), clientPair.getRight())
      ));
  }

  @Test
  public void get_positive_shouldCreateDifferentClients(TestContext context) {
    var tenantId1 = "tenant1";
    var tenantId2 = "tenant2";

    provider.get(tenantId1, smtpConfiguration())
      .compose(firstClient -> provider.get(tenantId2, smtpConfiguration())
        .map(secondClient -> Pair.of(firstClient, secondClient)))
      .onComplete(context.asyncAssertSuccess(clientPair ->
        assertNotSame(clientPair.getLeft(), clientPair.getRight())
      ));
  }

  @Test
  public void get_positive_shouldRemoveDifferentClients(TestContext context) {
    var config = smtpConfiguration();

    provider.get(TENANT_ID, smtpConfiguration())
      .compose(firstClient -> provider.remove(TENANT_ID).map(v -> firstClient))
      .compose(firstClient -> provider.get("tenant1", config)
        .map(secondClient -> Pair.of(firstClient, secondClient)))
      .onComplete(context.asyncAssertSuccess(clientPair -> {
        assertNotSame(clientPair.getLeft(), clientPair.getRight());
      }));
  }

  @Test
  public void remove_positive_emptyCache(TestContext context) {
    provider.remove(TENANT_ID).onComplete(context.asyncAssertSuccess());
  }

  private static SmtpConfiguration smtpConfiguration() {
    return buildSmtpConfiguration("test-user", "test-password", "localhost", 587, "");
  }

  private static SmtpConfiguration smtpConfiguration(String pass) {
    return buildSmtpConfiguration("test-user", pass, "localhost", 587, "");
  }
}

