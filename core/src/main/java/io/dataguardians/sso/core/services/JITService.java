package io.dataguardians.sso.core.services;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.dto.JITTrackerDTO;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.zt.JITApproval;
import io.dataguardians.sso.core.model.zt.JITReason;
import io.dataguardians.sso.core.model.zt.JITRequest;
import io.dataguardians.sso.core.model.zt.OpsJITRequest;
import io.dataguardians.sso.core.utils.JITUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JITService {

  private final SystemOptions systemOptions;
  MessageDigest digest;

  private final JITRequestService jitRequestService;

  {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public JITReason createReason(
      @NonNull String commandNeed, @NonNull String ticketId, @NonNull String ticketURI) {
    JITReason.JITReasonBuilder jitReasonBuilder =
        JITReason.builder().commandNeed(commandNeed).reasonIdentifier(ticketId);

    //jitReasonBuilder.requestLink(jriBuilder.build());
    return jitReasonBuilder.build();
  }


  public JITRequest createRequest(
      @NonNull String command,
      @NonNull JITReason reason,
      @NonNull User user,
      @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {

    JITRequest request =
        JITRequest.builder()
            .command(command)
            .jitReason(reason)
            .user(user)
            .commandHash(JITUtils.getCommandHash(command))
            .system(system)
            .lastUpdated(new Timestamp(System.currentTimeMillis()))
            .build();
    return request;
  }


  public boolean isApproved(
      @NonNull String command, @NonNull User user , @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = jitRequestService.getJITRequests(command, user, system);
    boolean approved = false;

    log.info("Checking if command is approved: " + command);
    if (requests.size() > 0) {
      log.info("Found JIT request for command: " + command);
      JITRequest request = requests.get(0);
      Optional<JITApproval> status = jitRequestService.getJITStatus(request);
      if (status.isPresent()) {
        approved = status.get().isApproved();
        log.info("Found  approved JIT request for command: " + command);
      }
    }

    return approved;
  }

  public boolean isDenied(
      @NonNull String command, @NonNull User user , @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = jitRequestService.getJITRequests(command, user, system);
    boolean approved = false;

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITApproval> status = jitRequestService.getJITStatus(request);
      if (status.isPresent()) {
        approved = status.get().isApproved();
        if (!approved) {
          return true;
        }
      }
    }

    return false;
  }

  public boolean isExpired(
      @NonNull String command, @NonNull User user , @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = jitRequestService.getJITRequests(command, user, system);

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITApproval> status = jitRequestService.getJITStatus(request);

      if (status.isPresent()) {
        var lastUpdated = status.get().getJitRequest().getLastUpdated().getTime();
        var currentTime = System.currentTimeMillis();
        if ((currentTime - lastUpdated) > systemOptions.getMaxJitDurationMs()) {
          return true;
        } else {
          return false;
        }
      }
    }

    return false;
  }

  public boolean isActive(
      @NonNull String command, @NonNull User user , @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = jitRequestService.getJITRequests(command, user, system);

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      log.info("JIT request has reached max uses: " + request.getId());
      Optional<JITApproval> status = jitRequestService.getJITStatus(request);

      if (status.isPresent()) {
        var lastUpdated = null != status.get().getJitRequest().getLastUpdated() ?
        status.get().getJitRequest().getLastUpdated().getTime() : System.currentTimeMillis();
        var currentTime = System.currentTimeMillis();
        if (systemOptions.getMaxJitUses() > 0
            && status.get().getUses() >= systemOptions.getMaxJitUses()) {
          log.info("JIT request has reached max uses: " + request.getId());
          return false;
        } else if ((currentTime - lastUpdated) > systemOptions.getMaxJitDurationMs()) {
          log.info("JIT request has exceeded time: " + request.getId());
          return false;
        } else {
          return true;
        }
      }
    }
    log.info("JIT request not found: " + command);
    return false;
  }

  public void approveJIT(@NonNull JITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setJITStatus(request, user, true);
  }

  public void approveOpsJIT(@NonNull OpsJITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setOpsJITStatus(request, user, true);
  }

  public void denyJIT(@NonNull JITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setJITStatus(request, user, false);
  }

  public void denyOpsJIT(@NonNull OpsJITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setOpsJITStatus(request, user, false);
  }

  public void incrementUses(String command, User user, HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = jitRequestService.getJITRequests(command, user, system);
    boolean approved = false;

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITApproval> status = jitRequestService.getJITStatus(request);
      if (status.isPresent()) {
        log.info("incrementing uses for command: " + command);
        jitRequestService.incrementJITUses(request);
      }
    }
  }

  public void revokeOpsJIT(JITRequest jitRequest, Long userId)
      throws SQLException, GeneralSecurityException {
    jitRequestService.revokeOpsJIT(jitRequest, userId);
  }

  public void revokeJIT(JITRequest jitRequest, Long userId)
      throws SQLException, GeneralSecurityException {
    jitRequestService.revokeJIT(jitRequest, userId);
  }

  public JITRequest addJITRequest(JITRequest request) {
    return jitRequestService.addJITRequest(request);
  }

  public boolean hasJITRequest(String command, User user, HostSystem system) {
    return jitRequestService.hasJITRequest(command, user.getId(), system.getId());
  }

    public List<JITTrackerDTO> getOpenJITRequests(User operatingUser) {
        return jitRequestService.getOpenJITRequests(operatingUser);
    }

  public List<JITTrackerDTO> getOpenOpsRequests(User operatingUser) {
      return jitRequestService.getOpenOpsRequests(operatingUser);
  }

  public OpsJITRequest getOpsJITRequest(Long jitId) {
    return jitRequestService.getOpsJITRequestById(jitId);
  }

  public JITRequest getJITRequest(Long jitId) {
    return jitRequestService.getJITRequestById(jitId);
  }
}
