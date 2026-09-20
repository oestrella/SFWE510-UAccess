package course;

import jakarta.validation.Valid;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// The controller handles HTTP and related DTOs
@RestController
@RequestMapping("/api/v1/courses")
class CourseController {

    private final CourseService service;

    CourseController(CourseService service) {
        this.service = service;
    }

    record StatusRequest(@NotNull Boolean active) { }

    @PostMapping
    ResponseEntity<EntityModel<CourseResponse>> create(@Valid @RequestBody CourseRequest request) {
        CourseResponse course = service.create(request);

        return ResponseEntity.created(URI.create("/api/v1/courses/" + course.id())).body(represent(course));
    }

    @GetMapping
    PageResponse<CourseResponse> list(
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.list(active, search, page, size);
    }

    @GetMapping("/{id}")
    EntityModel<CourseResponse> read(@PathVariable UUID id) {
        return represent(service.read(id));
    }

    @PutMapping("/{id}")
    EntityModel<CourseResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CourseRequest request) {

        return represent(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    EntityModel<CourseResponse> status(
            @PathVariable UUID id,
            @Valid @RequestBody StatusRequest request) {

        return represent(service.status(id, request.active()));
    }

    // DTO Links
    private EntityModel<CourseResponse> represent(CourseResponse dto) {

        return EntityModel.of(
                dto,
                Link.of("/api/v1/courses/" + dto.id()).withSelfRel(),
                Link.of("/api/v1/courses").withRel("collection"),
                Link.of("/api/v1/courses/" + dto.id() + "/status").withRel("status")
        );
    }
}
