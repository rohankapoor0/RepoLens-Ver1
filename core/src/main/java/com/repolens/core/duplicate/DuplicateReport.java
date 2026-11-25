package com.repolens.core.duplicate;

import java.util.List;

/**
 * Summary report of all detected duplicate repository clusters.
 */
public record DuplicateReport(
        int duplicateGroupsCount,
        int duplicateReposCount,
        List<DuplicateGroup> groups
) {}
