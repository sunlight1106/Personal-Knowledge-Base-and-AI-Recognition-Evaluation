package com.robustvision.platform.service;

import okhttp3.Call;
import org.springframework.stereotype.Component;
import com.robustvision.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class PersonalAiStreams {
    private final ConcurrentHashMap<String,Running> active=new ConcurrentHashMap<>();
    private final java.util.LinkedHashMap<String,Long> earlyCancellations=new java.util.LinkedHashMap<>();
    public static class Running {
        final long created=System.currentTimeMillis();final long owner;final AtomicBoolean cancelled=new AtomicBoolean();volatile Call call;
        Running(long owner){this.owner=owner;}
        public void attach(Call call){this.call=call;if(cancelled.get())call.cancel();}
        public void check(){if(cancelled.get())throw new BusinessException(HttpStatus.CONFLICT,"PERSONAL_AI_CANCELLED","已停止本次生成；已产生的用量仍以供应商账单为准");}
        public void cancel(){cancelled.set(true);Call current=call;if(current!=null)current.cancel();}
    }
    public synchronized Running begin(long owner,String token){
        pruneCancellations();
        if(earlyCancellations.remove(owner+":"+token)!=null){Running task=new Running(owner);task.cancel();task.check();}
        active.entrySet().removeIf(e->{if(System.currentTimeMillis()-e.getValue().created>180000){e.getValue().cancel();return true;}return false;});
        if(active.size()>=100)throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"PERSONAL_AI_BUSY","生成队列繁忙，请稍后再试");
        if(active.values().stream().filter(r->r.owner==owner).count()>=2)throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"PERSONAL_AI_BUSY","已有生成任务，请先结束后再试");
        Running task=new Running(owner);if(active.putIfAbsent(token,task)!=null)throw new BusinessException(HttpStatus.CONFLICT,"PERSONAL_AI_ALREADY_RUNNING","这个请求已经提交");return task;
    }
    public synchronized void cancel(long owner,String token){
        Running task=active.get(token);
        if(task!=null){if(task.owner==owner)task.cancel();return;}
        if(token==null||token.length()>200)return;
        pruneCancellations();
        if(earlyCancellations.size()>=1000)earlyCancellations.remove(earlyCancellations.keySet().iterator().next());
        earlyCancellations.put(owner+":"+token,System.currentTimeMillis());
    }
    private void pruneCancellations(){earlyCancellations.values().removeIf(at->System.currentTimeMillis()-at>180000);}
    public void end(String token,Running task){active.remove(token,task);}
}
