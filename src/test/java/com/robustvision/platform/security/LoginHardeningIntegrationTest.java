package com.robustvision.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:login_hardening;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE","app.bootstrap.enabled=false","app.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@Import(GroupCommunicationIntegrationTest.FastPasswords.class)
class LoginHardeningIntegrationTest {
    static final String PASSWORD="SyntheticOnly123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate db;
    UserEntity user;
    @BeforeEach void setup() {
        String name="guard"+UUID.randomUUID().toString().substring(0,8);
        var role=roles.save(new RoleEntity("GUARD_"+name,"Synthetic","Synthetic",Set.of("note:read","note:write")));
        user=users.save(new UserEntity(name,passwords.encode(PASSWORD),name,name+"@example.invalid",role));
    }
    ResultActions login(String identifier,String password,String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr(ip);return r;})
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",identifier,"password",password))));
    }
    String token(UserEntity who) throws Exception {
        return json.readTree(login(who.getUsername(),PASSWORD,"192.0.2.1").andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).path("data").path("token").asText();
    }
    int failures() { return db.queryForObject("SELECT failures FROM account_login_throttle WHERE owner_id=?",Integer.class,user.getId()); }
    @Test void aliasesAndChangingIpsCannotBypassCooldownAndCooldownDoesNotExtend() throws Exception {
        db.update("INSERT INTO account_security(owner_id,verified_email,last_counter,recovery_hashes,failed_attempts) VALUES (?,?,-1,'',0)",user.getId(),user.getEmail());
        String[] aliases={user.getUsername(),user.getEmail(),user.getIdentityCode()};
        for(int i=0;i<8;i++)login(aliases[i%3],"Incorrect123!","192.0.2."+(i+1)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
        var until=db.queryForObject("SELECT blocked_until FROM account_login_throttle WHERE owner_id=?",Timestamp.class,user.getId());
        login(user.getUsername(),PASSWORD,"198.51.100.1").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
        assertThat(failures()).isEqualTo(8);
        assertThat(db.queryForObject("SELECT blocked_until FROM account_login_throttle WHERE owner_id=?",Timestamp.class,user.getId())).isEqualTo(until);
        db.update("UPDATE account_login_throttle SET blocked_until=? WHERE owner_id=?",Timestamp.from(Instant.now().minusSeconds(1)),user.getId());
        login(user.getIdentityCode(),PASSWORD,"198.51.100.1").andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM account_login_throttle WHERE owner_id=?",Long.class,user.getId())).isZero();
    }
    @Test void oldFailuresExpireAndSuccessfulLoginResetsCounter() throws Exception {
        login(user.getUsername(),"incorrect","192.0.2.1").andExpect(status().isUnauthorized());
        db.update("UPDATE account_login_throttle SET failures=7,window_started_at=? WHERE owner_id=?",Timestamp.from(Instant.now().minusSeconds(901)),user.getId());
        login(user.getUsername(),"incorrect","192.0.2.1").andExpect(status().isUnauthorized());
        assertThat(failures()).isEqualTo(1);
        token(user);
        login(user.getUsername(),"incorrect","192.0.2.1").andExpect(status().isUnauthorized());
        assertThat(failures()).isEqualTo(1);
    }
    @Test void concurrentFailuresDoNotLoseUpdates() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls=new ArrayList<>();
            for(int i=0;i<8;i++) {final int index=i;calls.add(()->login(user.getUsername(),"incorrect","192.0.2."+(index+1)).andReturn().getResponse().getStatus());}
            for(var result:pool.invokeAll(calls,30,TimeUnit.SECONDS))assertThat(result.get()).isEqualTo(401);
            assertThat(failures()).isEqualTo(8);
        } finally {pool.shutdownNow();}
    }
    @Test void unknownAccountsDoNotAllocateCounters() throws Exception {
        long before=db.queryForObject("SELECT COUNT(*) FROM account_login_throttle",Long.class);
        login("unknown-"+UUID.randomUUID(),"incorrect","192.0.2.1").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM account_login_throttle",Long.class)).isEqualTo(before);
    }
    @Test void switchingAndReactivationShareProtectionWithoutRevokingSource() throws Exception {
        var source=users.save(new UserEntity("src"+user.getUsername(),passwords.encode(PASSWORD),"Source", "src"+user.getEmail(),user.getRole()));
        String session=token(source);
        for(int i=0;i<8;i++)mvc.perform(post("/api/v1/account/switch").header("Authorization","Bearer "+session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("username",user.getUsername(),"password","incorrect")))).andExpect(status().isForbidden());
        assertThat(failures()).isEqualTo(8);
        mvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+session)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/reactivate").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",user.getUsername(),"password",PASSWORD))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
    }
    @Test void ambiguousJsonIsRejectedBeforeAuthenticationOrSaving() throws Exception {
        String body="{\"username\":\""+user.getUsername()+"\",\"password\":\"SENSITIVE_SENTINEL\",\"password\":\""+PASSWORD+"\"}";
        var response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("SENSITIVE_SENTINEL",PASSWORD);
        String valid=json.writeValueAsString(Map.of("username",user.getUsername(),"password",PASSWORD));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(valid+" {}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/notes").header("Authorization","Bearer "+token(user)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"first\",\"title\":\"second\",\"content\":\"hello\",\"format\":\"MARKDOWN\"}")).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM note WHERE owner_id=?",Long.class,user.getId())).isZero();
    }
    @Test void ambiguousAuthorizationHeadersAreRejected() throws Exception {
        String bearer="Bearer "+token(user);
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer,"Bearer invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer+", Bearer invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",bearer)).andExpect(status().isOk());
    }
    @Test void onboardingOnlyReturnsAllowedStepsAndCannotMarkAnythingComplete() throws Exception {
        String bearer="Bearer "+token(user);
        mvc.perform(get("/api/v1/workspace/getting-started").header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value("note")).andExpect(jsonPath("$.data[0].completed").value(false));
        mvc.perform(post("/api/v1/workspace/getting-started").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isMethodNotAllowed());
        var empty=roles.save(new RoleEntity("EMPTY_"+user.getUsername(),"Empty","Synthetic",Set.of()));
        user.setRole(empty);users.save(user);
        mvc.perform(get("/api/v1/workspace/getting-started").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/workspace/getting-started")).andExpect(status().isUnauthorized());
    }
}
