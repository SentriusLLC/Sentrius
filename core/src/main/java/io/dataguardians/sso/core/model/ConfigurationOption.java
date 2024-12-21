package io.dataguardians.sso.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// ErrorOutput Entity
@Entity
@Table(name = "configuration_options")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "configuration_name")
    private String configurationName;

    @Column(name = "configuration_value", nullable = false)
    private String configurationValue;

}