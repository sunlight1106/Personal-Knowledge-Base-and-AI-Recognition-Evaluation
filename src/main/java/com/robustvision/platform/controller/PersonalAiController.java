package com.robustvision.platform.controller;

import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.domain.AiProvider;
import com.robustvision.platform.dto.PersonalAiDtos.*;
import com.robustvision.platform.service.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/personal-ai")
@PreAuthorize("isAuthenticated()")
public class PersonalAiController {
    private final PersonalAiSettingsService settings;
    private final PersonalAiService ai;
    public PersonalAiController(PersonalAiSettingsService settings, PersonalAiService ai) { this.settings = settings; this.ai = ai; }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> malformedRequest() {
        // Never forward Jackson exception text: it can contain a credential-bearing source fragment.
        return ResponseEntity.badRequest().body(ApiResponse.failure("PERSONAL_AI_REQUEST_INVALID", "请求格式无效"));
    }
    @GetMapping("/providers") public ApiResponse<List<ProviderView>> providers() { return ApiResponse.ok(settings.providers()); }
    @GetMapping("/settings") public ApiResponse<List<SettingView>> settings() { return ApiResponse.ok(settings.list()); }
    @PutMapping("/settings/{provider}") public ApiResponse<SettingView> save(@PathVariable AiProvider provider, @Valid @RequestBody SettingRequest request) {
        return ApiResponse.ok(settings.save(provider, request));
    }
    @DeleteMapping("/settings/{provider}") public ApiResponse<Void> delete(@PathVariable AiProvider provider) { settings.delete(provider); return ApiResponse.ok(null); }
    @GetMapping("/usage") public ApiResponse<List<UsageView>> usage() { return ApiResponse.ok(settings.usage()); }
    @PostMapping("/preview") @PreAuthorize("hasAuthority('personal-ai:use')")
    public ApiResponse<PreviewView> preview(@Valid @RequestBody PreviewRequest request) { return ApiResponse.ok(ai.preview(request)); }
    @PostMapping("/execute") @PreAuthorize("hasAuthority('personal-ai:use')")
    public ApiResponse<ResultView> execute(@Valid @RequestBody ExecuteRequest request) { return ApiResponse.ok(ai.execute(request)); }
    @org.springframework.beans.factory.annotation.Autowired private PersonalAiStreams streams;
    @org.springframework.beans.factory.annotation.Autowired private CurrentUserService current;
    @org.springframework.beans.factory.annotation.Autowired private com.fasterxml.jackson.databind.ObjectMapper mapper;
    @PostMapping("/stream") @PreAuthorize("hasAuthority('personal-ai:use')")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> stream(@Valid @RequestBody ExecuteRequest request){
        long owner=current.requireCurrent().getId();var task=streams.begin(owner,request.previewToken());
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body=output->{
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            java.util.function.Consumer<Object> send=value->{try{output.write(mapper.writeValueAsBytes(value));output.write(10);output.flush();}catch(java.io.IOException e){task.cancel();throw new java.io.UncheckedIOException(e);}};
            try{var result=ai.executeStream(request,text->send.accept(java.util.Map.of("type","delta","text",text)),task);send.accept(java.util.Map.of("type","result","data",result));}
            catch(com.robustvision.platform.common.BusinessException e){send.accept(java.util.Map.of("type","error","code",e.getCode(),"message",e.getMessage()));}
            catch(Exception e){send.accept(java.util.Map.of("type","error","code","PERSONAL_AI_INCOMPLETE","message","生成中断，已收到的文字可能不完整。请检查后再决定是否重试。"));}
            finally{task.cancel();streams.end(request.previewToken(),task);org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        };
        return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Accel-Buffering","no").contentType(org.springframework.http.MediaType.parseMediaType("application/x-ndjson")).body(body);
    }
    @PostMapping("/streams/{token}/cancel") public ApiResponse<Void> cancelStream(@PathVariable String token){streams.cancel(current.requireCurrent().getId(),token);return ApiResponse.ok(null);}
}
