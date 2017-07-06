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

package com.hazelcast.hzblame.utils;

import com.hazelcast.utils.PropertyReader;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.IOException;

public class CommandLineOptions {

    private static final int HELP_WIDTH = 160;
    private static final int HELP_INDENTATION = 2;

    private final OptionParser parser = new OptionParser();

    private final OptionSpec verboseSpec = parser.accepts("verbose",
            "Prints debug output.");

    private final OptionSpec eeSpec = parser.accepts("ee",
            "Specifies if OS or EE will be used.");

    private final OptionSpec<String> localGitRootSpec = parser.accepts("localGitRoot",
            "Sets the local Git root directory.")
            .withOptionalArg().ofType(String.class);

    private final OptionSpec<String> mavenProfileSpec = parser.accepts("mavenProfile",
            "Specifies the maven profile to run with.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> testModuleSpec = parser.accepts("testModule",
            "Specifies the test module to build.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> testClassSpec = parser.accepts("testClass",
            "Specifies the test class to execute.")
            .withRequiredArg().ofType(String.class);

    private final OptionSpec<String> testMethodSpec = parser.accepts("testMethod",
            "Specifies the test method to execute.")
            .withRequiredArg().ofType(String.class);

    private final PropertyReader propertyReader;
    private final OptionSet options;
    private final CommandLineAction action;

    public CommandLineOptions(String[] args, PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
        this.options = initOptions(args);
        this.action = initCommandLineAction();
    }

    public CommandLineAction getAction() {
        return action;
    }

    public void printHelp() throws IOException {
        parser.formatHelpWith(new BuiltinHelpFormatter(HELP_WIDTH, HELP_INDENTATION));
        parser.printHelpOn(System.out);
    }

    public boolean isVerbose() {
        return options.has(verboseSpec);
    }

    public boolean isEE() {
        return options.has(eeSpec);
    }

    public boolean hasMavenProfile() {
        return options.has(mavenProfileSpec);
    }

    public String getMavenProfile() {
        return options.valueOf(mavenProfileSpec);
    }

    public boolean hasTestModule() {
        return options.has(testModuleSpec);
    }

    public String getTestModule() {
        return options.valueOf(testModuleSpec);
    }

    public String getTestClass() {
        return options.valueOf(testClassSpec);
    }

    public boolean hasTestMethod() {
        return options.has(testMethodSpec);
    }

    public String getTestMethod() {
        return options.valueOf(testMethodSpec);
    }

    private OptionSet initOptions(String[] args) {
        parser.accepts("help", "Show help.").forHelp();
        return parser.parse(args);
    }

    private CommandLineAction initCommandLineAction() {
        initLocalGitRoot();

        return getCommandLineAction();
    }

    private void initLocalGitRoot() {
        if (options.has(localGitRootSpec)) {
            propertyReader.setLocalGitRoot(options.valueOf(localGitRootSpec));
        }
    }

    private CommandLineAction getCommandLineAction() {
        if (options.has("help")) {
            return CommandLineAction.PRINT_HELP;
        }
        if (!options.has(testClassSpec)) {
            System.err.println("You need to provide --testClass");
            System.exit(1);
        }
        return CommandLineAction.BLAME;
    }
}