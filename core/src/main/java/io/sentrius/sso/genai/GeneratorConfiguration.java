package io.sentrius.sso.genai;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
/**
 * Generator configuration object.
 */
public class GeneratorConfiguration {

    @Builder.Default
    protected int concurrentGenerations = 1;

    @Builder.Default
    protected int maxTokens = 4096;
}