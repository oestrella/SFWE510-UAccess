package course;

import jakarta.validation.constraints.*;

// Validates course request inputs before other checks
public record CourseRequest(
    @NotBlank @Size(max = 32) String courseCode,
    @NotBlank @Size(max = 160) String title,
    @Size(max = 2000) String description,
    @NotNull @Min(1) @Max(6) Integer credits,
    Boolean active) {

    public CourseRequest {
        if (courseCode != null)
            courseCode = courseCode.trim().toUpperCase(java.util.Locale.ROOT);

        if (title != null)
            title = title.trim();

        if (description != null)
            description = description.trim();
    }
}
