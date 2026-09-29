package enrollment;

import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Keep PostgreSQL real and replace only Course validation to isolate the enrollment API rules
@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "spring.config.import="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class EnrollmentApiIT {

    // Start a temporary PostgreSQL database so saving and deleting use real database rules
    @Container static PostgreSQLContainer db = new PostgreSQLContainer("postgres:17.9-alpine");

    // Give Spring the database address and login values chosen by Testcontainers
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", db::getJdbcUrl); r.add("spring.datasource.username", db::getUsername);
        r.add("spring.datasource.password", db::getPassword);
    }

    // MockMvc sends requests through the API controllers without starting a separate web server
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StudentService students;
    @Autowired EnrollmentRepository repository;


    // Replace Course checks with a mock
    // its void methods do nothing until a test makes them fail
    @MockitoBean CourseClient courses;

    @Test
    void registerScopeDropAndReEnroll() throws Exception {

        // Create two Students to check that an enrollment can only be accessed under its owner
        UUID student = students.create(new StudentRequest("E001", "Demo One", "one@example.invalid")).id();
        UUID other = students.create(new StudentRequest("E002", "Demo Two", "two@example.invalid")).id();

        // Use a made-up Course ID because the mocked client does not need a real Course record
        UUID course = UUID.randomUUID(); String path = "/api/v1/students/" + student + "/enrollments";
        String body = "{\"courseId\":\"" + course + "\"}";

        // Register the first Student; expect 201 (created) and a Location header for the new record
        String json = mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();

        // Save the enrollment ID so later requests can read and drop that same record
        String id = mapper.readTree(json).get("id").asText();

        // A second registration for the same Student and Course must return 409 (conflict)
        mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));

        // The other Student cannot read or delete this enrollment
        // failed requests must keep it intact
        String wrong = "/api/v1/students/" + other + "/enrollments/" + id;
        mvc.perform(get(wrong)).andExpect(status().isNotFound());
        mvc.perform(delete(wrong)).andExpect(status().isNotFound());
        assertTrue(repository.existsById(UUID.fromString(id)));

        // Make Course validation fail, then check that existing enrollment operations still work
        doThrow(new ApiException(503, "COURSE_UNAVAILABLE")).when(courses).requireActive(course);
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(path + "/" + id)).andExpect(status().isOk());
        mvc.perform(delete(path + "/" + id)).andExpect(status().isNoContent());

        // Dropping an already removed enrollment returns 404 instead of deleting another record
        mvc.perform(delete(path + "/" + id)).andExpect(status().isNotFound());
        assertEquals(0, repository.count());

        // Restore successful Course checks
        // re-enrolling should create a different enrollment ID
        reset(courses);
        String next = mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(id, mapper.readTree(next).get("id").asText());
    }

    @Test
    void rejectionNeverCreatesEnrollment() throws Exception {

        UUID student = students.create(new StudentRequest("E003", "Demo Three", "three@example.invalid")).id();
        UUID course = UUID.randomUUID(); String path = "/api/v1/students/" + student + "/enrollments";

        // Keep the starting count so each rejected request can be checked for unwanted database writes.
        long before = repository.count();

        // Simulate missing (404), inactive (409), and unavailable (503) Course checks.
        for (int code : new int[]{404, 409, 503}) {
            doThrow(new ApiException(code, "COURSE_UNAVAILABLE")).when(courses).requireActive(course);
            mvc.perform(post(path).contentType("application/json").content("{\"courseId\":\"" + course + "\"}"))
                    .andExpect(status().is(code));
            assertEquals(before, repository.count());
        }

        // A missing courseId is invalid input (400); listing enrollments for a missing Student returns 404.
        mvc.perform(post(path).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/students/" + UUID.randomUUID() + "/enrollments")).andExpect(status().isNotFound());
    }
}
