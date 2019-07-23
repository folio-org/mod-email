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
