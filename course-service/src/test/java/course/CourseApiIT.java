package course;
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

// Real PostgreSQL checks the migrations and constraints as well as the catalog endpoints
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.config.import="}) @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers
class CourseApiIT {

    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17.9-alpine");

    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl); r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CourseRepository repository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;


    @Test
    void databaseRejectsDuplicateCodesAndInvalidCredits() {
        repository.deleteAll();

        // Check the database independently of the validity of the request
        jdbc.update(
                "INSERT INTO courses VALUES (?, ?, ?, ?, ?, ?)",
                java.util.UUID.randomUUID(),
                "DB101",
                "Demo",
                null,
                3,
                true
        );

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            jdbc.update(
                    "INSERT INTO courses VALUES (?, ?, ?, ?, ?, ?)",
                    java.util.UUID.randomUUID(),
                    "DB101",
                    "Demo",
                    null,
                    3,
                    true)
        );

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
            jdbc.update(
                    "INSERT INTO courses VALUES (?, ?, ?, ?, ?, ?)",
                    java.util.UUID.randomUUID(),
                    "DB102",
                    "Demo",
                    null,
                    9,
                    true)
        );

        repository.deleteAll();
    }

    @Test
    void demoSeedIsRepeatable() {
        repository.deleteAll();
        assertEquals(0, repository.count());

        var seed = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("demo/seed.sql")
        );

        seed.execute(jdbc.getDataSource());
        seed.execute(jdbc.getDataSource());

        assertEquals(2, repository.count()); repository.deleteAll();
    }

    @Test
    void persistsUpdatesArchivesAndRejectsBadInput() throws Exception {
        // Start with an empty database
        repository.deleteAll();

        // Use a valid Course with lowercase letters and extra spaces
        String body = "{\"courseCode\":\" demo101 \",\"title\":\"Demo\",\"credits\":3}";

        // Creating the Course should return 201 and a Location header for the new record
        String json = mvc.perform(post("/api/v1/courses").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();

        // Save the generated ID so later requests use the same Course
        String id = mapper.readTree(json).get("id").asText();

        // See link back to its own API address.
        mvc.perform(get("/api/v1/courses/" + id)).andExpect(jsonPath("$._links.self.href").value("/api/v1/courses/" + id));


        // A missing Course returns 404, a stable error code, and a Spanish message when requested
        mvc.perform(get("/api/v1/courses/" + java.util.UUID.randomUUID()).header("Accept-Language", "es"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"))
            .andExpect(jsonPath("$.detail").value("No se encontro el curso."));

        // French is not supported, so the error message falls back to English
        mvc.perform(get("/api/v1/courses/" + java.util.UUID.randomUUID()).header("Accept-Language", "fr"))
            .andExpect(jsonPath("$.detail").value("Course was not found."));

        // Creating another Course with the same code should return 409 (conflict)
        mvc.perform(post("/api/v1/courses").contentType("application/json").content(body)).andExpect(status().isConflict());

        // Updating the existing Course should return 200 and its new title
        mvc.perform(put("/api/v1/courses/" + id).contentType("application/json").content(body.replace("Demo", "Updated")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated"));

        // Archive the Course twice to check that repeating the action still succeeds
        for (int i = 0; i < 2; i++) mvc.perform(patch("/api/v1/courses/" + id + "/status").contentType("application/json")
                .content("{\"active\":false}")).andExpect(status().isOk());

        // Archiving keeps the record and marks it inactive instead of deleting it
        mvc.perform(get("/api/v1/courses/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        // Searching archived Courses finds the updated record
        mvc.perform(get("/api/v1/courses?active=false&search=updated")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        // This shows ONLY active Courses, so it should be empty
        mvc.perform(get("/api/v1/courses")).andExpect(jsonPath("$.totalElements").value(0));

        // Nine credits exceeds the allowed 1-6
        mvc.perform(post("/api/v1/courses").contentType("application/json").content(body.replace("3", "9")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.credits").exists());

        // An ID in the wrong format returns 400 (bad request)
        mvc.perform(get("/api/v1/courses/not-a-uuid")).andExpect(status().isBadRequest());

        // A correctly formatted ID with no matching record returns 404 (not found)
        mvc.perform(get("/api/v1/courses/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());

        // A page cannot request more than 100 records
        mvc.perform(get("/api/v1/courses?size=101")).andExpect(status().isBadRequest());
    }
}
