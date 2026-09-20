package course;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.domain.Specification;

// Catalog rules here so the controller is more independent of database details
@Service
@Transactional(readOnly = true)
class CourseService {

    private final CourseRepository repository;

    CourseService(CourseRepository repository) {
        this.repository = repository;
    }

    Course get(UUID id) {

        return repository.findById(id).orElseThrow(() -> new ApiException(404, "COURSE_NOT_FOUND"));
    }

    @Transactional
    CourseResponse create(CourseRequest request) {
        String code = request.courseCode().trim().toUpperCase(Locale.ROOT);

        if (repository.existsByCourseCode(code))
            throw new ApiException(409, "COURSE_CODE_EXISTS");

        return CourseResponse.from(repository.saveAndFlush(new Course(request)));
    }

    @Transactional
    CourseResponse update(UUID id, CourseRequest request) {
        Course course = get(id);
        course.update(request);

        return CourseResponse.from(repository.saveAndFlush(course));
    }

    // Archive changes eligibility BUT preserves the course for existing enrollments.
    @Transactional
    CourseResponse status(UUID id, boolean active) {
        Course course = get(id);
        course.setActive(active);

        return CourseResponse.from(repository.saveAndFlush(course));
    }

    CourseResponse read(UUID id) {
        return CourseResponse.from(get(id));
    }

    PageResponse<CourseResponse> list(boolean active, String search, int page, int size) {

        Specification<Course> spec = (
                root,
                query,
                cb) -> cb.equal(root.get("active"),
                active
        );

        if (search != null && !search.isBlank()) {

            // Escape SQL wildcards
            String term = search.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");

            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("courseCode")), "%" + term + "%", '\\'),
                    cb.like(cb.lower(root.get("title")), "%" + term + "%", '\\')));
        }

        return PageResponse.from(repository.findAll(
                spec,
                PageResponse.pageable(page, size, "courseCode")).map(CourseResponse::from)
        );
    }

}
