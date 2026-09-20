package course;

import java.util.List;
import org.springframework.data.domain.*;

// A small page DTO keeps the API format
// This is independent and external from/to Spring Data
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static Pageable pageable(int page, int size, String field) {

        if (page < 0 || size < 1 || size > 100)
            throw new ApiException(400, "INVALID_PAGE");

        // The UUID breaks ties so repeated pages STILL have an ascending order
        return PageRequest.of(page, size, Sort.by(field).ascending().and(Sort.by("id").ascending()));
    }

    static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages()
        );
    }
}
