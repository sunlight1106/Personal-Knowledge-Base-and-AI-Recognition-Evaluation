package com.robustvision.platform.controller;
import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.FileLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/v1/files") @PreAuthorize("hasAuthority('file:read')")
public class FileLifecycleController {
    private final FileLifecycleService service;public FileLifecycleController(FileLifecycleService service){this.service=service;}
    public record Change(@NotBlank @Size(max=12) String action,@Size(max=255) String confirmName){}
    @GetMapping("/usage") Object usage(){return ApiResponse.ok(service.usage());}
    @GetMapping("/trash") Object trash(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int size){return ApiResponse.ok(service.trash(page,size));}
    @GetMapping("/{id}/references") Object references(@PathVariable String id){return ApiResponse.ok(service.references(id));}
    @PostMapping("/{id}/lifecycle") @PreAuthorize("hasAuthority('file:read') and hasAuthority('file:write')") Object change(@PathVariable String id,@Valid @RequestBody Change request){service.change(id,request.action(),request.confirmName());return ApiResponse.ok(null);}
}
