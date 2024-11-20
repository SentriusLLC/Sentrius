package io.dataguardians.sso.core.model.hostgroup;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.dataguardians.sso.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

@Data
@Getter
@Setter
@EqualsAndHashCode
@Builder
@Entity
@JsonDeserialize(builder = TimeConfigs.TimeConfigsBuilder.class)
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "time_configs")
public class TimeConfigs {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @JoinColumn(name = "time_config_id") // Foreign key in `TimeConfig` referencing this entity
  @Builder.Default
  private List<TimeConfigJson> timeConfigs = new ArrayList<>();

  public void addOrReplaceTimeConfig(TimeConfigJson newTimeConfig) {
    timeConfigs.removeIf(timeConfig -> newTimeConfig.getUuid().equals(timeConfig.getUuid()));
    timeConfigs.add(newTimeConfig);
  }

  public static String toJson(TimeConfigs timeConfigs) throws JsonProcessingException {
    return JsonUtil.MAPPER.writeValueAsString(timeConfigs);
  }

  public static TimeConfigs fromString(String str) throws JsonProcessingException {
    if (str == null || str.isEmpty()) {
      return null;
    }
    TimeConfigs tc = JsonUtil.MAPPER.readValue(str, TimeConfigs.class);
    for (TimeConfigJson timeConfig : tc.getTimeConfigs()) {
      //timeConfig.setStrings();
    }
    return tc;
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class TimeConfigsBuilder {
    // Lombok will generate builder methods here
  }
}
