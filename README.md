# UAccess Phase 1 by Osvaldo Estrella

UAccess is a course and student enrollment project for SFWE 510. It lets you create courses and students, enroll students, and manage their enrollments through an API. An API accepts requests from tools such as Postman and returns data as JSON.

The project has three services:

- **Course** manages courses and stores them in its own PostgreSQL database.
- **Enrollment** manages students and enrollments in a separate PostgreSQL database.
- **Config Server** provides settings for the other two services.

PostgreSQL stores the records. JPA lets the Java services save and read those records. Docker runs the services and databases in containers, while Docker volumes keep the database records between restarts.

## What you need

For the main walkthrough, install **Docker Desktop** and **Postman**. Start Docker Desktop and make sure it uses Linux containers and Docker Compose v2.

The Docker commands build and run Java inside containers. To build or run Java directly on your computer, you also need **JDK 21**. The optional Python checks need **Python 3.10 or newer**. The optional Newman command needs **Node.js and npm**.

Run all commands from the project folder: the folder containing `README.md`, `pom.xml`, and `compose.yml`. Open a terminal there before starting.

## 1. Set up the database passwords

If `.env` already exists, keep it and use its current values. If it is missing, copy `.env.example` to `.env`:

PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```sh
cp .env.example .env
```

Open `.env` and replace the two placeholder passwords with different private passwords. Keep the database usernames unless you need to change them. Docker Compose reads this file when it starts the databases and services.

`DEMO_ENABLED=true` adds sample records in development. The file is private and is not git-tracked.

## 2. Start the application

```sh
docker compose build
docker compose up --wait
docker compose ps
```

- `build` creates the application images used to make containers. The first build may take a few minutes while files download.
- `up --wait` starts the containers in the background and waits until they are ready.
- `ps` shows the containers. You should see five healthy containers: three services and two databases.

The service addresses are:

| Service | Address | Purpose |
| --- | --- | --- |
| Course | http://127.0.0.1:8081 | Course requests |
| Enrollment | http://127.0.0.1:8082 | Student and enrollment requests |
| Config Server | http://127.0.0.1:8071 | Service settings |

These addresses are available only on your computer. To check startup in your browser, open http://127.0.0.1:8081/actuator/health/readiness and http://127.0.0.1:8082/actuator/health/readiness. Both should show `UP`.

The databases use ports **5433** for Course and **5434** for Enrollment. Use a PostgreSQL client to connect to them.

## 3. Postman

1. Import `postman/UAccess.postman_collection.json` and `postman/UAccess.local.postman_environment.json` into Postman.
2. Select the **UAccess local** environment. It stores the service addresses and record IDs used by the requests.
3. Run the collection in its numbered order. Its tests check the responses and save each record's unique ID, also called a UUID.
4. Confirm that all tests pass. The collection creates courses and students, enrolls a student, checks errors, archives a course, and drops and recreates an enrollment.

Follow the [Phase 1 demo](docs/phase1-demo.md) for the complete walkthrough. It also shows how to change a banner (a short message from a service), keep records after replacing containers, and check what happens when a service stops.

See the [API guide](docs/api.md) for all request addresses, JSON fields, and error responses.

## 4. View logs, stop, and restart

To view messages from the services:

```sh
docker compose logs course-service enrollment-service config-server
```

To stop and remove the development containers:

```sh
docker compose down
```

This keeps the database volumes, so your saved records remain. Start development again with:

```sh
docker compose up --wait
```

If you change application code or a Dockerfile, run `docker compose build` before starting again. Keep the database volumes to retain your data.

## 5. Try the production settings

A profile is a group of settings. This project has `dev` for development and `prod` for a local demonstration of production settings. Development and production use the same ports, so stop development first:

```sh
docker compose down
docker compose -f compose.yml -f compose.prod.yml up --wait
```

The two `-f` arguments load the base configuration and the production settings. Both profiles use the same built application images, but their PostgreSQL volumes are separate. Development and production records stay separate.

Production does not create sample records and disables `/actuator/refresh`. Fresh production databases start empty; existing production records remain if you have run this profile before. The `/api/v1/info` responses show production settings. Only health and info management endpoints are available.

To stop production and return to development:

```sh
docker compose -f compose.yml -f compose.prod.yml down
docker compose up --wait
```

This is a local teaching project without authentication. It is not ready for public deployment.

## Extra: Locally Build Directly with JDK21 

Use this section if you want to work with the Java code directly. Install JDK 21 and keep Docker Desktop running: the integration tests use real PostgreSQL databases in temporary containers.

Replace the example JDK path below with the folder where JDK 21 is installed. `JAVA_HOME` tells the build where to find Java, and `Path` lets the terminal find the `java` command.

PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify
```

macOS/Linux:

```sh
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw verify
```

The included Maven Wrapper downloads Maven 3.9.16, so you do not need to install Maven separately. `verify` builds all three services, runs the unit and integration tests, and creates executable JAR files (packaged Java applications) in each service's `target` folder. Look for **BUILD SUCCESS** at the end. Use `test` instead of `verify` when you only want unit and controller tests.

For IDE's, like IntelliJ IDEA, open the project's `pom.xml` as a Maven project and select JDK 21 as the project SDK (the Java version used by the IDE).

### Python checks

With the development containers running, run:

```sh
python scripts/smoke.py
python scripts/verify_ops.py --refresh --persistence --outage --database-health
```

The smoke check creates test records and checks the main API actions. It logs and saves their IDs in `tmp/smoke-evidence.json`. Run it first because the operations check uses those saved IDs.

The four operations arguments select banner changes, record survival after container replacement, a Course outage, and database health checks. At least one check must be selected. These checks briefly stop or replace containers, then restore them while keeping the database volumes. Look for `PASS` messages and no errors. The scripts use Python's standard library; no extra Python packages are needed.

### Newman checks

Newman runs the Postman collection from a terminal. With Node.js and npm installed, run:

```sh
npx newman@6.2.1 run postman/UAccess.postman_collection.json -e postman/UAccess.local.postman_environment.json
```

`-e` loads the local environment used by the collection. If npx asks to install Newman, enter `y`. Check that the final report shows no failures.

### JUnit 5 learning check

The Maven tests use the JUnit 6 version required by Spring Boot 4. After a Maven build, you can also run the 20 unit and controller tests separately with JUnit 5:

```sh
python scripts/junit5_units.py
```

Keep `JAVA_HOME` set to JDK 21. This script uses a separate test process so the two JUnit versions do not mix.

### Check the JAR files outside Docker

After `verify` builds the JAR files, run this command with development selected and production stopped. Keep Docker Desktop running and `JAVA_HOME` set to JDK 21:

```sh
python scripts/verify_host.py
```

The script reads `.env`, temporarily runs the three Java services on your computer, and uses the PostgreSQL databases in Docker. It checks the API, then restores the development containers. Logs are saved in `tmp/`.


## Tools used in the project

| Tool | What it does |
| --- | --- |
| Java 21 | Runs the application code |
| Maven 3.9.16 | Builds the services and runs tests |
| Spring Boot 4.0.8 | Provides the web application framework |
| Spring Cloud 2025.1.3 | Connects the services to Config Server |
| PostgreSQL 17.9 | Stores records in the databases |
| JPA | Lets Java code save and read database records |
| Flyway | Creates and updates the database tables |

## Common Problems and Quick Checks

| Problem | What to check |
| --- | --- |
| Docker commands cannot connect | Start Docker Desktop and select Linux containers. |
| Downloads fail | Check your internet connection and access to Maven Central and Docker registries. |
| A port is already in use | Stop another application or development/production stack using that port. |
| Database login fails after editing `.env` | An existing database keeps its original password. Use the credentials that created it; editing `.env` does not update that stored password. Keep the volume to preserve records. |
| Course or Enrollment cannot load settings | Check that Config Server is running and can read `config-repo`. These services need it at startup. |
| Enrollment returns 503 while Course is stopped | This is expected: a new enrollment needs Course to check the course. The failed request does not save an enrollment. |
| A settings change does not appear | Only development banners support live refresh. Database and Course connection settings require a service restart. |

## More documentation

- [Phase 1 demo](docs/phase1-demo.md): follow the complete demonstration.
- [API guide](docs/api.md): request addresses, JSON fields, and errors.
- [Architecture](docs/architecture.md): how the services fit together.
- [Configuration](docs/configuration.md): service settings and banner refresh.
- [Verification results](docs/verification.md): checks already run and their results.
