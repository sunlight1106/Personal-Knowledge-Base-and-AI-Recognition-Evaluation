package com.robustvision.platform.controller;
import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.PhoneBindingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/v1/account/phone") @PreAuthorize("isAuthenticated()")
public class PhoneBindingController {
    private final PhoneBindingService service;
    public PhoneBindingController(PhoneBindingService service) { this.service=service; }
    public record Send(@NotBlank @Size(max=32) String phone,@NotBlank @Size(max=100) String password,@Size(max=32) String otp) {}
    public record Confirm(@NotBlank @Size(max=32) String phone,@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Remove(@NotBlank @Size(max=100) String password,@Size(max=32) String otp) {}
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public org.springframework.http.ResponseEntity<?> conflict() { return org.springframework.http.ResponseEntity.status(409).body(ApiResponse.failure("PHONE_UNAVAILABLE","此手机号暂不能绑定，请联系支持或使用其他号码")); }
    @GetMapping public Object state() { return ApiResponse.ok(service.state()); }
    @PostMapping("/code") public Object send(@Valid @RequestBody Send body) { service.request(body.phone(),body.password(),body.otp()); return ApiResponse.ok(null); }
    @PostMapping("/confirm") public Object confirm(@Valid @RequestBody Confirm body) { service.confirm(body.phone(),body.code());return ApiResponse.ok(null); }
    @DeleteMapping public Object remove(@Valid @RequestBody Remove body) { service.remove(body.password(),body.otp());return ApiResponse.ok(null); }
}
