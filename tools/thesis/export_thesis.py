"""AcademicCraft adapter; DOCX body formatting is provided by vendored lunwen-skill."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).parent / "vendor" / "lunwen"))
from generate_thesis_docx import build_doc, default_style_profile
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from diagram_design import render_mermaid, render_svg, split_mermaid_caption, validate_figure

LEGACY_FALLBACK_NOTE = "> 配图自动排版未通过校验，已保留原始结构图；Word 导出仍会绘制该图。"


def render_diagrams(markdown: str, output: Path) -> tuple[str, dict[str, Path]]:
    image_map: dict[str, Path] = {}
    pattern = re.compile(r"^```(mermaid|diagram-design)[ \t]*\r?\n(.*?)^```[ \t]*\r?(?=\n|$)(?:\n[ \t]*(?:\r?\n[ \t]*)*(图\s*\d[^\r\n]*))?", re.MULTILINE | re.DOTALL)

    def render(match: re.Match) -> str:
        number = len(image_map) + 1
        if number > 8:
            raise ValueError("A thesis export supports at most eight figures.")
        payload = json.loads(match.group(2)) if match.group(1) == "diagram-design" else None
        diagram, embedded_caption = split_mermaid_caption(match.group(2)) if payload is None else ("", None)
        caption = (match.group(3) or "").strip()
        if not caption:
            caption = (payload.get("caption") if payload else embedded_caption) or f"图4.{number} 系统设计图"
        if caption in image_map:
            raise ValueError("Duplicate figure caption: " + caption)
        image = output / f"diagram-{number}.png"
        if payload is not None:
            metadata = validate_figure(payload, output / f"check-{number}")
            render_svg(payload["svg"], image, metadata["type"])
        else:
            render_mermaid(diagram, image)
        image_map[caption] = image
        return f"```mermaid\n%% rendered figure\n```\n\n{caption}\n\n"

    prepared = pattern.sub(render, markdown)
    prepared = re.sub(rf"^{re.escape(LEGACY_FALLBACK_NOTE)}[ \t]*\r?$", "", prepared, flags=re.MULTILINE)
    return prepared, image_map


def field(paragraph, instruction: str) -> None:
    code = OxmlElement("w:fldSimple")
    code.set(qn("w:instr"), instruction)
    paragraph._p.append(code)


def export(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    markdown, images = render_diagrams(source.read_text(encoding="utf-8"), target.parent)
    prepared = target.parent / "prepared.md"
    prepared.write_text(markdown, encoding="utf-8")
    document = build_doc(prepared, images, default_style_profile())
    section = document.sections[0]
    section.page_width, section.page_height = Cm(21), Cm(29.7)
    usable_width = section.page_width - section.left_margin - section.right_margin
    for shape in document.inline_shapes:
        if shape.width > usable_width:
            shape.height = round(shape.height * usable_width / shape.width)
            shape.width = usable_width
    for table in document.tables:
        table.autofit = False
        column_width = round(usable_width / len(table.columns))
        for column in table.columns:
            column.width = column_width
        for row in table.rows:
            for cell in row.cells:
                cell.width = column_width
    headings = {line[3:].strip(): "Heading 1" for line in markdown.splitlines() if line.startswith("## ")}
    headings.update({line[4:].strip(): "Heading 2" for line in markdown.splitlines() if line.startswith("### ")})
    headings.update({line[5:].strip(): "Heading 3" for line in markdown.splitlines() if line.startswith("#### ")})
    for paragraph in document.paragraphs:
        if paragraph.text in headings:
            paragraph.style = headings[paragraph.text]
            paragraph.paragraph_format.keep_with_next = True
    if document.paragraphs:
        document.paragraphs[0].style = "Title"
    for style_name in ["Title", "Heading 1", "Heading 2", "Heading 3"]:
        document.styles[style_name].font.color.rgb = RGBColor(0, 0, 0)
    first_chapter = next((p for p in document.paragraphs if p.text.startswith("第一章")), None)
    if first_chapter is not None:
        title = first_chapter.insert_paragraph_before("目录")
        title.paragraph_format.page_break_before = True
        toc = first_chapter.insert_paragraph_before()
        field(toc, 'TOC \\o "1-3" \\h \\z \\u')
    field(section.footer.paragraphs[0], "PAGE")
    section.footer.paragraphs[0].alignment = WD_ALIGN_PARAGRAPH.CENTER
    document.core_properties.title = document.paragraphs[0].text if document.paragraphs else "论文初稿"
    settings = document.settings.element
    update = OxmlElement("w:updateFields")
    update.set(qn("w:val"), "true")
    settings.append(update)
    temporary = target.with_suffix(".partial.docx")
    document.save(temporary)
    temporary.replace(target)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    arguments = parser.parse_args()
    export(arguments.source, arguments.target)
