# Service settings

Config Server supplies settings to Course and Enrollment. The settings live in the project's `config-repo` folder, outside the application JAR files (packaged Java applications).

See the [README](../README.md) for startup and the [Phase 1 demo](phase1-demo.md) for the banner-change walkthrough.

## Profiles and files

A profile is a group of settings. Course and Enrollment use `dev` for development or `prod` for production settings. Each service selects its own profile.

Config Server uses `native`, which means it reads settings from local files. It listens on port `8071`. In Docker, the `config-repo` folder is mounted read-only: Config Server can read the files, and you edit them in the project folder on your computer.

| File in config-repo | What it contains |
| --- | --- |
| application.yml | Shared settings for Course and Enrollment. |
| course-service.yml | Course settings used with either profile. |
| enrollment-service.yml | Enrollment settings used with either profile, including how to call Course. |
| course-service-dev.yml and enrollment-service-dev.yml | Development banners, sample-record settings, and refresh access. |
| course-service-prod.yml and enrollment-service-prod.yml | Production banners, no sample records, and health/info access only. |

To see the settings Config Server supplies, open these addresses in a browser or send GET requests in Postman:

- `http://127.0.0.1:8071/course-service/dev`
- `http://127.0.0.1:8071/enrollment-service/prod`

Environment-variable placeholders may still appear in these responses, such as the reference to `COURSE_DB_PASSWORD`. Each receiving service fills them in from its own environment variables.

## Startup and database passwords

Course and Enrollment need Config Server when they start. Their JAR files include the connection settings needed to find it, and requests to it have time limits. If Config Server is unavailable, normal startup fails. Tests explicitly disable that connection so they can test the application separately.

Database passwords are not stored in the shared settings file. Each service's file refers to its own database environment variables. Docker Compose reads their values from `.env`; Java processes started manually need those values set in their terminals.

Only Enrollment receives the settings for calling the Course API. Each service uses only its own database credentials.

## Change a development banner

A banner is a short message returned by `/api/v1/info`. That response also shows the service name and active profiles.

1. Edit the banner in `config-repo/course-service-dev.yml` and save the file.
2. In Postman, send POST to `http://127.0.0.1:8081/actuator/refresh`. Use raw JSON `{}` and the header `Content-Type: application/json`.
3. Send GET to `http://127.0.0.1:8081/api/v1/info`. The response should show the new banner without rebuilding or restarting Course.
4. Restore the original banner and send the refresh request again.

For Enrollment, use `config-repo/enrollment-service-dev.yml` and port `8082`.

Refresh affects only the service instance receiving the request. Changing both banners requires a refresh request to each service. The banner's settings object can update while the application is running; that is what makes this demonstration work.

Only development banners are promised to refresh live. Database settings and the settings for calling Course require restarting the affected service.

## Production settings

Production uses the same application images as development, but selects `prod` and keeps its database records in separate Docker volumes.

- No sample records are inserted, even if `DEMO_ENABLED` is set to `true`.
- Only the health and info management endpoints are exposed.
- `/actuator/refresh` is unavailable. Restart the service to apply configuration changes.

## Developer note

The services load Config Server settings through Spring's Config Data mechanism in their application settings. There is no separate legacy `bootstrap.yml` file.

## Unfinished encryption exercise

Supplying `ENCRYPT_KEY` to Config Server remains a TODO. The optional encryption/Vault exercise has not been tested, and the current Docker Compose files do not set up Vault.
