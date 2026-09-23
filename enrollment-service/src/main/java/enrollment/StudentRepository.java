package enrollment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

// Student identity checks use only this service's own database
// This promotes service inter independence
interface StudentRepository extends JpaRepository<Student, UUID> {
    boolean existsByStudentNumberOrEmail(String studentNumber, String email);
}
