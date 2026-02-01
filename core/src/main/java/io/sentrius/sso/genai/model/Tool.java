package io.sentrius.sso.genai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tool {

    /**
     * The type of the tool. Currently, only "function" is widely
     * supported for custom implementations.
     */
    @Builder.Default
    private String type = "function";

    /**
     * The function definition associated with this tool.
     */
    private Function function;
}