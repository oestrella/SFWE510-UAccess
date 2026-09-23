package enrollment;

import java.util.UUID;

// The DTO exposes Student data without making the JPA entity, like it should
public record StudentResponse(UUID id, String studentNumber, String name, String email) {
    static StudentResponse from(Student student) {
        return new StudentResponse(student.getId(), student.getStudentNumber(), student.getName(), student.getEmail());
    }
}
