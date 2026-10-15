package com.robustvision.platform.service;
import java.util.*;
import java.util.regex.Pattern;

/** Deterministic local query expansion. No text or credentials leave the deployment. */
public final class NaturalQuery {
 private NaturalQuery(){}
 private static final List<List<String>> CONCEPTS=List.of(
  List.of("过拟合","overfitting","overfit","正则化","regularization","dropout","early stopping","早停"),
  List.of("欠拟合","underfit","underfitting","模型容量"),List.of("梯度","gradient","反向传播","backpropagation"),
  List.of("学习率","learning rate","步长","lr scheduler"),List.of("神经网络","neural network","感知机"),
  List.of("注意力","attention","transformer"),List.of("大模型","llm","语言模型","language model"),
  List.of("微调","fine tuning","finetune","lora","适配器"),List.of("词向量","embedding","嵌入","语义检索","vector"),
  List.of("分类","classification","classifier"),List.of("回归","regression"),List.of("聚类","clustering","kmeans"),
  List.of("概率","probability","贝叶斯","bayes"),List.of("熵","entropy","信息量","information theory"),
  List.of("矩阵","matrix","线性代数","linear algebra"),List.of("导数","derivative","微分","differentiation"),
  List.of("积分","integral","integration"),List.of("数据增强","augmentation","增广"),
  List.of("图像识别","image recognition","计算机视觉","computer vision"),List.of("文字识别","ocr","扫描件"),
  List.of("发音","pronunciation","音标","phonetic"),List.of("搭配","collocation","介词","preposition"),
  List.of("从句","clause","定语从句","relative clause"),List.of("时态","tense","过去完成","past perfect"),
  List.of("虚拟语气","subjunctive","条件句","conditional"),List.of("遗忘","forgetting","间隔复习","spaced repetition","fsrs"),
  List.of("多线程","concurrency","并发","thread","线程安全"),List.of("死锁","deadlock","互斥"),
  List.of("垃圾回收","gc","garbage collection","内存回收"),List.of("数据库","database","sql"),
  List.of("事务","transaction","acid"),List.of("缓存","cache","redis"),List.of("容器","docker","部署","deployment"),
  List.of("网络请求","http","接口","api"),List.of("性能","performance","延迟","latency","优化"),
  List.of("备份","backup","恢复","restore"),List.of("权限","authorization","越权","访问控制"),
  List.of("登录","login","authentication","认证"),List.of("加密","encryption","密钥","cryptography"),
  List.of("算法复杂度","complexity","时间复杂度","大o"),List.of("递归","recursion"),
  List.of("排序","sorting","quicksort"),List.of("链表","linked list"),List.of("哈希","hash","散列表"),
  List.of("树","tree","二叉树","binary tree"),List.of("进程","process","操作系统","operating system")
 );
 public static List<String> terms(String query){
  String lower=query.toLowerCase(Locale.ROOT);LinkedHashSet<String> terms=new LinkedHashSet<>();
  for(var group:CONCEPTS)if(group.stream().anyMatch(t->t.length()>1&&lower.contains(t)))terms.addAll(group);
  String stripped=lower.replaceAll("之前|那个|这个|帮我|查找|找到|一下|关于|解决|怎么|如何|方法|的|了|吗|呢|请|笔记|资料", " ");
  var matcher=Pattern.compile("[a-z][a-z0-9_+-]{1,35}|[\\p{IsHan}]{2,12}").matcher(stripped);
  while(matcher.find()){String term=matcher.group();if(!Set.of("the","find","my","about","how","to","of","in","is","that","for","what").contains(term))terms.add(term);}
  return terms.stream().limit(24).toList();
 }
}
