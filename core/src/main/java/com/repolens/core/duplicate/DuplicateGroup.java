package com.repolens.core.duplicate;

import java.util.List;

/**
 * A cluster of duplicate or near-duplicate repositories identified by the Disjoint Set Union algorithm.
 */
public record DuplicateGroup(
        int groupId,
        List<String> repos,
        List<String> reasons,
        String primaryReason
) {}
