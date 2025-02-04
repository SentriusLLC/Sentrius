package io.sentrius.sso.core.services;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.dto.JITTrackerDTO;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenApproval;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenRequest;
import io.sentrius.sso.core.model.zt.OpsApproval;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import io.sentrius.sso.core.repository.ZeroTrustAccessTokenApprovalRepository;
import io.sentrius.sso.core.repository.JITReasonRepository;
import io.sentrius.sso.core.repository.ZeroTrustAccessTokenRequestRepository;
import io.sentrius.sso.core.repository.OpsApprovalRepository;
import io.sentrius.sso.core.repository.OpsJITRequestRepository;
import io.sentrius.sso.core.utils.ZTATUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ZeroTrustRequestService {

    @Autowired
    private ZeroTrustAccessTokenRequestRepository ztatRequestRepository;

    @Autowired
    private OpsJITRequestRepository opsJITRequestRepository;

    @Autowired
    private JITReasonRepository ztatReasonRepository;

    @Autowired
    private ZeroTrustAccessTokenApprovalRepository ztatApprovalRepository;

    @Autowired
    private OpsApprovalRepository opsApprovalRepository;
    @Autowired
    private SystemOptions systemOptions;


    @Transactional(readOnly = true)
    public List<ZeroTrustAccessTokenRequest> getAllJITRequests() {
        return ztatRequestRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ZeroTrustAccessTokenRequest getAccessTokenRequestById(Long id) {
        return ztatRequestRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("JITRequest not found"));
    }


    public OpsZeroTrustAcessTokenRequest getOpsAccessTokenRequestById(Long ztatId) {
        return opsJITRequestRepository.findById(ztatId)
            .orElseThrow(() -> new RuntimeException("OpsJITRequest not found"));
    }

    @Transactional
    public OpsZeroTrustAcessTokenRequest createOpsTATRequest(OpsZeroTrustAcessTokenRequest ztatRequest) {
        try {
            OpsZeroTrustAcessTokenRequest savedRequest = opsJITRequestRepository.save(ztatRequest);
            log.info("JITRequest created: {}", savedRequest);
            return savedRequest;
        } catch (Exception e) {
            log.error("Error while creating JITRequest", e);
            throw new RuntimeException("Failed to create JITRequest", e);
        }
    }

    @Transactional
    public ZeroTrustAccessTokenRequest createTATRequest(ZeroTrustAccessTokenRequest ztatRequest) {
        try {
            ZeroTrustAccessTokenRequest savedRequest = ztatRequestRepository.save(ztatRequest);
            log.info("JITRequest created: {}", savedRequest);
            return savedRequest;
        } catch (Exception e) {
            log.error("Error while creating JITRequest", e);
            throw new RuntimeException("Failed to create JITRequest", e);
        }
    }

    @Transactional
    public void deleteJITRequest(Long id) {
        try {
            ztatRequestRepository.deleteById(id);
            log.info("JITRequest deleted with id: {}", id);
        } catch (Exception e) {
            log.error("Error while deleting JITRequest", e);
            throw new RuntimeException("Failed to delete JITRequest", e);
        }
    }

    @Transactional
    public ZeroTrustAccessTokenRequest updateJITRequest(Long id, ZeroTrustAccessTokenRequest updatedJITRequest) {
        ZeroTrustAccessTokenRequest existingRequest = getAccessTokenRequestById(id);

        ZeroTrustAccessTokenRequest jr = ZeroTrustAccessTokenRequest.builder()
            .id(id)
            .command(updatedJITRequest.getCommand())
            .ztatReason(updatedJITRequest.getZtatReason())
            .system(updatedJITRequest.getSystem())
            .user(updatedJITRequest.getUser())
            .build();

        try {
            ZeroTrustAccessTokenRequest savedRequest = ztatRequestRepository.save(jr);
            log.info("JITRequest updated: {}", savedRequest);
            return savedRequest;
        } catch (Exception e) {
            log.error("Error while updating JITRequest", e);
            throw new RuntimeException("Failed to update JITRequest", e);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasJITRequest(String command, Long userId, Long systemId) {
        return ztatRequestRepository.existsByCommandAndUserIdAndSystemId(command, userId, systemId);
    }

    @Transactional
    public ZeroTrustAccessTokenRequest addJITRequest(ZeroTrustAccessTokenRequest ztatRequest) {
        try {
            ztatRequest.setZtatReason( ztatReasonRepository.save(ztatRequest.getZtatReason()) );
            ZeroTrustAccessTokenRequest savedRequest = ztatRequestRepository.save(ztatRequest);
            log.info("JITRequest added: {}", savedRequest);
            return savedRequest;
        } catch (Exception e) {
            log.error("Error while adding JITRequest", e);
            throw new RuntimeException("Failed to add JITRequest", e);
        }
    }


    @Transactional(readOnly = true)
    public List<ZeroTrustAccessTokenRequest> getAccessTokenRequests(String command, User user, HostSystem system) {
        final String commandHash = ZTATUtils.getCommandHash(command);
        var requests = ztatRequestRepository.findJITRequests(commandHash, user.getId(), system.getId());
        for(var request : requests){
            request.getApprovals().size();
        }
        return requests;
    }

    public void revokeJIT(ZeroTrustAccessTokenRequest ztatRequest, Long userId) {
        // Check if the JITRequest is linked to the given user
        if (ztatRequest.getUser().getId().equals(userId)) {
            ztatRequestRepository.delete(ztatRequest);
        } else {
            throw new IllegalArgumentException("The JITRequest does not belong to the specified user.");
        }
    }

    public Optional<ZeroTrustAccessTokenApproval> getAccessTokenStatus(ZeroTrustAccessTokenRequest request) {
        var approvals = request.getApprovals();
        if (!approvals.isEmpty()) {
            return Optional.of(approvals.get(0));
        }
        // Implement logic to retrieve the JIT status (if applicable).
        // Example: Retrieve from a specific table or calculate based on data.
        return Optional.empty(); // Placeholder for actual implementation.
    }

    public void setOpsAccessTokenStatus(OpsZeroTrustAcessTokenRequest reqeust, User user, boolean approval) {
        opsApprovalRepository.deleteByZtatRequestId(reqeust.getId());

        OpsApproval opsApproval = new OpsApproval();
        opsApproval.setApprover(user);
        opsApproval.setApproved(approval);
        opsApproval.setZtatRequest(reqeust);
        opsApproval.setUses(0);
        opsApprovalRepository.save(opsApproval);
    }

    public void getAccessTokenStatus(ZeroTrustAccessTokenRequest request, User user, boolean approval) {
        ztatApprovalRepository.deleteByztatRequestId(request.getId());

        ZeroTrustAccessTokenApproval ztatApproval = new ZeroTrustAccessTokenApproval();
        ztatApproval.setApprover(user);
        ztatApproval.setApproved(approval);
        ztatApproval.setZtatRequest(request);
        ztatApproval.setUses(0);
        ztatApprovalRepository.save(ztatApproval);
    }

    public void incrementAccessTokenUses(ZeroTrustAccessTokenRequest request) {
        if (request.getSystem().getId().equals(-1L)) {
            log.info("Incrementing uses for JITRequest: {}", ztatApprovalRepository.findByZtatRequestId(request.getId()).isPresent());
            opsApprovalRepository.findByZtatRequestId(request.getId()).ifPresent(approval -> {
                if (approval.getUses() >= systemOptions.maxJitUses) {
                    throw new RuntimeException("JIT uses exceeded");
                }
                approval.setUses(approval.getUses() + 1);
                log.info("Incrementing uses for JITRequest: {}", request.getId());
                opsApprovalRepository.save(approval);
            });
        } else {
            log.info("Incrementing uses for JITRequest: {}", ztatApprovalRepository.findByZtatRequestId(request.getId()).isPresent());
            ztatApprovalRepository.findByZtatRequestId(request.getId()).ifPresent(approval -> {
                if (approval.getUses() >= systemOptions.maxJitUses) {
                    throw new RuntimeException("JIT uses exceeded");
                }
                approval.setUses(approval.getUses() + 1);
                log.info("Incrementing uses for JITRequest: {}", request.getId());
                ztatApprovalRepository.save(approval);
            });
        }
    }

    public void revokeOpsAccesToken(ZeroTrustAccessTokenRequest ztatRequest, Long userId) {
        opsApprovalRepository.deleteByZtatRequestId(ztatRequest.getId());
    }

    public List<JITTrackerDTO> getOpenAccessTokenRequests(@NonNull User currentUser) {
        List<ZeroTrustAccessTokenRequest> openRequests = ztatRequestRepository.findOpenJITRequests(null);


        // Map each JITRequest to a JITTrackerDTO
        List<JITTrackerDTO> ztatTrackerList = new ArrayList<>();
        for (ZeroTrustAccessTokenRequest request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }

    public List<JITTrackerDTO> getOpenOpsRequests(@NonNull User currentUser) {
        // Fetch open JIT requests
        List<OpsZeroTrustAcessTokenRequest> openRequests = opsJITRequestRepository.findOpenOpsJITRequests(null);

        List<JITTrackerDTO> ztatTrackerList = new ArrayList<>();
        for (OpsZeroTrustAcessTokenRequest request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }

    public List<JITTrackerDTO> getDeniedOpsAccessTokenRequests(@NonNull User currentUser) {
        // Fetch open JIT requests
        List<OpsZeroTrustAcessTokenRequest> openRequests = opsJITRequestRepository.findAllWithUnapprovedRequests(null);

        List<JITTrackerDTO> ztatTrackerList = new ArrayList<>();
        for (OpsZeroTrustAcessTokenRequest request : openRequests) {

            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;

    }



    public List<JITTrackerDTO> getApprovedOpsAccessTokenRequests(@NonNull User currentUser) {
        List<OpsZeroTrustAcessTokenRequest> openRequests = opsJITRequestRepository.findAllApprovedRequests(null);

        List<JITTrackerDTO> ztatTrackerList = new ArrayList<>();
        for (var request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }

    public List<JITTrackerDTO> getApprovedTerminalAccessTokenRequests(@NonNull User currentUser) {
        List<ZeroTrustAccessTokenRequest> openRequests = ztatRequestRepository.findAllApprovedRequests(null);

        List<JITTrackerDTO> ztatTrackerList = new ArrayList<>();
        for (var request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }


    private JITTrackerDTO convertToDTO(ZeroTrustAccessTokenRequest request) {
        return JITTrackerDTO.builder()
            .id(request.getId())
            .command(request.getCommand())
            .lastUpdated(request.getLastUpdated())
            .commandHash(request.getCommandHash())
            .userName(request.getUser().getUsername())
            .hostName(request.getSystem().getHost())
            .reasonIdentifier(request.getZtatReason() != null ? request.getZtatReason().getReasonIdentifier() : null)
            .reasonUrl(request.getZtatReason() != null ? request.getZtatReason().getUrl() : null)
            .usesRemaining(getUsesRemaining(request)) // Add logic to calculate uses remaining
            .canResubmit(false) // Define logic as needed
            .build();
    }

    private JITTrackerDTO convertToDTO(OpsZeroTrustAcessTokenRequest request) {
        return JITTrackerDTO.builder()
            .id(request.getId())
            .summary(request.getSummary())
            .command(request.getCommand())
            .commandHash(request.getCommandHash())
            .userName(request.getUser().getUsername())
            .hostName("")
            .reasonIdentifier(request.getZtatReason() != null ? request.getZtatReason().getReasonIdentifier() : null)
            .reasonUrl(request.getZtatReason() != null ? request.getZtatReason().getUrl() : null)
            .usesRemaining(getUsesRemaining(request)) // Add logic to calculate uses remaining
            .canResubmit(false) // Define logic as needed
            .build();
    }

    private Integer getUsesRemaining(ZeroTrustAccessTokenRequest request) {
             // get the latest approval
            List<ZeroTrustAccessTokenApproval> approval = request.getApprovals();
            if (!approval.isEmpty()) {
                return systemOptions.maxJitUses - approval.get(0).getUses();
            }

        return systemOptions.maxJitUses; // Update as needed based on your logic
    }

    private Integer getUsesRemaining(OpsZeroTrustAcessTokenRequest request) {

            List<OpsApproval> approval = request.getApprovals();
            if (!approval.isEmpty()) {
                return systemOptions.maxJitUses - approval.get(0).getUses();
            }

        return systemOptions.maxJitUses; // Update as needed based on your logic
    }

    public List<JITTrackerDTO> getDeniedTerminalAccessTokenRequests(@NonNull User currentUser) {
        List<ZeroTrustAccessTokenRequest> openRequests = ztatRequestRepository.findAllWithUnapprovedRequests( null);

        List<JITTrackerDTO> ztatTrackerList = new ArrayList<>();
        for (ZeroTrustAccessTokenRequest request : openRequests) {

            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;

    }

}
