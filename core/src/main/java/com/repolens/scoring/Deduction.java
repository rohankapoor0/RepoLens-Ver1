package com.repolens.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Itemized deduction recording points lost, evidence, why it matters, and actionable fix advice.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Deduction(
        ScoreCategory category,
        String ruleId,
        String evidence,
        String whyItMatters,
        int pointsLost,
        int pointsAvailable,
        String suggestedFix,
        List<String> fixSteps,
        String snippet,
        int effortMinutes,
        int pointsGainIfFixed
) {
    public Deduction {
        if (pointsLost <= 0) {
            throw new IllegalArgumentException("Points lost must be positive: " + pointsLost);
        }
        if (fixSteps == null) {
            fixSteps = Collections.emptyList();
        }
        if (pointsGainIfFixed <= 0) {
            pointsGainIfFixed = pointsLost;
        }
        if (effortMinutes <= 0) {
            effortMinutes = 15;
        }
    }

    /**
     * Backward-compatible accessor for reason (maps to evidence or suggestedFix).
     */
    @JsonProperty("reason")
    public String reason() {
        return evidence != null && !evidence.isBlank() ? evidence : suggestedFix;
    }

    /**
     * Backward-compatible constructor for existing tests and legacy callers.
     */
    public Deduction(ScoreCategory category, int pointsLost, String reason, String suggestedFix) {
        this(
                category,
                "LEGACY_RULE",
                reason,
                "Ensuring high repository hygiene builds contributor and user trust.",
                pointsLost,
                pointsLost,
                suggestedFix,
                List.of(suggestedFix),
                null,
                15,
                pointsLost
        );
    }
}
