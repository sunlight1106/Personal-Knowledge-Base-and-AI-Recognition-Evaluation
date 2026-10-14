package com.robustvision.platform.controller;

import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.LearningStatisticsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/learning") @PreAuthorize("isAuthenticated()")
public class LearningStatisticsController {
    @org.springframework.beans.factory.annotation.Autowired private com.robustvision.platform.service.LearningWorkspaceService workspace;
    @PostMapping("/cards/import") @PreAuthorize("hasAuthority('research:use')") public Object importCards(@Valid @RequestBody com.robustvision.platform.service.LearningWorkspaceService.CardBatch input){return ApiResponse.ok(workspace.importCards(input));}
    private final LearningStatisticsService service;
    public LearningStatisticsController(LearningStatisticsService service){this.service=service;}
    @GetMapping("/statistics") public Object mine(@RequestParam(defaultValue="30") int days){return ApiResponse.ok(service.mine(days));}
    @GetMapping("/today") public Object today(){return ApiResponse.ok(service.today());}
    @PostMapping("/pulse") public Object pulse(@Valid @RequestBody LearningStatisticsService.Pulse pulse){service.pulse(pulse);return ApiResponse.ok(null);}
    @GetMapping("/users/{id}/statistics") @PreAuthorize("hasAuthority('admin:stats') and hasAuthority('user:read')")
    public Object user(@PathVariable long id,@RequestParam(defaultValue="30") int days){return ApiResponse.ok(service.summary(id,days));}
}
