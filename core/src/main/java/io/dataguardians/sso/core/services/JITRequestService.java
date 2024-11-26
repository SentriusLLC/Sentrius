package io.dataguardians.sso.core.services;

import io.dataguardians.sso.core.config.SystemOptions;
import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.dto.JITTrackerDTO;
import io.dataguardians.sso.core.model.security.zt.JITStatus;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.zt.JITApproval;
import io.dataguardians.sso.core.model.zt.JITRequest;
import io.dataguardians.sso.core.model.zt.OpsApproval;
import io.dataguardians.sso.core.model.zt.OpsJITRequest;
import io.dataguardians.sso.core.repository.JITApprovalRepository;
import io.dataguardians.sso.core.repository.JITReasonRepository;
import io.dataguardians.sso.core.repository.JITRequestRepository;
import io.dataguardians.sso.core.repository.OpsApprovalRepository;
import io.dataguardians.sso.core.repository.OpsJITRequestRepository;
import io.dataguardians.sso.core.utils.JITUtils;
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
public class JITRequestService {

    @Autowired
    private JITRequestRepository jitRequestRepository;

    @Autowired
    private OpsJITRequestRepository opsJITRequestRepository;

    @Autowired
    private JITReasonRepository jitReasonRepository;

    @Autowired
    private JITApprovalRepository jitApprovalRepository;

    @Autowired
    private OpsApprovalRepository opsApprovalRepository;
    @Autowired
    private SystemOptions systemOptions;


    @Transactional(readOnly = true)
    public List<JITRequest> getAllJITRequests() {
        return jitRequestRepository.findAll();
    }

    @Transactional(readOnly = true)
    public JITRequest getJITRequestById(Long id) {
        return jitRequestRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("JITRequest not found"));
    }


    public OpsJITRequest getOpsJITRequestById(Long jitId) {
        return opsJITRequestRepository.findById(jitId)
            .orElseThrow(() -> new RuntimeException("OpsJITRequest not found"));
    }

    @Transactional
    public JITRequest createJITRequest(JITRequest jitRequest) {
        try {
            JITRequest savedRequest = jitRequestRepository.save(jitRequest);
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
            jitRequestRepository.deleteById(id);
            log.info("JITRequest deleted with id: {}", id);
        } catch (Exception e) {
            log.error("Error while deleting JITRequest", e);
            throw new RuntimeException("Failed to delete JITRequest", e);
        }
    }

    @Transactional
    public JITRequest updateJITRequest(Long id, JITRequest updatedJITRequest) {
        JITRequest existingRequest = getJITRequestById(id);

        JITRequest jr = JITRequest.builder()
            .id(id)
            .command(updatedJITRequest.getCommand())
            .jitReason(updatedJITRequest.getJitReason())
            .system(updatedJITRequest.getSystem())
            .user(updatedJITRequest.getUser())
            .build();

        try {
            JITRequest savedRequest = jitRequestRepository.save(jr);
            log.info("JITRequest updated: {}", savedRequest);
            return savedRequest;
        } catch (Exception e) {
            log.error("Error while updating JITRequest", e);
            throw new RuntimeException("Failed to update JITRequest", e);
        }
    }

    @Transactional(readOnly = true)
    public boolean hasJITRequest(String command, Long userId, Long systemId) {
        return jitRequestRepository.existsByCommandAndUserIdAndSystemId(command, userId, systemId);
    }

    @Transactional
    public JITRequest addJITRequest(JITRequest jitRequest) {
        try {
            jitRequest.setJitReason( jitReasonRepository.save(jitRequest.getJitReason()) );
            JITRequest savedRequest = jitRequestRepository.save(jitRequest);
            log.info("JITRequest added: {}", savedRequest);
            return savedRequest;
        } catch (Exception e) {
            log.error("Error while adding JITRequest", e);
            throw new RuntimeException("Failed to add JITRequest", e);
        }
    }


    @Transactional(readOnly = true)
    public List<JITRequest> getJITRequests(String command, User user, HostSystem system) {
        final String commandHash = JITUtils.getCommandHash(command);
        var requests = jitRequestRepository.findJITRequests(commandHash, user.getId(), system.getId());
        for(var request : requests){
            request.getApprovals().size();
        }
        return requests;
    }

    public void revokeJIT(JITRequest jitRequest, Long userId) {
        // Check if the JITRequest is linked to the given user
        if (jitRequest.getUser().getId().equals(userId)) {
            jitRequestRepository.delete(jitRequest);
        } else {
            throw new IllegalArgumentException("The JITRequest does not belong to the specified user.");
        }
    }

    public Optional<JITApproval> getJITStatus(JITRequest request) {
        var approvals = request.getApprovals();
        if (!approvals.isEmpty()) {
            return Optional.of(approvals.get(0));
        }
        // Implement logic to retrieve the JIT status (if applicable).
        // Example: Retrieve from a specific table or calculate based on data.
        return Optional.empty(); // Placeholder for actual implementation.
    }

    public void setOpsJITStatus(OpsJITRequest reqeust, User user, boolean approval) {
        opsApprovalRepository.deleteByJitRequestId(reqeust.getId());

        OpsApproval opsApproval = new OpsApproval();
        opsApproval.setApprover(user);
        opsApproval.setApproved(approval);
        opsApproval.setJitRequest(reqeust);
        opsApproval.setUses(0);
        opsApprovalRepository.save(opsApproval);
    }

    public void setJITStatus(JITRequest request, User user, boolean approval) {
        jitApprovalRepository.deleteByJitRequestId(request.getId());

        JITApproval jitApproval = new JITApproval();
        jitApproval.setApprover(user);
        jitApproval.setApproved(approval);
        jitApproval.setJitRequest(request);
        jitApproval.setUses(0);
        jitApprovalRepository.save(jitApproval);
    }

    public void incrementJITUses(JITRequest request) {
        if (request.getSystem().getId().equals(-1L)) {
            log.info("Incrementing uses for JITRequest: {}", jitApprovalRepository.findByJitRequestId(request.getId()).isPresent());
            opsApprovalRepository.findByJitRequestId(request.getId()).ifPresent(approval -> {
                if (approval.getUses() >= systemOptions.maxJitUses) {
                    throw new RuntimeException("JIT uses exceeded");
                }
                approval.setUses(approval.getUses() + 1);
                log.info("Incrementing uses for JITRequest: {}", request.getId());
                opsApprovalRepository.save(approval);
            });
        } else {
            log.info("Incrementing uses for JITRequest: {}", jitApprovalRepository.findByJitRequestId(request.getId()).isPresent());
            jitApprovalRepository.findByJitRequestId(request.getId()).ifPresent(approval -> {
                if (approval.getUses() >= systemOptions.maxJitUses) {
                    throw new RuntimeException("JIT uses exceeded");
                }
                approval.setUses(approval.getUses() + 1);
                log.info("Incrementing uses for JITRequest: {}", request.getId());
                jitApprovalRepository.save(approval);
            });
        }
    }

    public void revokeOpsJIT(JITRequest jitRequest, Long userId) {
        opsApprovalRepository.deleteByJitRequestId(jitRequest.getId());
    }

    public List<JITTrackerDTO> getOpenJITRequests(@NonNull User currentUser) {
        List<JITRequest> openRequests = jitRequestRepository.findOpenJITRequests(null);


        // Map each JITRequest to a JITTrackerDTO
        List<JITTrackerDTO> jitTrackerList = new ArrayList<>();
        for (JITRequest request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            jitTrackerList.add(dto);
        }

        return jitTrackerList;
    }

    public List<JITTrackerDTO> getOpenOpsRequests(@NonNull User currentUser) {
        // Fetch open JIT requests
        List<OpsJITRequest> openRequests = opsJITRequestRepository.findOpenOpsJITRequests(null);

        List<JITTrackerDTO> jitTrackerList = new ArrayList<>();
        for (OpsJITRequest request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            jitTrackerList.add(dto);
        }

        return jitTrackerList;
    }

    public List<JITTrackerDTO> getDeniedOpsJITRequests(@NonNull User currentUser) {
        // Fetch open JIT requests
        List<OpsJITRequest> openRequests = opsJITRequestRepository.findAllWithUnapprovedRequests(null);

        List<JITTrackerDTO> jitTrackerList = new ArrayList<>();
        for (OpsJITRequest request : openRequests) {

            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            jitTrackerList.add(dto);
        }

        return jitTrackerList;

    }



    public List<JITTrackerDTO> getApprovedOpsJITRequests(@NonNull User currentUser) {
        List<OpsJITRequest> openRequests = opsJITRequestRepository.findAllApprovedRequests(null);

        List<JITTrackerDTO> jitTrackerList = new ArrayList<>();
        for (var request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            jitTrackerList.add(dto);
        }

        return jitTrackerList;
    }

    public List<JITTrackerDTO> getApprovedTerminalJITRequests(@NonNull User currentUser) {
        List<JITRequest> openRequests = jitRequestRepository.findAllApprovedRequests(null);

        List<JITTrackerDTO> jitTrackerList = new ArrayList<>();
        for (var request : openRequests) {
            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            jitTrackerList.add(dto);
        }

        return jitTrackerList;
    }


    private JITTrackerDTO convertToDTO(JITRequest request) {
        return JITTrackerDTO.builder()
            .id(request.getId())
            .command(request.getCommand())
            .commandHash(request.getCommandHash())
            .userName(request.getUser().getUsername())
            .hostName(request.getSystem().getHost())
            .reasonIdentifier(request.getJitReason() != null ? request.getJitReason().getReasonIdentifier() : null)
            .reasonUrl(request.getJitReason() != null ? request.getJitReason().getUrl() : null)
            .usesRemaining(getUsesRemaining(request)) // Add logic to calculate uses remaining
            .canResubmit(false) // Define logic as needed
            .build();
    }

    private JITTrackerDTO convertToDTO(OpsJITRequest request) {
        return JITTrackerDTO.builder()
            .id(request.getId())
            .command(request.getCommand())
            .commandHash(request.getCommandHash())
            .userName(request.getUser().getUsername())
            .hostName(request.getSystem().getHost())
            .reasonIdentifier(request.getJitReason() != null ? request.getJitReason().getReasonIdentifier() : null)
            .reasonUrl(request.getJitReason() != null ? request.getJitReason().getUrl() : null)
            .usesRemaining(getUsesRemaining(request)) // Add logic to calculate uses remaining
            .canResubmit(false) // Define logic as needed
            .build();
    }

    private Integer getUsesRemaining(JITRequest request) {
             // get the latest approval
            List<JITApproval> approval = request.getApprovals();
            if (!approval.isEmpty()) {
                return systemOptions.maxJitUses - approval.get(0).getUses();
            }

        return systemOptions.maxJitUses; // Update as needed based on your logic
    }

    private Integer getUsesRemaining(OpsJITRequest request) {

            List<OpsApproval> approval = request.getApprovals();
            if (!approval.isEmpty()) {
                return systemOptions.maxJitUses - approval.get(0).getUses();
            }

        return systemOptions.maxJitUses; // Update as needed based on your logic
    }

    public List<JITTrackerDTO> getDeniedTerminalJITRequests(@NonNull User currentUser) {
        List<JITRequest> openRequests = jitRequestRepository.findAllWithUnapprovedRequests( null);

        List<JITTrackerDTO> jitTrackerList = new ArrayList<>();
        for (JITRequest request : openRequests) {

            var dto = convertToDTO(request);
            if (currentUser.getId() == request.getUser().getId()) {
                dto.setCurrentUser(true);
            }
            jitTrackerList.add(dto);
        }

        return jitTrackerList;

    }

}
