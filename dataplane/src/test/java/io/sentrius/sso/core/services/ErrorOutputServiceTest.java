package io.sentrius.sso.core.services;

import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.repository.ErrorOutputRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ErrorOutputServiceTest {

    @Mock
    private ErrorOutputRepository errorOutputRepository;

    @InjectMocks
    private ErrorOutputService errorOutputService;

    private ErrorOutput testErrorOutput;

    @BeforeEach
    void setUp() {
        testErrorOutput = new ErrorOutput();
        testErrorOutput.setId(1L);
        testErrorOutput.setLogTm(new Timestamp(System.currentTimeMillis()));
    }

    @Test
    void getAllErrorOutputsReturnsAllErrorOutputs() {
        List<ErrorOutput> errorOutputs = Arrays.asList(testErrorOutput);
        when(errorOutputRepository.findAll()).thenReturn(errorOutputs);

        List<ErrorOutput> result = errorOutputService.getAllErrorOutputs();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testErrorOutput, result.get(0));
        verify(errorOutputRepository).findAll();
    }

    @Test
    void saveErrorOutputSavesSuccessfully() {
        when(errorOutputRepository.save(any(ErrorOutput.class))).thenReturn(testErrorOutput);

        errorOutputService.saveErrorOutput(testErrorOutput);

        verify(errorOutputRepository).save(testErrorOutput);
    }

    @Test
    void saveErrorOutputSetsTimestampWhenNull() {
        ErrorOutput errorOutputWithoutTimestamp = new ErrorOutput();
        errorOutputWithoutTimestamp.setLogTm(null);
        
        when(errorOutputRepository.save(any(ErrorOutput.class))).thenReturn(errorOutputWithoutTimestamp);

        errorOutputService.saveErrorOutput(errorOutputWithoutTimestamp);

        assertNotNull(errorOutputWithoutTimestamp.getLogTm());
        verify(errorOutputRepository).save(errorOutputWithoutTimestamp);
    }

    @Test
    void getErrorOutputByIdReturnsErrorOutput() {
        when(errorOutputRepository.findById(1L)).thenReturn(Optional.of(testErrorOutput));

        ErrorOutput result = errorOutputService.getErrorOutputById(1L);

        assertNotNull(result);
        assertEquals(testErrorOutput, result);
        verify(errorOutputRepository).findById(1L);
    }

    @Test
    void getErrorOutputByIdThrowsExceptionWhenNotFound() {
        when(errorOutputRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> errorOutputService.getErrorOutputById(1L));
        verify(errorOutputRepository).findById(1L);
    }

    @Test
    void deleteErrorOutputDeletesSuccessfully() {
        doNothing().when(errorOutputRepository).deleteById(1L);

        errorOutputService.deleteErrorOutput(1L);

        verify(errorOutputRepository).deleteById(1L);
    }

    @Test
    void getErrorOutputsWithPageAndSizeReturnsPagedResults() {
        List<ErrorOutput> errorOutputs = Arrays.asList(testErrorOutput);
        Page<ErrorOutput> page = new PageImpl<>(errorOutputs);
        PageRequest pageRequest = PageRequest.of(0, 10);
        
        when(errorOutputRepository.findAllByOrderByLogTmDesc(pageRequest)).thenReturn(page);

        List<ErrorOutput> result = errorOutputService.getErrorOutputs(0, 10);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testErrorOutput, result.get(0));
        verify(errorOutputRepository).findAllByOrderByLogTmDesc(pageRequest);
    }

    @Test
    void getErrorOutputsWithPageRequestReturnsPage() {
        List<ErrorOutput> errorOutputs = Arrays.asList(testErrorOutput);
        Page<ErrorOutput> page = new PageImpl<>(errorOutputs);
        PageRequest pageRequest = PageRequest.of(0, 10);
        
        when(errorOutputRepository.findAllByOrderByLogTmDesc(pageRequest)).thenReturn(page);

        Page<ErrorOutput> result = errorOutputService.getErrorOutputs(pageRequest);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(testErrorOutput, result.getContent().get(0));
        verify(errorOutputRepository).findAllByOrderByLogTmDesc(pageRequest);
    }

    @Test
    void countReturnsCorrectCount() {
        when(errorOutputRepository.count()).thenReturn(5L);

        Long result = errorOutputService.count();

        assertEquals(5L, result);
        verify(errorOutputRepository).count();
    }

    @Test
    void clearDeletesAllErrorOutputs() {
        doNothing().when(errorOutputRepository).deleteAll();

        errorOutputService.clear();

        verify(errorOutputRepository).deleteAll();
    }
}