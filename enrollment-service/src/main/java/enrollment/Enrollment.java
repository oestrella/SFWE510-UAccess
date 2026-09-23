package enrollment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

// Student is local, BUT courseId is only an external reference
@Entity
@Table(name = "enrollments")
public class Enrollment {
    @Id private UUID id;
    @Column(nullable = false) private UUID studentId;
    @Column(nullable = false) private UUID courseId;
    @Column(nullable = false) private Instant enrolledAt;

    protected Enrollment() { }

    Enrollment(UUID studentId, UUID courseId) {

        // Re-enrollment creates a new record and timestamp instead of restoring a dropped row.
        id = UUID.randomUUID(); this.studentId = studentId; this.courseId = courseId; enrolledAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public UUID getCourseId() {
        return courseId;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }
}
