package com.robustvision.platform.controller;
import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.WorkspaceJobService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/v1/workspace-jobs") @PreAuthorize("isAuthenticated()")
public class WorkspaceJobController {
 private final WorkspaceJobService service;
 public WorkspaceJobController(WorkspaceJobService service){this.service=service;}
 @GetMapping public Object list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="ALL") String status){return ApiResponse.ok(service.list(page,status));}
 @GetMapping("/{id}") public Object get(@PathVariable String id){return ApiResponse.ok(service.get(id));}
 @DeleteMapping("/{id}") public Object delete(@PathVariable String id){service.delete(id);return ApiResponse.ok(null);}
 @PostMapping("/{id}/{action}") public Object action(@PathVariable String id,@PathVariable String action){return ApiResponse.ok(service.action(id,action));}
 @PostMapping(value="/documents",consumes="multipart/form-data") public Object upload(@RequestParam MultipartFile file,@RequestParam(defaultValue="true") boolean ocr,@RequestParam String requestId){return ApiResponse.ok(service.submit(file,ocr,requestId));}
}
