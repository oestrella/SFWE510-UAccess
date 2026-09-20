package course;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


// This entry point for the catalog service
// It is independently runnable as a Spring Boot service by design

@SpringBootApplication
public class CourseApplication {
    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
    }
}
