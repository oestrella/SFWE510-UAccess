package enrollment;

import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// A real HTTP stub plus PostgreSQL lets me test failure handling and concurrent registrations together.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers
class EnrollmentHttpIT {

    // Use a temporary real database; the test application runs on a randomly selected HTTP port.
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17.9-alpine");

    static HttpServer upstream;
    static ExecutorService upstreamThreads;
    // Each test selects how the fake Course server responds; volatile shares changes with its threads.
    static volatile String mode = "active";
    static {
        try {
            // Start a fake Course service on a free local port and handle requests on separate threads.
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstreamThreads = Executors.newCachedThreadPool(); upstream.setExecutor(upstreamThreads);
            upstream.createContext("/api/v1/courses/", exchange -> {
                // Capture the mode for this request so later changes do not alter an in-progress reply.
                String current = mode;
                String id = exchange.getRequestURI().getPath().substring("/api/v1/courses/".length());
                // Simulate missing/server errors, inactive Courses, broken JSON, or a wrongly typed active value.
                int status = current.equals("missing") ? 404 : current.equals("server") ? 500 : 200;
                String body = current.equals("malformed") ? "not json" : current.equals("wrongType") ?
                    "{\"id\":\"" + id + "\",\"active\":\"true\"}" :
                    "{\"id\":\"" + id + "\",\"active\":" + !current.equals("inactive") + "}";
                try {
                    // This delay exceeds the client's 250 ms read timeout, so the caller should give up.
                    if (current.equals("timeout")) Thread.sleep(650);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
                } catch (Exception ignored) { /* timed-out clients close their socket */ }
                finally { exchange.close(); }
            });
            upstream.start();
        } catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    }
    // Release the fake server and its worker threads when all tests finish.
    @AfterAll static void stopUpstream() { upstream.stop(0); upstreamThreads.shutdownNow(); }
    // Point Spring at the temporary database and fake Course server instead of development services.
    @DynamicPropertySource static void settings(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl); r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
        r.add("uaccess.course-api.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
        r.add("uaccess.course-api.read-timeout", () -> "250ms");
    }
    // Send real HTTP requests to the test application.
    @Autowired TestRestTemplate api;
    @Autowired StudentService students;
    @Autowired StudentRepository studentRepository;
    @Autowired EnrollmentRepository enrollments;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    // A spy checks calls to the actual Course client while keeping its real HTTP behavior.
    @MockitoSpyBean HttpCourseClient courseClient;

    @BeforeEach void resetState() {
        // Clear enrollments before Students because enrollment rows refer to Student records.
        enrollments.deleteAll(); studentRepository.deleteAll(); mode = "active";
        // Assert the transaction boundary at the actual outgoing HTTP call.
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "HTTP eligibility call must be outside a DB transaction");
            // After checking that no database transaction is open, continue with the real Course request.
            return invocation.callRealMethod();
        }).when(courseClient).requireActive(any());
    }
    // Create distinct Student identities for each scenario.
    UUID student(String suffix) {
        return students.create(new StudentRequest("HTTP" + suffix, "Demo " + suffix, suffix + "@example.invalid")).id();
    }
    String path(UUID student) { return "/api/v1/students/" + student + "/enrollments"; }
    // Send the enrollment request and keep both its status and JSON body for assertions.
    ResponseEntity<JsonNode> register(UUID student, UUID course) {
        return api.postForEntity(path(student), Map.of("courseId", course), JsonNode.class);
    }
    @Test void realHttpFailuresNeverPersistAndLocalOperationsRemainAvailable() {
        UUID student = student("one"), course = UUID.randomUUID();
        // Save one valid enrollment before testing failures; it should remain the only saved row.
        ResponseEntity<JsonNode> success = register(student, course); assertEquals(201, success.getStatusCode().value());
        String id = success.getBody().get("id").asText();
        // Try each Course failure and check both the API error and the unchanged database count.
        for (String failure : new String[]{"missing", "inactive", "server", "malformed", "wrongType", "timeout"}) {
            mode = failure;
            int expected = failure.equals("missing") ? 404 : failure.equals("inactive") ? 409 : 503;
            ResponseEntity<JsonNode> rejected = register(student, UUID.randomUUID());
            assertEquals(expected, rejected.getStatusCode().value(), failure);
            assertEquals(1, enrollments.count(), failure);
            // Student reads and enrollment lists use local data and should still work during Course failures.
            assertEquals(200, api.getForEntity("/api/v1/students/" + student, JsonNode.class).getStatusCode().value());
            assertEquals(1, api.getForObject(path(student), JsonNode.class).get("totalElements").asInt());
        }
        // Dropping the existing enrollment also works without a successful Course check.
        assertEquals(204, api.exchange(path(student) + "/" + id, HttpMethod.DELETE, null, Void.class).getStatusCode().value());
        assertEquals(0, enrollments.count());
    }
    @Test void archivePreservesEnrollmentAndBlocksNewStudent() {
        // Enroll the first Student, then make Course report inactive before the second Student tries.
        UUID first = student("first"), second = student("second"), course = UUID.randomUUID();
        assertEquals(201, register(first, course).getStatusCode().value()); mode = "inactive";
        assertEquals(409, register(second, course).getStatusCode().value());
        assertEquals(1, enrollments.count());
        // Archiving blocks new registration but keeps the first Student's existing enrollment.
        assertEquals(1, api.getForObject(path(first), JsonNode.class).get("totalElements").asInt());
    }
    @Test void concurrentSubmissionsLeaveOneRowAndConflictLoser() throws Exception {
        // Two callers signal that they are ready, then wait for the same start signal.
        UUID student = student("race"), course = UUID.randomUUID(); CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        // Release both callers together to exercise the database uniqueness protection.
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Callable<Integer> request = () -> { ready.countDown(); assertTrue(go.await(5, TimeUnit.SECONDS)); return register(student, course).getStatusCode().value(); };
            Future<Integer> a = workers.submit(request), b = workers.submit(request);
            // Wait until both workers are ready before releasing them together.
            assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
            List<Integer> statuses = new ArrayList<>(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
            // Order does not matter: expect one 201, one 409, and exactly one saved enrollment.
            Collections.sort(statuses); assertEquals(List.of(201, 409), statuses); assertEquals(1, enrollments.count());
        }
        // A direct duplicate insert must also fail, proving the database protects the Student/Course pair.
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO enrollments VALUES (?, ?, ?, now())", UUID.randomUUID(), student, course));
        assertEquals(1, enrollments.count());
    }
    @Test void studentIdentityUpdateConflictIsDatabaseBacked() {
        // Create two Students, then try to give the second Student the first Student's number.
        UUID one = student("identity1"), two = student("identity2");
        ResponseEntity<JsonNode> response = api.exchange("/api/v1/students/" + two, HttpMethod.PUT,
            new HttpEntity<>(new StudentRequest("HTTPidentity1", "Duplicate", "identity2@example.invalid")), JsonNode.class);
        // The API should report STUDENT_EXISTS, and the two saved Student numbers must remain different.
        assertEquals(409, response.getStatusCode().value()); assertEquals("STUDENT_EXISTS", response.getBody().get("code").asText());
        assertNotEquals(students.read(one).studentNumber(), students.read(two).studentNumber());
    }
    @Test void demoScriptIsRepeatableAndMigrationsContainNoSeeds() {
        // Check that the test starts with no Students or enrollments before loading sample data.
        assertEquals(0, studentRepository.count()); assertEquals(0, enrollments.count());
        org.springframework.jdbc.datasource.init.ResourceDatabasePopulator seed = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
            new org.springframework.core.io.ClassPathResource("demo/seed.sql"));
        // Apply the optional seed script twice; it should leave two Students instead of duplicating them.
        seed.execute(jdbc.getDataSource()); seed.execute(jdbc.getDataSource());
        assertEquals(2, studentRepository.count());
    }
}
