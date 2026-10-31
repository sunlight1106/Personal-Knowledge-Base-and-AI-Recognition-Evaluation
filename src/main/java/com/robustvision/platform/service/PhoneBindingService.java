package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class PhoneBindingService {
    public record State(boolean smsAvailable, String maskedPhone, boolean verified) {}
    private final UserRepository users; private final PhoneBindingRepository bindings; private final CurrentUserService current;
    private final PasswordEncoder passwords; private final AccountSecurityService security; private final SecretEncryptionService encryption; private final PhoneSmsGateway sms;
    public PhoneBindingService(UserRepository users, PhoneBindingRepository bindings, CurrentUserService current, PasswordEncoder passwords,
            AccountSecurityService security, SecretEncryptionService encryption, PhoneSmsGateway sms) {
        this.users=users;this.bindings=bindings;this.current=current;this.passwords=passwords;this.security=security;this.encryption=encryption;this.sms=sms;
    }
    private UserEntity owner() { return users.findLockedById(current.requireCurrent().getId()).orElseThrow(); }
    private PhoneBindingEntity binding(long id) { return bindings.findById(id).orElseGet(()->bindings.save(new PhoneBindingEntity(id))); }
    private void proof(UserEntity user,String password,String otp) {
        if(password==null || !passwords.matches(password,user.getPasswordHash())) throw bad("PASSWORD_INVALID","当前密码不正确");
        security.checkLogin(user,otp);
    }
    @Transactional(readOnly=true) public State state() {
        String phone=bindings.findById(current.requireCurrent().getId()).map(p->p.verifiedPhone).orElse(null);
        return new State(sms.available(),phone==null?null:phone.substring(0,3)+"****"+phone.substring(phone.length()-4),phone!=null);
    }
    @Transactional(noRollbackFor=BusinessException.class)
    public void request(String raw,String password,String otp) {
        if(!sms.available()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"SMS_UNAVAILABLE","尚未配置短信服务，手机号绑定暂不可用");
        var u=owner(); proof(u,password,otp); String phone=ContactDiscoveryPolicy.phone(raw);
        var b=binding(u.getId()); var now=Instant.now(); String day=LocalDate.now(ZoneOffset.UTC).toString();
        if(b.nextSendAt!=null && b.nextSendAt.isAfter(now) || day.equals(b.sendDay) && b.sendCount>=5)
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"SMS_LIMIT","发送过于频繁，请稍后再试；每天最多 5 次");
        b.sendCount=day.equals(b.sendDay)?b.sendCount+1:1;b.sendDay=day;b.nextSendAt=now.plusSeconds(60);
        b.clearChallenge(); bindings.saveAndFlush(b);
        String code=String.format(java.util.Locale.ROOT,"%06d",new SecureRandom().nextInt(1000000));
        // Reserve the send budget even if delivery times out. Never expose whether another user owns a number.
        sms.send(phone,code,UUID.randomUUID().toString());
        b.pendingPhone=phone;b.challengeSecret=encryption.encrypt(code);b.expiresAt=now.plusSeconds(300);b.attempts=0;
    }
    @Transactional(noRollbackFor=BusinessException.class)
    public void confirm(String raw,String code) {
        var u=owner();var b=binding(u.getId());String phone=ContactDiscoveryPolicy.phone(raw);
        if(b.expiresAt==null || !b.expiresAt.isAfter(Instant.now()) || b.attempts>=5 || !phone.equals(b.pendingPhone))
            throw bad("PHONE_CHALLENGE_INVALID","验证码已失效或手机号不匹配，请重新获取");
        b.attempts++;
        if(!MessageDigest.isEqual(encryption.decrypt(b.challengeSecret).getBytes(StandardCharsets.UTF_8),code.getBytes(StandardCharsets.UTF_8)))
            throw bad("PHONE_CODE_INVALID","验证码错误，请重新输入");
        b.verifiedPhone=phone;b.clearChallenge();
        bindings.saveAndFlush(b);
    }
    @Transactional(noRollbackFor=BusinessException.class)
    public void remove(String password,String otp) {
        var u=owner();proof(u,password,otp);var b=binding(u.getId());b.verifiedPhone=null;b.clearChallenge();u.setSearchByPhone(false);
    }
    private BusinessException bad(String code,String message) { return new BusinessException(HttpStatus.BAD_REQUEST,code,message); }
}
