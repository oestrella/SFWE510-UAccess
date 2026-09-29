package enrollment;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

// Mocking persistence keeps the normalization and duplicate-rule tests small
class StudentServiceTest {
    @Test
    void normalizesIdentityAndName() {

        StudentRepository repository = mock(StudentRepository.class);

        // Return the Student passed to save so the test can inspect the service's cleaned-up values.
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        // Supply extra spaces and mixed letter case to exercise input cleanup.
        StudentResponse student = new StudentService(repository).create(new StudentRequest(" s001 ", " Demo Student ", " DEMO@example.invalid "));

        // Student numbers become uppercase, names lose surrounding spaces, and emails become lowercase.
        assertEquals("S001", student.studentNumber()); assertEquals("Demo Student", student.name());
        assertEquals("demo@example.invalid", student.email()); assertNotNull(student.id());
    }

    @Test
    void missingAndDuplicateHaveStableCodes() {

        StudentRepository repository = mock(StudentRepository.class);

        // With no matching Student returned by the mock, reading must fail with 404.
        assertEquals(404, assertThrows(ApiException.class, () -> new StudentService(repository).read(UUID.randomUUID())).status);

        // Pretend the number or email is already in use and expect the STUDENT_EXISTS error.
        when(repository.existsByStudentNumberOrEmail("S001", "demo@example.invalid")).thenReturn(true);
        assertEquals("STUDENT_EXISTS", assertThrows(ApiException.class, () -> new StudentService(repository)
            .create(new StudentRequest("s001", "Demo", "demo@example.invalid"))).code);

        // Rejecting a duplicate must happen before attempting to save it.
        verify(repository, never()).saveAndFlush(any());
    }
}
