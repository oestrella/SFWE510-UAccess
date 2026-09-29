package enrollment;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

// This class has a HTTP server that makes timeout and bad-response cases repeatable without running the Course service
class HttpCourseClientTest {

    HttpServer server;
    UUID courseId = UUID.randomUUID();
    HttpCourseClient client;

    @BeforeEach
    void start() throws Exception {

        // Port 0 lets the computer pick a free local port for this test's fake Course server
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        // Use short connection and response time limits so failure tests finish quickly
        client = new HttpCourseClient(new CourseApiProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                Duration.ofMillis(100), Duration.ofMillis(150)));
    }

    // Close the server after each test so its port and connections are released
    @AfterEach
    void stop() {
        server.stop(0);
    }


    // Choose the status, JSON body, and delay returned for Course requests
    void respond(int status, String body, long delay) {

        server.createContext(
                "/api/v1/courses/",
                exchange -> {
                    try {
                        Thread.sleep(delay);
                        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes);
                    }
                    catch (Exception ignored) {

                    } // empty catch to not timeout and close the socket
                    finally {
                        exchange.close();
                    }
                }
        );

    }

    // A valid ID and active=true response should pass even with extra fields such as title
    @Test
    void activeCoursePasses() {
        respond(200, "{\"id\":\"" + courseId + "\",\"active\":true,\"title\":\"Demo\"}", 0);
        assertDoesNotThrow(() -> client.requireActive(courseId));
    }

    // Keep the missing-Course status as 404
    @Test
    void missingIs404() {
        respond(404, "{}", 0);
        assertEquals(404, assertThrows(ApiException.class, () -> client.requireActive(courseId)).status);
    }

    // A successful HTTP response can still describe an inactive Course; that becomes 409
    @Test
    void inactiveIs409() {
        respond(200, "{\"id\":\"" + courseId + "\",\"active\":false}", 0);
        assertEquals(409, assertThrows(ApiException.class, () -> client.requireActive(courseId)).status);
    }

    // Convert a Course server failure into 503 (Course unavailable)
    @Test
    void serverFailureIs503() {
        respond(500, "{}", 0);
        assertEquals(503, assertThrows(ApiException.class, () -> client.requireActive(courseId)).status);
    }

    // Valid JSON that leaves out the Course ID is still unusable and must return 503
    @Test
    void malformedIs503() {
        respond(200, "{\"active\":true}", 0);
        assertEquals(503, assertThrows(ApiException.class, () -> client.requireActive(courseId)).status);
    }

    // Text that cannot be read as JSON must also return 503
    @Test
    void invalidJsonIs503() {
        respond(200, "not json", 0);
        assertEquals(503, assertThrows(ApiException.class, () -> client.requireActive(courseId)).status);
    }

    // Delay the reply beyond the client's read timeout and expect 503
    @Test
    void timeoutIs503() {
        respond(200, "{}", 450);
        assertEquals(503, assertThrows(ApiException.class, () -> client.requireActive(courseId)).status);
    }

    // Stop the server before the request to simulate an unreachable Course service
    @Test
    void connectionFailureIs503() {
        server.stop(0);
        assertEquals(503, assertThrows(ApiException.class, () -> client.requireActive(courseId)).status);
    }
}
