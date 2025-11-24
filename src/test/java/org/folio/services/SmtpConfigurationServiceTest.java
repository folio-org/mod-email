package org.folio.services;

import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import io.vertx.core.Future;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.util.UUID;
import org.folio.rest.persist.Conn;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(VertxUnitRunner.class)
public class SmtpConfigurationServiceTest {

  @InjectMocks private SmtpConfigurationService smtpConfigurationService;
  @Mock private Conn conn;
  @Mock private RowSet<Row> rowSet;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);

  @Test
  public void deleteSmtpConfiguration_positive(TestContext context) {
    var id = UUID.randomUUID().toString();

    when(conn.delete("smtp_configuration", id)).thenReturn(Future.succeededFuture(rowSet));
    when(rowSet.rowCount()).thenReturn(1);

    smtpConfigurationService.deleteSmtpConfiguration(conn, id)
      .onComplete(context.asyncAssertSuccess(Assert::assertTrue));
  }

  @Test
  public void deleteSmtpConfiguration_positive_zeroRowsDeleted(TestContext context) {
    var id = UUID.randomUUID().toString();

    when(conn.delete("smtp_configuration", id)).thenReturn(Future.succeededFuture(rowSet));
    when(rowSet.rowCount()).thenReturn(0);

    smtpConfigurationService.deleteSmtpConfiguration(conn, id)
      .onComplete(context.asyncAssertSuccess(Assert::assertFalse));
  }
}
