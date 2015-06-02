Hazelcast QA Tools
==================

A collection of tools for the QA process.

# Configuration

To get this tools working you have to create two config files:

## ~/.github

In this configuration file you have to put in your GitHub credentials.

```
login = username
password = password
```

## ~./hazelcast-qa

In this configuration file you have to put in your SonarQube credentials as well as additional configuration parameters.

```
# SonarQube domain and credentials
host = hostname
username = username
password = password

# SonarQube Resource Id of the project
projectResourceId = 12345

# GitHub configuration
gitHubRepository = organization/repository

# Default value for minimum code coverage
minCodeCoverage = 87.5

# Default value for minimum code coverage for modified files
minCodeCoverageModified = 60.0

```

# Installation

```
mvn clean install
```

Use the bash scripts with the created JAR file.

# QA Sonar

A tool to generate a code coverage table from a list of pull requests.

## List projects

Prints a list of all projects in the configured SonarQube instance.
Useful to retrieve the resourceIDs for PR analysis configuration.

Usage: `qa-sonar --listProjects`

## Analysis of Pull Requests

Analyses the code coverage of list of PRs.

Usage: `qa-sonar --pullRequests 1,2,3 --minCodeCoverage 87.5 --minCodeCoverageModified 75 --outputFile code-coverage.txt`
