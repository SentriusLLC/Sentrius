package io.sentrius.sso.core.model.verbs;

import java.util.Map;

public interface InputInterpreterIfc<T> {

    T interpret(Map<String,Object> input) throws Exception;

}
