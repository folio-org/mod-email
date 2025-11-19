package org.folio.services.email.impl;

import static org.folio.util.StubUtils.buildSmtpConfiguration;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class MailClientProviderTest {

  private Vertx vertx;
  private MailClientProvider provider;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    provider = new MailClientProvider(vertx);
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    provider.cleanAll()
      .onComplete(ar -> {
        vertx.close(context.asyncAssertSuccess(v -> async.complete()));
      });
  }

  @Test
  public void shouldCreateMailClientForTenant(TestContext context) {
    Async async = context.async();
    SmtpConfiguration config = buildSmtpConfiguration("user", "pass", "localhost", 587, "");

    provider.get("tenant1", config)
      .onComplete(context.asyncAssertSuccess(client -> {
        assertNotNull("Mail client should not be null", client);
        async.complete();
      }));
  }

  @Test
  public void shouldReuseSameClientWhenConfigurationUnchanged(TestContext context) {
    Async async = context.async();
    SmtpConfiguration config = buildSmtpConfiguration("user", "pass", "localhost", 587, "");

    provider.get("tenant1", config)
      .compose(firstClient ->
        provider.get("tenant1", config)
          .onComplete(context.asyncAssertSuccess(secondClient -> {
            assertSame("Should reuse the same client instance", firstClient, secondClient);
            async.complete();
          }))
      );
  }

  @Test
  public void shouldInitNewClientWhenConfigurationChanges(TestContext context) {
    Async async = context.async();
    SmtpConfiguration config1 = buildSmtpConfiguration("user", "pass", "localhost", 587, "");
    SmtpConfiguration config2 = buildSmtpConfiguration("user2", "pass2", "localhost", 587, "");

    provider.get("tenant1", config1)
      .compose(firstClient ->
        provider.get("tenant1", config2)
          .onComplete(context.asyncAssertSuccess(secondClient -> {
            assertNotSame("Should create a new client when configuration changes",
              firstClient, secondClient);
            async.complete();
          }))
      );
  }

  @Test
  public void shouldMaintainSeparateClientsPerTenant(TestContext context) {
    Async async = context.async();
    SmtpConfiguration config = buildSmtpConfiguration("user", "pass", "localhost", 587, "");

    provider.get("tenant1", config)
      .compose(client1 ->
        provider.get("tenant2", config)
          .onComplete(context.asyncAssertSuccess(client2 -> {
            assertNotSame("Different tenants should have separate client instances",
              client1, client2);
            async.complete();
          }))
      );
  }

  @Test
  public void shouldRemoveForTenant(TestContext context) {
    Async async = context.async();
    SmtpConfiguration config = buildSmtpConfiguration("user", "pass", "localhost", 587, "");

    provider.get("tenant1", config)
      .compose(firstClient -> provider.remove("tenant1"))
      .compose(v -> provider.get("tenant1", config))
      .onComplete(context.asyncAssertSuccess(newClient -> {
        assertNotNull("Should be able to create a new client after removal", newClient);
        async.complete();
      }));
  }
}

