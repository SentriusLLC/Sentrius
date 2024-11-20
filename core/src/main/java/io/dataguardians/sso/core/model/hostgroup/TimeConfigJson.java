package io.dataguardians.sso.core.model.hostgroup;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@Data
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "time_config")
public class TimeConfigJson {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "time_config_id")
  private Long id;

  @Column(name = "uuid", nullable = false, unique = true)
  private String uuid = UUID.randomUUID().toString();

  @Column(name = "title")
  private String title;

  @Column(name = "configuration")
  private String configurationJson;

}
