package enrollment;

import jakarta.validation.constraints.*;

// This formats and trims identity fields
public record StudentRequest(
        @NotBlank @Size(max = 32) String studentNumber,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Email @Size(max = 254) String email
) {

    public StudentRequest {
        if (studentNumber != null)
            studentNumber = studentNumber.trim().toUpperCase(java.util.Locale.ROOT);

        if (name != null)
            name = name.trim();

        if (email != null)
            email = email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
