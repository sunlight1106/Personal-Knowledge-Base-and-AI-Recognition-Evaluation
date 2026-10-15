package com.robustvision.platform.controller;

import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/operations")
public class OperationsController {
    private final CurrentUserService current;private final RuntimeHealthService health;
    public OperationsController(CurrentUserService current,RuntimeHealthService health){this.current=current;this.health=health;}
    @GetMapping public Object status(){current.requireSuperAdmin();return ApiResponse.ok(health.snapshot());}
}
