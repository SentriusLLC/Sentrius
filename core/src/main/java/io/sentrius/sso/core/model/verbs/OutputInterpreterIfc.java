package io.sentrius.sso.core.model.verbs;

import java.util.Map;

public interface OutputInterpreterIfc {

    Map<String,Object> interpret(VerbResponse input) throws Exception;

}
