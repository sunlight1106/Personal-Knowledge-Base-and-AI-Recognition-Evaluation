# 词汇自测

首页、工作台和背单词页面提供入口，公开地址为 `/vocabulary-test`。测试不要求登录。

- **勾选认识的词**：第一轮每层 8 词，共 40 词；第二轮每层至少 4 词，再向自评认识比例最接近一半的两层各补 10 词。两轮共 80 个不同词，只勾选能说出至少一个词义的词。
- **英译汉**：每层 6 题，共 30 道四选一释义题，可选“不认识”。估算比例采用 `max(0, (答对比例 − 0.25) / 0.75)`，降低随机猜测影响；这不能消除所有猜测偏差。
- **汉译英**：每层 6 题，共 30 道目标词拼写题。提供首字母和长度，因此衡量的是有提示的回忆。忽略大小写和两端空白，接受已列入规则的常见英美变体；不模糊接受拼错的词，同义表达可能不是本题目标。

每层按样本比例乘以该层词条数，结果给出宽范围。范围采用各层 Wilson 区间端点的加权和，并作保守取整；这里没有把自适应取样后的范围宣称为经验证的 95% 人群置信区间。自评偏差、题库覆盖、抽样池筛选与提示效应仍然存在。**这是内置可测词库范围内的教学参考，不代表英语全部词汇量，不映射 CEFR，也不预测考试通过。** 三种模式的结果不可直接等同。

题库采用已有 ECDICT 固定快照和学习目录，保留 MIT 许可与来源（见 `database/vocabulary/ECDICT-LICENSE.txt`、`THIRD_PARTY_NOTICES.md`）。从有词频、适合独立提问的词条中筛选并按频率排序，排除明确变形、专名、交叉引用和不适宜公开提问的释义。五层分别覆盖前 1000、接下来的 2000、3000、3000 和剩余词条；每层固定抽出 100 个候选，运行时随机选题。生成器及源快照校验：

```powershell
python scripts/build-vocabulary-assessment.py .runtime/ecdict.csv
```

生成的数据只随测试路由延迟加载，首页不下载题库。代码不会在运行时下载原词典。勾选交互借鉴两阶段自测思路，没有复制外部测试的题目、文案或准确率承诺。

推荐由各层表现和词书中各层词汇的组成共同计算，优先选择约七成可能熟悉的词书。核心层表现较弱时，先推荐基础书；用户可以切换备考或阅读方向。链接只打开现有词书，不自动替换学习计划；点击学习仍按后端权限与用户归属检查。

答案只保存在当前页面内存，不上传、不写入账号、浏览器持久存储或学习统计。离开/刷新会清空，未完成时提示；切换账号清空旧结果。完成后可以下载不含身份信息和逐题答案的 JSON 摘要。此功能是自测，不是防作弊考试，客户端包含答案，修改结果不会获得权限或修改学习进度。

## 检查

- 单元测试覆盖分层抽样、补测去重、极端答案、重复 ID、越界答案、猜测校正、拼写变体与推荐方向。
- 界面测试覆盖三种完整流程、返回修改、跳过、空选择、重置、双语、账号切换，以及推荐词书入口与权限。
- 词书跳转不会静默保存设置，未知或已不可用的词书显示提示。

English: Three public, local-only checks offer two-stage recognition, English-to-Chinese meaning choices and cued Chinese-to-English spelling. Results are uncalibrated, catalog-relative educational ranges. They do not certify proficiency or alter mastery. The licensed offline bank is lazy-loaded; recommendations open existing books subject to normal account permissions.
