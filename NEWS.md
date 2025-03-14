## 2025-03-14 v1.19.0
* Update mod-email to Java 21 [FOLIO-4225](https://folio-org.atlassian.net/browse/FOLIO-4225)

## 2025-01-10 v1.18.1
* Remove PII from mod-email [MODEMAIL-104](https://folio-org.atlassian.net/browse/MODEMAIL-104)


## 2024-10-30 v1.18.0
* Upgrade RMB version to V.35.3.0 [MODEMAIL-99](https://folio-org.atlassian.net/browse/MODEMAIL-99)
* Configurable expiration time [MODEMAIL-81](https://folio-org.atlassian.net/browse/MODEMAIL-81) 


## 2024-03-19 v1.17.0
* Upgrade Folio spring dependency version ([MODEMAIL-96](https://issues.folio.org/browse/MODEMAIL-96))


## 2023-10-11 v1.16.0
* Use GitHub Workflows api-lint and api-schema-lint and api-doc (MODEMAIL-92)
* Update to Java 17 (MODEMAIL-93)


## 2023-02-23 v1.15.3
* Move sensitive SMTP information out of mod-configuration (MODEMAIL-70)
* Upgrade to RMB 35.0.0 and Vertx 4.3.3 (MODEMAIL-76)
* Logging improvement - check log4j configuration (MODEMAIL-90)
* 
## 2022-10-18 v1.15.0
* Update copyright year (FOLIO-1021)
* Upgrade to RMB 35.0.0 and Vertx 4.3.3 (MODEMAIL-78)

## 2022-06-27 v1.14.0
* Upgrade to RMB v34.0.0 (MODEMAIL-74)
* Retry logic implementation for failed emails (MODEMAIL-73)

## 2022-02-22 v1.13.0
* Upgrade to RMB v33.2.4 (MODEMAIL-69)
* Use new api-lint and api-doc CI facilities (FOLIO-3231)
* Upgrade to RMB v33.0.4 and Log4j v2.16.0 (MODEMAIL-66)

## 2021-09-30 v1.12.0
 * Setting EMAIL_START_TLS_OPTIONS to DISABLED (MODEMAIL-61)
 * Add auth methods for smtp config (MODEMAIL-41)

## 2021-06-11 v1.11.0
 * Add support for configurable custom headers (MODEMAIL-57)
 * Upgrade to RMB 33.0.0 and Vertx 4.1.0 (MODEMAIL-58)

## 2021-03-09 v1.10.0
 * Update RMB to v32.1.0 and Vertx to v4.0.0 (MODEMAIL-48)
 * Fix logging in Java 11
 * Support both 'http.port' and 'port' property variables (MODEMAIL-43)
 * Update RMB to v31.1.5 and Vertx to v3.9.4 (MODEMAIL-44)

## 2020-10-08 v1.9.0
 * Update RMB to v31, upgrade to JDK11 (MODEMAIL-39)

## 2020-06-11 v1.8.0
 * Update RMB version to 30.0.1 and Vertx to 3.9.1 (MODEMAIL-36)

## 2020-03-12 v1.7.0
 * Fix expired message deletion response to return status 204 with no content (MODEMAIL-33).

## 2019-12-03 v1.6.0
 * Fix incorrect response status when SMTP configuration is missing in `mod-configuration` (MODEMAIL-24)
 * Protect endpoint with required permissions (MODEMAIL-25)
 * Fix security vulnerabilities reported in jackson-databind (MODEMAIL-28)
 * Use JVM features to manage container memory (MODEMAIL-30)

## 2019-09-10 v1.5.0
 * Fix security vulnerabilities reported in jackson-databind (MODEMAIL-22)
 * Implement an endpoint for getting all emails for metrics (MODEMAIL-21)

## 2019-07-23 v1.4.0
 * Fix security vulnerabilities reported in jackson-databind
 * Typo on variable name: "SMPT" instead of "SMTP" (MODEMAIL-19)

## 2019-06-11 v1.3.0
 * Fix security vulnerabilities reported in jackson-databind
 * Add links to README additional info (FOLIO-473)
 * Initial module metadata (FOLIO-2003)

## 2019-05-10 v1.2.0
 * Module descriptor - remove _tenant interface (MODEMAIL-13)
 * Increase test coverage for mod-email (MODEMAIL-12)
 * Add 'EMAIL_FROM' config description to README
 * The 'Vertx.MailClient' configuration for sending email should not cache the first configuration for the SMTP server (MODEMAIL-14)
 
## 2019-03-14 v1.1.0
 * Fix security vulnerabilities reported in jackson-databind (MODEMAIL-5)
 * Setting 'from' property for sending email
 * Update copyright year (FOLIO-1021)

## 2018-10-15 v1.0.0
 * Add the endpoint /email with POST method
 * Implement EmailAPI and MailServiceImpl services

 API:

 | METHOD |  URL                          | DESCRIPTION                                                       |
 |--------|-------------------------------|-------------------------------------------------------------------|
 | POST   | /email                        | Push email to mod-email for sending message to recipient          |
