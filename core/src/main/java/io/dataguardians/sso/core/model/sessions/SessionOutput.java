package io.dataguardians.sso.core.model.sessions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import io.dataguardians.automation.auditing.PersistentMessage;
import io.dataguardians.automation.auditing.Trigger;
import io.dataguardians.protobuf.Session;
import io.dataguardians.sso.core.model.ConnectedSystem;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/** Output from ssh session */
@Slf4j
@SuperBuilder(toBuilder = true)
@Getter
@EqualsAndHashCode
public class SessionOutput  {


    private final ConnectedSystem connectedSystem;
    private StringBuilder output = new StringBuilder(256);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    ConcurrentLinkedDeque<PersistentMessage> persistentMessage = new ConcurrentLinkedDeque<>();

    ConcurrentLinkedDeque<Trigger> warn = new ConcurrentLinkedDeque<>();
    ConcurrentLinkedDeque<Trigger> deny = new ConcurrentLinkedDeque<>();

    ConcurrentLinkedDeque<Trigger> ztat = new ConcurrentLinkedDeque<>();

    private AtomicReference<Trigger> sessionMessage = new AtomicReference<>();

    public SessionOutput(ConnectedSystem connectedSystem) {
        this.connectedSystem = connectedSystem;
    }

    public Long getSessionId() {
        return connectedSystem.getSession().getId();
    }

    public String getOutput() {
        lock.lock();
        try {
            return output.toString();
        } finally {
            lock.unlock();
        }
    }

    public void append(char[] str, int offset, int count) {
        lock.lock();
        try {
            output.append(str, offset, count);
            notEmpty.signalAll(); // Notify waiting threads
        } finally {
            lock.unlock();
        }
    }

    public void append(String str) {
        lock.lock();
        try {
            output.append(str);
            notEmpty.signalAll(); // Notify waiting threads
        } finally {
            lock.unlock();
        }
    }

    public void addSystemMessage(Trigger trigger) {
        lock.lock();
        try {
            log.info("Setting trigger and notifying");
            sessionMessage.set(trigger);
            notEmpty.signalAll(); // Notify waiting threads
        } finally {
            lock.unlock();
        }
    }

    public void addWarning(Trigger trigger) {
        lock.lock();
        try {
            warn.add(trigger);
            notEmpty.signalAll(); // Notify waiting threads
        } finally {
            lock.unlock();
        }

    }

    public void addPersistentMessage(PersistentMessage message) {
        lock.lock();
        try {
            persistentMessage.add(message);
            notEmpty.signalAll(); // Notify waiting threads
        } finally {
            lock.unlock();
        }
    }

    public void addDenial(Trigger trigger) {
        lock.lock();
        try {
            deny.add(trigger);
            notEmpty.signalAll(); // Notify waiting threads
        } finally {
            lock.unlock();
        }
    }

    /*public Trigger getNextWarning() {
        return warn.isEmpty() ? null : warn.pop();
    }

    public PersistentMessage getNextPeristentMessage() {
        return persistentMessage.isEmpty() ? null : persistentMessage.pop();
    }

    public Trigger getNextDenial() {
        return deny.isEmpty() ? null : deny.pop();
    }
*/
    public void addJIT(Trigger trg) {
        String message =
            "This command will require approval. Your command will not execute until approval is"
                + " garnered.If approval is not already submitted you will be notified when it is"
                + " submitted.. "
                + trg.getDescription();
        lock.lock();
        try {
            ztat.add(new Trigger(trg.getAction(), message));
            warn.add(new Trigger(trg.getAction(), message));
            notEmpty.signalAll(); // Notify waiting threads
        } finally {
            lock.unlock();
        }
    }


    private Session.TerminalMessage getTrigger(Trigger trigger){
       return getTrigger(trigger, Session.MessageType.USER_DATA);
    }

    private Session.TerminalMessage getTrigger(Trigger trigger, Session.MessageType messageType){
        var terminalMessage = Session.TerminalMessage.newBuilder();
        terminalMessage.setType(messageType);
        Session.Trigger.Builder triggerBuilder = Session.Trigger.newBuilder();
        switch(trigger.getAction()){
            case DENY_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.DENY_ACTION);
                break;
            case JIT_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.JIT_ACTION);
                break;
            case RECORD_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.RECORD_ACTION);
                break;
            case APPROVE_ACTION:
                triggerBuilder.setAction(Session.TriggerAction.APPROVE_ACTION);
                break;
            default:
                break;
        }
        triggerBuilder.setDescription(trigger.getDescription().isEmpty() ? "" : trigger.getDescription());
        terminalMessage.setTrigger(triggerBuilder.build());
        return terminalMessage.build();
    }

    public List<Session.TerminalMessage> waitForOutput(Long time,
                                                       TimeUnit unit, Predicate<SessionOutput> condition) throws InterruptedException {
        List<Session.TerminalMessage> messages = new ArrayList<>();
        lock.lock();
        try {
            while ((output.length() == 0 && warn.isEmpty() && ztat.isEmpty() && deny.isEmpty()) && condition.test(this) && sessionMessage.get() == null) {
                notEmpty.await(time, unit); // Wait until notified
            }

            if (!output.isEmpty()) {
                Session.TerminalMessage.Builder terminalMessage = Session.TerminalMessage.newBuilder();
                terminalMessage.setType(Session.MessageType.USER_DATA);
                terminalMessage.setCommand(output.toString());
                output = new StringBuilder(256);
                messages.add( terminalMessage.build());
            }

            var systemTrigger = sessionMessage.getAndSet(null);
            if (null != systemTrigger){
                log.info("System Trigger: {}", systemTrigger);
                messages.add( getTrigger(systemTrigger, Session.MessageType.SESSION_DATA));
            }

            if (!warn.isEmpty()){
                var trigger = warn.pop();
                messages.add( getTrigger(trigger));
            }
            if (!deny.isEmpty()){
                var trigger = deny.pop();
                messages.add( getTrigger(trigger));
            }

            return messages;
        } finally {
            lock.unlock();
        }
    }
}
