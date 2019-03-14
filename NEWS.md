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
