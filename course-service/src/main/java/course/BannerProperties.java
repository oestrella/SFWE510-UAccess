package course;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

// This class lets me demonstrate Config Server refresh without rebuilding the JAR
@Component @RefreshScope @Validated
@ConfigurationProperties("uaccess")
public class BannerProperties {

    @NotBlank
    private String banner;

    public String getBanner() {
        return banner;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }

}
