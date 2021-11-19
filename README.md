# debezium-oracle-query-tool

This project is a command-line client for Oracle LogMiner.
It is meant to work in conjunction with an environment already configured for the Debezium for Oracle connector.
This tool allows you to execute specific commands that can obtain valuable information for debugging purposes when submitting bug reports.

## Installation

You can obtain early access binaries of dbzoqt (x86) for Linux, macOS, and Windows from [here](https://gonowhere.com).
This is a rolling release, new binaries re published upon each commit pushed to this repository.

Note: on macOS, you need to remove the quarantine flag after downloading, as the distribution currently is not signed:
```shell
xattr -r -d com.apple.quarantine path /to/dbzoqt-1.0.0-SNAPSHOT-osx-x86_64/
```

We are planning to include the dbzoqt binaries with the Debezium Tooling image soon!

## Quickstart

Before you can start using dbzoqt you need to have configured your Oracle database as outlined by the Debezium for Oracle connector [documentation](https://debezium.io/documentation/reference/stable/connectors/oracle.adoc).
This tool relies on the user account for the connector to be able to authenticate and perform specific read-only actions against the Oracle database.

## Usage

Display the help to learn about using _dbzoqt_:
```shell script
Usage: dbzoqt [-hV] [COMMAND]
A command-line query tool for Oracle by Debezium
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  list-changes  Lists all change events
  help          Displays help information about the specified command
```

## Development

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

### Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw compile quarkus:dev
```

To send the command line arguments, pass the `-Dquarkus.args` option:

```shell script
./mvnw compile quarkus:dev -Dquarkus.args='list-changes'
```

In dev mode, remote debuggers can connect to the running application on port 5005.
In order to wait for a debugging to connect, pass the `-Dsuspend` option.

### Packaging and running the application

The application cab be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app` directory.
Be aware that it's notan _uber-jar_ as the dependencies are copied in the `target/quarkus-app/lib/` directory.

The application is now runnable using `java-jar target/quarkus-app/quarkus-run.jar`.
You should define an alias _dbzoqt_:

```shell script
alias dbzoqt="java -jar target/quarkus-app/quarkus-run.jar"
```

### Creating a native executable

You can also create a native executable using:

```shell script
./mvnw package -Pnative
```

You can then execute your native executable with `./target/debezium-oracle-query-tool-1.0.0-SNAPSHOT-runner`.

As above, either define an alias _dbzoqt_ or rename the resulting executable accordingly.

### Related Quarkus Guides

- Picocli ([guide](https://quarkus.io/guides/picocli)): Develop command line applications with Picocli
- Quarkus native apps ([guide](https://quarkus.io/guides/maven-tooling.html)): Develop native applications with Quarkus and GraalVM

## License

This code base is available under the Apache License, version 2.