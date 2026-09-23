package enrollment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// The nested route carries the Student identity through every enrollment operation.
@RestController
@RequestMapping("/api/v1/students/{studentId}/enrollments")
class EnrollmentController {

    private final EnrollmentService service;

    EnrollmentController(EnrollmentService service) {
        this.service = service;
    }

    record Registration(@NotNull UUID courseId) { }

    @PostMapping
    ResponseEntity<EnrollmentResponse> register(@PathVariable UUID studentId, @Valid @RequestBody Registration request) {
        EnrollmentResponse enrollment = service.register(studentId, request.courseId());

        return ResponseEntity.created(URI.create("/api/v1/students/" + studentId + "/enrollments/" + enrollment.id())).body(enrollment);
    }

    @GetMapping
    PageResponse<EnrollmentResponse> list(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.list(studentId, page, size);
    }

    @GetMapping("/{enrollmentId}")
    EnrollmentResponse read(
            @PathVariable UUID studentId,
            @PathVariable UUID enrollmentId) {

        return service.read(studentId, enrollmentId);
    }

    @DeleteMapping("/{enrollmentId}") ResponseEntity<Void> drop(
            @PathVariable UUID studentId,
            @PathVariable UUID enrollmentId) {

        service.drop(studentId, enrollmentId); return ResponseEntity.noContent().build();
    }
}
