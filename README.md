# mod-email

Copyright (C) 2018-2019 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

<!-- ../../okapi/doc/md2toc -l 2 -h 4 README.md -->
* [Introduction](#introduction)
* [Compiling](#compiling)
* [Docker](#docker)
* [Installing the module](#installing-the-module)
* [Deploying the module](#deploying-the-module)
* [Additional information](#additional-information)

## Introduction

The module provides the role of delivering messages using SMTP server to send email.
This module use `mod-configuration` to get connection parameters.

The module supports the following configuration for SMTP server:

 |  PARAMETERS              |  DESCRIPTION                           |  EXAMPLES                      |
 |--------------------------|----------------------------------------|--------------------------------|
 |  EMAIL_SMTP_HOST         |  the hostname of the smtp server       | 'localhost'                    |
 |  EMAIL_SMTP_PORT         |  the port of the smtp server           | 502                            |
 |  EMAIL_SMTP_LOGIN_OPTION |  the login mode for the connection     | DISABLED, OPTIONAL or REQUIRED |
 |  EMAIL_TRUST_ALL         |  trust all certificates on ssl connect | true or false                  |
 |  EMAIL_SMTP_SSL          |  sslOnConnect mode for the connection  | true or false                  |
 |  EMAIL_START_TLS_OPTIONS |  TLS security mode for the connection  | NONE, OPTIONAL or REQUIRED     |
 |  EMAIL_USERNAME          |  the username for the login            | 'login'                        |
 |  EMAIL_PASSWORD          |  the password for the login            | 'password'                      |
 |  EMAIL_FROM              |  'from' property of the email          | noreply@folio.org              |


 Required configuration options:
  * EMAIL_SMTP_HOST
  * EMAIL_SMTP_PORT
  * EMAIL_USERNAME
  * EMAIL_PASSWORD
  * EMAIL_FROM
  * EMAIL_SMTP_SSL

 The main name of the module : "SMPT_SERVER"

 Module configuration example:

 ```
curl -X POST \
  http://localhost:9130/configurations/entries \
  -H 'Content-Type: application/json' \
  -H 'X-Okapi-Tenant: <tenant>' \
  -H 'x-okapi-token: <token>' \
  -d
    '{
      "module": "SMPT_SERVER",
      "configName": "locale",
      "code": "EMAIL_SMTP_HOST",
      "description": "server smtp host",
      "default": true,
      "enabled": true,
      "value": "smtp.googlemail.com"
    }'
 ```

## API

Module provides next API:

 | METHOD |  URL                          | DESCRIPTION                                                       |
 |--------|-------------------------------|-------------------------------------------------------------------|
 | POST   | /email                        | Push email to mod-email for sending message to recipient          |


## Compiling

```
   mvn install
```

See that it says "BUILD SUCCESS" near the end.

## Docker
Build the docker container with:

  * docker build -t mod-email .

Test that it runs with:

  * docker run -t -i -p 8081:8081 mod-email

## Installing the module

Follow the guide of
[Deploying Modules](https://github.com/folio-org/okapi/blob/master/doc/guide.md#example-1-deploying-and-using-a-simple-module)
sections of the Okapi Guide and Reference, which describe the process in detail.

First of all you need a running Okapi instance.
(Note that [specifying](../README.md#setting-things-up) an explicit 'okapiurl' might be needed.)

```
   cd .../okapi
   java -jar okapi-core/target/okapi-core-fat.jar dev
```

We need to declare the module to Okapi:

```
curl -w '\n' -X POST -D -   \
   -H "Content-type: application/json"   \
   -d @target/ModuleDescriptor.json \
   http://localhost:9130/_/proxy/modules
```

That ModuleDescriptor tells Okapi what the module is called, what services it
provides, and how to deploy it.

## Deploying the module

Next we need to deploy the module. There is a deployment descriptor in
`target/DeploymentDescriptor.json`. It tells Okapi to start the module on 'localhost'.

Deploy it via Okapi discovery:

```
curl -w '\n' -D - -s \
  -X POST \
  -H "Content-type: application/json" \
  -d @target/DeploymentDescriptor.json  \
  http://localhost:9130/_/discovery/modules
```

Then we need to enable the module for the tenant:

```
curl -w '\n' -X POST -D -   \
    -H "Content-type: application/json"   \
    -d @target/TenantModuleDescriptor.json \
    http://localhost:9130/_/proxy/tenants/<tenant_name>/modules
```

## Additional information

### Issue tracker

See project [MODEMAIL](https://issues.folio.org/browse/MODEMAIL)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker/).
