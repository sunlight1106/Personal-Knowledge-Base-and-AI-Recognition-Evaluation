package com.robustvision.platform.service;

import org.apache.commons.math3.linear.*;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded latent semantic analysis over already-authorized candidates. No persistent shared index. */
public final class LocalSemanticRanker {
    private LocalSemanticRanker(){}
    public record Match(int index,double score){}
    private static final Pattern TOKEN=Pattern.compile("[a-z][a-z0-9_]{1,35}|[\\p{IsHan}]{2,}");
    private static final Set<String> STOP=Set.of("the","and","for","with","this","that","from","are","was","have","not","you","your","可以","一个","我们","这个","进行","以及");
    static Map<String,Integer> tokens(String text){
        Map<String,Integer> out=new HashMap<>();var m=TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while(m.find()){String word=m.group();if(word.charAt(0)>127){for(int i=0;i<word.length()-1;i++)add(out,word.substring(i,i+2));}else add(out,word);}
        return out;
    }
    private static void add(Map<String,Integer> out,String token){if(!STOP.contains(token))out.merge(token,1,Integer::sum);}
    public static List<Match> rank(String query,List<String> documents){
        if(documents.isEmpty())return List.of();if(documents.size()>160)throw new IllegalArgumentException("Too many candidates");
        var q=tokens(query+" "+String.join(" ",NaturalQuery.terms(query)));
        List<Map<String,Integer>> docs=documents.stream().map(s->tokens(s.substring(0,Math.min(s.length(),6500)))).toList();
        Map<String,Integer> df=new HashMap<>();docs.forEach(d->d.keySet().forEach(t->df.merge(t,1,Integer::sum)));
        var terms=df.keySet().stream().sorted(Comparator.<String>comparingInt(t->q.containsKey(t)?Integer.MAX_VALUE:df.get(t)).reversed().thenComparing(t->t)).limit(1024).toList();
        if(terms.isEmpty()||q.keySet().stream().noneMatch(df::containsKey))return List.of();
        int n=docs.size(),m=terms.size();double[][] a=new double[m][n];double[] queryVector=new double[m];
        for(int t=0;t<m;t++){String term=terms.get(t);double idf=1+Math.log((1d+n)/(1+df.get(term)));
            queryVector[t]=q.containsKey(term)?(1+Math.log(q.get(term)))*idf:0;
            for(int d=0;d<n;d++)if(docs.get(d).containsKey(term))a[t][d]=(1+Math.log(docs.get(d).get(term)))*idf;}
        double qnorm=norm(queryVector);if(qnorm==0)return List.of();for(int t=0;t<m;t++)queryVector[t]/=qnorm;
        for(int d=0;d<n;d++){double norm=0;for(int t=0;t<m;t++)norm+=a[t][d]*a[t][d];norm=Math.sqrt(norm);if(norm>0)for(int t=0;t<m;t++)a[t][d]/=norm;}
        var matrix=new Array2DRowRealMatrix(a,false);var svd=new SingularValueDecomposition(matrix);
        int dimensions=Math.max(1,Math.min(Math.min(24,svd.getRank()),Math.max(2,n/3)));
        var u=svd.getU();var vMatrix=svd.getV();var singularValues=svd.getSingularValues();double[] latentQuery=new double[dimensions];
        for(int k=0;k<dimensions;k++)for(int t=0;t<m;t++)latentQuery[k]+=u.getEntry(t,k)*queryVector[t];
        double latentNorm=norm(latentQuery);List<Match> result=new ArrayList<>();
        for(int d=0;d<n;d++){double exact=0,product=0,squared=0;for(int t=0;t<m;t++)exact+=queryVector[t]*a[t][d];
            for(int k=0;k<dimensions;k++){double v=vMatrix.getEntry(d,k)*singularValues[k];product+=v*latentQuery[k];squared+=v*v;}
            double latent=latentNorm>1e-10&&squared>1e-10?Math.max(0,product/(latentNorm*Math.sqrt(squared))):0;
            double score=.55*exact+.45*latent;if(score>=.08)result.add(new Match(d,score));}
        result.sort(Comparator.comparingDouble(Match::score).reversed().thenComparingInt(Match::index));return result;
    }
    private static double norm(double[] vector){double sum=0;for(double v:vector)sum+=v*v;return Math.sqrt(sum);}
}
