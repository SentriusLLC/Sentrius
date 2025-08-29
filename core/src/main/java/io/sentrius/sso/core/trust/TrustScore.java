package io.sentrius.sso.core.trust;

import java.util.Map;

public class TrustScore {
    private int minimum;
    private int marginalThreshold = 50; // optional override
    private Map<String, Double> weightings;

    public int getMinimum() {
        return minimum;
    }

    public int getMarginalThreshold() {
        return marginalThreshold;
    }

    public Map<String, Double> getWeightings() {
        return weightings;
    }
}