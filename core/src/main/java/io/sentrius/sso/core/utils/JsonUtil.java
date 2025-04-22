package io.sentrius.sso.core.utils;

import java.util.List;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtil {

  public static ObjectMapper MAPPER = new ObjectMapper();

  public static <T> List<T> convertArrayNodeToList(ArrayNode arrayNode, TypeReference<List<T>> typeRef)
      throws JsonProcessingException {
    return JsonUtil.MAPPER.readValue(arrayNode.toString(), typeRef);
  }

}
