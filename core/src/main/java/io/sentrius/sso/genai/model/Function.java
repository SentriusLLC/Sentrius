package io.sentrius.sso.genai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Function {

    /**
     * The name of the function to be called.
     * Must be a-z, A-Z, 0-9, or contain underscores and dashes.
     */
    private String name;

    /**
     * A description of what the function does, used by the model
     * to choose when and how to call the function.
     */
    private String description;

    /**
     * The parameters the functions accepts, described as a JSON Schema object.
     * Use Map.of() or a JSON-serializable object here.
     */
    private Object parameters;
}