package io.dataguardians.sso.core.model;


import java.io.ByteArrayInputStream;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@NoArgsConstructor
@Getter
@AllArgsConstructor
public class ContentResponse {
    @Builder.Default String contentType = "text/plain";
    String utfHttpResponse;
    ByteArrayInputStream binStream;
    @Builder.Default boolean isBinary = false;
    @Builder.Default ServletResponseType type = ServletResponseType.FORWARD;
}
