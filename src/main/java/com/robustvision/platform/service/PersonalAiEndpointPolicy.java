package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.AiProvider;
import okhttp3.Dns;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;

/** Fail-closed endpoint validation. Deployment allowlisting never permits private network addresses. */
@Component
public class PersonalAiEndpointPolicy {
    private static final ExecutorService DNS = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), runnable -> { Thread thread = new Thread(runnable, "personal-ai-dns"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> customBases;
    private final Dns resolver;
    public PersonalAiEndpointPolicy(String allowedBases) { this(allowedBases, Dns.SYSTEM); }
    @Autowired
    public PersonalAiEndpointPolicy(@Value("${app.personal-ai.allowed-base-urls:}") String allowedBases,
                                   @Value("${app.personal-ai.dns-mode:system}") String dnsMode) {
        this(allowedBases, createResolver(dnsMode));
    }
    PersonalAiEndpointPolicy(String allowedBases, Dns resolver) {
        this.resolver = resolver;
        Set<String> bases = new HashSet<>();
        for (String value : allowedBases.split(",")) if (!value.isBlank()) bases.add(canonical(value.trim()));
        customBases = Set.copyOf(bases);
    }
    static Dns createResolver(String mode) {
        if ("system".equals(mode)) return Dns.SYSTEM;
        if (!"cloudflare".equals(mode)) throw new IllegalArgumentException("PERSONAL_AI_DNS_MODE must be system or cloudflare");
        // Explicit deployment opt-in. Only DNS questions go to this fixed resolver, never API keys or prompts.
        var client = new OkHttpClient.Builder().proxy(Proxy.NO_PROXY).followRedirects(false)
                .followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(3, TimeUnit.SECONDS).callTimeout(4, TimeUnit.SECONDS).build();
        try {
            return new DnsOverHttps.Builder().client(client).url(HttpUrl.get("https://cloudflare-dns.com/dns-query"))
                    .bootstrapDnsHosts(InetAddress.getByAddress(new byte[]{1, 1, 1, 1}),
                            InetAddress.getByAddress(new byte[]{1, 0, 0, 1}))
                    .includeIPv6(false).build();
        } catch (UnknownHostException impossible) { throw new IllegalStateException(impossible); }
    }
    public boolean customEndpointAllowed() { return !customBases.isEmpty(); }
    public String validateBase(AiProvider provider, String supplied) {
        String value = supplied == null || supplied.isBlank()
                ? provider.baseUrls().stream().findFirst().orElseThrow(PersonalAiEndpointPolicy::invalid)
                : supplied.trim();
        String base = canonical(value);
        if (!provider.baseUrls().contains(base) && !customBases.contains(base)) throw invalid();
        return base;
    }
    public List<InetAddress> resolvePublic(String host) {
        Future<List<InetAddress>> lookup = null;
        try {
            lookup = DNS.submit(() -> resolver.lookup(host));
            return validateAddresses(lookup.get(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw dnsFailed();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
            throw dnsFailed();
        } finally { if (lookup != null && !lookup.isDone()) lookup.cancel(true); }
    }
    static List<InetAddress> validateAddresses(List<InetAddress> addresses) {
        if (addresses.stream().anyMatch(PersonalAiEndpointPolicy::isFakeIp)) throw new BusinessException(HttpStatus.BAD_GATEWAY,
                "PERSONAL_AI_DNS_FAKE_IP", "当前网络将供应商域名解析为虚拟 IP，请在代理中排除此域名的 Fake-IP 解析，或由部署者启用加密 DNS");
        if (addresses.isEmpty() || addresses.stream().anyMatch(address -> !isPublic(address))) throw invalid();
        return List.copyOf(addresses);
    }
    private static boolean isFakeIp(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && (bytes[0] & 255) == 198 && ((bytes[1] & 255) == 18 || (bytes[1] & 255) == 19);
    }
    static boolean requestNotSent(String code) {
        return Set.of("PERSONAL_AI_ENDPOINT_REJECTED", "PERSONAL_AI_DNS_FAKE_IP", "PERSONAL_AI_DNS_FAILED").contains(code);
    }
    private static BusinessException dnsFailed() { return new BusinessException(HttpStatus.BAD_GATEWAY,
            "PERSONAL_AI_DNS_FAILED", "无法解析供应商域名，请检查网络或部署的 DNS 设置；尚未向供应商发送请求"); }
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255, b = bytes[1] & 255;
        if (bytes.length == 4) {
            int c = bytes[2] & 255;
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && (b == 0 || b == 168 || (b == 88 && c == 99)))
                    || (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                    || (a == 203 && b == 0 && c == 113));
        }
        // Only global unicast; deny ULA/link-local/mapped/NAT64/6to4 and special-purpose 2001::/23.
        if (bytes.length != 16 || (a & 0xe0) != 0x20) return false;
        if (a == 0x20 && b == 0x02) return false;
        if (a == 0x20 && b == 0x01 && ((bytes[2] & 0xfe) == 0
                || ((bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8))) return false;
        // Documentation prefix 3fff::/20.
        return !(a == 0x3f && b == 0xff && (bytes[2] & 0xf0) == 0);
    }
    private static String canonical(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || !host.matches("[A-Za-z0-9.-]+")
                    || host.endsWith(".") || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || value.contains("\\") || value.contains("%") || value.length() > 500) throw invalid();
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (path.contains("//") || !path.matches("[A-Za-z0-9/_-]*") || path.contains("..")) throw invalid();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return "https://" + host.toLowerCase(Locale.ROOT) + path;
        } catch (IllegalArgumentException exception) { throw invalid(); }
    }
    static BusinessException invalid() { return new BusinessException(HttpStatus.BAD_REQUEST,
            "PERSONAL_AI_ENDPOINT_REJECTED", "供应商地址不安全或不在部署允许列表中"); }
    static BusinessException unavailable() { return new BusinessException(HttpStatus.BAD_GATEWAY,
            "PERSONAL_AI_UPSTREAM_FAILED", "未取得供应商的完整可用结果；请求可能已发送并产生费用。请先核对供应商记录，不要重复发送；未使用其他密钥或本地降级"); }
}
