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

package com.hazelcast.common;

import com.hazelcast.utils.PropertyReader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import static com.hazelcast.utils.GitUtils.cleanupBranch;
import static com.hazelcast.utils.GitUtils.createBranch;
import static com.hazelcast.utils.GitUtils.getGit;
import static com.hazelcast.utils.GitUtils.resetCompileCounters;
import static com.hazelcast.utils.Repository.EE;
import static com.hazelcast.utils.Repository.OS;

public abstract class AbstractGitClass {

    private final PropertyReader propertyReader;

    protected Git gitOS;
    protected Git gitEE;

    protected Repository repoOS;
    protected Repository repoEE;

    protected RevWalk walkOS;
    protected RevWalk walkEE;

    protected RevCommit currentCommitOS;
    protected RevCommit currentCommitEE;
    protected RevCommit lastCommitOS;

    protected AbstractGitClass(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
    }

    protected void initRepositories(String branchName) throws Exception {
        gitOS = getGit(propertyReader, OS.getRepositoryName());
        gitEE = getGit(propertyReader, EE.getRepositoryName());

        cleanupBranch(null, gitOS);
        cleanupBranch(null, gitEE);

        repoOS = gitOS.getRepository();
        repoEE = gitEE.getRepository();

        walkOS = new RevWalk(repoOS);
        walkEE = new RevWalk(repoEE);

        Ref headOS = repoOS.exactRef(Constants.HEAD);
        Ref headEE = repoEE.exactRef(Constants.HEAD);

        currentCommitOS = walkOS.parseCommit(headOS.getObjectId());
        currentCommitEE = walkEE.parseCommit(headEE.getObjectId());

        lastCommitOS = currentCommitOS;

        createBranch(branchName, gitOS, currentCommitOS);
        createBranch(branchName, gitEE, currentCommitEE);

        resetCompileCounters();
    }
}
