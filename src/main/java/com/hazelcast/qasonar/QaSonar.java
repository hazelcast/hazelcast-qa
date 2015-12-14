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

import com.hazelcast.qasonar.codecoverage.CodeCoverageAnalyzer;
import com.hazelcast.qasonar.codecoverage.CodeCoveragePrinter;
import com.hazelcast.qasonar.codecoverage.CodeCoverageReader;
import com.hazelcast.qasonar.csvmerge.CsvMerge;
import com.hazelcast.qasonar.ideaconverter.IdeaConverter;
import com.hazelcast.qasonar.listprojects.ListProjects;
import com.hazelcast.qasonar.outputMerge.OutputMerge;
import com.hazelcast.qasonar.utils.CommandLineOptions;
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.PropertyReaderBuilder;
import com.hazelcast.qasonar.utils.WhiteList;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;

import static com.hazelcast.qasonar.utils.Utils.debug;
import static com.hazelcast.qasonar.utils.Utils.debugCommandLine;
import static com.hazelcast.qasonar.utils.Utils.debugGreen;
import static com.hazelcast.qasonar.utils.Utils.setDebug;
import static com.hazelcast.qasonar.utils.WhiteListBuilder.fromJsonFile;

public final class QaSonar {

    private QaSonar() {
    }

    public static void main(String[] args) throws IOException {
        PropertyReader propertyReader = PropertyReaderBuilder.fromPropertyFile();

        CommandLineOptions cliOptions = new CommandLineOptions(args, propertyReader);
        setDebug(cliOptions.isVerbose());

        switch (cliOptions.getAction()) {
            case PRINT_HELP:
                cliOptions.printHelp();
                break;

            case IDEA_CONVERTER:
                IdeaConverter converter = new IdeaConverter();
                converter.run();
                break;

            case CSV_MERGE:
                CsvMerge csvMerge = new CsvMerge();
                csvMerge.run();
                break;

            case OUTPUT_MERGE:
                OutputMerge outputMerge = new OutputMerge(propertyReader);
                outputMerge.run();
                break;

            case LIST_PROJECTS:
                ListProjects listProjects = new ListProjects(propertyReader);
                listProjects.run();
                break;

            case PULL_REQUESTS:
                debugCommandLine(propertyReader, cliOptions);

                debug("Parsing whitelist...");
                WhiteList whiteList = fromJsonFile(propertyReader);

                debug("Connecting to GitHub...");
                GitHub github = GitHub.connect();
                GHRepository repo = github.getRepository(propertyReader.getGitHubRepository());

                debug("Reading code coverage data...");
                CodeCoverageReader reader = new CodeCoverageReader(propertyReader, repo);
                reader.run(cliOptions.getPullRequests());

                debug("Analyzing code coverage data...");
                CodeCoverageAnalyzer analyzer = new CodeCoverageAnalyzer(reader.getFiles(), propertyReader, repo, whiteList);
                analyzer.run();

                debug("Printing code coverage data...");
                CodeCoveragePrinter printer = new CodeCoveragePrinter(analyzer.getFiles(), propertyReader, cliOptions);
                printer.run();

                debugGreen("Done!\n");
                break;

            default:
                throw new IllegalStateException("Unwanted command line action: " + cliOptions.getAction());
        }
    }
}
