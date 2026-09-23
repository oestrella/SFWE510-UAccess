package enrollment;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

// Including studentId in the lookup prevents one Student from accessing another Student's enrollment.
interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<Enrollment> findByIdAndStudentId(UUID id, UUID studentId);

    Page<Enrollment> findByStudentId(UUID studentId, Pageable pageable);

}
