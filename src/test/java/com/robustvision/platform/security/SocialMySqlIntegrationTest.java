package com.robustvision.platform.security;

import com.robustvision.platform.database.IsolatedMySql;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;

@Tag("mysql")
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = "jdbc:mysql:.*")
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true", "app.bootstrap.enabled=false", "app.worker.enabled=false", "app.rate-limit.enabled=false"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SocialMySqlIntegrationTest extends SocialIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired javax.sql.DataSource source;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @org.springframework.beans.factory.annotation.Autowired com.robustvision.platform.repository.ContactLinkRepository links;
    @org.springframework.beans.factory.annotation.Autowired com.robustvision.platform.service.ContactBlockGuard blocks;
    static IsolatedMySql fixture;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        if (fixture == null) fixture = new IsolatedMySql();
        fixture.properties(registry);
    }
    @AfterAll static void cleanup() throws Exception { if (fixture != null) fixture.close(); }

    @Test void committedBlockOverridesAnOlderRepeatableReadSnapshot() throws Exception {
        long id = request();
        action(id, b, "accept").andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.executeWithoutResult(status -> {
            var snapshot = links.findById(id).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(snapshot.blocked()).isFalse();
            // A different committed connection represents a simultaneous blacklist request.
            try (var connection = source.getConnection(); var update = connection.prepareStatement("UPDATE contact_link SET low_blocked=TRUE WHERE id=?")) {
                connection.setAutoCommit(true);
                update.setLong(1, id); update.executeUpdate();
            } catch (java.sql.SQLException error) { throw new IllegalStateException(error); }
            org.assertj.core.api.Assertions.assertThat(links.findById(id).orElseThrow().blocked()).isFalse();
            org.assertj.core.api.Assertions.assertThat(blocks.anyBlocked(alice.getId(), java.util.List.of(bob.getId()))).isTrue();
        });
    }
}
