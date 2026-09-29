package enrollment;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// These tests check that failed validation never reaches the local write step
class EnrollmentServiceTest {
    @Test
    void validationFailureNeverStartsWrite() {

        // Mocks replace the other components so this test only checks the registration rules.
        StudentService students = mock(StudentService.class);
        CourseClient courses = mock(CourseClient.class);
        EnrollmentMutation mutation = mock(EnrollmentMutation.class);
        UUID student = UUID.randomUUID(), course = UUID.randomUUID();

        // Simulate a failed Course check before registration can reach the database write component.
        doThrow(new ApiException(503, "COURSE_UNAVAILABLE")).when(courses).requireActive(course);
        EnrollmentService service = new EnrollmentService(students, mock(EnrollmentRepository.class), courses, mutation);

        // Return the Course error to the caller and confirm that no write method was called.
        assertEquals(503, assertThrows(ApiException.class, () -> service.register(student, course)).status);
        verifyNoInteractions(mutation);
    }
    @Test
    void missingStudentNeverCallsCourse() {
        StudentService students = mock(StudentService.class);
        CourseClient courses = mock(CourseClient.class);

        // Make the first Student lookup fail
        // Course checks and database writes should never start.
        UUID student = UUID.randomUUID();

        doThrow(new ApiException(404, "STUDENT_NOT_FOUND")).when(students).read(student);

        EnrollmentMutation mutation = mock(EnrollmentMutation.class);

        EnrollmentService service = new EnrollmentService(students, mock(EnrollmentRepository.class), courses, mutation);

        assertThrows(ApiException.class, () -> service.register(student, UUID.randomUUID()));

        // No interactions means NEITHER component had any method called
        verifyNoInteractions(courses, mutation);
    }
}
