package org.folio.util;

import static io.vertx.core.Future.succeededFuture;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import io.vertx.core.Future;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AsyncUtil {

  public static <T> Future<Collection<T>> mapInOrder(Collection<T> collection,
    Function<T, Future<T>> mapper) {

    final List<T> results = new ArrayList<>();

    return collection.stream()
      .reduce(succeededFuture(),
        (Future<T> f, T e) -> f.compose(r -> mapper.apply(e).onSuccess(results::add)),
        (a, b) -> succeededFuture())
      .map(results);
  }
}
