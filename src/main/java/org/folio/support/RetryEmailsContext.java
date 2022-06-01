package org.folio.support;

import java.util.Collection;
import java.util.Map;

import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@With
public class RetryEmailsContext {
  private final Map<String, String> okapiHeaders;
  private Collection<EmailEntity> emails;
  private Configurations configurations;
}
