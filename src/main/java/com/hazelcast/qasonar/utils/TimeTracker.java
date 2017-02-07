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

package com.hazelcast.qasonar.utils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static com.hazelcast.qasonar.utils.DebugUtils.print;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class TimeTracker {

    private static final float PERCENTAGE = 100f;
    private static final ConcurrentMap<TimeTrackerLabel, Long> TIME_MAP = new ConcurrentHashMap<>();

    private TimeTracker() {
    }

    public static void record(TimeTrackerLabel label, long elapsedNanos) {
        TIME_MAP.compute(label, (s, oldValue) -> oldValue == null ? elapsedNanos : oldValue + elapsedNanos);
    }

    public static void printTimeTracks() {
        long totalTime = 0;
        for (Map.Entry<TimeTrackerLabel, Long> entry : TIME_MAP.entrySet()) {
            totalTime += entry.getValue();
        }
        if (totalTime == 0) {
            return;
        }

        print("TimeTracker statistics");
        for (Map.Entry<TimeTrackerLabel, Long> entry : sortByValue(TIME_MAP).entrySet()) {
            long duration = entry.getValue();
            float percentage = PERCENTAGE * duration / totalTime;
            print("%s: %d ms (%.2f%%)", entry.getKey(), NANOSECONDS.toMillis(duration), percentage);
        }
        print("Total time: %d ms (%.2f%%)", NANOSECONDS.toMillis(totalTime), PERCENTAGE);
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
