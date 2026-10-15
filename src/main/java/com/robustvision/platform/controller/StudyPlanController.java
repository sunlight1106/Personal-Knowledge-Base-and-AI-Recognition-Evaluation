package com.robustvision.platform.controller;
import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.StudyPlanService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/learning/plan") @PreAuthorize("isAuthenticated()")
public class StudyPlanController {
 private final StudyPlanService service;
 public StudyPlanController(StudyPlanService service){this.service=service;}
 @GetMapping public Object get(){return ApiResponse.ok(service.get());}
 @PutMapping public Object save(@Valid @RequestBody StudyPlanService.Settings body){return ApiResponse.ok(service.save(body));}
}
