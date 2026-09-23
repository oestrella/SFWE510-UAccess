package enrollment;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Student operations stay local, so they can still work when the Course service is unavailable.
@Service
@Transactional(readOnly = true)
class StudentService {
    private final StudentRepository repository;

    StudentService(StudentRepository repository) {
        this.repository = repository;
    }

    Student get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(404, "STUDENT_NOT_FOUND"));
    }

    @Transactional
    StudentResponse create(StudentRequest request) {
        if (repository.existsByStudentNumberOrEmail(request.studentNumber().trim().toUpperCase(Locale.ROOT), request.email()))
            throw new ApiException(409, "STUDENT_EXISTS");

        return StudentResponse.from(repository.saveAndFlush(new Student(request)));
    }

    @Transactional
    StudentResponse update(UUID id, StudentRequest request) {
        Student student = get(id);
        student.update(request);
        return StudentResponse.from(repository.saveAndFlush(student));
    }

    StudentResponse read(UUID id) {
        return StudentResponse.from(get(id));
    }

    PageResponse<StudentResponse> list(int page, int size) {
        return PageResponse.from(
                repository
                        .findAll(PageResponse.pageable(page, size, "studentNumber"))
                        .map(StudentResponse::from)
        );
    }
}
