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

package com.hazelcast.hzmatch;

import com.hazelcast.hzmatch.match.Match;
import com.hazelcast.hzmatch.utils.CommandLineOptions;
import com.hazelcast.utils.PropertyReader;
import com.hazelcast.utils.PropertyReaderBuilder;

import static com.hazelcast.utils.DebugUtils.setDebug;

public final class HazelcastMatch {

    private HazelcastMatch() {
    }

    public static void main(String[] args) throws Exception {
        PropertyReader propertyReader = PropertyReaderBuilder.fromPropertyFile();

        CommandLineOptions commandLineOptions = new CommandLineOptions(args, propertyReader);
        setDebug(commandLineOptions.isVerbose());

        switch (commandLineOptions.getAction()) {
            case PRINT_HELP:
                commandLineOptions.printHelp();
                break;

            case MATCH:
                Match match = new Match(propertyReader, commandLineOptions);
                match.run();
                break;

            default:
                throw new IllegalStateException("Unwanted command line action: " + commandLineOptions.getAction());
        }
    }
}
