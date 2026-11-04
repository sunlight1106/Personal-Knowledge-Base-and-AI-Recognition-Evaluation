# 第三方许可

## 内置词库

新增词书使用 [ECDICT](https://github.com/skywind3000/ECDICT) 固定版本的词条、释义和音标。原 MIT 许可全文保存在 [ECDICT-LICENSE.txt](database/vocabulary/ECDICT-LICENSE.txt)，版本、校验值及各书词量见 [catalog.json](database/vocabulary/catalog.json)。选词规则与使用方法见 [词汇学习指南](docs/VOCABULARY.md)。未引入具体出版社词书的封面、原版编排或例句。

词汇自测复用同一固定快照的词频、释义和音标，生成离线分层题库。抽样与估算说明见 [词汇自测](docs/VOCABULARY-ASSESSMENT.md)。题目未复制外部词汇量测试。

## 软件依赖与服务组件

词条缺失音标的离线补充使用 eng_to_ipa 0.0.2 的 CMU 字典转换结果，另含补充标注。保留 [eng_to_ipa MIT 许可](database/vocabulary/ENG-TO-IPA-LICENSE.txt) 和 [CMU 字典许可](database/vocabulary/CMUDICT-LICENSE.txt)。转换仅在生成数据时进行，不是运行时依赖。补充列表见 [phonetic-supplement.json](database/vocabulary/phonetic-supplement.json)。

Vue、Vue Router、Vite、Spring Boot 及其他依赖保留各自许可证。Docker 服务独立适用其组件许可，本项目 MIT 许可不替代这些条款。

当前 MinIO 从固定上游源码构建，镜像保留其 LICENSE；预构建 Release 同时提供 `minio-sources.tar.gz`（对应源码、依赖源码与构建脚本）。见 [Minio.Dockerfile](scripts/components/Minio.Dockerfile) 和 [运行说明](scripts/components/minio-runtime.md)。

模型供应商的 API 条款、费用及模型授权由供应商决定。本仓库不附带模型权重或供应商额度。

复习调度使用 [java-fsrs 1.0.0](https://github.com/open-spaced-repetition/java-fsrs)，适用 MIT 许可；许可全文见 [java-fsrs MIT 许可](docs/licenses/JAVA-FSRS-LICENSE.txt)。PDF 正文提取使用 Apache PDFBox 3.0.8，适用 Apache License 2.0。

本地关联搜索的矩阵分解使用 [Apache Commons Math 3.6.1](https://commons.apache.org/proper/commons-math/)，适用 Apache License 2.0；其 JAR 保留原 LICENSE 与 NOTICE。

## Vocabulary expansion 0.13

`database/vocabulary/v49.json` adds 16 book collections using the same pinned ECDICT MIT snapshot as V17. Existing entries retain their original definitions and pronunciation data. New entries identify ECDICT or original authorship in their `source` field. `topics-v49.json` contains the project-authored topic selections; `supplement-v49.json` contains original short definitions and broad American IPA, licensed under the repository MIT license. The original V17 corpus and its checksums remain unchanged.
