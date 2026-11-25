package com.repolens.web.throttle;

import com.repolens.web.exception.VisitorThrottleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window rate limiter enforcing per-visitor throttling by IP address.
 * Limits the number of distinct GitHub usernames an IP can analyze within an hour.
 */
@Service
public class VisitorThrottleService {

    private final int maxDistinctUsernamesPerHour;
    private final Map<String, List<UsernameAccess>> visitorAccessMap = new ConcurrentHashMap<>();

    private record UsernameAccess(String username, Instant timestamp) {}

    public VisitorThrottleService(@Value("${repolens.throttle.max-distinct-usernames-per-hour:10}") int maxUsernames) {
        this.maxDistinctUsernamesPerHour = Math.max(1, maxUsernames);
    }

    /**
     * Checks if the visitor IP is permitted to analyze the target username.
     *
     * @param clientIp client IP address
     * @param username target GitHub username
     * @throws VisitorThrottleException if quota exceeded
     */
    public synchronized void checkAndRecordAccess(String clientIp, String username) {
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = "127.0.0.1";
        }
        String cleanUser = username != null ? username.trim().toLowerCase() : "";

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(1));

        List<UsernameAccess> accesses = visitorAccessMap.computeIfAbsent(clientIp, k -> new ArrayList<>());
        // Prune entries older than 1 hour
        accesses.removeIf(a -> a.timestamp().isBefore(cutoff));

        // Count distinct usernames analyzed in the last hour
        long distinctCount = accesses.stream()
                .map(UsernameAccess::username)
                .distinct()
                .count();

        boolean alreadyAnalyzed = accesses.stream().anyMatch(a -> a.username().equalsIgnoreCase(cleanUser));

        if (!alreadyAnalyzed && distinctCount >= maxDistinctUsernamesPerHour) {
            long waitMinutes = 60;
            if (!accesses.isEmpty()) {
                Instant oldest = accesses.get(0).timestamp();
                waitMinutes = Math.max(1, 60 - Duration.between(oldest, now).toMinutes());
            }
            throw new VisitorThrottleException(
                    maxDistinctUsernamesPerHour,
                    waitMinutes,
                    "Visitor rate limit reached: You have analyzed " + distinctCount + " distinct usernames in the last hour. " +
                            "Please wait approximately " + waitMinutes + " minute(s) before analyzing additional accounts."
            );
        }

        // Record this access
        accesses.add(new UsernameAccess(cleanUser, now));
    }

    public void clear() {
        visitorAccessMap.clear();
    }
}
