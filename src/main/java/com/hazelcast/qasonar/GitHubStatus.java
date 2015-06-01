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

package com.hazelcast.qasonar;

public enum GitHubStatus {

    ADDED("added"),
    MODIFIED("modified"),
    RENAMED("renamed"),
    REMOVED("removed");

    private String status;

    GitHubStatus(String status) {
        this.status = status;
    }

    public static GitHubStatus fromString(String status) {
        if (status != null) {
            for (GitHubStatus gitHubStatus : GitHubStatus.values()) {
                if (status.equalsIgnoreCase(gitHubStatus.status)) {
                    return gitHubStatus;
                }
            }
        }
        throw new IllegalStateException("Unknown status: " + status);
    }

    @Override
    public String toString() {
        return status;
    }
}
