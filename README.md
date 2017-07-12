Hazelcast QA Tools
==================

A collection of tools for the QA process.

# Installation

Clone the repository and compile the project via Maven.

```bash
mvn clean install
```

Use the created `hazelcast-qa.jar` file to start the tools.
There are example bash scripts in the root directory of the project.
You might have to adjust the `PROJECT_HOME` in those script files to your project directory.

# Configuration

To get the tools working you have to create a single config file.

## ~/.hazelcast-qa

In this configuration file you have to specify your SonarQube and GitHub credentials as well as additional configuration parameters.

<b>Note:</b> For HZ Match and HZ Blame the `localGitRoot` setting is sufficient. All other settings are just needed for the QA Sonar tool.

```bash
# Git configuration
localGitRoot = /home/username/IdeaProjects/

# GitHub configuration
gitHubLogin = username
gitHubToken = token
gitHubRepository = organization/repository

# SonarQube domain and credentials
host = http://www.hostname.example
username = username
password = password

# SonarQube Resource Id of the project
projectResourceIds = 12345

# Default value for minimum code coverage
minCodeCoverage = 85.0

# Default value for minimum code coverage for modified files
minCodeCoverageModified = 60.0
```

# QA Sonar

A tool to generate a code coverage table from a list of pull requests.

## List projects

Prints a list of all projects in the configured SonarQube instance.
Useful to retrieve the resourceIDs for PR analysis configuration.

Usage:
```bash
qa-sonar --listProjects
```

## Analysis of PRs

Analyses the code coverage of list of PRs.

Usage:
```bash
qa-sonar --pullRequests 23,42 --minCodeCoverage 85 --minCodeCoverageModified 60 --outputFile code-coverage.txt
```

## Merge of results

If your feature has PRs from several repositories, you may want to merge the results for a single Confluence page.

Usage:
```bash
qa-sonar --verbose --pullRequests 23 --outputFile feature-os.txt
qa-sonar --verbose --pullRequests 42 --outputFile feature-ee.txt --gitHubRepository hazelcast/hazelcast-enterprise
qa-sonar --outputMerge --outputFile feature
```
This creates a merged file named `feature.txt` with the combined analysis of both `feature-*.txt` files.

The output file suffix has to match an element of the `suffixList` of the class `Repository`, e.g. `os` or `ee`.

## List PRs by milestone

Retrieves a list of PRs for a given GitHub milestone.

Usage:
```bash
#!/bin/bash

MILESTONE=3.9

printf "#!/bin/bash\n\n" > failures.sh

qa-sonar --listPullRequests ${MILESTONE} --scriptFile failures.sh --optionalParameters "--verbose --printFailsOnly --minThresholdModified 10" --outputFile ${MILESTONE}-failures-os.txt
qa-sonar --listPullRequests ${MILESTONE} --scriptFile failures.sh --optionalParameters "--verbose --printFailsOnly --minThresholdModified 10" --outputFile ${MILESTONE}-failures-ee.txt --gitHubRepository hazelcast/hazelcast-enterprise
qa-sonar --listPullRequests ${MILESTONE} --scriptFile failures.sh --optionalParameters "--verbose --printFailsOnly --minThresholdModified 10" --outputFile ${MILESTONE}-failures-mc.txt --gitHubRepository hazelcast/management-center

printf "qa-sonar --outputMerge --outputFile ${MILESTONE}-failures\n" >> failures.sh
```

# HZ Match

A tool to create a map of matching Hazelcast OS and EE commits, to build older versions of Hazelcast Enterprise.

Usage:
```bash
hz-match
```

You can override the number of iterated EE commits by defining a custom limit:
```bash
hz-match --limit 5
```

# HZ Blame

A tool to find a guilty commit via a failing reproducer.

You have to give the tool the same parameters as a manual Maven execution for a single test needs. It also support Hazelcast Enterprise tests.

Usage:
```bash
# with Maven
mvn test -Ptest-coverage-nightly -pl hazelcast-client -Dtest=com.hazelcast.client.map.impl.nearcache.ClientMapNearCachePreloaderTest
mvn test -Pnightly-build -pl hazelcast-enterprise -Dtest=com.hazelcast.map.HDMapMemoryLeakStressTest

# with HZ Blame
hz-blame --mavenProfile test-coverage-nightly --testModule hazelcast-client --testClass com.hazelcast.client.map.impl.nearcache.ClientMapNearCachePreloaderTest
hz-blame --ee --mavenProfile nightly-build --testModule hazelcast-enterprise --testClass com.hazelcast.map.HDMapMemoryLeakStressTest
```

HZ Blame will execute the tests by iterating through the Hazelcast versions, until it finds a commit which passes without errors.

You can limit the number of iterated commits via `--limit`.

You can define a start commit via `--startCommit` to start with a previous version instead of `HEAD`.

You can execute a dry run via `--dry` to check the commit traversal, but without compilation and test execution.
