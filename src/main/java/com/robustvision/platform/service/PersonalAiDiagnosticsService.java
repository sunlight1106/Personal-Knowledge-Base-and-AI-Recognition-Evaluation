package com.robustvision.platform.service;
import com.robustvision.platform.common.BusinessException;
import com.robustvision.platform.domain.*;
import com.robustvision.platform.repository.PersonalAiSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
@Service
public class PersonalAiDiagnosticsService {
 @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.jdbc.core.JdbcTemplate db;
 private final CurrentUserService current;private final PersonalAiSettingRepository settings;private final PersonalAiTransport transport;private final SecretEncryptionService encryption;private final PersonalAiEndpointPolicy endpoints;private final PersonalAiRateLimiter limits;private final PersonalAiPersistenceService persistence;private final boolean remote;
 private final Map<String,Pending> pending=new HashMap<>();
 private record Pending(long owner,String setting,long revision,String mode,Instant expires){}
 public record Preview(String token,String mode,String model,String endpoint,String request,Instant expiresAt){}
 public record Result(boolean success,String code,String message,long milliseconds,List<String> models,Long inputTokens,Long outputTokens,String usageStatus){}
 public PersonalAiDiagnosticsService(CurrentUserService current,PersonalAiSettingRepository settings,PersonalAiTransport transport,SecretEncryptionService encryption,PersonalAiEndpointPolicy endpoints,PersonalAiRateLimiter limits,PersonalAiPersistenceService persistence,@Value("${app.personal-ai.remote-enabled:false}") boolean remote){this.current=current;this.settings=settings;this.transport=transport;this.encryption=encryption;this.endpoints=endpoints;this.limits=limits;this.persistence=persistence;this.remote=remote;}
 private BusinessException bad(String code,String message){return new BusinessException(HttpStatus.BAD_REQUEST,code,message);}
 public Preview preview(AiProvider provider,String mode){
  Long owner=current.requireCurrent().getId();if(!Set.of("MODELS","CONNECTION","VISION").contains(mode))throw bad("DIAGNOSTIC_INVALID","检查类型无效");
  var setting=settings.findByOwnerIdAndProvider(owner,provider).filter(s->s.isEnabled()&&s.getEncryptedKey()!=null&&!s.getEncryptedKey().isBlank()).orElseThrow(()->bad("PERSONAL_AI_CONFIG_REQUIRED","先保存并启用自己的供应商配置"));
  if(!remote)throw bad("PERSONAL_AI_REMOTE_DISABLED","部署者尚未开启外部模型调用。请先启用 PERSONAL_AI_REMOTE_ENABLED，再进行接入检查");
  if(!mode.equals("MODELS"))PersonalAiSettingsService.validateModel(provider,setting.getModel());
  String base=endpoints.validateBase(provider,setting.getBaseUrl());String token=UUID.randomUUID().toString()+UUID.randomUUID();Instant expires=Instant.now().plusSeconds(300);limits.preview(owner);
  synchronized(pending){pending.entrySet().removeIf(e->e.getValue().expires().isBefore(Instant.now())||e.getValue().owner()==owner);if(pending.size()>=256)throw bad("DIAGNOSTIC_BUSY","检查服务繁忙，请稍后再试");pending.put(token,new Pending(owner,setting.getId(),setting.getRevision(),mode,expires));}
  return new Preview(token,mode,setting.getModel(),base,mode.equals("MODELS")?"GET /models，仅查询模型目录":mode.equals("VISION")?"发送系统生成的 16×16 蓝色测试图片，请模型识别颜色。不会发送个人图片、笔记或记忆；供应商可能计费。":"仅发送固定短句 Reply with OK only.，输出上限 128 tokens。不发送笔记、记忆或文件，供应商可能计费。",expires);
 }
 public Result execute(String token,boolean confirmed){
  Long owner=current.requireCurrent().getId();if(!confirmed)throw bad("DIAGNOSTIC_CONFIRM_REQUIRED","请确认发送预览中的检查请求");Pending approved;
  synchronized(pending){approved=pending.get(token);if(approved==null||approved.owner()!=owner)throw bad("DIAGNOSTIC_EXPIRED","预览无效，请重新检查");pending.remove(token);}
  if(!approved.expires().isAfter(Instant.now()))throw bad("DIAGNOSTIC_EXPIRED","预览已经过期");var setting=settings.findById(approved.setting()).filter(s->s.getOwnerId().equals(owner)&&s.getRevision()==approved.revision()&&s.isEnabled()).orElseThrow(()->bad("DIAGNOSTIC_CHANGED","配置已更改，请重新预览"));
  long start=System.nanoTime();try(var permit=limits.acquire(owner)){
   persistence.requireActiveOwner(owner);
   String key=encryption.decrypt(setting.getEncryptedKey());
   try {
    if(approved.mode().equals("MODELS")){var models=transport.models(setting.getProvider(),setting.getBaseUrl(),key);return remember(approved,new Result(true,"MODELS_RECEIVED",models.contains(setting.getModel())?"模型目录包含当前模型。实际调用能力请再做连接测试。":"已获取首批模型；未找到当前模型不代表一定不可用，可继续连接测试。",(System.nanoTime()-start)/1000000,models,null,null,"NOT_APPLICABLE"));}
    var result=transport.execute(setting.getProvider(),approved.mode().equals("VISION")?vision(setting):transport.prepareDiagnostic(setting.getProvider(),setting.getBaseUrl(),setting.getModel()),key);String status="SAVED";
    try{persistence.recordUsage(new PersonalAiUsageEntity(owner,setting.getProvider(),setting.getModel(),"diagnostic","SUCCEEDED",result.inputTokens(),result.outputTokens(),null));}catch(RuntimeException ignored){status="UNCONFIRMED";}
    boolean visionOk=!approved.mode().equals("VISION")||result.text().toLowerCase(java.util.Locale.ROOT).matches("(?s).*(blue|蓝).*" );
    return remember(approved,new Result(visionOk,visionOk?"CONNECTED":"VISION_UNCONFIRMED",approved.mode().equals("VISION")?(visionOk?"模型正确识别了测试图片的蓝色。":"接口返回了结果，但没有正确识别测试颜色，图片能力尚未确认。"):"当前模型已返回文本，可用于文字调用。图片能力需单独验证。",(System.nanoTime()-start)/1000000,List.of(),result.inputTokens(),result.outputTokens(),status));
   }catch(BusinessException e){
    String status="NOT_APPLICABLE";
    if(PersonalAiEndpointPolicy.requestNotSent(e.getCode()))status="NOT_SENT";
    else if(!approved.mode().equals("MODELS")){
     status="SAVED";
     try{persistence.recordUsage(new PersonalAiUsageEntity(owner,setting.getProvider(),setting.getModel(),"diagnostic","FAILED",null,null,e.getCode()));}
     catch(RuntimeException ignored){status="UNCONFIRMED";}
    }
    String message=approved.mode().equals("MODELS")&&e.getCode().equals("PERSONAL_AI_UPSTREAM_FAILED")
      ?"未能取得模型目录，请检查供应商地址与网络连接；本次未发起模型生成请求。":e.getMessage();
    return remember(approved,new Result(false,e.getCode(),message,(System.nanoTime()-start)/1000000,List.of(),null,null,status));
   }
  }
 }
 public record Check(String mode,boolean success,String code,Instant checkedAt){}
 public List<Check> checks(AiProvider provider){long owner=current.requireCurrent().getId();var setting=settings.findByOwnerIdAndProvider(owner,provider).orElse(null);if(setting==null||db==null)return List.of();return db.query("SELECT mode,success,code,checked_at FROM ai_diagnostic WHERE owner_id=? AND setting_id=? AND revision=? ORDER BY mode",(r,i)->new Check(r.getString(1),r.getBoolean(2),r.getString(3),r.getTimestamp(4).toInstant()),owner,setting.getId(),setting.getRevision());}
 private Result remember(Pending p,Result result){if(db!=null){String id=p.setting()+":"+p.mode();try{int updated=db.update("UPDATE ai_diagnostic SET revision=?,success=?,code=?,checked_at=? WHERE id=? AND owner_id=?",p.revision(),result.success(),result.code(),java.sql.Timestamp.from(Instant.now()),id,p.owner());if(updated==0)db.update("INSERT INTO ai_diagnostic(id,owner_id,setting_id,revision,mode,success,code,checked_at) VALUES(?,?,?,?,?,?,?,?)",id,p.owner(),p.setting(),p.revision(),p.mode(),result.success(),result.code(),java.sql.Timestamp.from(Instant.now()));}catch(org.springframework.dao.DataAccessException ignored){/* Diagnostics remain usable when history persistence fails. */}}return result;}
 private PersonalAiTransport.Payload vision(PersonalAiSettingEntity setting){try{var image=new java.awt.image.BufferedImage(16,16,java.awt.image.BufferedImage.TYPE_INT_RGB);for(int x=0;x<16;x++)for(int y=0;y<16;y++)image.setRGB(x,y,0x0000ff);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);return transport.prepareVision(setting.getProvider(),setting.getBaseUrl(),setting.getModel(),"Identify the dominant color of this image. Answer with one color name.","What color is this image?",bytes.toByteArray(),"image/png");}catch(Exception e){throw PersonalAiEndpointPolicy.unavailable();}}
}
