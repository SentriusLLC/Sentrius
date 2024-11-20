package io.dataguardians.rollback;

import java.util.Set;
import io.dataguardians.automation.sideeffects.SideEffect;

public interface RollbackFunction {

  /**
   * Roll back. The input are those which need to be rolled back. The return are those which cannot
   * be rolled back.
   *
   * @param in
   * @return
   */
  Set<SideEffect> rollback(Set<SideEffect> in);
}
