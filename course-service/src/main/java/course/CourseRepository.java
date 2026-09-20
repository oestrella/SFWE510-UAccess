package course;

import java.util.UUID;
import org.springframework.data.jpa.repository.*;

// Spring Data handles this service's persistence
// This repository does not reach into another service

interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {

    boolean existsByCourseCode(String code);
}
