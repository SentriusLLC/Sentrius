package io.sentrius.sso.core.services.security;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.dto.ZtatDTO;
import io.sentrius.sso.core.model.HostSystem;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.model.zt.OpsUse;
import io.sentrius.sso.core.model.zt.RequestCommunicationLink;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenApproval;
import io.sentrius.sso.core.model.zt.ZeroTrustAccessTokenRequest;
import io.sentrius.sso.core.model.zt.OpsApproval;
import io.sentrius.sso.core.model.zt.OpsZeroTrustAcessTokenRequest;
import io.sentrius.sso.core.model.zt.ZtatUse;
import io.sentrius.sso.core.repository.OpsUseRepository;
import io.sentrius.sso.core.repository.RequestCommunicationLinkRepository;
import io.sentrius.sso.core.repository.ZeroTrustAccessTokenApprovalRepository;
import io.sentrius.sso.core.repository.JITReasonRepository;
import io.sentrius.sso.core.repository.ZeroTrustAccessTokenRequestRepository;
import io.sentrius.sso.core.repository.OpsApprovalRepository;
import io.sentrius.sso.core.repository.OpsJITRequestRepository;
import io.sentrius.sso.core.repository.ZtatUseRepository;
import io.sentrius.sso.core.utils.ZTATUtils;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    @Autowired
    private ZtatUseRepository ztatUseRepository;

    @Autowired
    private RequestCommunicationLinkRepository requestCommunicationLinkRepository;
    @Autowired
    private OpsUseRepository opsUseRepository;


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
            if (ztatRequest.getZtatReason() != null && ztatRequest.getZtatReason().getId() == null) {
                // save the reason if it is new
                ztatRequest.setZtatReason(ztatReasonRepository.save(ztatRequest.getZtatReason()));
            }
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

    @Transactional
    public OpsZeroTrustAcessTokenRequest addJITRequest(OpsZeroTrustAcessTokenRequest ztatRequest) {
        try {
            ztatRequest.setZtatReason( ztatReasonRepository.save(ztatRequest.getZtatReason()) );
            OpsZeroTrustAcessTokenRequest savedRequest = opsJITRequestRepository.save(ztatRequest);
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

    @Transactional(readOnly = true)
    public Optional<OpsApproval> getOpsTokenStatus(String token ) {
        try {
            return opsApprovalRepository.findByToken(UUID.fromString(token));
        }catch (IllegalArgumentException e){
            log.error("Invalid UUID token format: {}", token);
            return Optional.empty();
        }
    }

    public Optional<ZeroTrustAccessTokenApproval> getAccessTokenStatus(ZeroTrustAccessTokenRequest request) {
        var approvals = request.getApprovals();
        log.info("Approvals for request {}: {}", request.getId(), approvals.size());
        if (!approvals.isEmpty()) {
            return Optional.of(approvals.get(0));
        }

        return Optional.empty(); // Placeholder for actual implementation.
    }

    @Transactional
    public OpsApproval setOpsAccessTokenStatus(OpsZeroTrustAcessTokenRequest reqeust, User user, boolean approval) {
        opsApprovalRepository.deleteByZtatRequestId(reqeust.getId());

        OpsApproval opsApproval = new OpsApproval();
        opsApproval.setApprover(user);
        opsApproval.setApproved(approval);
        opsApproval.setZtatRequest(reqeust);
        opsApproval.setUses(0);
        return opsApprovalRepository.save(opsApproval);
    }

    @Transactional
    public void getAccessTokenStatus(ZeroTrustAccessTokenRequest request, User user, boolean approval) {
        ztatApprovalRepository.deleteByztatRequestId(request.getId());

        ZeroTrustAccessTokenApproval ztatApproval = new ZeroTrustAccessTokenApproval();
        ztatApproval.setApprover(user);
        ztatApproval.setApproved(approval);
        ztatApproval.setZtatRequest(request);
        ztatApproval.setUses(0);
        ztatApprovalRepository.save(ztatApproval);
    }

    @Transactional
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

                ztatUseRepository.save(ZtatUse.builder().ztatApproval(approval).user(request.getUser()).build());
                log.info("Incrementing uses for JITRequest: {}", request.getId());
                ztatApprovalRepository.save(approval);

                approval.setUses(approval.getUses() + 1);
                ztatApprovalRepository.save(approval);
            });
        }
    }

    public void revokeOpsAccesToken(ZeroTrustAccessTokenRequest ztatRequest, Long userId) {
        opsApprovalRepository.deleteByZtatRequestId(ztatRequest.getId());
    }

    public List<ZtatDTO> getOpenAccessTokenRequests(@NonNull User currentUser) {
        List<ZeroTrustAccessTokenRequest> openRequests = ztatRequestRepository.findOpenJITRequests(null);


        // Map each JITRequest to a JITTrackerDTO
        List<ZtatDTO> ztatTrackerList = new ArrayList<>();
        for (ZeroTrustAccessTokenRequest request : openRequests) {
            var dto = convertToDTO(request);
            if (Objects.equals(currentUser.getId(), request.getUser().getId())) {
                log.info("Current user matches request user: {}", request.getUser().getUsername());
                dto.setCurrentUser(true);
            }
            else {
                log.info("Current user does not match request user: {} vs {}", currentUser.getUsername(), request.getUser().getUsername());
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }

    public List<ZtatDTO> getOpenOpsRequests(@NonNull User currentUser) {
        // Fetch open JIT requests
        List<OpsZeroTrustAcessTokenRequest> openRequests = opsJITRequestRepository.findOpenOpsJITRequests(null);

        List<ZtatDTO> ztatTrackerList = new ArrayList<>();
        for (OpsZeroTrustAcessTokenRequest request : openRequests) {
            var dto = convertToDTO(request);
            if (Objects.equals(currentUser.getId(), request.getUser().getId())) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }

    public List<ZtatDTO> getDeniedOpsAccessTokenRequests(@NonNull User currentUser) {
        // Fetch open JIT requests
        List<OpsZeroTrustAcessTokenRequest> openRequests = opsJITRequestRepository.findAllWithUnapprovedRequests(null);

        List<ZtatDTO> ztatTrackerList = new ArrayList<>();
        for (OpsZeroTrustAcessTokenRequest request : openRequests) {

            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;

    }



    public List<ZtatDTO> getApprovedOpsAccessTokenRequests(@NonNull User currentUser) {
        List<OpsZeroTrustAcessTokenRequest> openRequests = opsJITRequestRepository.findAllApprovedRequests(null);
        log.info("Approved Ops Access Token Requests: {}", openRequests.size());
        List<ZtatDTO> ztatTrackerList = new ArrayList<>();
        for (var request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }

    public List<ZtatDTO> getApprovedTerminalAccessTokenRequests(@NonNull User currentUser) {
        List<ZeroTrustAccessTokenRequest> openRequests = ztatRequestRepository.findAllApprovedRequests(null);

        List<ZtatDTO> ztatTrackerList = new ArrayList<>();
        for (var request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;
    }


    private ZtatDTO convertToDTO(ZeroTrustAccessTokenRequest request) {
        return ZtatDTO.builder()
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

    private ZtatDTO convertToDTO(OpsZeroTrustAcessTokenRequest request) {
        return ZtatDTO.builder()
            .id(request.getId())
            .summary(request.getSummary())
            .command(request.getCommand())
            .commandHash(request.getCommandHash())
            .userName(request.getUser().getUsername())
            .communicationIds( request.getCommunicationLinks().stream()
                .map(RequestCommunicationLink::getCommunication)
                .map(AgentCommunication::getCommunicationId)
                .map(UUID::toString)
                .toList())
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
                var uses = ztatUseRepository.getUses(approval.get(0));
                var usesRemaining = systemOptions.maxJitUses - uses.size();
                return Math.max(usesRemaining, 0);
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

    public List<ZtatDTO> getDeniedTerminalAccessTokenRequests(@NonNull User currentUser) {
        List<ZeroTrustAccessTokenRequest> openRequests = ztatRequestRepository.findAllWithUnapprovedRequests( null);

        List<ZtatDTO> ztatTrackerList = new ArrayList<>();
        for (ZeroTrustAccessTokenRequest request : openRequests) {

            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            ztatTrackerList.add(dto);
        }

        return ztatTrackerList;

    }


    @Transactional
    public void addCommunicationLink(RequestCommunicationLink link) {
        requestCommunicationLinkRepository.save(link);
    }

    public void incrementAccessTokenUses(OpsApproval request) {
        log.info("Incrementing uses for JITRequest: {}",
            opsApprovalRepository.findByZtatRequestId(request.getZtatRequest().getId()).isPresent());
        opsApprovalRepository.findByZtatRequestId(request.getZtatRequest().getId()).ifPresent(approval -> {
            if (approval.getUses() >= systemOptions.maxJitUses) {
                throw new RuntimeException("JIT uses exceeded");
            }
            log.info("Incrementing uses for JITRequest: {}", request.getId());
            opsUseRepository.save(OpsUse.builder().opsApproval(approval).user(request.getApprover()).build());
            log.info("Incrementing uses for JITRequest: {}", request.getId());
            approval.setUses( approval.getUses() + 1 );
            opsApprovalRepository.save(approval);
        });
    }
}
