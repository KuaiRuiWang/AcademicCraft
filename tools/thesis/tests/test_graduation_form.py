import sys
import tempfile
import unittest
from pathlib import Path

from docx import Document

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from graduation_form import inspect, render


class GraduationFormTest(unittest.TestCase):
    def test_school_template_round_trip(self):
        with tempfile.TemporaryDirectory() as folder:
            template = Path(folder) / "school.docx"
            output = Path(folder) / "result.docx"
            school = Document()
            school.sections[0].header.paragraphs[0].text = "计算机学院"
            table = school.add_table(2, 2)
            table.cell(0, 0).text = "课题名称"
            table.cell(1, 0).text = "主要任务："
            note = school.add_paragraph("提交材料：")
            note.add_run("{{验收").bold = True
            note.add_run("方式}}")
            note.add_run("。").italic = True
            school.add_paragraph("导师：{{指导教师}}")
            school.add_table(1, 1).cell(0, 0).text = "一、进度安排"
            school.save(template)

            self.assertEqual(inspect(template), ["课题名称", "主要任务", "验收方式", "进度安排"])
            sections = {"课题名称": "借阅系统", "主要任务": "实现借阅登记", "验收方式": "现场演示", "进度安排": "第1周调研"}
            render({"title": "借阅系统", "kind": "任务书", "sections": sections}, template, output)
            result = Document(output)
            self.assertEqual(result.tables[0].cell(1, 1).text, "实现借阅登记")
            self.assertEqual(result.paragraphs[0].text, "提交材料：现场演示。")
            self.assertTrue(result.paragraphs[0].runs[1].bold)
            self.assertTrue(result.paragraphs[0].runs[-1].italic)
            self.assertEqual(result.paragraphs[1].text, "导师：待填写")
            self.assertEqual(result.tables[1].cell(0, 0).text, "一、进度安排\n第1周调研")
            self.assertEqual(result.sections[0].header.paragraphs[0].text, "计算机学院")
            self.assertEqual(Document(template).tables[0].cell(1, 1).text, "")

    def test_default_report_and_unrecognized_form(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / "report.docx"
            render({"title": "借阅系统", "kind": "开题报告", "sections": {"研究内容": "拟开展借阅需求调研。"}},
                   None, output)
            result = Document(output)
            self.assertEqual(result.paragraphs[0].text, "开题报告")
            self.assertEqual(result.paragraphs[-1].text, "拟开展借阅需求调研。")
            with self.assertRaisesRegex(ValueError, "未找到可生成的栏目"):
                inspect(output)
