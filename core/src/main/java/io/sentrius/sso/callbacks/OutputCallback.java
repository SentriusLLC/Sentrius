package io.sentrius.sso.callbacks;

import io.sentrius.sso.core.model.ScriptOutput;

public interface OutputCallback {

    void onOutput(ScriptOutput outputToAppend);
}
