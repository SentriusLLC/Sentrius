package io.dataguardians.sso.core.services;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import io.dataguardians.config.security.zt.JITConfigProvider;
import io.dataguardians.sso.core.model.Host;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.actors.UserActor;
import io.dataguardians.sso.core.model.security.zt.JITStatus;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.zt.JITReason;
import io.dataguardians.sso.core.model.zt.JITRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JITService {

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
            .system(system)
            .build();
    return request;
  }


  public boolean isApproved(
      @NonNull String command, @NonNull User user , @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = jitRequestService.getJITRequests(command, user, system);
    boolean approved = false;

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITStatus> status = jitRequestService.getJITStatus(request);
      if (status.isPresent()) {
        approved = status.get().isApproved();
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
      Optional<JITStatus> status = jitRequestService.getJITStatus(request);
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
      Optional<JITStatus> status = jitRequestService.getJITStatus(request);

      if (status.isPresent()) {
        var lastUpdated = status.get().getLast_updated();
        var currentTime = System.currentTimeMillis();
        if ((currentTime - lastUpdated) > JITConfigProvider.JITConfigProviderFactory.getConfigProvider().getMaxJitDurationMs()) {
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
      Optional<JITStatus> status = jitRequestService.getJITStatus(request);

      if (status.isPresent()) {
        var lastUpdated = status.get().getLast_updated();
        var currentTime = System.currentTimeMillis();
        if (JITConfigProvider.JITConfigProviderFactory.getConfigProvider().getMaxJitUses() > 0
            && status.get().getUses() >= JITConfigProvider.JITConfigProviderFactory.getConfigProvider().getMaxJitUses()) {
          return false;
        } else if ((currentTime - lastUpdated) > JITConfigProvider.JITConfigProviderFactory.getConfigProvider().getMaxJitDurationMs()) {
          return false;
        } else {
          return true;
        }
      }
    }

    return false;
  }

  public void approveJIT(@NonNull JITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setJITStatus(request, user, true);
  }

  public void approveOpsJIT(@NonNull JITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setOpsJITStatus(request, user, true);
  }

  public void denyJIT(@NonNull JITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setJITStatus(request, user, false);
  }

  public void denyOpsJIT(@NonNull JITRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    jitRequestService.setOpsJITStatus(request, user, false);
  }

  public void incrementUses(String command, User user, HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = jitRequestService.getJITRequests(command, user, system);
    boolean approved = false;

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITStatus> status = jitRequestService.getJITStatus(request);
      if (status.isPresent()) {
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
}
