package com.robustvision.platform.config;
import com.robustvision.platform.service.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class OperationHistoryConfig implements WebMvcConfigurer {
 private final CurrentUserService current;private final OperationHistory history;
 public OperationHistoryConfig(CurrentUserService current,OperationHistory history){this.current=current;this.history=history;}
 @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(new HandlerInterceptor(){
  @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){
   String path=req.getRequestURI(),method=req.getMethod(),kind=null,title=null;
   if(method.equals("POST")&&path.matches("/api/v1/vocabulary/offline/[^/]+/sync")){kind="SYNC";title="离线词汇同步";}
   else if(method.equals("POST")&&path.equals("/api/v1/vocabulary/books/import")){kind="IMPORT";title="导入词书";}
   else if(method.equals("POST")&&path.equals("/api/v1/vocabulary/backup/restore")){kind="IMPORT";title="恢复词汇备份";}
   else if(method.equals("GET")&&(path.equals("/api/v1/vocabulary/backup")||path.matches("/api/v1/notes/[^/]+/export")||path.matches("/api/v1/research/documents/[^/]+/original"))){kind="EXPORT";title="导出与下载资料";}
   if(kind!=null)try{req.setAttribute("workspaceOperation",history.start(current.requireCurrent().getId(),kind,title));}catch(Exception ignored){/* History failure must not repeat or block the business operation. */}
   return true;
  }
  @Override public void afterCompletion(HttpServletRequest req,HttpServletResponse res,Object handler,Exception ex){var id=req.getAttribute("workspaceOperation");if(id!=null)try{history.finish(id.toString(),ex==null&&res.getStatus()<400);}catch(Exception ignored){}}
 }).addPathPatterns("/api/v1/**");}
}
