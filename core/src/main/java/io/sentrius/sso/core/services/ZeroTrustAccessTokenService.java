package io.sentrius.sso.core.services;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.dto.JITTrackerDTO;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenApproval;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenReason;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenRequest;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import io.sentrius.sso.core.utils.ZTATUtils;
import jakarta.transaction.Transactional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZeroTrustAccessTokenService {

  private final SystemOptions systemOptions;
  MessageDigest digest;

  private final ZeroTrustRequestService ztatRequestService;

  {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public ZeroTrustAccessTokenReason createReason(
      @NonNull String commandNeed, @NonNull String ticketId, @NonNull String ticketURI) {
    ZeroTrustAccessTokenReason.ZeroTrustAccessTokenReasonBuilder ztatReasonBuilder =
        ZeroTrustAccessTokenReason.builder().commandNeed(commandNeed).reasonIdentifier(ticketId);

    //ztatReasonBuilder.requestLink(jriBuilder.build());
    return ztatReasonBuilder.build();
  }


  public ZeroTrustAccessTokenRequest createRequest(
      @NonNull String command,
      @NonNull ZeroTrustAccessTokenReason reason,
      @NonNull User user,
      @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {

    ZeroTrustAccessTokenRequest request =
        ZeroTrustAccessTokenRequest.builder()
            .command(command)
            .ztatReason(reason)
            .user(user)
            .commandHash(ZTATUtils.getCommandHash(command))
            .system(system)
            .lastUpdated(new Timestamp(System.currentTimeMillis()))
            .build();
    return request;
  }

  public OpsZeroTrustAcessTokenRequest createOpsRequest(
      @NonNull String summary,
      @NonNull String command,
      @NonNull ZeroTrustAccessTokenReason reason,
      @NonNull User user){

    OpsZeroTrustAcessTokenRequest request =
        OpsZeroTrustAcessTokenRequest.builder()
            .command(command)
            .ztatReason(reason)
            .user(user)
            .commandHash(ZTATUtils.getCommandHash(command))
            .summary(summary)
            .lastUpdated(new Timestamp(System.currentTimeMillis()))
            .build();
    return request;
  }



  public boolean isApproved(
      @NonNull String command, @NonNull User user , @NonNull HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<ZeroTrustAccessTokenRequest> requests = ztatRequestService.getAccessTokenRequests(command, user, system);
    boolean approved = false;

    log.info("Checking if command is approved: " + command);
    if (requests.size() > 0) {
      log.info("Found JIT request for command: " + command);
      ZeroTrustAccessTokenRequest request = requests.get(0);
      Optional<ZeroTrustAccessTokenApproval> status = ztatRequestService.getAccessTokenStatus(request);
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
    List<ZeroTrustAccessTokenRequest> requests = ztatRequestService.getAccessTokenRequests(command, user, system);
    boolean approved = false;

    if (requests.size() > 0) {
      ZeroTrustAccessTokenRequest request = requests.get(0);
      Optional<ZeroTrustAccessTokenApproval> status = ztatRequestService.getAccessTokenStatus(request);
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
    List<ZeroTrustAccessTokenRequest> requests = ztatRequestService.getAccessTokenRequests(command, user, system);

    if (requests.size() > 0) {
      ZeroTrustAccessTokenRequest request = requests.get(0);
      Optional<ZeroTrustAccessTokenApproval> status = ztatRequestService.getAccessTokenStatus(request);

      if (status.isPresent()) {
        var lastUpdated = status.get().getZtatRequest().getLastUpdated().getTime();
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
    List<ZeroTrustAccessTokenRequest> requests = ztatRequestService.getAccessTokenRequests(command, user, system);

    if (requests.size() > 0) {
      ZeroTrustAccessTokenRequest request = requests.get(0);
      log.info("JIT request has reached max uses: " + request.getId());
      Optional<ZeroTrustAccessTokenApproval> status = ztatRequestService.getAccessTokenStatus(request);

      if (status.isPresent()) {
        var lastUpdated = null != status.get().getZtatRequest().getLastUpdated() ?
        status.get().getZtatRequest().getLastUpdated().getTime() : System.currentTimeMillis();
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

  public void approveAccessToken(@NonNull ZeroTrustAccessTokenRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    ztatRequestService.getAccessTokenStatus(request, user, true);
  }

  public void approveOpsAccessToken(@NonNull OpsZeroTrustAcessTokenRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    ztatRequestService.setOpsAccessTokenStatus(request, user, true);
  }

  @Transactional
  public void denyAccessToken(@NonNull ZeroTrustAccessTokenRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    ztatRequestService.getAccessTokenStatus(request, user, false);
  }

  @Transactional
  public void denyOpsAccessToken(@NonNull OpsZeroTrustAcessTokenRequest request, @NonNull User user)
      throws SQLException, GeneralSecurityException {
    ztatRequestService.setOpsAccessTokenStatus(request, user, false);
  }

  public void incrementUses(String command, User user, HostSystem system)
      throws SQLException, GeneralSecurityException {
    List<ZeroTrustAccessTokenRequest> requests = ztatRequestService.getAccessTokenRequests(command, user, system);
    boolean approved = false;

    if (requests.size() > 0) {
      ZeroTrustAccessTokenRequest request = requests.get(0);
      Optional<ZeroTrustAccessTokenApproval> status = ztatRequestService.getAccessTokenStatus(request);
      if (status.isPresent()) {
        log.info("incrementing uses for command: " + command);
        ztatRequestService.incrementAccessTokenUses(request);
      }
    }
  }

  public void revokeOpsJIT(ZeroTrustAccessTokenRequest ztatRequest, Long userId)
      throws SQLException, GeneralSecurityException {
    ztatRequestService.revokeOpsAccesToken(ztatRequest, userId);
  }

  public void revokeJIT(ZeroTrustAccessTokenRequest ztatRequest, Long userId)
      throws SQLException, GeneralSecurityException {
    ztatRequestService.revokeJIT(ztatRequest, userId);
  }

  public ZeroTrustAccessTokenRequest addJITRequest(ZeroTrustAccessTokenRequest request) {
    return ztatRequestService.addJITRequest(request);
  }

  public boolean hasJITRequest(String command, User user, HostSystem system) {
    return ztatRequestService.hasJITRequest(command, user.getId(), system.getId());
  }

    public List<JITTrackerDTO> getOpenJITRequests(User operatingUser) {
        return ztatRequestService.getOpenAccessTokenRequests(operatingUser);
    }

  public List<JITTrackerDTO> getOpenOpsRequests(User operatingUser) {
      return ztatRequestService.getOpenOpsRequests(operatingUser);
  }

  public OpsZeroTrustAcessTokenRequest getOpsJITRequest(Long ztatId) {
    return ztatRequestService.getOpsAccessTokenRequestById(ztatId);
  }

  public ZeroTrustAccessTokenRequest getZtatRequest(Long ztatId) {
    return ztatRequestService.getAccessTokenRequestById(ztatId);
  }
}
