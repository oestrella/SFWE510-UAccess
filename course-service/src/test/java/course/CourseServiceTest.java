package course;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

// This is a mock repository that lets us test catalog rules without starting a database
class CourseServiceTest {

    @Test
    void normalizesAndDefaultsActive() {
        CourseRepository repository = mock(CourseRepository.class);

        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        CourseResponse course = new CourseService(repository).create( new CourseRequest(
            " sfwe510 ",
            " Cloud Native ",
            null,
            3,
            null)
        );

        assertEquals("SFWE510", course.courseCode()); assertEquals("Cloud Native", course.title());

        assertTrue(course.active()); assertNotNull(course.id());
    }

    @Test
    void missingAndDuplicateHaveStableCodes() {
        CourseRepository repository = mock(CourseRepository.class);

        assertEquals(404, assertThrows(ApiException.class, () -> new CourseService(repository).read(UUID.randomUUID())).status);
        when(repository.existsByCourseCode("SFWE510")).thenReturn(true);

        assertEquals("COURSE_CODE_EXISTS", assertThrows(ApiException.class, () -> new CourseService(repository)
            .create(new CourseRequest("sfwe510", "Title", null, 3, true))).code);

        verify(repository, never()).saveAndFlush(any());
    }
}
