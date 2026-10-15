package com.robustvision.platform.config;

import com.robustvision.platform.service.RuntimeHealthService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class RuntimeMetricsFilter extends OncePerRequestFilter {
    private final RuntimeHealthService health;
    public RuntimeMetricsFilter(RuntimeHealthService health){this.health=health;}
    @Override protected boolean shouldNotFilter(HttpServletRequest request){
        String path=request.getRequestURI();
        return !path.startsWith("/api/")||path.contains("/events")||path.contains("/stream")||path.equals("/api/v1/operations");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        long start=System.nanoTime();int status=500;
        try{chain.doFilter(request,response);status=response.getStatus();}
        finally{health.record(System.currentTimeMillis()/1000,System.nanoTime()-start,status);}
    }
}
