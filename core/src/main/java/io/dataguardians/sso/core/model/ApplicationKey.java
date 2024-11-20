package io.dataguardians.sso.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Value object for applications ssh keys */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "application_key")
@Entity
public class ApplicationKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Primary key with auto-generated value
    Long id;

    @Column(name = "private_key", columnDefinition = "TEXT", nullable = false)

    String privateKey;
    @Column(name = "public_key", columnDefinition = "TEXT", nullable = false)

    String publicKey;
    @Column(name = "passphrase")
    String passphrase;

    @Builder.Default boolean isFile = false;
}
