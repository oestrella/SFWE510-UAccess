package enrollment;

import java.time.Instant;
import java.util.UUID;

// NOTE: API gets a UTC enrollment time
public record EnrollmentResponse(UUID id, UUID studentId, UUID courseId, Instant enrolledAt) {

    static EnrollmentResponse from(Enrollment e) {
        return new EnrollmentResponse(
                e.getId(),
                e.getStudentId(),
                e.getCourseId(),
                e.getEnrolledAt()
        );
    }
}
