package enrollment;

import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

// Showing only the banner and profile makes the external configuration visible without exposing secrets
@RestController
class InfoController {
    private final BannerProperties settings;
    private final Environment environment;

    InfoController(BannerProperties settings, Environment environment) {
        this.settings = settings;
        this.environment = environment;
    }

    @GetMapping("/api/v1/info")
    Map<String, Object> info() {
        return Map.of(
                "service",
                "enrollment-service",
                "banner",
                settings.getBanner(),
                "profiles",
                environment.getActiveProfiles()
        );
    }

}
