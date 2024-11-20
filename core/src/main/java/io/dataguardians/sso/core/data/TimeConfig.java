package io.dataguardians.sso.core.data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dataguardians.sso.core.model.hostgroup.TimeConfigJson;
import io.dataguardians.sso.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TimeConfig {

    private Long id;

    @Builder.Default
    private String uuid = UUID.randomUUID().toString();

    private String title;

    private String configurationJson;

    private Set<Integer> daysOfWeek = new HashSet<>();

    private int beginRangeHour;

    private int beginRangeMinute;

    private int beginRangeSecond;

    private int endRangeHour;

    private int endRangeMinute;

    private int endRangeSecond;

    private String startStr;

    private String endStr;

    private String startRecur;

    private String endRecur;

    private String duration;

    public static TimeConfig convertFromHttp(TimeConfigJson tc) throws
        JsonProcessingException {
        var str = tc.getConfigurationJson();
        if (str == null || str.isEmpty()) {
            return null;
        }

        ObjectNode node = (ObjectNode) JsonUtil.MAPPER.readTree(str);
        TimeConfig timeConfig = builder().build();

        timeConfig.uuid = node.has("id") ? node.get("id").asText() : UUID.randomUUID().toString();
        timeConfig.title = node.has("title") ? node.get("title").asText() : null;
        timeConfig.startRecur = node.has("startRecur") ? node.get("startRecur").asText() : null;
        timeConfig.endRecur = node.has("endRecur") ? node.get("endRecur").asText() : null;
        timeConfig.duration = node.has("duration") ? node.get("duration").asText() : null;

        if (node.has("daysOfWeek")) {
            node.get("daysOfWeek").forEach(day -> timeConfig.daysOfWeek.add(day.asInt()));
        }

        if (node.has("start") && node.has("end")) {
            OffsetDateTime start = OffsetDateTime.parse(node.get("start").asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            OffsetDateTime end = OffsetDateTime.parse(node.get("end").asText(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            timeConfig.startStr = node.get("start").asText();
            timeConfig.beginRangeHour = start.getHour();
            timeConfig.beginRangeMinute = start.getMinute();
            timeConfig.beginRangeSecond = start.getSecond();

            timeConfig.endStr = node.get("end").asText();
            timeConfig.endRangeHour = end.getHour();
            timeConfig.endRangeMinute = end.getMinute();
            timeConfig.endRangeSecond = end.getSecond();
        }
        return timeConfig;
    }

    public void setStrings() {
        if (startStr == null || startStr.isEmpty()) {
            LocalDateTime startLDT = LocalDateTime.of(
                LocalDate.now(), LocalTime.of(beginRangeHour, beginRangeMinute, beginRangeSecond));
            OffsetDateTime startODT = OffsetDateTime.of(startLDT, getCurrentZoneOffset());
            startStr = startODT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        if (endStr == null || endStr.isEmpty()) {
            LocalDateTime endLDT = LocalDateTime.of(
                LocalDate.now(), LocalTime.of(endRangeHour, endRangeMinute, endRangeSecond));
            OffsetDateTime endODT = OffsetDateTime.of(endLDT, getCurrentZoneOffset());
            endStr = endODT.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
    }

    private static ZoneOffset getCurrentZoneOffset() {
        return ZoneId.systemDefault().getRules().getOffset(LocalDateTime.now());
    }
}