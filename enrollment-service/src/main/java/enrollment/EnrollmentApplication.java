package enrollment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// This entry point for the enrollment service
@org.springframework.boot.context.properties.ConfigurationPropertiesScan
@SpringBootApplication
public class EnrollmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnrollmentApplication.class, args);
    }
}
