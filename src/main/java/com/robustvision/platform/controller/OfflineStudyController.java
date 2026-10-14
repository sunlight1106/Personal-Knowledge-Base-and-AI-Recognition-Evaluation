package com.robustvision.platform.controller;
import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.OfflineStudyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController @RequestMapping("/api/v1/vocabulary/offline") @PreAuthorize("hasAuthority('vocabulary:use')")
public class OfflineStudyController {
 private final OfflineStudyService service;public OfflineStudyController(OfflineStudyService service){this.service=service;}
 public record Prepare(@NotBlank @Size(max=36) String bookId){}
 @PostMapping public Object prepare(@Valid @RequestBody Prepare r){return ApiResponse.ok(service.prepare(r.bookId()));}
 @PostMapping("/{id}/sync") public Object sync(@PathVariable String id,@Valid @RequestBody OfflineStudyService.Sync r){return ApiResponse.ok(service.sync(id,r));}
}
