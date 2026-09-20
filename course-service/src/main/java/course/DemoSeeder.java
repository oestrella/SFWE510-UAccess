package course;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;

// Both dev profile and explicit opt-in are needed before the test demo data loads.
@Configuration
@Profile("dev")
@ConditionalOnProperty(name = "uaccess.demo-enabled", havingValue = "true")
class DemoSeeder {

    @Bean
    ApplicationRunner seedDemo(DataSource dataSource) {
        return arguments -> new ResourceDatabasePopulator(
                new ClassPathResource("demo/seed.sql")).execute(dataSource
        );
    }
}
