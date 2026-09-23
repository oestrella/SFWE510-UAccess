package enrollment;

import java.net.URI;
import java.time.Duration;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// Checks the external HTTP settings at startup
@Validated
@ConfigurationProperties("uaccess.course-api")
public record CourseApiProperties(@NotNull URI baseUrl, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {

    @AssertTrue(message = "Course URL must use HTTP(S); timeouts must be positive and at most 30 seconds")
    public boolean isValid() {
        return baseUrl != null
                && ("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))
                && baseUrl.getHost() != null && bounded(connectTimeout) && bounded(readTimeout);
    }

    private static boolean bounded(Duration value) {
        return value != null
                && !value.isNegative()
                && !value.isZero()
                && value.compareTo(Duration.ofSeconds(30)) <= 0;
    }
}
