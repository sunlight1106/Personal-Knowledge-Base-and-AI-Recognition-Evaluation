package com.robustvision.platform.controller;

import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/operations")
public class OperationsController {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ConfigurableApplicationContext context;
    private final CurrentUserService current;private final RuntimeHealthService health;
    public OperationsController(CurrentUserService current,RuntimeHealthService health){this.current=current;this.health=health;}
    @GetMapping public Object status(){current.requireSuperAdmin();return ApiResponse.ok(health.snapshot());}
    @GetMapping("/startup") public Object startup(){
        current.requireSuperAdmin();
        if(!(context.getApplicationStartup() instanceof org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup startup))return ApiResponse.ok(java.util.List.of());
        return ApiResponse.ok(startup.getBufferedTimeline().getEvents().stream().sorted(java.util.Comparator.comparing(org.springframework.boot.context.metrics.buffering.StartupTimeline.TimelineEvent::getDuration).reversed()).limit(30).map(event->{
            String bean="";for(var tag:event.getStartupStep().getTags())if(tag.getKey().equals("beanName"))bean=tag.getValue();
            return java.util.Map.of("name",event.getStartupStep().getName(),"bean",bean,"durationMs",event.getDuration().toMillis());
        }).toList());
    }
}
