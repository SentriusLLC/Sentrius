package io.sentrius.sso.core.services.selfhealing;

import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession;
import io.sentrius.sso.core.model.selfhealing.SelfHealingSession.HealingStatus;
import io.sentrius.sso.core.repository.selfhealing.SelfHealingSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SelfHealingSessionService {

    @Autowired
    private SelfHealingSessionRepository sessionRepository;

    @Transactional(readOnly = true)
    public List<SelfHealingSession> getAllSessions() {
        return sessionRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<SelfHealingSession> getSessionsByStatus(HealingStatus status) {
        return sessionRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public Optional<SelfHealingSession> getSessionById(Long id) {
        return sessionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SelfHealingSession> getSessionByErrorOutputId(Long errorOutputId) {
        return sessionRepository.findByErrorOutputId(errorOutputId);
    }

    @Transactional
    public SelfHealingSession createSession(ErrorOutput errorOutput, String podName) {
        SelfHealingSession session = SelfHealingSession.builder()
                .errorOutput(errorOutput)
                .podName(podName)
                .status(HealingStatus.PENDING)
                .startedAt(new Timestamp(System.currentTimeMillis()))
                .build();
        
        SelfHealingSession saved = sessionRepository.save(session);
        log.info("Created self-healing session {} for error {}", saved.getId(), errorOutput.getId());
        return saved;
    }

    @Transactional
    public SelfHealingSession updateSession(SelfHealingSession session) {
        SelfHealingSession saved = sessionRepository.save(session);
        log.info("Updated self-healing session {}: status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public void updateSessionStatus(Long sessionId, HealingStatus status) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(status);
            if (status == HealingStatus.COMPLETED || status == HealingStatus.FAILED) {
                session.setCompletedAt(new Timestamp(System.currentTimeMillis()));
            }
            sessionRepository.save(session);
            log.info("Updated session {} status to {}", sessionId, status);
        });
    }

    @Transactional
    public void recordSecurityAnalysis(Long sessionId, boolean isSecurityConcern, String analysis) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setIsSecurityConcern(isSecurityConcern);
            session.setSecurityAnalysis(analysis);
            session.setStatus(HealingStatus.ANALYZING);
            sessionRepository.save(session);
            log.info("Recorded security analysis for session {}: isConcern={}", sessionId, isSecurityConcern);
        });
    }

    @Transactional
    public void recordGitHubPR(Long sessionId, String prUrl) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setGithubPrUrl(prUrl);
            sessionRepository.save(session);
            log.info("Recorded GitHub PR for session {}: {}", sessionId, prUrl);
        });
    }
}
