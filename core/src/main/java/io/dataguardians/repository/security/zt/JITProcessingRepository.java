package io.dataguardians.repository.security.zt;


import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import io.dataguardians.sso.core.model.actors.UserActor;
import io.dataguardians.sso.core.model.security.zt.JITReason;
import io.dataguardians.sso.core.model.security.zt.JITRequest;
import io.dataguardians.sso.core.model.security.zt.JITStatus;
import io.dataguardians.sso.core.model.security.zt.JITTracker;
import io.dataguardians.repository.CursorPaginator;

/** Repository interface for managing JIT operations */
public interface JITProcessingRepository {

    JITReason addJITReason(JITReason reason) throws SQLException, GeneralSecurityException;

    JITRequest addJITRequest(JITRequest request) throws SQLException, GeneralSecurityException;

    JITRequest addOpsJITRequest(JITRequest request) throws SQLException, GeneralSecurityException;

    boolean hasJITRequest(String command, Long userId, Long systemId)
        throws SQLException, GeneralSecurityException;

    boolean hasOpsJITRequest(String command, Long userId)
        throws SQLException, GeneralSecurityException;

    List<JITRequest> getJITRequests(String command, Long userId, Long systemId)
        throws SQLException, GeneralSecurityException;

    List<JITRequest> getOpsJITRequests(String command, Long userId)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getOpenJITRequests(CursorPaginator CursorPaginator)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getOpenJITRequests(CursorPaginator CursorPaginator, UserActor currentUser)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getOpenOpsJITRequests(CursorPaginator CursorPaginator, UserActor currentUser)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getDeniedJITs(CursorPaginator CursorPaginator)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getDeniedJITs(CursorPaginator CursorPaginator, UserActor currentUser)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getDeniedOpsJITs(CursorPaginator CursorPaginator, UserActor currentUser)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getApprovedJITs(CursorPaginator CursorPaginator)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getApprovedJITs(CursorPaginator CursorPaginator, UserActor currentUser)
        throws SQLException, GeneralSecurityException;

    List<JITTracker> getApprovedOpsJITs(CursorPaginator CursorPaginator, UserActor currentUser)
        throws SQLException, GeneralSecurityException;

    JITRequest getOpsJitRequest(Long requestId);

    JITRequest getTerminalJitRequest(Long requestId);

    Optional<JITStatus> getJITStatus(JITRequest request)
        throws SQLException, GeneralSecurityException;

    Long setJITStatus(Long jitId, Long userId, boolean approval)
        throws SQLException, GeneralSecurityException;

    Long setOpsJITStatus(Long jitId, Long userId, boolean approval)
        throws SQLException, GeneralSecurityException;

    void revokeOpsJIT(JITRequest jit, Long userId)
        throws SQLException, GeneralSecurityException;

    void incrementJITUses(JITRequest request)
        throws SQLException, GeneralSecurityException;

    void revokeJIT(JITRequest jit, Long userId)
        throws SQLException, GeneralSecurityException;
}
