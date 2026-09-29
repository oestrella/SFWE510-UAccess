package course;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// These controller tests check that internal database errors do not become public response details.
class ApiErrorsTest {

    //Model View Controller
    MockMvc mvc(CourseService service) {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();

        messages.setBasename("messages");
        messages.setFallbackToSystemLocale(false);

        return MockMvcBuilders.standaloneSetup(
                new CourseController(service)).setControllerAdvice(new ApiAdvice(messages)).build();
    }

    @Test
    void unrelatedIntegrityFailureRemainsSanitized500() throws Exception {
        CourseService service = mock(CourseService.class);
        UUID id = UUID.randomUUID();

        when(service.read(id)).thenThrow(new DataIntegrityViolationException("SQL secret password stacktrace"));

        mvc(service).perform(get("/api/v1/courses/" + id)).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
            .andExpect(jsonPath("$.instance").value("/api/v1/courses/" + id));
    }

    @Test
    void onlyKnownUniqueConstraintBecomesConflict() throws Exception {
        CourseService service = mock(CourseService.class);
        UUID id = UUID.randomUUID();

        when(service.read(id)).thenThrow(new DataIntegrityViolationException("sql", new org.hibernate.exception.ConstraintViolationException(
            "sql", new java.sql.SQLException(), "uq_courses_code")));

        mvc(service).perform(get("/api/v1/courses/" + id)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_CODE_EXISTS"));
    }

    @Test
    void malformedBodyAndUnsupportedMethodUseProblemDetails() throws Exception {

        MockMvc api = mvc(mock(CourseService.class));

        api.perform(post("/api/v1/courses").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        api.perform(delete("/api/v1/courses/" + UUID.randomUUID()))
            .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
