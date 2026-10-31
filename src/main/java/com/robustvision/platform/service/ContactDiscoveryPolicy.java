package com.robustvision.platform.service;

import com.robustvision.platform.domain.UserEntity;
import com.robustvision.platform.security.Permissions;
import java.util.Set;

public final class ContactDiscoveryPolicy {
    private ContactDiscoveryPolicy() {}
    public static boolean protectedAccount(UserEntity user) {
        return Set.of("ADMIN","CHECK","REVIEWER","MODERATOR","AUDITOR").contains(user.getRole().getCode())
            || user.getRole().getPermissions().stream().anyMatch(Set.of("moderation:review","user:write","role:write")::contains)
            || Permissions.effective(user).stream().anyMatch(Set.of("moderation:review","user:write","role:write")::contains);
    }
    public static boolean canDiscover(UserEntity user) { return user.isDiscoverable() && !protectedAccount(user); }
    public static String phone(String raw) {
        String value=raw==null?"":raw.trim().replace(" ","").replace("-","");
        if(value.matches("1[3-9][0-9]{9}")) value="+86"+value;
        if(!value.matches("\\+[1-9][0-9]{7,14}")) throw new com.robustvision.platform.common.BusinessException(
            org.springframework.http.HttpStatus.BAD_REQUEST,"PHONE_INVALID","请输入含国家区号的完整手机号，例如 +8613812345678");
        return value;
    }
}
