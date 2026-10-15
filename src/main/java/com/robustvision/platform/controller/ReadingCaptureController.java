package com.robustvision.platform.controller;
import com.robustvision.platform.service.*;
import com.robustvision.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/v1/learning/clips") @PreAuthorize("isAuthenticated()")
public class ReadingCaptureController {
 private final ReadingCaptureService service;
 public ReadingCaptureController(ReadingCaptureService service){this.service=service;}
 @PostMapping public Object capture(@Valid @RequestBody ReadingCaptureService.Capture body){return ApiResponse.ok(service.capture(body));}
 @GetMapping public Object list(){return ApiResponse.ok(service.list());}
 @DeleteMapping("/{id}") public Object delete(@PathVariable String id){service.delete(id);return ApiResponse.ok(null);}
}
