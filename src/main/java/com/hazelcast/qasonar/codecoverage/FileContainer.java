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

class FileContainer {

    public enum CoverageType {
        SONAR,
        IDEA
    }

    String resourceId;

    String pullRequests;
    String fileName;
    GitHubStatus status;

    String coverage;
    String lineCoverage;
    String branchCoverage;

    double numericCoverage;
    double numericLineCoverage;
    double numericBranchCoverage;

    double ideaCoverage;

    int gitHubChanges;
    int gitHubAdditions;
    int gitHubDeletions;

    boolean isForCoverageCalculation;
    CoverageType coverageType;

    String comment;
    boolean qaCheck;
    boolean qaCheckSet;

    public boolean isForCoverageCalculation() {
        return isForCoverageCalculation;
    }

    public void useForCoverageCalculation(CoverageType coverageType) {
        this.isForCoverageCalculation = true;
        this.coverageType = coverageType;
    }

    double getCoverageForCalculation() {
        switch (coverageType) {
            case SONAR:
                return numericCoverage;
            case IDEA:
                return ideaCoverage;
            default:
                throw new IllegalStateException("CoverageType has not been set!");
        }
    }

    boolean isQaCheckSet() {
        return qaCheckSet;
    }

    void pass() {
        setValues(null, true);
    }

    void pass(String comment) {
        setValues(comment, true);
    }

    void fail(String comment) {
        setValues(comment, false);
    }

    private void setValues(String newComment, boolean qaCheck) {
        if (qaCheckSet) {
            throw new IllegalStateException("QA Check already set to " + qaCheck + " with comment " + comment);
        }
        if (newComment != null) {
            this.comment = (comment == null) ? newComment : newComment + "\n" + comment;
        }
        this.qaCheck = qaCheck;
        this.qaCheckSet = true;
    }
}
