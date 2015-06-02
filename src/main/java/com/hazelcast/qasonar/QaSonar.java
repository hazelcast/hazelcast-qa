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

import com.hazelcast.qa.PropertyReader;
import com.hazelcast.qa.PropertyReaderBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;

public final class QaSonar {

    private QaSonar() {
    }

    public static void main(String[] args) throws IOException {
        PropertyReader propertyReader = PropertyReaderBuilder.fromPropertyFile();

        CommandLineOptions commandLineOptions = new CommandLineOptions(args, propertyReader);
        switch (commandLineOptions.getAction()) {
            case PRINT_HELP:
                commandLineOptions.printHelp();
                break;

            case LIST_PROJECTS:
                ListProjects listProjects = new ListProjects(propertyReader);
                listProjects.run();
                break;

            case PULL_REQUESTS:
                GitHub github = GitHub.connect();
                GHRepository repo = github.getRepository(propertyReader.getGitHubRepository());

                CodeCoverageReader reader = new CodeCoverageReader(propertyReader, repo);
                for (Integer pullRequest : commandLineOptions.getPullRequests()) {
                    reader.addPullRequest(pullRequest);
                }

                CodeCoverageAnalyzer analyzer = new CodeCoverageAnalyzer(reader.getTableEntries(), propertyReader, repo);
                analyzer.run();

                CodeCoveragePrinter printer = new CodeCoveragePrinter(analyzer.getTableEntries(), propertyReader);
                if (commandLineOptions.isPlainOutput()) {
                    printer.plain();
                } else {
                    printer.markUp(commandLineOptions.getPullRequests());
                }
                break;

            default:
                throw new IllegalStateException("Unwanted command line action: " + commandLineOptions.getAction());
        }
    }
}
