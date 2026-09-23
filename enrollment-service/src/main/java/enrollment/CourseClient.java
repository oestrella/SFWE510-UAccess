package enrollment;

import java.util.UUID;

// Interface that lets tests replace Course validation without a shared Course entity
interface CourseClient {
    void requireActive(UUID courseId);
}
