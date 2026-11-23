# 论文导出工具来源

`vendor/lunwen/generate_thesis_docx.py` 与 `markdown_utils.py` 原样来自
[Doryoku1223/lunwen-skill](https://github.com/Doryoku1223/lunwen-skill)，
固定提交 `b8dd1089f45b092fd08dce61dffc1c56e8ec80b1`（2026-04-05）。
上游 MIT 许可证及版权声明见 [vendor/lunwen/LICENSE](vendor/lunwen/LICENSE)。

`export_thesis.py` 是 AcademicCraft 的接入层，负责 Mermaid 图表、A4 页面、Word 标题样式、目录域和页码。
上游 Skill 未安装为后台运行时，写作由本项目 Java 服务调用既有模型完成。

## Diagram Design

`vendor/diagram-design/` 中的规范、模板、Mermaid 语义提取器与校验脚本来自
[cathrynlavery/diagram-design](https://github.com/cathrynlavery/diagram-design)，
固定提交 `ff6fffa70a5cc520aa73b2ff77e1dcc0f5cc3733`（2026-11-23，2.6.172）。
MIT 许可证与 Cathryn Lavery 的版权声明保存在 `vendor/diagram-design/LICENSE`。
只随项目包含所需的静态规范、模板和 Python 工具，未安装上游 Agent 插件，未引入图标或外部字体资源。

`diagram_design.py`、`render_svg.mjs` 及论文配图提示词是 AcademicCraft 的适配层。
适配保留源图节点、边、字段和图题，使用离线中文字体和论文白底样式，
复用现有 Mermaid CLI 的 Puppeteer/Chrome 环境渲染 PNG，不依赖 Playwright。
