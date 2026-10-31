package com.robustvision.platform.security;

import com.robustvision.platform.domain.PhoneBindingEntity;
import com.robustvision.platform.repository.PhoneBindingRepository;
import com.robustvision.platform.service.PhoneSmsGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SocialDiscoveryIntegrationTest extends SocialIntegrationTest {
    @MockBean PhoneSmsGateway sms;
    @Autowired PhoneBindingRepository phones;
    @Autowired JdbcTemplate db;
    void privacy(String token,boolean all,boolean number,boolean email,boolean phone)throws Exception{
        mvc.perform(json(put("/api/v1/social/discovery"),token,Map.of("discoverable",all,"searchByNumber",number,"searchByEmail",email,"searchByPhone",phone))).andExpect(status().isOk());
    }
    void search(String query,String type,int count)throws Exception{
        mvc.perform(auth(get("/api/v1/social/people").param("q",query).param("type",type),a)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(count));
    }
    @Test void uniqueNumberIsCaseInsensitiveAndOldNumberStopsResolving()throws Exception{
        String number="Nr"+UUID.randomUUID().toString().replace("-","").substring(0,14);
        mvc.perform(json(put("/api/v1/social/number"),b,Map.of("contactNumber",number))).andExpect(status().isOk()).andExpect(jsonPath("$.data.contactNumber").value(number.toLowerCase(Locale.ROOT)));
        mvc.perform(json(put("/api/v1/social/number"),o,Map.of("contactNumber",number.toUpperCase(Locale.ROOT)))).andExpect(status().isConflict());
        for(String invalid:List.of("abc","name_123","名字123456","a".repeat(25))) mvc.perform(json(put("/api/v1/social/number"),b,Map.of("contactNumber",invalid))).andExpect(status().isBadRequest());
        search(number,"number",1);search(number.substring(0,6),"number",0);
        mvc.perform(json(put("/api/v1/social/number"),b,Map.of("contactNumber",number+"x"))).andExpect(status().isOk());search(number,"number",0);
        assertThat(users.findById(bob.getId()).orElseThrow().getIdentityCode()).isEqualTo(bob.getIdentityCode());
    }
    @Test void separatePrivacySwitchesAndVerifiedEmailCannotBeBypassed()throws Exception{
        privacy(b,true,false,true,false);
        search(bob.getEmail(),"email",0);
        mvc.perform(auth(get("/api/v1/account/security"),b)).andExpect(status().isOk());
        db.update("UPDATE account_security SET verified_email=? WHERE owner_id=?",bob.getEmail(),bob.getId());
        search(bob.getEmail().toUpperCase(Locale.ROOT),"email",1);search(bob.getEmail().substring(0,10),"email",0);search(bob.getUsername(),"name",0);
        mvc.perform(auth(get("/api/v1/social/people").param("q",bob.getEmail()),a)).andExpect(jsonPath("$.data[0].email").doesNotExist()).andExpect(jsonPath("$.data[0].phone").doesNotExist());
        privacy(b,false,true,true,true);search(bob.getEmail(),"email",0);
        mvc.perform(json(post("/api/v1/social/contacts"),a,Map.of("userId",bob.getId()))).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/v1/social/discovery"),b,Map.of("discoverable",true))).andExpect(status().isBadRequest());
    }
    @Test void staffProtectionFollowsRolesOverridesAndPendingApplications()throws Exception{
        search(admin.getUsername(),"name",0);
        mvc.perform(json(post("/api/v1/social/contacts"),a,Map.of("userId",admin.getId()))).andExpect(status().isNotFound());
        long contact=request();bob.getPermissionOverrides().put("moderation:review",true);users.saveAndFlush(bob);
        privacy(b,true,true,true,true);search(bob.getIdentityCode(),"name",0);
        mvc.perform(auth(get("/api/v1/social/discovery"),b)).andExpect(jsonPath("$.data.protectedAccount").value(true));
        action(contact,b,"accept").andExpect(status().isNotFound());
        mvc.perform(json(post("/api/v1/social/contacts"),a,Map.of("userId",bob.getId()))).andExpect(status().isNotFound());
    }
    @Test void simultaneousNumberClaimsHaveOneWinner()throws Exception{
        String number="race"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{
            var results=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(String token:List.of(b,o))results.add(executor.submit(()->{start.await();return mvc.perform(json(put("/api/v1/social/number"),token,Map.of("contactNumber",number))).andReturn().getResponse().getStatus();}));
            start.countDown();var codes=new ArrayList<Integer>();for(var future:results)codes.add(future.get(30,java.util.concurrent.TimeUnit.SECONDS));assertThat(codes).containsExactlyInAnyOrder(200,409);
        }finally{executor.shutdownNow();}
    }
    @Test void phoneRequiresDeliveryAndSuccessfulVerificationThenSupportsUnlink()throws Exception{
        String phone="+86138"+String.format("%08d",bob.getId());
        mvc.perform(json(post("/api/v1/account/phone/code"),b,Map.of("phone",phone,"password","SyntheticSocial123!"))).andExpect(status().isServiceUnavailable());
        when(sms.available()).thenReturn(true);
        mvc.perform(json(post("/api/v1/account/phone/code"),b,Map.of("phone",phone,"password","wrong"))).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/account/phone/code"),b,Map.of("phone",phone,"password","SyntheticSocial123!"))).andExpect(status().isOk());
        var code=org.mockito.ArgumentCaptor.forClass(String.class);verify(sms).send(eq(phone),code.capture(),anyString());
        privacy(b,true,true,false,true);search(phone,"phone",0);
        mvc.perform(json(post("/api/v1/account/phone/confirm"),o,Map.of("phone",phone,"code",code.getValue()))).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/account/phone/confirm"),b,Map.of("phone",phone,"code",code.getValue()))).andExpect(status().isOk());search(phone,"phone",1);
        mvc.perform(auth(get("/api/v1/account/phone"),b)).andExpect(jsonPath("$.data.verified").value(true)).andExpect(jsonPath("$.data.challengeSecret").doesNotExist());
        mvc.perform(json(post("/api/v1/account/phone/confirm"),b,Map.of("phone",phone,"code",code.getValue()))).andExpect(status().isBadRequest());
        mvc.perform(json(delete("/api/v1/account/phone"),b,Map.of("password","SyntheticSocial123!"))).andExpect(status().isOk());search(phone,"phone",0);
    }
    @Test void phoneFailuresPersistLimitsAndBoundNumbersStayUnique()throws Exception{
        when(sms.available()).thenReturn(true);String phone="+86139"+String.format("%08d",bob.getId());
        mvc.perform(json(post("/api/v1/account/phone/code"),b,Map.of("phone",phone,"password","SyntheticSocial123!"))).andExpect(status().isOk());
        var code=org.mockito.ArgumentCaptor.forClass(String.class);verify(sms).send(eq(phone),code.capture(),anyString());
        mvc.perform(json(post("/api/v1/account/phone/code"),b,Map.of("phone",phone,"password","SyntheticSocial123!"))).andExpect(status().isTooManyRequests());
        String wrong=code.getValue().equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++)mvc.perform(json(post("/api/v1/account/phone/confirm"),b,Map.of("phone",phone,"code",wrong))).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/account/phone/confirm"),b,Map.of("phone",phone,"code",code.getValue()))).andExpect(status().isBadRequest());
        assertThat(phones.findById(bob.getId()).orElseThrow().attempts).isEqualTo(5);
        var pending=phones.findById(bob.getId()).orElseThrow();pending.attempts=0;phones.saveAndFlush(pending);
        var existing=new PhoneBindingEntity(outsider.getId());existing.verifiedPhone=phone;phones.saveAndFlush(existing);
        mvc.perform(json(post("/api/v1/account/phone/confirm"),b,Map.of("phone",phone,"code",code.getValue()))).andExpect(status().isConflict());
        assertThat(phones.findById(bob.getId()).orElseThrow().verifiedPhone).isNull();
    }
}
