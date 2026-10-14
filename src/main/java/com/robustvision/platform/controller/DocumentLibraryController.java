package com.robustvision.platform.controller;
import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/v1/research/documents") @PreAuthorize("hasAuthority('research:use')")
public class DocumentLibraryController {
    private final DocumentLibraryService service;private final FileService files;
    public DocumentLibraryController(DocumentLibraryService service,FileService files){this.service=service;this.files=files;}
    @GetMapping public Object list(){return ApiResponse.ok(service.list());}
    @GetMapping("/passages/{id}") public Object passage(@PathVariable String id){return ApiResponse.ok(service.passage(id));}
    @PostMapping(consumes="multipart/form-data") @PreAuthorize("hasAuthority('research:use') and hasAuthority('note:write')") public Object upload(@RequestParam MultipartFile file){return ApiResponse.ok(service.upload(file));}
    @GetMapping("/{id}") public Object read(@PathVariable String id){return ApiResponse.ok(service.parts(id));}
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('research:use') and hasAuthority('note:write')") public Object delete(@PathVariable String id){service.delete(id);return ApiResponse.ok(null);}
    @GetMapping("/{id}/original") public ResponseEntity<byte[]> original(@PathVariable String id){var f=service.original(id);return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(f.getOriginalName(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(f.getContentType())).body(files.readBytes(f));}
}
