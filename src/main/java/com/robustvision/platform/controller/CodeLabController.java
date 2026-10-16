package com.robustvision.platform.controller;

import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.CodeLabService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/code-lab")
@PreAuthorize("hasAuthority('code-lab:use')")
public class CodeLabController {
    private final CodeLabService service;
    public CodeLabController(CodeLabService service){this.service=service;}
    @PostMapping("/runs") public ApiResponse<Map<String,Object>> execute(@Valid @RequestBody CodeLabService.Input input){return ApiResponse.ok(service.execute(input));}
    @GetMapping("/runs") public ApiResponse<List<Map<String,Object>>> list(){return ApiResponse.ok(service.list());}
    @GetMapping("/runs/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable UUID id){return ApiResponse.ok(service.get(id));}
    @DeleteMapping("/runs/{id}") public ApiResponse<Void> delete(@PathVariable UUID id){service.delete(id);return ApiResponse.ok(null);}
}
