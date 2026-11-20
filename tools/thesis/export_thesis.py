"""AcademicCraft adapter; DOCX body formatting is provided by vendored lunwen-skill."""
from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).parent / "vendor" / "lunwen"))
from generate_thesis_docx import build_doc, default_style_profile
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH


def render_diagrams(markdown: str, output: Path) -> tuple[str, dict[str, Path]]:
    image_map: dict[str, Path] = {}
    pattern = re.compile(r"```mermaid\s*\n(.*?)\n```[ \t]*(?:\r?\n[ \t]*)*([^\n]*)", re.DOTALL)

    def render(match: re.Match) -> str:
        # Diagrams are user-editable: only render local text/geometry, never external assets or directives.
        if re.search(r"(?i)(?:https?|file|data|javascript):|%%\{|<\s*(?:script|iframe|img|svg)\b|\bclick\s|\b(?:img|image|icon)\s*:|\burl\s*\(", match.group(1)):
            raise ValueError("Mermaid diagrams cannot load external resources, links, or configuration directives.")
        number = len(image_map) + 1
        if number > 8:
            raise ValueError("A thesis export supports at most eight Mermaid diagrams.")
        caption = match.group(2).strip()
        if not re.match(r"^图\s*\d", caption):
            suffix = caption
            caption = f"图4.{number} 系统设计图"
        else:
            suffix = ""
        source = output / f"diagram-{number}.mmd"
        image = output / f"diagram-{number}.png"
        source.write_text(match.group(1), encoding="utf-8")
        cli = Path(os.environ.get("THESIS_MERMAID_CLI", str(Path(__file__).parent / "node_modules/@mermaid-js/mermaid-cli/src/cli.js")))
        if not cli.is_file():
            raise RuntimeError("Mermaid CLI is missing; run npm ci in tools/thesis or set THESIS_MERMAID_CLI.")
        config = output / "mermaid-config.json"
        config.write_text('{"securityLevel":"strict","theme":"neutral","fontFamily":"Microsoft YaHei, Noto Sans CJK SC, sans-serif"}', encoding="utf-8")
        subprocess.run(["node", str(cli.resolve()), "-i", str(source), "-o", str(image),
                        "-c", str(config), "-b", "white", "-s", "2"], check=True, timeout=45)
        image_map[caption] = image
        return f"```mermaid\n{match.group(1)}\n```\n\n{caption}\n\n{suffix}"

    return pattern.sub(render, markdown), image_map


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
