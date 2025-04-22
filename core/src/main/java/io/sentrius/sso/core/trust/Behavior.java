package io.sentrius.sso.core.trust;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Behavior {

    @JsonProperty("minimum_positive_runs")
    private int minimumPositiveRuns;

    @JsonProperty("max_incidents")
    private int maxIncidents;

    @JsonProperty("incident_types")
    private IncidentTypes incidentTypes;
}
