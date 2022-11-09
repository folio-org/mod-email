package org.folio.matchers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import io.vertx.core.json.JsonObject;

public class JsonMatchers {

  public static Matcher<String> matchesJson(JsonObject expectedProperties) {
    return matchesJson(expectedProperties, List.of());
  }

  public static Matcher<String> matchesJson(JsonObject expectedProperties,
    List<String> ignoredProperties) {

    return new TypeSafeMatcher<>() {
      @Override
      public boolean matchesSafely(String actualString) {
        JsonObject actual = new JsonObject(actualString);

        List<String> actualFieldNames = new ArrayList<>(actual.fieldNames());
        actualFieldNames.removeAll(ignoredProperties);

        List<String> expectedFieldNames = new ArrayList<>(expectedProperties.fieldNames());
        expectedFieldNames.removeAll(ignoredProperties);

        if (!actualFieldNames.containsAll(expectedFieldNames) ||
          !expectedFieldNames.containsAll(actualFieldNames)) {

          return false;
        }

        for (Map.Entry<String, Object> expectedProperty : expectedProperties) {
          final String propertyKey = expectedProperty.getKey();
          final Object expectedValue = expectedProperty.getValue();
          final Object actualValue = actual.getValue(propertyKey);

          if (ignoredProperties.contains(propertyKey)) {
            continue;
          }

          if (!Objects.equals(expectedValue, actualValue)) {
            return false;
          }
        }

        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Json object has following properties")
          .appendValue(expectedProperties.toString());

      }
    };
  }
}
