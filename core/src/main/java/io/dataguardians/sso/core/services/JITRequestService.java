package io.dataguardians.sso.core.services;

import io.dataguardians.sso.core.model.zt.JITRequest;
import io.dataguardians.sso.core.repository.JITRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class JITRequestService {

    @Autowired
    private JITRequestRepository jitRequestRepository;

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
}
