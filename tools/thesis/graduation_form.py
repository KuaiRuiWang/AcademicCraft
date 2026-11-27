"""Inspect and fill Word forms for graduation assignments and research proposals."""
import argparse
import json
import re
from pathlib import Path
from zipfile import ZipFile

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Pt
from docx.text.paragraph import Paragraph
from lxml import etree

WORD_NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}


def validate_package(path: Path) -> None:
    # 只接受普通 Word 文档，检查包内组件，读取时不解压到文件系统。
    with ZipFile(path) as package:
        entries = package.infolist()
        if len(entries) > 1200 or sum(item.file_size for item in entries) > 48 * 1024 * 1024:
            raise ValueError("解压后的模板过大")
        if len({item.filename for item in entries}) != len(entries):
            raise ValueError("模板包含重复组件")
        parser = etree.XMLParser(resolve_entities=False, no_network=True)
        mimetype = etree.fromstring(package.read("[Content_Types].xml"), parser)
        if not any(p.get("PartName") == "/word/document.xml" and p.get("ContentType") ==
                   "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml" for p in mimetype):
            raise ValueError("请保存为标准的 .docx 文件")
        for item in entries:
            name = item.filename.lower()
            if any(part in name for part in ("vbaproject", "/embeddings/", "/activex/")):
                raise ValueError("模板包含宏或嵌入对象")
            if not name.endswith((".xml", ".rels")):
                continue
            raw = package.read(item)
            if re.search(rb"<!\s*(?:DOCTYPE|ENTITY)", raw.replace(b"\x00", b""), re.I):
                raise ValueError("模板包含不支持的 XML 声明")
            if name.endswith(".rels"):
                relationships = etree.fromstring(raw, parser)
                if any(rel.get("TargetMode") == "External" and not rel.get("Type", "").endswith("/hyperlink")
                       for rel in relationships):
                    raise ValueError("模板引用了外部文档或图片")
            if name.startswith("word/") and name.endswith(".xml"):
                root = etree.fromstring(raw, parser)
                instructions = [str(value) for value in root.xpath(
                    "//w:instrText/text() | //w:fldSimple/@w:instr", namespaces=WORD_NS)]
                for field_text in instructions + ["".join(instructions)]:
                    if re.search(r"\b(?:DDEAUTO|DDE|INCLUDETEXT|INCLUDEPICTURE|LINK|IMPORT|DATABASE)\b", field_text, re.I):
                        raise ValueError("模板包含自动链接域")


MARKER = re.compile(r"\{\{\s*([\w、（）() /-]{1,48}?)\s*\}\}")
TITLE_LABELS = {"题目", "课题名称", "课题题目", "课题", "论文题目", "毕业设计题目", "设计题目"}
FORM_LABELS = set("""
课题任务 任务内容 主要任务 设计任务 研究目标 课题目标 任务要求 基本要求 技术要求 主要技术指标
主要内容 研究内容 研究方法 研究背景 选题依据 选题背景 选题意义 研究意义 研究目的 研究背景与意义
国内外研究现状 国内外研究现状及发展趋势 研究现状 研究内容与方法 研究方案 技术路线 可行性分析
进度安排 进度计划 工作进度安排 研究计划 预期成果 预期目标 成果要求 主要参考文献 参考文献
课题内容及要求 主要任务与要求 课题目标与要求 研究内容与技术路线 计划进度与阶段成果
""".split()) | TITLE_LABELS
MANUAL = re.compile(r"姓名|学号|签[名字章]|审批|意见|日期|^(学生|指导教师|指导老师|导师|学校|学院|专业班级|专业|班级)$")
PARA = qn("w:p")


def label(text):
    cleaned = re.sub(r"^\s*(?:[一二三四五六七八九十\d]+[、.．]|[（(][一二三四五六七八九十\d]+[）)])\s*", "", text)
    return re.sub(r"[\s：:]+", "", cleaned)


def field_name(name):
    return "课题名称" if name in TITLE_LABELS else name.strip()


def template_slots(document):
    """Yield explicit markers, or blank spaces following a known school form label."""
    body = document.element.body
    for node in list(body.iter(PARA)):
        paragraph = Paragraph(node, document)
        for match in MARKER.finditer(paragraph.text):
            yield paragraph, match.group(0), field_name(match[1])
        name = label(paragraph.text)
        if name not in FORM_LABELS:
            continue
        target = node.getnext()
        own_cell = node.getparent()
        if target is None and own_cell.tag == qn("w:tc"):
            cell = own_cell.getnext()
            if cell is not None and cell.tag == qn("w:tc"):
                paragraphs = cell.findall(PARA)
                if len(paragraphs) == 1 and cell.find(qn("w:tbl")) is None:
                    target = paragraphs[0]
            if cell is None and len(own_cell.getparent().findall(qn("w:tc"))) == 1:
                # 整行合并的学校表格常把栏目标题和填空区放在同一单元格。
                target = own_cell.add_p()
        if (target is not None and target.tag == PARA
                and not Paragraph(target, document).text.strip()
                and not target.xpath(".//w:drawing | .//w:fldChar | .//w:pict")):
            yield Paragraph(target, document), None, field_name(name)


def inspect(template):
    validate_package(template)
    fields = list(dict.fromkeys(name for _, _, name in template_slots(Document(template))
                                if not MANUAL.search(name)))
    if not any(name != "课题名称" for name in fields):
        raise ValueError("未找到可生成的栏目。请在待填写的位置加入 {{研究内容}} 等栏目名后重新导入。")
    if len(fields) > 30:
        raise ValueError("每份模板最多支持 30 个生成栏目")
    return fields


def replace_marker(paragraph, marker, value):
    # 学校模板经常将同一个占位符拆成多个 run，保留周围文字与原字号。
    runs = paragraph.runs
    text = "".join(run.text for run in runs)
    start = text.find(marker)
    if start < 0:
        raise ValueError("请将占位符放在普通段落或表格单元格内")
    end = start + len(marker)
    offset = 0
    for run in runs:
        old = run.text
        stop = offset + len(old)
        if offset < end and stop > start:
            prefix = old[:max(0, start - offset)]
            suffix = old[max(0, end - offset):]
            run.text = prefix + (value if offset <= start < stop else "") + suffix
        offset = stop


def render(spec, template, output):
    values = spec["sections"]
    if template:
        document = Document(template)
        for paragraph, marker, name in list(template_slots(document)):
            value = "待填写" if MANUAL.search(name) else values[name]
            if marker:
                replace_marker(paragraph, marker, value)
            else:
                run = next(iter(paragraph.runs), None)
                if run is None:
                    run = paragraph.add_run()
                run.text = value
    else:
        document = Document()
        style = document.styles["Normal"]
        style.font.name = "Times New Roman"
        style.element.get_or_add_rPr().rFonts.set(qn("w:eastAsia"), "宋体")
        style.font.size = Pt(12)
        style.paragraph_format.line_spacing = 1.5
        heading = document.add_heading(spec["kind"], 0)
        heading.alignment = WD_ALIGN_PARAGRAPH.CENTER
        document.add_paragraph("课题名称　" + spec["title"])
        document.add_paragraph("学生姓名　____________　　　指导教师　____________")
        for name, value in values.items():
            if name != "课题名称":
                document.add_heading(name, 1)
                for line in value.splitlines():
                    if line.strip():
                        document.add_paragraph(line.strip())
    document.core_properties.title = spec["title"] + " · " + spec["kind"]
    document.save(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("inspect", "render"))
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    folder = args.directory
    template = folder / "template.docx"
    if args.action == "inspect":
        try:
            print(json.dumps({"fields": inspect(template)}, ensure_ascii=False))
        except ValueError as exc:
            print(json.dumps({"error": str(exc)}, ensure_ascii=False))
        except Exception:
            print(json.dumps({"error": "无法读取模板，请使用未加密的标准 .docx 文档。"}, ensure_ascii=False))
    else:
        render(json.loads((folder / "content.json").read_text(encoding="utf-8")),
               template if template.exists() else None, folder / "result.docx")
