package io.sentrius.sso;

import io.sentrius.sso.core.model.automation.Automation;
import io.sentrius.sso.core.model.automation.AutomationCronEntry;
import io.sentrius.sso.core.model.users.User;
import io.sentrius.sso.core.repository.automation.ScriptAssignmentRepository;
import io.sentrius.sso.core.repository.automation.ScriptCronEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.CronExpression;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// filepath: api/src/test/java/io/sentrius/sso/ServerCronServiceTest.java





class ServerCronServiceTest {

  @Mock
  private Scheduler scheduler;

  @Mock
  private ScriptCronEntryRepository scriptCronEntryRepository;

  @Mock
  private ScriptAssignmentRepository scriptAssignmentRepository;

  @InjectMocks
  private ServerCronService serverCronService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void testGetNextExpectedRun_ValidCron() throws Exception {
    String validCron = "0 0 12 * * ?";
    Date now = new Date();
    Date expectedDate = new CronExpression(validCron).getNextValidTimeAfter(now);

    Date result = serverCronService.getNextExpectedRun(validCron);

    assertNotNull(result);
    assertEquals(expectedDate, result);
  }

  @Test
  void testGetNextExpectedRun_InvalidCron() {
    String invalidCron = "invalid cron";

    Date result = serverCronService.getNextExpectedRun(invalidCron);

    assertNull(result);
  }

  @Test
  void testGetNextExpectedRun_NullOrEmptyCron() {
    assertNull(serverCronService.getNextExpectedRun(null));
    assertNull(serverCronService.getNextExpectedRun(""));
    assertNull(serverCronService.getNextExpectedRun("x x x x x"));
  }

  @Test
  void testSanitizeCronExpression_ValidCron() {
    String cron = "0 0 12 * * *";
    String expected = "0 0 12 * * ?";

    String result = serverCronService.sanitizeCronExpression(cron);

    assertEquals(expected, result);
  }

  @Test
  void testSanitizeCronExpression_InvalidCron() {
    String cron = "invalid cron";

    String result = serverCronService.sanitizeCronExpression(cron);

    assertEquals("0 invalid cron", result);
  }

  @Test
  void testReloadCronEntries_Success() throws Exception {
    var cronEntry = mock(AutomationCronEntry.class);
    var automation = mock(Automation.class);
    when(cronEntry.getAutomation()).thenReturn(automation);
    when(automation.getId()).thenReturn(1L);
    when(automation.getUser()).thenReturn(mock(User.class));
    when(automation.getScript()).thenReturn("script");
    when(automation.getType()).thenReturn("type");
    when(scriptCronEntryRepository.findAll()).thenReturn(List.of(cronEntry));
    when(scriptAssignmentRepository.findAllByAutomationId(1L)).thenReturn(new ArrayList<>());

    List<String> warnings = serverCronService.reloadCronEntries();

    verify(scheduler).clear();
    verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    assertTrue(warnings.isEmpty());
  }

  @Test
  void testReloadCronEntries_InvalidCron() throws Exception {
    var cronEntry = mock(AutomationCronEntry.class);
    var automation = mock(Automation.class);
    when(cronEntry.getAutomation()).thenReturn(automation);
    when(cronEntry.getScriptCron()).thenReturn("invalid cron");
    when(automation.getId()).thenReturn(1L);
    when(scriptCronEntryRepository.findAll()).thenReturn(List.of(cronEntry));

    List<String> warnings = serverCronService.reloadCronEntries();

    verify(scheduler).clear();
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).contains("invalid cron"));
  }
}