package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.common.BusinessException;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.net.Proxy;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Deployment-owned HTTPS adapter. It never accepts an endpoint or credentials from users. */
@Component
public class PhoneSmsGateway {
    private final String endpoint, token;
    private final PersonalAiEndpointPolicy addresses;
    private final ObjectMapper json;
    public PhoneSmsGateway(@Value("${app.sms.endpoint:}") String endpoint, @Value("${app.sms.token:}") String token,
                           PersonalAiEndpointPolicy addresses, ObjectMapper json) {
        this.endpoint=endpoint; this.token=token; this.addresses=addresses; this.json=json;
    }
    public boolean available() { return !endpoint.isBlank() && !token.isBlank(); }
    public void send(String phone, String code, String requestId) {
        if (!available()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"SMS_UNAVAILABLE","尚未配置短信服务，手机号绑定暂不可用");
        try {
            HttpUrl url=HttpUrl.get(endpoint);
            if (!url.isHttps() || !url.username().isEmpty() || !url.password().isEmpty() || url.query()!=null || url.fragment()!=null || url.port()!=443)
                throw new IllegalArgumentException("Invalid SMS endpoint");
            var resolved=addresses.resolvePublic(url.host());
            var client=new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).dns(host -> resolved)
                    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                    .connectTimeout(5,TimeUnit.SECONDS).callTimeout(10,TimeUnit.SECONDS).build();
            var body=RequestBody.create(json.writeValueAsBytes(Map.of("phone",phone,"code",code,"expiresIn",300,"requestId",requestId)),MediaType.get("application/json"));
            var request=new Request.Builder().url(url).header("Authorization","Bearer "+token).header("Idempotency-Key",requestId).post(body).build();
            try(var response=client.newCall(request).execute()) { if (!response.isSuccessful()) throw new IllegalStateException("SMS rejected"); }
        } catch(Exception failure) {
            // Never propagate a provider response, phone number, code or credential into logs/UI.
            throw new BusinessException(HttpStatus.BAD_GATEWAY,"SMS_DELIVERY_FAILED","短信发送未确认，请稍后重试，避免连续发送");
        }
    }
}
