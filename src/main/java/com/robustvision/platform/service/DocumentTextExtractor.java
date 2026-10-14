package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import javax.xml.stream.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

@Component
public class DocumentTextExtractor {
    public record Part(String locator,String text) {}
    private static final int MAX_CHARS=500_000;
    public List<Part> extract(byte[] bytes,String name){
        if(bytes.length>10*1024*1024)throw invalid("文档不能超过 10 MB");
        try {
            List<Part> result=new ArrayList<>();String lower=name.toLowerCase(Locale.ROOT);
            if(lower.endsWith(".pdf")){
                try(var pdf=Loader.loadPDF(bytes)){
                    if(pdf.isEncrypted()||!pdf.getCurrentAccessPermission().canExtractContent())throw invalid("文档已加密或不允许提取文字");
                    if(pdf.getNumberOfPages()>300)throw invalid("一次最多导入 300 页，请先拆分文档");
                    PDFTextStripper reader=new PDFTextStripper();reader.setSortByPosition(true);int total=0;
                    for(int page=1;page<=pdf.getNumberOfPages();page++){reader.setStartPage(page);reader.setEndPage(page);String text=reader.getText(pdf).trim();total+=text.length();if(total>MAX_CHARS)throw invalid("文字超过 50 万字，请先拆分文档");split(result,"第 "+page+" 页",text);}
                }
            }else if(lower.endsWith(".docx")){
                byte[] xml=null;int expanded=0;
                try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;int entries=0;
                    while((entry=zip.getNextEntry())!=null){if(++entries>2000)throw invalid("文档包含过多内部文件");byte[] data=zip.readNBytes(20*1024*1024+1);expanded+=data.length;if(data.length>20*1024*1024||expanded>50*1024*1024)throw invalid("文档解压后过大");if(entry.getName().equals("word/document.xml"))xml=data;}
                }
                if(xml==null)throw invalid("不是有效的 Word .docx 文档");
                XMLInputFactory factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);factory.setXMLResolver((publicId,systemId,base,namespace)->{throw new XMLStreamException("External entities disabled");});
                XMLStreamReader reader=factory.createXMLStreamReader(new ByteArrayInputStream(xml));StringBuilder text=new StringBuilder();int paragraph=0,total=0;boolean inText=false;
                try {while(reader.hasNext()){int event=reader.next();if(event==XMLStreamConstants.DTD||event==XMLStreamConstants.ENTITY_REFERENCE)throw invalid("文档含不支持的 XML 内容");if(event==XMLStreamConstants.START_ELEMENT&&reader.getLocalName().equals("t"))inText=true;if(event==XMLStreamConstants.CHARACTERS&&inText){text.append(reader.getText());total+=reader.getTextLength();if(total>MAX_CHARS)throw invalid("文字超过 50 万字，请先拆分文档");}if(event==XMLStreamConstants.END_ELEMENT){String local=reader.getLocalName();if(local.equals("t"))inText=false;if(local.equals("p")){paragraph++;split(result,"段落 "+paragraph,text.toString().trim());text.setLength(0);}}}}finally{reader.close();}
            }else throw invalid("请选择 PDF 或 Word .docx 文档");
            if(result.isEmpty())throw invalid("没有提取到文字。扫描版 PDF 请先进行 OCR，再导入可搜索的 PDF");
            return result;
        }catch(BusinessException e){throw e;}catch(Exception e){throw invalid("文档无法读取，请确认文件完整、未加密，并使用 PDF 或 .docx 格式");}
    }
    private void split(List<Part> out,String locator,String text){for(int start=0;start<text.length();start+=4000){if(out.size()>=2000)throw invalid("文档段落过多，请拆分后导入");out.add(new Part(locator+(text.length()>4000?" · 片段 "+(start/4000+1):""),text.substring(start,Math.min(text.length(),start+4000))));}}
    private BusinessException invalid(String message){return new BusinessException(HttpStatus.BAD_REQUEST,"DOCUMENT_INVALID",message);}
}
