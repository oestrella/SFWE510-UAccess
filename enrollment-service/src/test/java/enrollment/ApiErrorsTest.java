package enrollment;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// These controller tests check that internal database errors do not become public response details
class ApiErrorsTest {

    //Model View Controller
    MockMvc mvc(StudentService service) {

        // Load the same error messages used by the application; do not depend on the computer's language
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

        messages.setBasename("messages");
        messages.setFallbackToSystemLocale(false);

        // Test the Student controller and its error handler without starting the full application
        return MockMvcBuilders.standaloneSetup(new StudentController(service)).setControllerAdvice(new ApiAdvice(messages)).build();
    }

    @Test
    void unrelatedIntegrityFailureRemainsSanitized500() throws Exception {
        StudentService service = mock(StudentService.class);
        UUID id = UUID.randomUUID();

        // Simulate a database error containing private-looking details and no recognized constraint name
        when(service.read(id)).thenThrow(
                new DataIntegrityViolationException(
                    "SQL secret password stacktrace",
                    new org.hibernate.exception.ConstraintViolationException("sql", new java.sql.SQLException(),
                            (String) null))
        );

        // The public response should use a general 500 message and identify the failed request path
        mvc(service).perform(get("/api/v1/students/" + id)).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
            .andExpect(jsonPath("$.instance").value("/api/v1/students/" + id)
        );
    }

    @Test
    void onlyKnownUniqueConstraintBecomesConflict() throws Exception {
        StudentService service = mock(StudentService.class);
        UUID id = UUID.randomUUID();

        // This known email uniqueness rule is an expected conflict, so the handler should return 409
        when(service.read(id)).thenThrow(new DataIntegrityViolationException("sql", new org.hibernate.exception.ConstraintViolationException(
            "sql", new java.sql.SQLException(), "uq_students_email")));

        mvc(service).perform(get("/api/v1/students/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_EXISTS"));
    }

    @Test
    void malformedBodyAndUnsupportedMethodUseProblemDetails() throws Exception {

        // Neither request below should need a working Student service.
        MockMvc api = mvc(mock(StudentService.class));

        // Incomplete JSON must return 400 with the INVALID_INPUT error code.

        api.perform(post("/api/v1/students").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        // Student deletion is not supported, so DELETE returns 405 and a matching error code.
        api.perform(delete("/api/v1/students/" + UUID.randomUUID()))
            .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

    }
}
