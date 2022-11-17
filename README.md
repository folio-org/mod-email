# mod-email

Copyright (C) 2018-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

<!-- ../../okapi/doc/md2toc -l 2 -h 4 README.md -->

## Gaol

`mod-email` provides an API for delivering email messages.

## Configuration
For email delivery `mod-email` relies on the external SMTP server,
configuration for which is stored by `mod-email` in its own database.
The module also provides CRUD API for managing it.
If `mod-email` couldn't find SMTP configuration in its own DB, it'll try to
fetch one from`mod-configuration`, copy it to the DB and then delete it from
`mod-configuration`.

### Supported configuration parameters

| mod-email parameter | mod-configuration parameter | DESCRIPTION                           | EXAMPLES                        |
|---------------------|---------------------------------------|-----------------------------|---------------------------------|
| host                | EMAIL_SMTP_HOST             | the hostname of the smtp server       | 'localhost'                     |
| port                | EMAIL_SMTP_PORT             | the port of the smtp server           | 502                             |
| loginOption         | EMAIL_SMTP_LOGIN_OPTION     | the login mode for the connection     | DISABLED, OPTIONAL or REQUIRED  |
| trustAll            | EMAIL_TRUST_ALL             | trust all certificates on ssl connect | true or false                   |
| ssl                 | EMAIL_SMTP_SSL              | sslOnConnect mode for the connection  | true or false                   |
| startTlsOptions     | EMAIL_START_TLS_OPTIONS     | TLS security mode for the connection  | DISABLED, OPTIONAL or REQUIRED  |
| username            | EMAIL_USERNAME              | the username for the login            | 'login'                         |
| password            | EMAIL_PASSWORD              | the password for the login            | 'password'                      |
| from                | EMAIL_FROM                  | 'from' property of the email          | noreply@folio.org               |
| authMethods         | AUTH_METHODS                | authentication methods                | 'CRAM-MD5 LOGIN PLAIN'          |

### Configuration using `mod-email`'s API

This method of configuration is preferred.

Required configuration parameters:
* Host
* Port
* User name
* Password

SMTP configuration (`GET /smtp-configuration/{id}`) example:
```
{
    "id": "d2db040b-3247-4b2b-8db4-5ef449e092ad",
    "host": "example-host.com",
    "port": 587,
    "username": "example-username",
    "password": "example-password",
    "ssl": false,
    "trustAll": false,
    "loginOption": "NONE",
    "startTlsOptions": "OPTIONAL",
    "authMethods": "",
    "from": "noreply@folio.org",
    "emailHeaders": [
        {
            "name": "Reply-To",
            "value": "noreply@folio.org"
        }
    ]
}
```

Use `GET /smtp-configuration` to get all configurations. It's forbidden to
create more than one configuration, but this call helps to find the ID of
the configuration entry to use it in `GET`, `PUT`, `DELETE` requests.

### Configuration using `mod-configuration` (deprecated)

This method of configuration is deprecated and needs to be avoided.

All SMTP configuration entries need their `module` field to be set to
`SMTP_SERVER`.

Module configuration example:

 ```
curl -X POST \
  http://localhost:9130/configurations/entries \
  -H 'Content-Type: application/json' \
  -H 'X-Okapi-Tenant: <tenant>' \
  -H 'x-okapi-token: <token>' \
  -d
    '{
      "module": "SMTP_SERVER",
      "configName": "smtp",
      "code": "EMAIL_SMTP_HOST",
      "description": "server smtp host",
      "default": true,
      "enabled": true,
      "value": "smtp.googlemail.com"
    }'
 ```

The example of request body with authentication methods configuration:

 ```
 {
     "module": "SMTP_SERVER",
     "configName": "smtp",
     "code": "AUTH_METHODS",
     "description": "Authentication methods",
     "default": true,
     "enabled": true,
     "value": "CRAM-MD5 LOGIN PLAIN"
 }
 ```

## API

Module provides next API:

| METHOD | URL                      | DESCRIPTION                                              |
|--------|--------------------------|----------------------------------------------------------|
| POST   | /email                   | Push email to mod-email for sending message to recipient |
| GET    | /smtp-configuration      | Get all SMTP configurations                              |
| GET    | /smtp-configuration/{id} | Get SMTP configuration                                   |
| POST   | /smtp-configuration/{id} | Post SMTP configuration                                  |
| PUT    | /smtp-configuration/{id} | Put SMTP configuration                                   |
| DELETE | /smtp-configuration/{id} | Delete SMTP configuration                                |


## Compiling

```
   mvn install
```

See that it says "BUILD SUCCESS" near the end.

## Port

When running the jar file the module looks for the `http.port` and `port`
system property variables in this order, and uses the default 8081 as fallback. Example:

`java -Dhttp.port=8008 -jar target/mod-email-fat.jar`

The Docker container exposes port 8081.

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

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-email).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-email).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-email/).

