package course;

import java.util.UUID;

// A response DTO keeps the public API separate from the JPA entity.
public record CourseResponse(
        UUID id,
        String courseCode,
        String title,
        String description,
        Integer credits,
        Boolean active ) {

    static CourseResponse from(Course course) {
        return new CourseResponse(
                course.getId(),
                course.getCourseCode(),
                course.getTitle(),
                course.getDescription(),
                course.getCredits(),
                course.getActive()
        );
    }
}
