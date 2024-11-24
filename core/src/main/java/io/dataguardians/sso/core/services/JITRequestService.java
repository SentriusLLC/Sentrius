package io.dataguardians.sso.core.services;

import io.dataguardians.sso.core.model.HostSystem;
import io.dataguardians.sso.core.model.security.zt.JITStatus;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.model.zt.JITApproval;
import io.dataguardians.sso.core.model.zt.JITRequest;
import io.dataguardians.sso.core.model.zt.OpsApproval;
import io.dataguardians.sso.core.repository.JITApprovalRepository;
import io.dataguardians.sso.core.repository.JITRequestRepository;
import io.dataguardians.sso.core.repository.OpsApprovalRepository;
import io.dataguardians.sso.core.utils.JITUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class JITRequestService {

    @Autowired
    private JITRequestRepository jitRequestRepository;

    @Autowired
    private JITApprovalRepository jitApprovalRepository;

    @Autowired
    private OpsApprovalRepository opsApprovalRepository;


    @Transactional(readOnly = true)
    public List<JITRequest> getAllJITRequests() {
        return jitRequestRepository.findAll();
    }

    @Transactional(readOnly = true)
    public JITRequest getJITRequestById(Long id) {
        return jitRequestRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("JITRequest not found"));
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
        return jitRequestRepository.findJITRequests(commandHash, user.getId(), system.getId());
    }

    public void revokeJIT(JITRequest jitRequest, Long userId) {
        // Check if the JITRequest is linked to the given user
        if (jitRequest.getUser().getId().equals(userId)) {
            jitRequestRepository.delete(jitRequest);
        } else {
            throw new IllegalArgumentException("The JITRequest does not belong to the specified user.");
        }
    }

    public Optional<JITStatus> getJITStatus(JITRequest request) {
        // Implement logic to retrieve the JIT status (if applicable).
        // Example: Retrieve from a specific table or calculate based on data.
        return Optional.empty(); // Placeholder for actual implementation.
    }

    public void setOpsJITStatus(JITRequest reqeust, User user, boolean approval) {
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
            opsApprovalRepository.findById(request.getId()).ifPresent(approval -> {
                approval.setUses(approval.getUses() + 1);
                opsApprovalRepository.save(approval);
            });
        } else {
            jitApprovalRepository.findById(request.getId()).ifPresent(approval -> {
                approval.setUses(approval.getUses() + 1);
                jitApprovalRepository.save(approval);
            });
        }
    }

    public void revokeOpsJIT(JITRequest jitRequest, Long userId) {
        opsApprovalRepository.deleteByJitRequestId(jitRequest.getId());
    }
}
