package io.dataguardians.callbacks;

import io.dataguardians.sso.core.model.ScriptOutput;

public interface OutputCallback {

    void onOutput(ScriptOutput outputToAppend);
}
