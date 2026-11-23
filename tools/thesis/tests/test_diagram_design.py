from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from diagram_design import extract, validate_figure, validate_svg, render_svg

SOURCE = Path(__file__).with_name("sample_diagram.mmd").read_text(encoding="utf-8")
SVG = Path(__file__).with_name("sample_diagram.svg").read_text(encoding="utf-8")


class DiagramDesignTest(unittest.TestCase):
    def test_uses_upstream_extractor_and_preserves_er_fields_and_sequence_order(self):
        diagram = extract(SOURCE)
        self.assertEqual(diagram["type"], "architecture")
        self.assertEqual(extract('flowchart TD\nA{条件} --> B[结果]')["type"], "flowchart")
        self.assertEqual([node["label"] for node in diagram["nodes"]], ["应用页面", "业务接口", "数据存储"])
        er = extract('erDiagram\n USER ||--o{ TASK : owns\n USER {\n int id PK\n }\n TASK {\n int ownerId FK\n }')
        self.assertEqual(er["type"], "er")
        self.assertIn("int ownerId FK", er["nodes"][1]["fields"])
        sequence = extract('sequenceDiagram\n A->>B: request\n B-->>A: response')
        self.assertEqual(sequence["type"], "sequence")
        self.assertEqual([edge["label"] for edge in sequence["edges"]], ["request", "response"])

    def test_accepts_real_local_geometry_and_renders_chinese_png(self):
        with tempfile.TemporaryDirectory() as folder:
            metadata = validate_svg(SOURCE, SVG, Path(folder))
            self.assertEqual(metadata["width"], 720)
            target = Path(folder) / "diagram.png"
            render_svg(SVG, target)
            self.assertEqual(target.read_bytes()[:8], b'\x89PNG\r\n\x1a\n')
            from struct import unpack
            self.assertEqual(unpack('>II', target.read_bytes()[16:24]), (2160, 1080))

    def test_rejects_missing_nodes_edges_labels_and_changed_endpoints(self):
        for replacement in [
            SVG.replace('data-node-id="C"', 'data-node-id="D"'),
            SVG.replace('data-edge-id="e2"', 'data-edge-id="wrong"'),
            SVG.replace('data-from="A" data-to="B"', 'data-from="C" data-to="B"'),
            SVG.replace('>数据存储</text>', '>错误模块</text>'),
        ]:
            with self.subTest(svg=replacement[-200:]), tempfile.TemporaryDirectory() as folder:
                with self.assertRaises(ValueError):
                    validate_svg(SOURCE, replacement, Path(folder))

    def test_inert_label_metadata_does_not_replace_visible_source_labels(self):
        annotated = SVG.replace('data-node-id="C"', 'data-node-id="C" data-label="数据存储"')
        with tempfile.TemporaryDirectory() as folder:
            validate_svg(SOURCE, annotated, Path(folder))
            with self.assertRaisesRegex(ValueError, 'Missing visible source label or field'):
                validate_svg(SOURCE, annotated.replace('>数据存储</text>', '>其他</text>'), Path(folder))

    def test_rejects_active_and_external_svg_before_starting_browser(self):
        for fragment in ['<script>alert(1)</script>', '<image href="http://169.254.169.254/"/>', '<foreignObject/>', '<style>@import "https://example.com";</style>']:
            with tempfile.TemporaryDirectory() as folder, patch('diagram_design.render_svg') as render:
                with self.assertRaises(ValueError):
                    validate_figure({'version': 1, 'source': SOURCE, 'svg': SVG.replace('</svg>', fragment+'</svg>')}, Path(folder), render=True)
                render.assert_not_called()

    def test_rejects_clipped_geometry(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, 'viewBox|clipped'):
                validate_svg(SOURCE, SVG.replace('x="600" y="166"', 'x="900" y="166"'), Path(folder))

    def test_rejects_source_text_covered_by_a_later_shape(self):
        covered = SVG.replace('</svg>', '<rect x="40" y="120" width="160" height="80" fill="#ffffff"/></svg>')
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, 'covered by a later shape'):
                render_svg(covered, Path(folder) / 'covered.png')

    def test_preserves_er_cardinality_and_foreign_key_fields(self):
        source = Path(__file__).with_name('sample_er.mmd').read_text(encoding='utf-8')
        svg = Path(__file__).with_name('sample_er.svg').read_text(encoding='utf-8')
        with tempfile.TemporaryDirectory() as folder:
            validate_svg(source, svg, Path(folder))
            for old, new in [('int ownerId FK', 'int wrongId'), ('>0..*</text>', '>1</text>')]:
                with self.assertRaisesRegex(ValueError, 'Missing visible source label or field'):
                    validate_svg(source, svg.replace(old, new), Path(folder))


if __name__ == '__main__':
    unittest.main()
