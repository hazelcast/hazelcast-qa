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

package com.hazelcast.qasonar;

import com.hazelcast.qasonar.codecoverage.PullRequests;
import com.hazelcast.qasonar.csvmerge.CsvMerge;
import com.hazelcast.qasonar.ideaconverter.IdeaConverter;
import com.hazelcast.qasonar.listprojects.ListProjects;
import com.hazelcast.qasonar.listpullrequests.ListPullRequests;
import com.hazelcast.qasonar.outputMerge.OutputMerge;
import com.hazelcast.qasonar.utils.CommandLineOptions;
import com.hazelcast.qasonar.utils.PropertyReader;
import com.hazelcast.qasonar.utils.PropertyReaderBuilder;

import java.io.IOException;

import static com.hazelcast.qasonar.utils.DebugUtils.setDebug;

public final class QaSonar {

    private QaSonar() {
    }

    public static void main(String[] args) throws IOException {
        PropertyReader propertyReader = PropertyReaderBuilder.fromPropertyFile();

        CommandLineOptions commandLineOptions = new CommandLineOptions(args, propertyReader);
        setDebug(commandLineOptions.isVerbose());

        switch (commandLineOptions.getAction()) {
            case PRINT_HELP:
                commandLineOptions.printHelp();
                break;

            case IDEA_CONVERTER:
                IdeaConverter converter = new IdeaConverter(propertyReader);
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

            case LIST_PULL_REQUESTS:
                ListPullRequests listPullRequests = new ListPullRequests(propertyReader, commandLineOptions);
                listPullRequests.run();
                break;

            case PULL_REQUESTS:
                PullRequests pullRequests = new PullRequests(propertyReader, commandLineOptions);
                pullRequests.run();
                break;

            default:
                throw new IllegalStateException("Unwanted command line action: " + commandLineOptions.getAction());
        }
    }
}
