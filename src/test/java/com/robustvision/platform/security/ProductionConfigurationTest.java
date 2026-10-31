package com.robustvision.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationTest {
    @Test void productionYamlHasNoDuplicateKeysAndKeepsPhoneBindingSeparateFromSandboxBilling() throws Exception {
        var sources=new YamlPropertySourceLoader().load("production",new FileSystemResource("src/main/resources/application.yml"));
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getProperty("app.sms.endpoint")).isEqualTo("${SMS_GATEWAY_URL:}");
        assertThat(sources.get(0).getProperty("app.sms.token")).isEqualTo("${SMS_GATEWAY_TOKEN:}");
        assertThat(sources.get(0).getProperty("app.sms.mode")).isEqualTo("${SMS_MODE:sandbox}");
    }
}
