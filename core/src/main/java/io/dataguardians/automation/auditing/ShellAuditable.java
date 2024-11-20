package io.dataguardians.automation.auditing;

import java.util.concurrent.ConcurrentLinkedDeque;

public class ShellAuditable {
/*
  static ConcurrentLinkedDeque<PersistentMessage> persistentMessage = new ConcurrentLinkedDeque<>();

  static ConcurrentLinkedDeque<Trigger> warn = new ConcurrentLinkedDeque<>();
  static ConcurrentLinkedDeque<Trigger> deny = new ConcurrentLinkedDeque<>();

  static ConcurrentLinkedDeque<Trigger> jit = new ConcurrentLinkedDeque<>();

  public static void addWarning(Trigger trigger) {
    warn.add(trigger);
  }

  public static void addPersistentMessage(PersistentMessage message) {
    persistentMessage.add(message);
  }

  public static void addDenial(Trigger trigger) {
    deny.add(trigger);
  }

  public static Trigger getNextWarning() {
    return warn.isEmpty() ? null : warn.pop();
  }

  public static PersistentMessage getNextPeristentMessage() {
    return persistentMessage.isEmpty() ? null : persistentMessage.pop();
  }

  public static Trigger getNextDenial() {
    return deny.isEmpty() ? null : deny.pop();
  }

  public static void addJIT(Trigger trg) {
    String message =
        "This command will require approval. Your command will not execute until approval is"
            + " garnered.If approval is not already submitted you will be notified when it is"
            + " submitted.. "
            + trg.getDescription();
    jit.add(new Trigger(trg.getAction(), message));
    warn.add(new Trigger(trg.getAction(), message));
  }*/
}
