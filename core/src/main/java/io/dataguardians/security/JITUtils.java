package io.dataguardians.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import io.dataguardians.config.security.zt.JITConfigProvider;
import io.dataguardians.sso.core.model.Host;
import io.dataguardians.sso.core.model.actors.UserActor;
import io.dataguardians.sso.core.model.security.zt.JITReason;
import io.dataguardians.sso.core.model.security.zt.JITRequest;
import io.dataguardians.sso.core.model.security.zt.JITRequestLink;
import io.dataguardians.sso.core.model.security.zt.JITStatus;
import io.dataguardians.repository.security.zt.JITServiceLocator;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

public class JITUtils {

  static MessageDigest digest;

  static {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static JITReason createReason(
      @NonNull String commandNeed, @NonNull String ticketId, @NonNull String ticketURI) {
    JITReason.JITReasonBuilder jitReasonBuilder = JITReason.builder().commandNeed(commandNeed);
    JITRequestLink.JITRequestLinkBuilder jriBuilder = JITRequestLink.builder();
    if (JITConfigProvider.JITConfigProviderFactory.getConfigProvider().getJitRequiresTicket()) {
      if (StringUtils.isNotEmpty(ticketId) && StringUtils.isNotEmpty(ticketURI)) {
        jriBuilder = jriBuilder.identifier(ticketId).uri(ticketURI);
      }
    }
    jitReasonBuilder.requestLink(jriBuilder.build());
    return jitReasonBuilder.build();
  }

  public static JITRequest createRequest(
      @NonNull String command, @NonNull JITReason reason, UserActor user, Host system)
      throws SQLException, GeneralSecurityException {
    return createRequest(command, reason, user.getId(), system.getId());
  }

  public static JITRequest createRequest(
      @NonNull String command,
      @NonNull JITReason reason,
      @NonNull Long userId,
      @NonNull Long systemId)
      throws SQLException, GeneralSecurityException {

    JITRequest request =
        JITRequest.builder()
            .command(command)
            .reason(reason)
            .userId(userId)
            .systemId(systemId)
            .build();
    return request;
  }

  public static String getCommandHash(@NonNull String command) {
    String originalString = command.trim();
    return DigestUtils.sha256Hex(originalString);
  }

  public static boolean isApproved(
      @NonNull String command, @NonNull Long userId, @NonNull Long systemId)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = JITServiceLocator.getRepository().getJITRequests(command, userId, systemId);
    boolean approved = false;

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITStatus> status = JITServiceLocator.getRepository().getJITStatus(request);
      if (status.isPresent()) {
        approved = status.get().isApproved();
      }
    }

    return approved;
  }

  public static boolean isDenied(
      @NonNull String command, @NonNull Long userId, @NonNull Long systemId)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = JITServiceLocator.getRepository().getJITRequests(command, userId, systemId);
    boolean approved = false;

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITStatus> status = JITServiceLocator.getRepository().getJITStatus(request);
      if (status.isPresent()) {
        approved = status.get().isApproved();
        if (!approved) {
          return true;
        }
      }
    }

    return false;
  }

  public static boolean isExpired(
      @NonNull String command, @NonNull Long userId, @NonNull Long systemId)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = JITServiceLocator.getRepository().getJITRequests(command, userId, systemId);

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITStatus> status = JITServiceLocator.getRepository().getJITStatus(request);

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

  public static boolean isActive(
      @NonNull String command, @NonNull Long userId, @NonNull Long systemId)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = JITServiceLocator.getRepository().getJITRequests(command, userId, systemId);

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITStatus> status = JITServiceLocator.getRepository().getJITStatus(request);

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

  public static void approveJIT(@NonNull JITRequest request, @NonNull Long userId)
      throws SQLException, GeneralSecurityException {
    JITServiceLocator.getRepository().setJITStatus(request.getId(), userId, true);
  }

  public static void approveOpsJIT(@NonNull JITRequest request, @NonNull Long userId)
      throws SQLException, GeneralSecurityException {
    JITServiceLocator.getRepository().setOpsJITStatus(request.getId(), userId, true);
  }

  public static void denyJIT(@NonNull JITRequest request, @NonNull Long userId)
      throws SQLException, GeneralSecurityException {
    JITServiceLocator.getRepository().setJITStatus(request.getId(), userId, false);
  }

  public static void denyOpsJIT(@NonNull JITRequest request, @NonNull Long userId)
      throws SQLException, GeneralSecurityException {
    JITServiceLocator.getRepository().setOpsJITStatus(request.getId(), userId, false);
  }

  public static void incrementUses(String command, Long userId, long systemId)
      throws SQLException, GeneralSecurityException {
    List<JITRequest> requests = JITServiceLocator.getRepository().getJITRequests(command, userId, systemId);
    boolean approved = false;

    if (requests.size() > 0) {
      JITRequest request = requests.get(0);
      Optional<JITStatus> status = JITServiceLocator.getRepository().getJITStatus(request);
      if (status.isPresent()) {
        JITServiceLocator.getRepository().incrementJITUses(request);
      }
    }
  }

  public static void revokeOpsJIT(JITRequest jitRequest, Long userId)
      throws SQLException, GeneralSecurityException {
    JITServiceLocator.getRepository().revokeOpsJIT(jitRequest, userId);
  }

  public static void revokeJIT(JITRequest jitRequest, Long userId)
      throws SQLException, GeneralSecurityException {
    JITServiceLocator.getRepository().revokeJIT(jitRequest, userId);
  }
}
