package io.dataguardians.sso.integrations.openai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The ComplianceRule class represents a set of rules that must be followed to comply with a certain standard or regulation.
 * It provides various methods for defining and enforcing compliance rules.
 *
 * This class contains the following methods:
 * <ul>
 *     <li>addRule(): Adds a new rule to the set of compliance rules</li>
 *     <li>deleteRule(): Removes a rule from the set of compliance rules</li>
 *     <li>checkCompliance(): Checks whether a given set of data complies with the defined rules</li>
 * </ul>
 *
 * For more information about each method, refer to their respective Javadoc.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ComplianceRule {

    String rule;
}
