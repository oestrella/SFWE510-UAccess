package enrollment;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Registration combines a remote eligibility check with a separate local database write.
@Service
class EnrollmentService {
    private final StudentService students;
    private final EnrollmentRepository enrollments;
    private final CourseClient courses;
    private final EnrollmentMutation mutations;

    EnrollmentService(StudentService students, EnrollmentRepository enrollments, CourseClient courses, EnrollmentMutation mutations) {
        this.students = students;
        this.enrollments = enrollments;
        this.courses = courses;
        this.mutations = mutations;
    }

    public EnrollmentResponse register(UUID studentId, UUID courseId) {

        // Avoid holding a database transaction open while waiting on another service.
        students.read(studentId); courses.requireActive(courseId);

        return mutations.register(studentId, courseId);
    }

    // Listing existing records uses local data and does not depend on Course availability.
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> list(UUID studentId, int page, int size) {
        students.get(studentId);

        return PageResponse.from(
                enrollments.findByStudentId(studentId, PageResponse.pageable(page, size, "enrolledAt"))
                .map(EnrollmentResponse::from)
        );
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse read(UUID studentId, UUID enrollmentId) {
        students.get(studentId);

        return EnrollmentResponse.from(
                enrollments.findByIdAndStudentId(enrollmentId, studentId)
                .orElseThrow(() -> new ApiException(404, "ENROLLMENT_NOT_FOUND"))
        );
    }

    public void drop(UUID studentId, UUID enrollmentId) {
        mutations.drop(studentId, enrollmentId);
    }
}
