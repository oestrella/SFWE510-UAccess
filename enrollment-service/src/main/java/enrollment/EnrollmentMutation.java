package enrollment;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// This Spring bean gives local writes a transaction after the HTTP check
@Service
class EnrollmentMutation {

    private final EnrollmentRepository enrollments;
    private final StudentService students;

    EnrollmentMutation(EnrollmentRepository enrollments, StudentService students) {
        this.enrollments = enrollments; this.students = students;
    }

    // Only local work runs in this transaction. The upstream check has already completed.
    @Transactional
    public EnrollmentResponse register(UUID studentId, UUID courseId) {
        students.get(studentId);
        if (enrollments.existsByStudentIdAndCourseId(studentId, courseId)) throw new ApiException(409, "ALREADY_ENROLLED");
        // The database unique constraint also catches duplicates when requests arrive together.
        return EnrollmentResponse.from(enrollments.saveAndFlush(new Enrollment(studentId, courseId)));
    }

    @Transactional
    public void drop(UUID studentId, UUID enrollmentId) {
        students.get(studentId);
        Enrollment enrollment = enrollments.findByIdAndStudentId(enrollmentId, studentId)
                .orElseThrow(() -> new ApiException(404, "ENROLLMENT_NOT_FOUND"));
        // Drop removes only the enrollment found under this Student, not the Student or Course.
        enrollments.delete(enrollment); enrollments.flush();
    }

}
