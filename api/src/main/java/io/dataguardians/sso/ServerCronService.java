package io.dataguardians.sso;

import io.dataguardians.sso.core.repository.automation.ScriptAssignmentRepository;
import io.dataguardians.sso.core.repository.automation.ScriptCronEntryRepository;
import io.dataguardians.sso.core.utils.ScriptCronTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerCronService {

  private final Scheduler scheduler;
  // Replace ScriptDB with repository
  private final ScriptCronEntryRepository scriptCronEntryRepository;
  private final ScriptAssignmentRepository scriptAssignmentRepository;


  @PostConstruct
  public void init() {
    try {
      scheduler.start();
      reloadCronEntries();
    } catch (SchedulerException e) {
      log.error("Failed to initialize scheduler: {}", e.getMessage(), e);
    }
  }

  public Date getNextExpectedRun(String cron) {
    if (cron == null || cron.isEmpty() || "x x x x x".equals(cron)) {
      return null;
    }
    try {
      CronExpression cronExpression = new CronExpression(sanitizeCronExpression(cron));
      return cronExpression.getNextValidTimeAfter(new Date());
    } catch (ParseException e) {
      log.error("Failed to parse cron expression: {}", e.getMessage(), e);
      return null;
    }
  }

  private String sanitizeCronExpression(String expr) {
    String[] splitOnSpace = expr.split(" ");
    if (splitOnSpace.length == 5) {
      if (splitOnSpace[4].equals("*") && splitOnSpace[2].equals("*")) {
        splitOnSpace[4] = "?";
      } else {
        if (splitOnSpace[2].equals("*")) {
          splitOnSpace[2] = "?";
        } else if (splitOnSpace[4].equals("*")) {
          splitOnSpace[4] = "?";
        }
      }
      return "0 " + String.join(" ", splitOnSpace);
    }
    return "0 " + expr;
  }

  public List<String> reloadCronEntries() {
    List<String> cronWarnings = new ArrayList<>();
    try {
      scheduler.clear();
      var cronEntries = scriptCronEntryRepository.findAll(); // Assuming a method in repository

      for (var cronEntry : cronEntries) {
        JobDataMap datamap = new JobDataMap();
        var automation = cronEntry.getAutomation();
        datamap.put("script_id", automation.getId());
        datamap.put("user_id", automation.getUser().getId());
        datamap.put("script", automation.getScript());
        datamap.put("script_type", automation.getType());
        var assignments = scriptAssignmentRepository.findAllByAutomationId(automation.getId());
        List<Long> systemIds = new ArrayList<>();
        assignments.forEach(assignment -> systemIds.add(assignment.getId()));
        datamap.put("system_ids", systemIds);

        String ident = "cronscriptrunner" + automation.getId();
        JobDetail job = newJob(ScriptCronTask.class)
            .withIdentity(ident, "cronscriptrunner")
            .setJobData(datamap)
            .build();

        try {
          CronTrigger trigger = newTrigger()
              .withIdentity(ident, "cronscriptrunner")
              .withSchedule(cronSchedule(sanitizeCronExpression(cronEntry.getScriptCron())))
              .build();
          scheduler.scheduleJob(job, trigger);
        } catch (Exception pe) {
          cronWarnings.add("Cron expression " + cronEntry.getScriptCron() + " for script " +
              automation.getId() + " is invalid");
        }
      }
    } catch (SchedulerException e) {
      log.error("Error reloading cron entries: {}", e.getMessage(), e);
    }
    return cronWarnings;
  }
}
