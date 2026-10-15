package com.robustvision.platform.service;
import com.robustvision.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

/** Local OCR only. Arguments are fixed; no user-supplied executable, language or path. */
@Component
public class LocalOcrService {
 public String read(PDDocument document,int page,Runnable check) {
  Path temp=null;Process process=null;
  try {
   check.run();temp=Files.createTempDirectory("pkb-ocr-");var box=document.getPage(page).getCropBox();
   double points=Math.max(1,(double)box.getWidth()*box.getHeight());float dpi=(float)Math.min(180,72*Math.sqrt(8_000_000/points));
   if(dpi<20)throw invalid("页面尺寸过大，请缩小后重试");
   var renderer=new PDFRenderer(document);renderer.setSubsamplingAllowed(true);
   var image=renderer.renderImageWithDPI(page,dpi,org.apache.pdfbox.rendering.ImageType.RGB);
   Path png=temp.resolve("page.png"),text=temp.resolve("text.txt"),log=temp.resolve("error.log");ImageIO.write(image,"png",png.toFile());image.flush();
   process=new ProcessBuilder("tesseract",png.toString(),"stdout","-l","eng+chi_sim","--psm","3").redirectOutput(text.toFile()).redirectError(log.toFile()).start();
   long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
   while(!process.waitFor(250,TimeUnit.MILLISECONDS)){check.run();if(System.nanoTime()>end)throw invalid("本页文字识别超时，请拆分或压缩文档后重试");}
   check.run();if(process.exitValue()!=0)throw invalid("本地 OCR 无法处理此页，请检查图片质量和服务环境");
   if(Files.size(text)>100_000)throw invalid("单页识别文字过多，请检查文件");return Files.readString(text).trim();
  }catch(java.io.IOException e){throw invalid("本地 OCR 未就绪。Docker 部署已包含中文和英文识别环境");}
  catch(InterruptedException e){Thread.currentThread().interrupt();throw invalid("文字识别已中断，可在任务中心重试");}
  finally{if(process!=null&&process.isAlive())process.destroyForcibly();if(temp!=null)try(var paths=Files.list(temp)){paths.forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}catch(Exception ignored){}if(temp!=null)try{Files.deleteIfExists(temp);}catch(Exception ignored){}}
 }
 private BusinessException invalid(String text){return new BusinessException(HttpStatus.BAD_REQUEST,"OCR_FAILED",text);}
}
