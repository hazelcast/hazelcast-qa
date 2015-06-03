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

package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.utils.GitHubStatus;

class TableEntry {

    String resourceId;

    String pullRequest;
    String fileName;
    GitHubStatus status;

    String coverage;
    String lineCoverage;
    String branchCoverage;

    String comment;
    boolean qaCheck;
    boolean qaCheckSet;

    double numericCoverage;
    double numericLineCoverage;
    double numericBranchCoverage;

    int gitHubChanges;
    int gitHubAdditions;
    int gitHubDeletions;

    boolean isQaCheckSet() {
        return qaCheckSet;
    }

    void pass() {
        if (qaCheckSet) {
            throw new IllegalStateException("QA Check already set to " + qaCheck + " with comment " + comment);
        }
        this.qaCheck = true;
        this.qaCheckSet = true;
    }

    void pass(String comment) {
        if (qaCheckSet) {
            throw new IllegalStateException("QA Check already set to " + qaCheck + " with comment " + comment);
        }
        this.comment = comment;
        this.qaCheck = true;
        this.qaCheckSet = true;
    }

    void fail(String comment) {
        if (qaCheckSet) {
            throw new IllegalStateException("QA Check already set to " + qaCheck + " with comment " + comment);
        }
        this.comment = comment;
        this.qaCheck = false;
        this.qaCheckSet = true;
    }
}
