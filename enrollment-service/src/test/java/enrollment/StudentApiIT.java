package enrollment;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

// The API runs against PostgreSQL so persistence and identity conflicts use the real database behavior
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.config.import="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers

class StudentApiIT {

    // Start a temporary PostgreSQL database instead of using the development database
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17.9-alpine");

    // Configure Spring with the temporary database's generated connection values.
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      db::getJdbcUrl);
        r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }

    // Send controller requests with MockMvc and read JSON responses with ObjectMapper.
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void persistsUpdatesAndEnforcesIdentityConstraints() throws Exception {

        // Use extra spaces and uppercase email text to check how the API cleans up input
        String body = "{\"studentNumber\":\" s001 \",\"name\":\"Demo\",\"email\":\"DEMO@example.invalid \"}";

        // Creating a Student should return 201 and a Location header; save its ID for later requests
        String json = mvc.perform(
                post("/api/v1/students")
                        .contentType("application/json")
                        .content(body))
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String id = mapper.readTree(json).get("id").asText();

        // The response's self link should point back to this Student's API address
        mvc.perform(get("/api/v1/students/" + id))
                .andExpect(jsonPath("$._links.self.href").value("/api/v1/students/" + id));

        // A missing Student returns 404 with a stable error code and the requested Spanish message
        mvc.perform(get("/api/v1/students/" + java.util.UUID.randomUUID()).header("Accept-Language", "es"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("No se encontro el estudiante."));

        // Unsupported languages, such as French, use the English error message
        mvc.perform(get("/api/v1/students/" + java.util.UUID.randomUUID()).header("Accept-Language", "fr"))
            .andExpect(jsonPath("$.detail").value("Student was not found."));

        // Reading the saved Student should show the trimmed, lowercase email
        mvc.perform(get("/api/v1/students/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("demo@example.invalid"));

        // Updating the existing Student should change its name and return 200
        mvc.perform(put("/api/v1/students/" + id).contentType("application/json").content(body.replace("Demo", "Updated")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"));

        // Reusing the Student number and email must return 409 (conflict)
        mvc.perform(post("/api/v1/students").contentType("application/json").content(body)).andExpect(status().isConflict());

        // A different Student number still conflicts if the email is already taken
        mvc.perform(post("/api/v1/students").contentType("application/json").content(body.replace("s001", "s002")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDENT_EXISTS"));

        // An invalid email returns 400 and identifies the email field in the error response
        mvc.perform(post("/api/v1/students").contentType("application/json").content(body.replace("DEMO@example.invalid ", "invalid")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.email").exists());

        // Rejected creations must leave only the original Student in the list
        mvc.perform(get("/api/v1/students")).andExpect(jsonPath("$.totalElements").value(1));

        // A badly formatted ID is a bad request (400), but updating a missing valid ID returns 404
        mvc.perform(get("/api/v1/students/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/students/" + java.util.UUID.randomUUID()).contentType("application/json").content(body))
            .andExpect(status().isNotFound());
    }
}
