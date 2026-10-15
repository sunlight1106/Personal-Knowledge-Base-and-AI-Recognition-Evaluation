package com.robustvision.platform.service;

import java.util.*;

/** Extracts original contiguous text; never fabricates or rewrites a source. */
public final class PassageSelection {
    private PassageSelection(){}
    public record Passage(int start,int end,String text){}
    public static List<Passage> select(String body,String query,int budget){
        if(body==null||body.isBlank())return List.of();
        if(body.length()<=budget)return List.of(new Passage(0,body.length(),body));
        var terms=NaturalQuery.terms(query);String lower=body.toLowerCase(Locale.ROOT);
        int window=budget/2;TreeSet<Integer> starts=new TreeSet<>();starts.add(0);
        for(String term:terms){int at=lower.indexOf(term),hits=0;while(at>=0&&hits++<12){starts.add(Math.max(0,Math.min(at-window/3,body.length()-window)));at=lower.indexOf(term,at+term.length());}}
        var ranked=starts.stream().sorted(Comparator.<Integer>comparingDouble(start->score(lower.substring(start,Math.min(body.length(),start+window)),terms)).reversed().thenComparingInt(i->i)).toList();
        List<Passage> result=new ArrayList<>();
        for(int start:ranked){int end=Math.min(body.length(),start+window);if(result.stream().anyMatch(p->start<p.end()&&end>p.start()))continue;
            result.add(new Passage(start,end,body.substring(start,end)));if(result.size()==2)break;}
        result.sort(Comparator.comparingInt(Passage::start));return List.copyOf(result);
    }
    private static double score(String text,List<String> terms){double score=0;for(String term:terms)if(text.contains(term))score+=1+Math.log(1+term.length());return score;}
    public static String context(String body,String query,int budget){return select(body,query,budget).stream().map(p->"[原文片段 "+(p.start()+1)+"–"+p.end()+"]\n"+p.text()).collect(java.util.stream.Collectors.joining("\n\n"));}
}
