"""AcademicCraft adapter for the pinned, MIT-licensed Diagram Design tools."""
from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

VENDOR = Path(__file__).parent / "vendor" / "diagram-design"
sys.path.insert(0, str(VENDOR / "scripts"))
import mermaid_extract
import self_check
import verify_geometry

SVG_NS = "http://www.w3.org/2000/svg"
FIGURE_RE = re.compile(r"^```diagram-design[ \t]*\r?\n(.*?)^```[ \t]*\r?$", re.MULTILINE | re.DOTALL)
TYPES = {"flowchart": "architecture", "sequenceDiagram": "sequence", "erDiagram": "er", "stateDiagram-v2": "state", "stateDiagram": "state"}
TAGS = {"svg", "title", "desc", "defs", "marker", "g", "rect", "text", "tspan", "path", "line", "circle", "ellipse", "polygon", "polyline"}
ATTRS = set("id viewBox width height x y x1 y1 x2 y2 cx cy r rx ry d points fill fill-opacity stroke stroke-opacity opacity stroke-width stroke-linecap stroke-linejoin stroke-dasharray font-family font-size font-weight font-style text-anchor dominant-baseline dy dx transform marker-start marker-end markerWidth markerHeight refX refY orient markerUnits role aria-labelledby data-node-id data-edge-id data-from data-to data-label".split())
NUMBER = r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)"


def extract(source: str) -> dict:
    if len(source) > 40_000:
        raise ValueError("Diagram source is too large; split the diagram.")
    with tempfile.TemporaryDirectory() as folder:
        path = Path(folder) / "source.mmd"
        path.write_text(source, encoding="utf-8")
        blocks = mermaid_extract.load_blocks(path)
        diagrams = [mermaid_extract.parse_block(block) for block in blocks]
        if len(diagrams) != 1 or diagrams[0].kind not in TYPES:
            raise ValueError("Use one flowchart, sequenceDiagram, erDiagram, or stateDiagram-v2 per figure.")
        diagram = json.loads(mermaid_extract.to_json(path, blocks, diagrams))["diagrams"][0]
    if len(diagram["nodes"]) > 24 or len(diagram["edges"]) > 40:
        raise ValueError("Diagram is too dense for a thesis page; split it into smaller figures.")
    diagram["type"] = TYPES[diagram["kind"]]
    if diagram["kind"] == "flowchart" and any(n["shape"] in {"diamond", "rhombus"} for n in diagram["nodes"]):
        diagram["type"] = "flowchart"
    if diagram["kind"] == "erDiagram":
        cardinalities = {"||": "1", "|o": "0..1", "o|": "0..1", "}o": "0..*", "o{": "0..*", "}|": "1..*", "|{": "1..*", "u": "u"}
        for edge in diagram["edges"]:
            symbols, _, relation = edge["label"].partition(" · ")
            left, _, right = symbols.split()
            edge["cardinality"] = [cardinalities[left], cardinalities[right]]
            edge["label"] = relation
    return diagram


def visible_text(element: ET.Element) -> str:
    return re.sub(r"\s+", "", "".join("".join(child.itertext()) for child in element.iter() if child.tag == f"{{{SVG_NS}}}text"))


def contains_labels(element: ET.Element, labels: list[str]) -> None:
    text = visible_text(element)
    for label in labels:
        if re.sub(r"\s+", "", label) not in text:
            raise ValueError("Missing visible source label or field: " + label[:100])


def validate_svg(source: str, svg: str, directory: Path) -> dict:
    # Only inert local geometry reaches either the browser or the saved chapter.
    if len(svg) > 120_000 or re.search(r"<!\s*(?:DOCTYPE|ENTITY)|<\?", svg, re.I):
        raise ValueError("SVG must be bounded local geometry without XML declarations or entities.")
    root = ET.fromstring(svg)
    if root.tag != f"{{{SVG_NS}}}svg":
        raise ValueError("Expected one SVG root with the SVG namespace.")
    elements = list(root.iter())
    if len(elements) > 1_500:
        raise ValueError("SVG contains too many elements.")
    for element in elements:
        if element.tag.removeprefix(f"{{{SVG_NS}}}") not in TAGS or not element.tag.startswith(f"{{{SVG_NS}}}"):
            raise ValueError("SVG contains a disallowed element: " + element.tag)
        for name, value in element.attrib.items():
            if name not in ATTRS:
                raise ValueError("SVG contains a disallowed attribute: " + name)
            if name in {"fill", "stroke"} and not re.fullmatch(r"none|black|white|currentColor|#[0-9a-fA-F]{3,8}", value):
                raise ValueError("SVG paint must use local solid colors.")
            if name in {"opacity", "fill-opacity", "stroke-opacity"} and not re.fullmatch(r"0(?:\.\d+)?|1(?:\.0+)?", value):
                raise ValueError("SVG opacity must be between zero and one.")
            if name in {"marker-start", "marker-end"} and not re.fullmatch(r"url\(#[\w-]+\)", value):
                raise ValueError("SVG markers must reference local IDs.")
            if name == "transform" and not re.fullmatch(rf"translate\(\s*{NUMBER}(?:[ ,]+{NUMBER})?\s*\)", value):
                raise ValueError("Use translate only; keep diagram coordinates explicit.")
            if name == "d" and not re.fullmatch(r"[MmLlHhVvQqCcSsTtAaZzEe0-9\s,.+\-]+", value):
                raise ValueError("Invalid SVG path.")
            if re.search(r"(?:https?|file|data|javascript):|@import|url\(", value, re.I) and name not in {"marker-start", "marker-end"}:
                raise ValueError("SVG cannot load external resources.")
    box = [float(n) for n in re.split(r"[\s,]+", root.get("viewBox", "").strip())]
    if len(box) != 4 or not all(math.isfinite(n) for n in box) or box[:2] != [0, 0] or not 400 <= box[2] <= 960 or not 160 <= box[3] <= 1440:
        raise ValueError("Use a bounded viewBox starting at 0 0, width 400–960 and height 160–1440.")
    diagram = extract(source)
    nodes = [element for element in elements if "data-node-id" in element.attrib]
    edges = [element for element in elements if "data-edge-id" in element.attrib]
    if sorted(n.get("data-node-id") for n in nodes) != sorted(n["id"] for n in diagram["nodes"]):
        raise ValueError("SVG must preserve every source node exactly once using data-node-id.")
    if sorted(e.get("data-edge-id") for e in edges) != sorted(e["id"] for e in diagram["edges"]):
        raise ValueError("SVG must preserve every source edge exactly once using data-edge-id.")
    by_node = {n.get("data-node-id"): n for n in nodes}
    for node in diagram["nodes"]:
        labels = [] if node["shape"] in {"start", "end"} else [node["label"]]
        contains_labels(by_node[node["id"]], [*labels, *node["fields"]])
    by_edge = {e.get("data-edge-id"): e for e in edges}
    for edge in diagram["edges"]:
        rendered = by_edge[edge["id"]]
        if (rendered.get("data-from"), rendered.get("data-to")) != (edge["source"], edge["target"]):
            raise ValueError("SVG changed the endpoints of edge " + edge["id"])
        contains_labels(rendered, [edge["label"], *edge.get("cardinality", [])])
    all_text = visible_text(root)
    for note in diagram["notes"]:
        if re.sub(r"\s+", "", note) not in all_text:
            raise ValueError("SVG omitted a source note.")
    # Retain semantic regions such as sequence alt/else; coordinates remain model-authored.
    for fragment in diagram["fragments"]:
        for key in ("label", "condition"):
            label = fragment.get(key, "")
            if label and re.sub(r"\s+", "", str(label)) not in all_text:
                raise ValueError("SVG omitted a source fragment: " + str(label))
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "diagram.html"
    path.write_text('<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>论文配图</title></head><body>' + svg + '</body></html>', encoding="utf-8")
    findings = self_check.verify(path, offline=True) + verify_geometry.check(path)
    if findings:
        raise ValueError("; ".join(findings[:6]))
    return {"width": box[2], "height": box[3], "type": diagram["type"]}


def render_svg(svg: str, target: Path, kind: str = "") -> None:
    source = target.with_suffix(".svg")
    source.write_text(svg, encoding="utf-8")
    cli = Path(os.environ.get("THESIS_MERMAID_CLI", str(Path(__file__).parent / "node_modules/@mermaid-js/mermaid-cli/src/cli.js")))
    result = subprocess.run(["node", str(Path(__file__).parent / "render_svg.mjs"), str(source), str(target), str(cli.resolve()), kind], capture_output=True, text=True, encoding="utf-8", timeout=45)
    if result.returncode:
        raise ValueError("SVG render failed: " + result.stderr[-1600:])


def validate_figure(payload: dict, directory: Path, render: bool = False) -> dict:
    if payload.get("version") != 1 or not isinstance(payload.get("source"), str) or not isinstance(payload.get("svg"), str):
        raise ValueError("Invalid Diagram Design figure; expected version 1, source and svg.")
    metadata = validate_svg(payload["source"], payload["svg"], directory)
    if render:
        render_svg(payload["svg"], directory / "diagram.png", metadata["type"])
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["extract", "validate", "validate-chapter"])
    parser.add_argument("source", type=Path)
    args = parser.parse_args()
    if args.command == "extract":
        result = extract(args.source.read_text(encoding="utf-8"))
    elif args.command == "validate":
        result = validate_figure(json.loads(args.source.read_text(encoding="utf-8")), args.source.parent, render=True)
    else:
        content = args.source.read_text(encoding="utf-8")
        matches = list(FIGURE_RE.finditer(content))
        if len(matches) != len(re.findall(r"^```diagram-design[ \t]*\r?$", content, re.MULTILINE)):
            raise ValueError("A Diagram Design block is not closed.")
        if len(matches) > 8:
            raise ValueError("A chapter supports at most eight figures.")
        for index, match in enumerate(matches):
            validate_figure(json.loads(match.group(1)), args.source.parent / str(index), render=True)
        result = {"figures": len(matches)}
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, ET.ParseError, mermaid_extract.ExtractError, OSError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
