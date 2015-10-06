/*
 * Copyright (c) 2008-2015, Hazelcast, Inc. All Rights Reserved.
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

import java.util.ArrayList;
import java.util.List;

public class WhiteList {

    enum WhiteListEntryType {
        EQUALS,
        STARTS_WITH,
        ENDS_WITH,
        CONTAINS,
        REGEX
    }

    private final List<WhiteListEntry> whiteListEntries = new ArrayList<WhiteListEntry>();

    void addEntry(String type, String value, String justification, String comment) {
        whiteListEntries.add(new WhiteListEntry(type, value, justification, comment));
    }

    public WhiteListResult getWhitelistResultOrNull(String fileName) {
        for (WhiteListEntry entry : whiteListEntries) {
            if (entry.matches(fileName)) {
                return new WhiteListResult(entry.justification, entry.comment);
            }
        }
        return null;
    }

    private final class WhiteListEntry {

        private final WhiteListEntryType type;
        private final String value;
        private final String justification;
        private final String comment;

        private WhiteListEntry(String type, String value, String justification, String comment) {
            this.type = WhiteListEntryType.valueOf(type);
            this.value = value;
            this.justification = justification;
            this.comment = comment;
        }

        private boolean matches(String fileName) {
            switch (type) {
                case EQUALS:
                    return fileName.equals(value);
                case STARTS_WITH:
                    return fileName.startsWith(value);
                case ENDS_WITH:
                    return fileName.endsWith(value);
                case CONTAINS:
                    return fileName.contains(value);
                case REGEX:
                    return fileName.matches(value);
                default:
                    throw new UnsupportedOperationException("Unsupported whitelist type: " + type);
            }
        }
    }
}
