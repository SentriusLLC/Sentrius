package io.dataguardians.repository.security.zt;


import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.model.actors.UserActor;
import io.dataguardians.sso.core.model.security.zt.JITReason;
import io.dataguardians.sso.core.model.security.zt.JITRequest;
import io.dataguardians.sso.core.model.security.zt.JITStatus;
import io.dataguardians.sso.core.model.security.zt.JITTracker;
import io.dataguardians.repository.CursorPaginator;

public class InMemoryJITProcessingRepository implements JITProcessingRepository {

    private final Map<Long, JITReason> jitReasons = new HashMap<>();
    private final Map<Long, JITRequest> jitRequests = new HashMap<>();
    private final Map<Long, JITStatus> jitStatuses = new HashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public JITReason addJITReason(JITReason reason) {
        long id = idGenerator.getAndIncrement();
        reason = reason.builder().id(id).build();
        jitReasons.put(id, reason);
        return reason;
    }

    @Override
    public JITRequest addJITRequest(JITRequest request) throws GeneralSecurityException {
        JITReason reason = request.getReason();
        if (reason.getId() == -1) {
            reason = addJITReason(reason);
        }
        long id = idGenerator.getAndIncrement();
        request = request.builder().id(id).reason(reason).build();
        jitRequests.put(id, request);
        return request;
    }

    @Override
    public JITRequest addOpsJITRequest(JITRequest request) throws GeneralSecurityException {
        return addJITRequest(request);
    }

    @Override
    public boolean hasJITRequest(String command, Long userId, Long systemId) {
        return jitRequests.values().stream()
            .anyMatch(req -> req.getUserId().equals(userId) && req.getSystemId().equals(systemId) &&
                req.getCommand().equals(command));
    }

    @Override
    public boolean hasOpsJITRequest(String command, Long userId) {
        return jitRequests.values().stream()
            .anyMatch(req -> req.getUserId().equals(userId) && req.getCommand().equals(command) &&
                req.getSystemId().equals(-1L));
    }

    @Override
    public List<JITRequest> getJITRequests(String command, Long userId, Long systemId) {
        return jitRequests.values().stream()
            .filter(req -> req.getUserId().equals(userId) && req.getSystemId().equals(systemId) &&
                req.getCommand().equals(command))
            .collect(Collectors.toList());
    }

    @Override
    public List<JITRequest> getOpsJITRequests(String command, Long userId) {
        return jitRequests.values().stream()
            .filter(req -> req.getUserId().equals(userId) && req.getCommand().equals(command) &&
                req.getSystemId().equals(-1L))
            .collect(Collectors.toList());
    }

    @Override
    public List<JITTracker> getOpenJITRequests(CursorPaginator paginator) {
        return getOpenJITRequests(paginator, null);
    }

    @Override
    public List<JITTracker> getOpenJITRequests(CursorPaginator paginator, UserActor currentUser) {
        return jitRequests.values().stream()
            .filter(req -> currentUser == null || req.getUserId().equals(currentUser.getId()))
            .map(req -> toJITTracker(req, currentUser))
            .collect(Collectors.toList());
    }

    @Override
    public List<JITTracker> getOpenOpsJITRequests(CursorPaginator paginator, UserActor currentUser) {
        return getOpenJITRequests(paginator, currentUser).stream()
            .filter(tracker -> tracker.getSystemId().equals(-1L))
            .collect(Collectors.toList());
    }

    @Override
    public List<JITTracker> getDeniedJITs(CursorPaginator paginator) {
        return getDeniedJITs(paginator, null);
    }

    @Override
    public List<JITTracker> getDeniedJITs(CursorPaginator paginator, UserActor currentUser) {
        // Example assumes 'denied' status is determined based on some logic.
        return new ArrayList<>();
    }

    @Override
    public List<JITTracker> getDeniedOpsJITs(CursorPaginator paginator, UserActor currentUser) {
        return getDeniedJITs(paginator, currentUser).stream()
            .filter(tracker -> tracker.getSystemId().equals(-1L))
            .collect(Collectors.toList());
    }

    @Override
    public List<JITTracker> getApprovedJITs(CursorPaginator paginator) {
        return getApprovedJITs(paginator, null);
    }

    @Override
    public List<JITTracker> getApprovedJITs(CursorPaginator paginator, UserActor currentUser) {
        // Example assumes 'approved' status is determined based on some logic.
        return new ArrayList<>();
    }

    @Override
    public List<JITTracker> getApprovedOpsJITs(CursorPaginator paginator, UserActor currentUser) {
        return getApprovedJITs(paginator, currentUser).stream()
            .filter(tracker -> tracker.getSystemId().equals(-1L))
            .collect(Collectors.toList());
    }

    @Override
    public JITRequest getOpsJitRequest(Long requestId) {
        return jitRequests.get(requestId);
    }

    @Override
    public JITRequest getTerminalJitRequest(Long requestId) {
        return jitRequests.get(requestId);
    }

    @Override
    public Optional<JITStatus> getJITStatus(JITRequest request) {
        return Optional.ofNullable(jitStatuses.get(request.getId()));
    }

    @Override
    public Long setJITStatus(Long jitId, Long userId, boolean approval) {
        long id = idGenerator.getAndIncrement();
        JITStatus status = new JITStatus();
        status.setId(id);
        status.setApproved(approval);
        jitStatuses.put(jitId, status);
        return id;
    }

    @Override
    public Long setOpsJITStatus(Long jitId, Long userId, boolean approval) {
        return setJITStatus(jitId, userId, approval);
    }

    @Override
    public void revokeOpsJIT(JITRequest jit, Long userId) {
        jitRequests.remove(jit.getId());
    }

    @Override
    public void incrementJITUses(JITRequest request) {
        JITStatus status = jitStatuses.get(request.getId());
        if (status != null) {
            status.setUses(status.getUses() + 1);
        }
    }

    @Override
    public void revokeJIT(JITRequest jit, Long userId) {
        jitRequests.remove(jit.getId());
    }

    private JITTracker toJITTracker(JITRequest request, UserActor currentUser) {
        // Convert a JITRequest to a JITTracker with mock data for the user and host system.
        return JITTracker.builder()
            .id(request.getId())
            .userId(request.getUserId())
            .command(request.getCommand())
            .systemId(request.getSystemId())
            .user(currentUser)
            .reason(request.getReason())
            .hostSystem(null) // Use mock system data as needed.
            .build();
    }
}
