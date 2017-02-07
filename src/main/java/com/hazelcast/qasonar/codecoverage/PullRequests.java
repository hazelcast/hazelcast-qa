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

package com.hazelcast.qasonar.codecoverage;

import com.hazelcast.qasonar.utils.CommandLineOptions;
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.WhiteList;
import org.kohsuke.github.GHRepository;

import java.io.IOException;

import static com.hazelcast.qasonar.utils.DebugUtils.debug;
import static com.hazelcast.qasonar.utils.DebugUtils.debugCommandLine;
import static com.hazelcast.qasonar.utils.DebugUtils.debugGreen;
import static com.hazelcast.qasonar.utils.GitHubUtils.getGitHubRepository;
import static com.hazelcast.qasonar.utils.TimeTracker.printTimeTracks;
import static com.hazelcast.qasonar.utils.WhiteListBuilder.fromJsonFile;

public class PullRequests {

    private final PropertyReader propertyReader;
    private final CommandLineOptions commandLineOptions;

    public PullRequests(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        this.propertyReader = propertyReader;
        this.commandLineOptions = commandLineOptions;
    }

    public void run() throws IOException {
        debugCommandLine(propertyReader, commandLineOptions);

        debug("Parsing whitelist...");
        WhiteList whiteList = fromJsonFile();

        debug("Connecting to GitHub...");
        GHRepository repo = getGitHubRepository(propertyReader);

        debug("Reading code coverage data for %d PRs...", commandLineOptions.getPullRequests().size());
        JsonDownloader jsonDownloader = new JsonDownloader(propertyReader);
        CodeCoverageReader reader = new CodeCoverageReader(propertyReader, repo, jsonDownloader);
        reader.run(commandLineOptions.getPullRequests());

        debug("Analyzing code coverage data of %d files...", reader.getFiles().size());
        CodeCoverageAnalyzer analyzer = new CodeCoverageAnalyzer(reader.getFiles(), propertyReader, repo, whiteList);
        analyzer.run();

        debug("Printing code coverage data...");
        CodeCoveragePrinter printer = new CodeCoveragePrinter(reader.getPullRequests(), analyzer.getFiles(),
                propertyReader, commandLineOptions);
        printer.run();

        debugGreen("Done!\n");
        printTimeTracks();
    }
}
