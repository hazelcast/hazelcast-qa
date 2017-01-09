/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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

package com.hazelcast.qasonar.utils;

import org.fusesource.jansi.Ansi.Color;

import static com.hazelcast.qasonar.utils.Utils.appendCommandLine;
import static java.lang.String.format;
import static org.fusesource.jansi.Ansi.ansi;

public final class DebugUtils {

    private static volatile boolean debug;

    private DebugUtils() {
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void setDebug(boolean debug) {
        DebugUtils.debug = debug;
    }

    public static void debug(String msg, Object... parameters) {
        if (isDebug()) {
            print(msg, parameters);
        }
    }

    public static void debugGreen(String msg, Object... parameters) {
        debugColor(Color.GREEN, msg, parameters);
    }

    public static void debugYellow(String msg, Object... parameters) {
        debugColor(Color.YELLOW, msg, parameters);
    }

    public static void debugRed(String msg, Object... parameters) {
        debugColor(Color.RED, msg, parameters);
    }

    private static void debugColor(Color color, String msg, Object... parameters) {
        if (isDebug()) {
            printColor(color, msg, parameters);
        }
    }

    public static void print(String msg, Object... parameters) {
        printColor(null, msg, parameters);
    }

    public static void printGreen(String msg, Object... parameters) {
        printColor(Color.GREEN, msg, parameters);
    }

    public static void printYellow(String msg, Object... parameters) {
        printColor(Color.YELLOW, msg, parameters);
    }

    public static void printRed(String msg, Object... parameters) {
        printColor(Color.RED, msg, parameters);
    }

    private static void printColor(Color color, String msg, Object... parameters) {
        if (parameters.length > 0) {
            msg = format(msg, parameters);
        }
        if (color != null) {
            msg = ansi().fg(color).a(msg).reset().toString();
        }
        System.out.println(msg);
    }

    public static void debugCommandLine(PropertyReader propertyReader, CommandLineOptions commandLineOptions) {
        if (isDebug()) {
            StringBuilder sb = new StringBuilder();
            appendCommandLine(propertyReader, sb, commandLineOptions.getPullRequests(), true);
            debug(sb.toString());
        }
    }
}
