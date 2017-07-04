/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static com.hazelcast.utils.DebugUtils.print;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class TimeTracker {

    private static final float PERCENTAGE = 100f;
    private static final ConcurrentMap<TimeTrackerLabel, Long> DURATIONS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<TimeTrackerLabel, Long> INVOCATIONS = new ConcurrentHashMap<>();

    private TimeTracker() {
    }

    public static void record(TimeTrackerLabel label, long elapsedNanos) {
        DURATIONS.compute(label, (s, oldDuration) -> oldDuration == null ? elapsedNanos : oldDuration + elapsedNanos);
        INVOCATIONS.compute(label, (s, oldInvocations) -> oldInvocations == null ? 1 : oldInvocations + 1);
    }

    public static void printTimeTracks() {
        long totalDuration = 0;
        long totalInvocations = 0;
        for (Map.Entry<TimeTrackerLabel, Long> entry : DURATIONS.entrySet()) {
            totalDuration += entry.getValue();
            totalInvocations += INVOCATIONS.get(entry.getKey());
        }
        if (totalDuration == 0 || totalInvocations == 0) {
            return;
        }

        print("\nTimeTracker statistics");
        for (Map.Entry<TimeTrackerLabel, Long> entry : sortByValue(DURATIONS).entrySet()) {
            TimeTrackerLabel label = entry.getKey();
            long duration = entry.getValue();
            long invocations = INVOCATIONS.get(label);
            float percentage = PERCENTAGE * duration / totalDuration;
            long average = (long) (duration / (double) invocations);
            print("%s: %d ms (%.2f%%) (%d invocations) (%d ms per invocation)", label, NANOSECONDS.toMillis(duration), percentage,
                    invocations, NANOSECONDS.toMillis(average));
        }
        print("Total time: %d ms (%.2f%%) (%d invocations)%n", NANOSECONDS.toMillis(totalDuration), PERCENTAGE, totalInvocations);
    }

    private static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Collections.reverseOrder()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
}
