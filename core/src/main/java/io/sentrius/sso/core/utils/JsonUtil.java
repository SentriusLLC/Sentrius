package io.sentrius.sso.core.utils;

import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtil {

  public static ObjectMapper MAPPER = new ObjectMapper();

  static {
    JsonUtil.MAPPER.registerModule(new JavaTimeModule());
    JsonUtil.MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public static <T> List<T> convertArrayNodeToList(ArrayNode arrayNode, TypeReference<List<T>> typeRef)
      throws JsonProcessingException {
    return JsonUtil.MAPPER.readValue(arrayNode.toString(), typeRef);
  }

}
