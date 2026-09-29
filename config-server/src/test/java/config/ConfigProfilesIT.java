package config;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

// I check the actual Config Server responses for both services and both runtime profiles.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ConfigProfilesIT {
    @DynamicPropertySource static void repo(DynamicPropertyRegistry r) {
        r.add("spring.cloud.config.server.native.search-locations", () -> Path.of("../config-repo").toAbsolutePath().toUri().toString());
    }
    @Autowired TestRestTemplate client;
    @Test void resolvesBothServicesAndProfiles() {
        for (String service : new String[]{"course-service", "enrollment-service"}) {
            for (String profile : new String[]{"dev", "prod"}) {
                JsonNode config = client.getForObject("/" + service + "/" + profile, JsonNode.class);
                assertNotNull(config); assertEquals(service, config.get("name").asText());
                String banner = null, exposure = null;
                // The first matching property source has precedence in the Config Server response.
                for (JsonNode source : config.get("propertySources")) {
                    if (banner == null && source.get("source").has("uaccess.banner")) banner = source.get("source").get("uaccess.banner").asText();
                    if (exposure == null && source.get("source").has("management.endpoints.web.exposure.include"))
                        exposure = source.get("source").get("management.endpoints.web.exposure.include").asText();
                }
                assertEquals("UAccess " + service + " " + profile.toUpperCase(java.util.Locale.ROOT), banner);
                assertEquals(profile.equals("dev") ? "health,info,refresh" : "health,info", exposure);
                assertTrue(config.toString().contains("ddl-auto"));
            }
        }
    }
}
