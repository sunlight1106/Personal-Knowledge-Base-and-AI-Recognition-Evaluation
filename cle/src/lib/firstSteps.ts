/** Shared by the workspace guide and the public help page. Completion stays server-owned. */
export const firstSteps: Record<string, { title: string; purpose: string; steps: string[]; outcome: string; help: string }> = {
  note: {
    title: '写下第一篇笔记',
    purpose: '想记录自己的理解、整理课堂笔记或代码，从这里开始。',
    steps: ['打开「我的笔记 → 新建笔记」，或在工作台选择「写下第一篇笔记」。', '填写标题和正文。学习库用于分类；不确定格式时，保留 Markdown，直接输入普通文字即可。', '在右侧查看预览，等到显示「已保存到服务器」，再返回笔记列表。'],
    outcome: '笔记列表里能找到这篇笔记；重新打开，标题和正文仍然存在。', help: 'knowledge-notes',
  },
  vocabulary: {
    title: '完成一次词汇练习',
    purpose: '想记单词、练搭配或复习旧词，使用背单词。',
    steps: ['打开「背单词 → 我的词书」，选择一本词书，再回到「今日学习」。', '第一次可以保留默认练习方式，点击「开始学习」或「学习新词」。', '先理解场景和搭配，再按题目要求回忆并提交。很熟的词可以 Skip；之后可在单词本恢复学习。'],
    outcome: '提交一次答案后，在「学习记录」查看记录。答过一次不等于已经掌握。', help: 'vocabulary',
  },
  document: {
    title: '导入一份学习资料',
    purpose: '已有 PDF 或 Word，先导入文档阅读；自己的总结再写进笔记。',
    steps: ['打开「学习中心 → 文档阅读」，点击「导入文档」，选择 PDF 或 DOCX 文件。', '等待处理完成。扫描件可以启用本地文字识别；可在「任务与同步」查看处理进度。', '点击文档标题阅读，检查识别出的文字；选中想保留的内容，整理为摘录或笔记。'],
    outcome: '文档出现在列表中，打开后能看到正文；扫描件的识别文字仍需检查。', help: 'learning-workspace',
  },
  review: {
    title: '制作一张复习卡',
    purpose: '把学过的知识变成问题，用回忆检查自己是否记住。',
    steps: ['打开「学习中心 → 复习卡」，找到「制作一张复习卡」。', '问题写要回忆的内容，答案写解释或例子，选择学科后点击「保存复习卡」。', '在卡片列表点击问题，先自己回忆，再点「显示答案」，按实际情况选择「忘记了」「有点难」「记得」或「很熟练」。'],
    outcome: '保存后卡片出现在列表；完成评分后会显示下次复习时间。', help: 'learning-workspace',
  },
}
