package enrollment;

import jakarta.persistence.*;
import java.util.*;

// Student will be part of the Enrollment database
@Entity
@Table(name = "students")
public class Student {
    @Id private UUID id;
    @Column(nullable = false, length = 32) private String studentNumber;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 254) private String email;

    protected Student() { }

    Student(StudentRequest request) {
        id = UUID.randomUUID();
        update(request);
    }

    void update(StudentRequest request) {
        studentNumber = request.studentNumber().trim().toUpperCase(Locale.ROOT);
        name = request.name().trim();
        email = request.email().trim().toLowerCase(Locale.ROOT);
    }

    public UUID getId() {
        return id;
    }

    public String getStudentNumber() {
        return studentNumber;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}
