package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robustvision.platform.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Service
public class CodeLabRunner {
    private final ObjectMapper mapper;
    private final String url,token;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public CodeLabRunner(ObjectMapper mapper,@Value("${CODE_LAB_SERVICE_URL:http://code-lab:8091}") String url,
                         @Value("${CODE_LAB_SERVICE_TOKEN:}") String token){this.mapper=mapper;this.url=url;this.token=token;}
    public JsonNode run(JsonNode payload){
        if(token.length()<32)throw unavailable();
        try{
            var call=HttpRequest.newBuilder(URI.create(url+"/execute")).timeout(Duration.ofSeconds(8))
                    .header("Authorization","Bearer "+token).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(payload))).build();
            var response=client.send(call,HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;try(var body=response.body()){bytes=body.readNBytes(1_800_001);}
            if(response.statusCode()==429)throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,"CODE_LAB_BUSY","执行环境忙碌，请稍后再试。");
            if(response.statusCode()!=200||bytes.length>1_800_000)throw unavailable();
            JsonNode result=mapper.readTree(bytes);
            if(result==null||!result.isObject()||!result.path("steps").isArray()
                    ||!java.util.Set.of("completed","error","limited").contains(result.path("status").asText()))throw unavailable();
            return result;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
        catch(IOException|IllegalArgumentException e){throw unavailable();}
    }
    private BusinessException unavailable(){return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"CODE_LAB_UNAVAILABLE","代码执行环境暂未就绪，请稍后重试。");}
}
