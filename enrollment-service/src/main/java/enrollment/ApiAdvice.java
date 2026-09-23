package enrollment;

import jakarta.servlet.http.HttpServletRequest;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.net.URI;
import java.util.*;

// One place for API errors keeps the HTTP contract consistent across the endpoints.
@RestControllerAdvice
class ApiAdvice {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiAdvice.class);
    private final org.springframework.context.MessageSource messages;

    ApiAdvice(org.springframework.context.MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> business(ApiException ex, HttpServletRequest request) {

        return problem(ex.status, ex.code, request, Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.put(e.getField(),
                messages.getMessage("validation." + e.getCode(), null, "Invalid value.",
                        org.springframework.context.i18n.LocaleContextHolder.getLocale())));

        return problem(400, "INVALID_INPUT", request, fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> malformed(Exception ex, HttpServletRequest request) {

        return problem(400, "INVALID_INPUT", request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {

        // Only named uniqueness constraints are expected conflicts; other failures remain 500.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {

            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null
                    && Set.of("uq_students_number", "uq_students_email").contains(violation.getConstraintName()))
                return problem(409, "STUDENT_EXISTS", request, Map.of());
        }

        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "uq_enrollments_pair".equals(violation.getConstraintName()))
                return problem(409, "ALREADY_ENROLLED", request, Map.of());
        }

        return problem(500, "INTERNAL_ERROR", request, Map.of());
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> unknownPath(Exception ex, HttpServletRequest request) {

        return problem(404, "RESOURCE_NOT_FOUND", request, Map.of());
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> method(Exception ex, HttpServletRequest request) {

        return problem(405, "METHOD_NOT_ALLOWED", request, Map.of());
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ProblemDetail> media(Exception ex, HttpServletRequest request) {

        return problem(415, "UNSUPPORTED_MEDIA_TYPE", request, Map.of());
    }


    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest request) {

        log.error("Unexpected request failure for {}", request.getRequestURI(), ex);

        return problem(500, "INTERNAL_ERROR", request, Map.of());
    }
    private ResponseEntity<ProblemDetail> problem(int status, String code, HttpServletRequest request, Map<String, String> fields) {

        // Accept-Language changes the detail, while clients can still rely on the same code.
        String detail = messages.getMessage(code, null, "An unexpected error occurred.",
                org.springframework.context.i18n.LocaleContextHolder.getLocale());

        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        p.setInstance(URI.create(request.getRequestURI())); p.setProperty("code", code);

        if (!fields.isEmpty()) p.setProperty("fields", fields);

        return ResponseEntity.status(status).body(p);
    }
}
