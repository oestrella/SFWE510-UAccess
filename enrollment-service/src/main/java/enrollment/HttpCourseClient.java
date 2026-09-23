package enrollment;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.*;

// This calls the Course API instead of reading the database, like it should
@Component
class HttpCourseClient implements CourseClient {
    private final RestClient client;

    HttpCourseClient(CourseApiProperties settings) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // Bound the wait so a slow upstream becomes a controlled 503.
        factory.setConnectTimeout(settings.connectTimeout()); factory.setReadTimeout(settings.readTimeout());
        client = RestClient.builder().baseUrl(settings.baseUrl().toString()).requestFactory(factory).build();
    }

    @Override public void requireActive(UUID courseId) {
        tools.jackson.databind.JsonNode course;

        try {
            course = client.get()
                    .uri("/api/v1/courses/{id}", courseId)
                    .retrieve()
                    .body(tools.jackson.databind.JsonNode.class);
        }
        catch (HttpClientErrorException.NotFound ex) {
            throw new ApiException(404, "COURSE_NOT_FOUND");
        }
        catch (RestClientException ex) {
            throw new ApiException(503, "COURSE_UNAVAILABLE");
        }

        // A successful HTTP status is not enough: the ID and boolean flag must match the contract.
        if (course == null ||
                !course.isObject() ||
                !course.path("id").isTextual() ||
                !courseId.toString().equalsIgnoreCase(course.path("id").asText()) ||
                !course.path("active").isBoolean()) {

            throw new ApiException(503, "COURSE_UNAVAILABLE");
        }

        if (!course.path("active").booleanValue())
            throw new ApiException(409, "COURSE_INACTIVE");
    }

}
