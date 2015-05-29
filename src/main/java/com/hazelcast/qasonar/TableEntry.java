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

public class TableEntry {

    String resourceId;

    String pullRequest;
    String fileName;

    String coverage;
    String lineCoverage;
    String branchCoverage;

    String comment;
    boolean qaCheck;

    double numericCoverage;
    double numericLineCoverage;
    double numericBranchCoverage;

    void pass() {
        this.qaCheck = true;
    }

    void pass(String comment) {
        this.qaCheck = true;
        this.comment = comment;
    }

    void fail(String comment) {
        this.qaCheck = false;
        this.comment = comment;
    }
}
