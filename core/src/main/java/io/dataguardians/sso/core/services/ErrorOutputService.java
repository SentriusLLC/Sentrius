package io.dataguardians.sso.core.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.dataguardians.sso.core.model.ErrorOutput;
import io.dataguardians.sso.core.repository.ErrorOutputRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Service
public class ErrorOutputService {

    @Autowired
    private ErrorOutputRepository errorOutputRepository;

    @Transactional(readOnly = true)
    public List<ErrorOutput> getAllErrorOutputs() {
        return errorOutputRepository.findAll();
    }

    @Transactional
    public void saveErrorOutput(ErrorOutput errorOutput) {
        try {
            if (errorOutput.getLogTm() == null){
                errorOutput.setLogTm(new java.sql.Timestamp(System.currentTimeMillis()));
            }
            errorOutputRepository.save(errorOutput);
            log.info("ErrorOutput saved: {}", errorOutput);
        } catch (Exception e) {
            log.error("Error while saving ErrorOutput", e);
        }
    }

    @Transactional(readOnly = true)
    public ErrorOutput getErrorOutputById(Long id) {
        return errorOutputRepository.findById(id).orElseThrow(() -> new RuntimeException("ErrorOutput not found"));
    }

    @Transactional
    public void deleteErrorOutput(Long id) {
        try {
            errorOutputRepository.deleteById(id);
            log.info("ErrorOutput deleted with id: {}", id);
        } catch (Exception e) {
            log.error("Error while deleting ErrorOutput", e);
        }
    }

    @Transactional(readOnly = true)
    public List<ErrorOutput> getErrorOutputs(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ErrorOutput> result = errorOutputRepository.findAllByOrderByLogTmDesc(pageRequest);
        return result.getContent();
    }

    @Transactional(readOnly = true)
    public Page<ErrorOutput> getErrorOutputs(PageRequest pageRequest) {
        Page<ErrorOutput> result = errorOutputRepository.findAllByOrderByLogTmDesc(pageRequest);
        return result;
    }

    @Transactional(readOnly = true)
    public Long count() {
        return errorOutputRepository.count();
    }

    @Transactional
    public void clear() {
        errorOutputRepository.deleteAll();
    }
}

