## 2021-12-21 v1.11.1
 * Upgrade to RMB to v33.0.4 and Log4j 2.16.0 (MODEMAIL-66)

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
