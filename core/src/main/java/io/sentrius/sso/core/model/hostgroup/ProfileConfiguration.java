package io.sentrius.sso.core.model.hostgroup;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentrius.sso.core.model.security.SessionRule;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileConfiguration {

  private Long id;

  @Builder.Default
  private String configurationName = "name";

  @Builder.Default
  private Boolean terminalsLocked = false;

  @Builder.Default
  private Integer maxConcurrentSessions = Integer.MAX_VALUE;

  @Builder.Default
  private String daysOfWeek = "MoTuWeThFrSaSu";

  @Builder.Default
  private Integer beginRangeHour = 0;

  @Builder.Default
  private Integer beginRangeMinute = 0;

  @Builder.Default
  private Integer beginRangeSecond = 0;

  @Builder.Default
  private Integer endRangeHour = 24;

  @Builder.Default
  private Integer endRangeMinute = 59;

  @Builder.Default
  private Integer endRangeSecond = 59;

  @Builder.Default
  private Boolean allowSudo = true;

  @Builder.Default
  private Boolean rangeLimitSSH = false;

  @Builder.Default
  private Boolean approveViaTicket = true;

  @Builder.Default
  private List<SessionRule> sessionRules = new ArrayList<>();

  @Builder.Default
  private Map<Long, Long> userOverrideTypeMap = new HashMap<>();

  public static String toJson(ProfileConfiguration config) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.writeValueAsString(config);
  }

  public static ProfileConfiguration fromJson(String json) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(json, ProfileConfiguration.class);
  }

  public static Properties serialize(ProfileConfiguration config) throws IllegalAccessException {
    Properties props = new Properties();
    for (Field field : config.getClass().getDeclaredFields()) {
      Object val = field.get(config);
      if (val != null) {
        props.put(field.getName(), val.toString());
      }
    }
    return props;
  }

  public static ProfileConfiguration.ProfileConfigurationBuilder from(Long id, Properties properties) {
    ProfileConfiguration.ProfileConfigurationBuilder builder = ProfileConfiguration.builder().id(id);
    properties.forEach((key, value) -> {
      try {
        Field field = ProfileConfiguration.class.getDeclaredField((String) key);
        field.setAccessible(true);
        field.set(builder, parsePropertyValue(field.getType(), value));
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    });
    return builder;
  }

  private static Object parsePropertyValue(Class<?> fieldType, Object value) {
    if (fieldType == Integer.class) {
      return Integer.valueOf(value.toString());
    } else if (fieldType == Boolean.class) {
      return Boolean.valueOf(value.toString());
    } else {
      return value.toString();
    }
  }
}
