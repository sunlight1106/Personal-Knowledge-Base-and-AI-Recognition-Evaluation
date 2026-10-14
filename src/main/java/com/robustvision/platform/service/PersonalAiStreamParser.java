package com.robustvision.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.robustvision.platform.domain.AiProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Bounded SSE reader. Holds a secret-sized tail so credentials split across events cannot leak. */
final class PersonalAiStreamParser {
    static PersonalAiTransport.Completion read(InputStream input, AiProvider provider, ObjectMapper mapper,
            String secret, Consumer<String> delta, Runnable check) throws IOException {
        StringBuilder text=new StringBuilder(); int emitted=0,total=0; boolean complete=false; Long in=null,out=null;
        ByteArrayOutputStream line=new ByteArrayOutputStream();
        while(true){
            check.run(); int c=input.read(); if(c<0)break;
            if(++total>2*1024*1024)throw PersonalAiEndpointPolicy.unavailable();
            if(c!='\n'){if(line.size()>65536)throw PersonalAiEndpointPolicy.unavailable();line.write(c);continue;}
            String raw=line.toString(StandardCharsets.UTF_8).trim();line.reset();
            if(!raw.startsWith("data:"))continue;
            String data=raw.substring(5).trim();if(data.equals("[DONE]"))break;if(data.isEmpty())continue;
            JsonNode root=mapper.readTree(data);if(root.has("error"))throw PersonalAiEndpointPolicy.unavailable();
            String fragment="";
            switch(provider.protocol()){
                case OPENAI_CHAT -> {var choice=root.path("choices").path(0);var d=choice.path("delta");
                    if(d.hasNonNull("refusal"))throw PersonalAiEndpointPolicy.unavailable();
                    fragment=d.path("content").asText("");
                    if(choice.hasNonNull("finish_reason")){if(!"stop".equals(choice.path("finish_reason").asText()))throw PersonalAiEndpointPolicy.unavailable();complete=true;}
                    if(root.path("usage").isObject()){in=count(root.path("usage").path("prompt_tokens"));out=count(root.path("usage").path("completion_tokens"));}
                }
                case ANTHROPIC -> {String type=root.path("type").asText();
                    if(type.equals("error"))throw PersonalAiEndpointPolicy.unavailable();
                    if(type.equals("message_start")){var u=root.path("message").path("usage");in=count(u.path("input_tokens"));for(String key:new String[]{"cache_read_input_tokens","cache_creation_input_tokens"})if(u.has(key)&&in!=null){Long n=count(u.path(key));in=n==null?null:in+n;}}
                    if(type.equals("content_block_delta")&&root.path("delta").path("type").asText().equals("text_delta"))fragment=root.path("delta").path("text").asText("");
                    if(type.equals("message_delta")){if(!root.path("delta").path("stop_reason").asText().equals("end_turn"))throw PersonalAiEndpointPolicy.unavailable();complete=true;out=count(root.path("usage").path("output_tokens"));}
                    if(type.equals("message_stop"))break;
                }
                case GEMINI -> {var candidate=root.path("candidates").path(0);
                    if(root.path("promptFeedback").hasNonNull("blockReason"))throw PersonalAiEndpointPolicy.unavailable();
                    for(var p:candidate.path("content").path("parts"))if(!p.path("thought").asBoolean(false))fragment+=p.path("text").asText("");
                    if(candidate.has("finishReason")){if(!candidate.path("finishReason").asText().equals("STOP"))throw PersonalAiEndpointPolicy.unavailable();complete=true;}
                    if(root.has("usageMetadata")){var u=root.path("usageMetadata");in=count(u.path("promptTokenCount"));out=count(u.path("candidatesTokenCount"));if(u.has("thoughtsTokenCount")&&out!=null){Long n=count(u.path("thoughtsTokenCount"));out=n==null?null:out+n;}}
                }
            }
            text.append(fragment);
            if(text.length()>PersonalAiTransport.MAX_OUTPUT_CHARS||(!secret.isEmpty()&&text.indexOf(secret)>=0))throw new PersonalAiUpstreamFailure(in,out);
            int safe=Math.max(0,text.length()-secret.length());if(safe>emitted){delta.accept(text.substring(emitted,safe));emitted=safe;}
        }
        check.run();if(!complete||text.toString().isBlank())throw new PersonalAiUpstreamFailure(in,out);
        if(text.length()>emitted)delta.accept(text.substring(emitted));
        return new PersonalAiTransport.Completion(text.toString().trim(),in,out);
    }
    private static Long count(JsonNode n){return n.isIntegralNumber()&&n.canConvertToLong()&&n.asLong()>=0&&n.asLong()<=9_007_199_254_740_991L?n.asLong():null;}
}
