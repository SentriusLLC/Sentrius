package io.sentrius.sso.genai;

import java.util.ArrayList;
import java.util.List;
import io.sentrius.sso.genai.model.ComplianceRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * The ComplianceConfiguration class represents the configuration settings for compliance checks.
 * It provides methods for setting and retrieving configuration values such as the maximum allowed error rate for
 * compliance checks and the types of compliance checks to perform.
 *
 * This class provides the following methods:
 *
 * - setMaxErrorRate(double rate): Sets the maximum allowed error rate for compliance checks.
 * - getMaxErrorRate(): Returns the current maximum allowed error rate for compliance checks.
 * - setComplianceTypes(List<String> types): Sets the types of compliance checks to perform.
 * - getComplianceTypes(): Returns the current types of compliance checks to perform.
 *
 * The ComplianceConfiguration class is part of the compliance checking subsystem and is intended to be used in
 * conjunction with other classes that perform compliance checks on data. By configuring the compliance configuration
 * object, users can specify the types of checks to be performed, and the maximum allowed error rate for those checks.
 * This enables users to ensure that the data they are working with complies with the relevant standards or regulations.
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ComplianceConfiguration {

    @Builder.Default
    List<ComplianceRule> rules = new ArrayList<>();
}
