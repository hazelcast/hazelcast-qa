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

public class WhiteListResult {

    private final String justification;
    private final String comment;

    WhiteListResult(String justification, String comment) {
        this.justification = justification;
        this.comment = comment;
    }

    public String getJustification() {
        return justification;
    }

    public String getComment() {
        return comment;
    }

    public boolean isJustification() {
        return (justification != null);
    }
}
