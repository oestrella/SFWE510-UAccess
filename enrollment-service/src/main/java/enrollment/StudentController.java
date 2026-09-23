package enrollment;

import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Student responses include links
// Implements REST HATEOAS
@RestController
@RequestMapping("/api/v1/students")
class StudentController {
    private final StudentService service;

    StudentController(StudentService service) { this.service = service; }
    @PostMapping
    ResponseEntity<EntityModel<StudentResponse>> create(@Valid @RequestBody StudentRequest request) {
        StudentResponse student = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/students/" + student.id())).body(represent(student));
    }
    @GetMapping
    PageResponse<StudentResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }

    @GetMapping("/{id}")
    EntityModel<StudentResponse> read(@PathVariable UUID id) {
        return represent(service.read(id));
    }

    @PutMapping("/{id}")
    EntityModel<StudentResponse> update(@PathVariable UUID id, @Valid @RequestBody StudentRequest request) {
        return represent(service.update(id, request));
    }

    private EntityModel<StudentResponse> represent(StudentResponse dto) {
        return EntityModel.of(
                dto,
                Link.of("/api/v1/students/" + dto.id()).withSelfRel(),
                Link.of("/api/v1/students").withRel("collection"),
                Link.of("/api/v1/students/" + dto.id() + "/enrollments").withRel("enrollments")
        );
    }
}
