package io.sentrius.sso.core.model;


import java.sql.Timestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Value object that contains script information */
@Builder
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ScriptOutput {
    Long id;

    Long scriptId;

    Long systemId;

    String output;

    @Builder.Default String classOutput = "accordion-collapse collapse";

    Timestamp outputTime;

    public synchronized void appendOutput(String outputToAppend) {
        this.output += outputToAppend;
    }
}
