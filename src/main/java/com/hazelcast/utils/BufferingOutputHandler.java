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

package com.hazelcast.utils;

import org.apache.maven.shared.invoker.InvocationOutputHandler;

import java.util.LinkedList;
import java.util.List;

import static com.hazelcast.utils.DebugUtils.print;
import static com.hazelcast.utils.DebugUtils.printRed;
import static java.lang.String.format;

@SuppressWarnings("WeakerAccess")
public class BufferingOutputHandler implements InvocationOutputHandler {

    private final List<String> lines = new LinkedList<>();

    @Override
    public void consumeLine(String line) {
        lines.add(line);
    }

    public String findErrors() {
        if (contains("COMPILATION ERROR")) {
            return "There were compilation errors!";
        } else if (contains("No tests were executed!")) {
            return "Test could not be found, please check if you have specified the correct module and profile!";
        } else if (contains("[ERROR] There are test failures.")) {
            return format("There were test failures!%n%s", getTestFailures());
        }
        return null;
    }

    public boolean contains(String pattern) {
        for (String line : lines) {
            if (line.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    public Object getTestFailures() {
        StringBuilder sb = new StringBuilder();
        boolean addLine = false;
        for (String line : lines) {
            if (line.contains("Failed tests:") || line.contains("Tests in error:")) {
                addLine = true;
            } else if (addLine) {
                if (line.isEmpty()) {
                    addLine = false;
                } else {
                    sb.append(line);
                }
            }
        }
        if (sb.length() == 0) {
            return "No error logs were found!";
        }
        return sb.toString();
    }

    public void printErrors() {
        for (String line : lines) {
            if (line.contains("ERROR")) {
                printRed(line);
            }
        }
    }

    public void printAll() {
        for (String line : lines) {
            print(line);
        }
    }

    public void clear() {
        lines.clear();
    }
}
