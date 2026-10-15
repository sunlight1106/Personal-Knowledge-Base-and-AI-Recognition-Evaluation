package com.robustvision.platform.controller;

import com.robustvision.platform.common.ApiResponse;
import com.robustvision.platform.service.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/workspace/getting-started")
public class GettingStartedController {
    private final CurrentUserService current;private final JdbcTemplate jdbc;
    public GettingStartedController(CurrentUserService current,JdbcTemplate jdbc){this.current=current;this.jdbc=jdbc;}
    public record Step(String id,String title,String description,String url,boolean completed){}
    @GetMapping public Object steps(){
        var u=current.requireCurrent();List<Step> steps=new ArrayList<>();
        if(current.hasPermission(u,"note:write")&&current.hasPermission(u,"note:read"))steps.add(new Step("note","写下第一篇笔记","记录一个想法，也可以从模板开始。","/app/notes/new?template=study",exists("SELECT COUNT(*) FROM note WHERE owner_id=? AND deleted_at IS NULL",u.getId())));
        if(current.hasPermission(u,"vocabulary:use"))steps.add(new Step("vocabulary","完成一次词汇练习","选择词书，开始一次场景与搭配练习。","/app/vocabulary",exists("SELECT COUNT(*) FROM learning_event WHERE owner_id=? AND kind='WORD'",u.getId())));
        if(current.hasPermission(u,"research:use")&&current.hasPermission(u,"note:write"))steps.add(new Step("document","导入一份学习资料","上传 PDF 或 Word，阅读并保存摘录。","/app/research?tab=documents",exists("SELECT COUNT(*) FROM study_document WHERE owner_id=?",u.getId())));
        if(current.hasPermission(u,"research:use"))steps.add(new Step("review","制作一张复习卡","把一个知识点变成问题，之后定期回顾。","/app/research?tab=cards",exists("SELECT COUNT(*) FROM learning_record WHERE owner_id=? AND kind='CARD'",u.getId())));
        return ApiResponse.ok(steps);
    }
    private boolean exists(String sql,long owner){return jdbc.queryForObject(sql,Long.class,owner)>0;}
}
