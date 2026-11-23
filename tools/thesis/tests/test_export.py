import os
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from export_thesis import export, render_diagrams
from docx import Document


class ExportTest(unittest.TestCase):
    def test_exports_new_svg_figures_alongside_legacy_mermaid(self):
        payload = {
            'version': 1,
            'source': Path(__file__).with_name('sample_diagram.mmd').read_text(encoding='utf-8'),
            'svg': Path(__file__).with_name('sample_diagram.svg').read_text(encoding='utf-8'),
            'caption': '图4.2 应用系统架构',
        }
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / 'mixed.md'
            content = Path(__file__).with_name('sample.md').read_text(encoding='utf-8')
            second = {**payload, 'caption': '图4.3 第二张连续配图'}
            source.write_text(content + '\n\n```diagram-design\n' + json.dumps(payload, ensure_ascii=False) + '\n```\n\n```diagram-design\n' + json.dumps(second, ensure_ascii=False) + '\n```\n\n### 后续说明\n三张配图均应保留。', encoding='utf-8')
            target = Path(folder) / 'mixed.docx'
            export(source, target)
            document = Document(target)
            self.assertEqual(len(document.inline_shapes), 3)
            text = '\n'.join(p.text for p in document.paragraphs)
            self.assertEqual(text.count('图4.2 应用系统架构'), 1)
            self.assertEqual(text.count('图4.3 第二张连续配图'), 1)
            self.assertIn('三张配图均应保留', text)
            self.assertNotIn('diagram-design', text)
            self.assertNotIn('<svg', text)

    def test_rejects_external_resources_before_starting_browser(self):
        with tempfile.TemporaryDirectory() as folder, patch("export_thesis.subprocess.run") as run:
            with self.assertRaisesRegex(ValueError, "external resources"):
                render_diagrams('```mermaid\nflowchart LR\nA@{img: "http://169.254.169.254/secret"}\n```\n', Path(folder))
            run.assert_not_called()

    def test_exports_real_diagram_tables_and_word_navigation(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "thesis.docx"
            export(Path(__file__).with_name("sample.md"), target)
            document = Document(target)
            self.assertEqual(document.paragraphs[0].style.name, "Title")
            self.assertEqual(len(document.inline_shapes), 1)
            self.assertEqual(len(document.tables), 2)
            section = document.sections[0]
            self.assertAlmostEqual(section.page_width.cm, 21, places=1)
            width = section.page_width - section.left_margin - section.right_margin
            self.assertLessEqual(document.inline_shapes[0].width, width)
            self.assertEqual(next(p for p in document.paragraphs if p.text == "第一章 绪论").style.name, "Heading 1")
            self.assertFalse(any("```mermaid" in p.text for p in document.paragraphs))
            self.assertTrue(any("待验证" in c.text for t in document.tables for r in t.rows for c in r.cells))
            with ZipFile(target) as archive:
                self.assertIn('TOC', archive.read("word/document.xml").decode())
                self.assertIn('PAGE', archive.read("word/footer1.xml").decode())

    def test_missing_renderer_does_not_publish_a_partial_document(self):
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, {"THESIS_MERMAID_CLI": str(Path(folder) / "missing.js")}):
            target = Path(folder) / "thesis.docx"
            with self.assertRaisesRegex(RuntimeError, "Mermaid CLI is missing"):
                export(Path(__file__).with_name("sample.md"), target)
            self.assertFalse(target.exists())


if __name__ == "__main__":
    unittest.main()
