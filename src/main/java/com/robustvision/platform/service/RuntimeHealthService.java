package com.robustvision.platform.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Aggregate counters only: never stores URLs, account IDs, bodies, or credentials. */
@Service
public class RuntimeHealthService {
    public record Alert(String id,String severity,String title,String action,String url) {}
    public record Snapshot(Instant checkedAt,long requests,long errors,double averageMs,double p95Ms,
                           long freeBytes,long totalBytes,long failedJobs,List<Alert> alerts) {}
    private static final int WINDOW=300;
    private final long[] seconds=new long[WINDOW],counts=new long[WINDOW],errors=new long[WINDOW],nanos=new long[WINDOW];
    private final long[][] buckets=new long[WINDOW][8];
    private static final long[] LIMITS={50,100,250,500,1000,2000,5000,Long.MAX_VALUE};
    private final JdbcTemplate jdbc;private final BackupStatusService backup;
    public RuntimeHealthService(JdbcTemplate jdbc,BackupStatusService backup){this.jdbc=jdbc;this.backup=backup;}
    public synchronized void record(long epochSecond,long durationNanos,int status){
        int n=Math.floorMod(epochSecond,WINDOW);
        if(seconds[n]!=epochSecond){seconds[n]=epochSecond;counts[n]=errors[n]=nanos[n]=0;Arrays.fill(buckets[n],0);}
        counts[n]++;if(status>=500)errors[n]++;nanos[n]+=Math.max(0,durationNanos);
        long ms=Math.max(0,durationNanos/1_000_000);int b=0;while(ms>LIMITS[b])b++;buckets[n][b]++;
    }
    public synchronized double[] sample(long now){
        long count=0,error=0,duration=0;long[] histogram=new long[8];
        for(int i=0;i<WINDOW;i++)if(seconds[i]>now-WINDOW&&seconds[i]<=now){count+=counts[i];error+=errors[i];duration+=nanos[i];for(int j=0;j<8;j++)histogram[j]+=buckets[i][j];}
        long target=(long)Math.ceil(count*.95),cumulative=0;double p95=0;
        if(count>0)for(int j=0;j<8;j++){cumulative+=histogram[j];if(cumulative>=target){p95=j==7?-1:LIMITS[j];break;}}
        return new double[]{count,error,count==0?0:duration/1_000_000d/count,p95};
    }
    public Snapshot snapshot(){
        var now=Instant.now();var sample=sample(now.getEpochSecond());List<Alert> alerts=new ArrayList<>();
        if(sample[0]>=20&&sample[1]/sample[0]>=.05)alerts.add(new Alert("http-errors","danger","请求失败较多","检查服务日志中的错误码与时间，不要上传密钥或正文。","/app/admin"));
        if(sample[0]>=20&&(sample[3]<0||sample[3]>=2000))alerts.add(new Alert("http-slow","warning","请求响应偏慢","检查正在运行的导入任务与服务器资源。","/app/research?tab=tasks"));
        long free=-1,total=-1;
        try{var disk=Files.getFileStore(Path.of(System.getProperty("user.dir")));free=disk.getUsableSpace();total=disk.getTotalSpace();
            if(free<1_073_741_824L||total>0&&free/(double)total<.05)alerts.add(new Alert("storage-low","danger","服务磁盘空间不足","整理过期文件，并检查数据库与对象存储所在磁盘。","/app/research?tab=trash"));
        }catch(Exception ignored){alerts.add(new Alert("storage-unknown","warning","磁盘状态暂不可用","在部署机器上检查可用空间。","/docs"));}
        long failed=jdbc.queryForObject("SELECT COUNT(*) FROM workspace_job WHERE status='FAILED' AND updated_at>=?",Long.class,java.sql.Timestamp.from(now.minusSeconds(86400)));
        if(failed>0)alerts.add(new Alert("jobs-failed","warning","最近一天有任务失败","用户可在任务与同步中查看自己的失败原因并重试。","/app/research?tab=tasks"));
        var status=backup.read();
        if(status.isEmpty()||!status.containsKey("lastSuccess"))alerts.add(new Alert("backup-missing","warning","尚无成功备份记录","配置加密备份，并安排一次恢复演练。","/app/research?tab=trash"));
        else if(status.containsKey("lastError")||status.containsKey("warning"))alerts.add(new Alert("backup-warning","danger","备份需要处理","检查最近备份和恢复演练结果。","/app/research?tab=trash"));
        return new Snapshot(now,(long)sample[0],(long)sample[1],sample[2],sample[3],free,total,failed,List.copyOf(alerts));
    }
}
