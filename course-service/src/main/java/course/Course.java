package course;

import jakarta.persistence.*;
import java.util.UUID;

// Only the Course service owns this entity
// Enrollment refers to it by UUID over HTTP
@Entity
@Table(name = "courses")
public class Course {

    @Id private UUID id;
    @Column(nullable = false, length = 32) private String courseCode;
    @Column(nullable = false, length = 160) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false) private Integer credits;
    @Column(nullable = false) private Boolean active;

    protected Course() { }
    Course(CourseRequest request) {
        id = UUID.randomUUID();
        update(request);
    }

    void update(CourseRequest request) {

        courseCode = request.courseCode()
                .trim().toUpperCase(java.util.Locale.ROOT);

        title = request.title().trim();

        description = request.description() == null ? null : request.description().trim();

        credits = request.credits();

        // Omitting active on an update must not accidentally reactivate an archived course.
        active = request.active() == null ? (active == null ? true : active) : request.active();
    }

    void setActive(boolean value) {
        active = value;
    }

    public UUID getId() {
        return id;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getCredits() {
        return credits;
    }

    public Boolean getActive() {
        return active;
    }
}
