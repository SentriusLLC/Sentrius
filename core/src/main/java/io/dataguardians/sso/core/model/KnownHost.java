package io.dataguardians.sso.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;


@Getter
@Entity
@Setter
@Table(name = "known_hosts", uniqueConstraints = @UniqueConstraint(columnNames = {"hostname", "keyType"}))
public class KnownHost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String hostname;

    @Column(nullable = false)
    private String keyType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String keyValue;
}
