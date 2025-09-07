package io.sentrius.sso.core.embeddings;

import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.sentrius.sso.core.dto.ztat.TokenDTO;
import io.sentrius.sso.core.exceptions.ZtatException;

public interface EmbeddingService {
    float[] embed(TokenDTO dto, String text) throws ZtatException, JsonProcessingException;
    List<float[]> embed(TokenDTO dto, List<String> text) throws ZtatException, JsonProcessingException;
}
