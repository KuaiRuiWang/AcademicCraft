# 论文导出工具来源

`vendor/lunwen/generate_thesis_docx.py` 与 `markdown_utils.py` 原样来自
[Doryoku1223/lunwen-skill](https://github.com/Doryoku1223/lunwen-skill)，
固定提交 `b8dd1089f45b092fd08dce61dffc1c56e8ec80b1`（2026-04-05）。
上游 MIT 许可证及版权声明见 [vendor/lunwen/LICENSE](vendor/lunwen/LICENSE)。

`export_thesis.py` 是 AcademicCraft 的接入层，负责 Mermaid 图表、A4 页面、Word 标题样式、目录域和页码。
上游 Skill 未安装为后台运行时，写作由本项目 Java 服务调用既有模型完成。
