package io.dataguardians.sso.integrations.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * Inspired by liLittleCat's ChatGPT package.
 *
 * Codifies the Message request
 * </p>
 *
 * original @author <a href="https://github.com/LiLittleCat">LiLittleCat</a>
 *
 * @since 2023/3/2
 */
/**
 * The Message class represents a message that can be sent between users. This class is immutable and contains information about the message.
 * It has the following methods:
 *
 * 1. getMessageText(): returns the text of the message
 * 2. getSender(): returns the user who sent the message
 * 3. getRecipient(): returns the user who received the message
 * 4. getTimestamp(): returns the timestamp of the message
 * 5. toString(): returns the string representation of the message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    @JsonProperty(value = "role")
    public String role;

    @JsonProperty(value = "content")
    public String content;

    @JsonProperty(value = "refusal")
    public String refusal;
}
