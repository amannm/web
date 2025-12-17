"""
convert_openai_docs_html_to_gfm.py

Custom-tailored converter for the OpenAI docs HTML snippets in:
  - responses.html
  - conversations.html
  - streaming.html

These files are not generic HTML pages: they include React/Doc-site widgets
like "param tables" built from nested <div> elements and syntax-highlighted
code blocks with separate DOM for line numbers. This script parses those HTML
files and emits GitHub-flavored Markdown (GFM) that preserves the document's
content and structure.

Usage:
  python3 convert_openai_docs_html_to_gfm.py
  python3 convert_openai_docs_html_to_gfm.py responses.html conversations.html streaming.html
  python3 convert_openai_docs_html_to_gfm.py --output-dir out responses.html
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass, field
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable, Iterator


@dataclass(slots=True)
class Node:
    tag: str | None
    attrs: dict[str, str] = field(default_factory=dict)
    children: list["Node"] = field(default_factory=list)
    text: str = ""

    def is_text(self) -> bool:
        return self.tag is None

    def attr(self, key: str) -> str | None:
        return self.attrs.get(key)

    def classes(self) -> set[str]:
        raw = self.attrs.get("class", "")
        if not raw:
            return set()
        return {c for c in raw.split() if c}


class HtmlTreeBuilder(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self._root = Node(tag="document")
        self._stack: list[Node] = [self._root]

    @property
    def root(self) -> Node:
        return self._root

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        node = Node(tag=tag.lower(), attrs={k: (v or "") for (k, v) in attrs})
        self._stack[-1].children.append(node)
        if tag.lower() in _VOID_TAGS:
            return
        self._stack.append(node)

    def handle_startendtag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        node = Node(tag=tag.lower(), attrs={k: (v or "") for (k, v) in attrs})
        self._stack[-1].children.append(node)

    def handle_endtag(self, tag: str) -> None:
        tag = tag.lower()
        for i in range(len(self._stack) - 1, 0, -1):
            if self._stack[i].tag == tag:
                del self._stack[i:]
                return

    def handle_data(self, data: str) -> None:
        if not data:
            return
        self._stack[-1].children.append(Node(tag=None, text=data))


_VOID_TAGS = {
    "area",
    "base",
    "br",
    "col",
    "embed",
    "hr",
    "img",
    "input",
    "link",
    "meta",
    "param",
    "source",
    "track",
    "wbr",
}


def parse_html(path: Path) -> Node:
    parser = HtmlTreeBuilder()
    parser.feed(path.read_text(encoding="utf-8"))
    parser.close()
    return parser.root


def iter_descendants(node: Node) -> Iterator[Node]:
    # Depth-first traversal without O(n^2) list shifts.
    stack = list(reversed(node.children))
    while stack:
        current = stack.pop()
        yield current
        if current.children:
            stack.extend(reversed(current.children))


def node_text(node: Node, *, preserve_whitespace: bool) -> str:
    parts: list[str] = []
    for child in iter_descendants(node):
        if child.tag is None:
            parts.append(child.text)
        elif child.tag == "br":
            parts.append("\n" if preserve_whitespace else " ")
    text = "".join(parts)
    return text if preserve_whitespace else _collapse_inline_whitespace(text)


def first_descendant_with_class(node: Node, class_name: str) -> Node | None:
    for child in iter_descendants(node):
        if class_name in child.classes():
            return child
    return None


def descendants_with_tag(node: Node, tag: str) -> list[Node]:
    return [c for c in iter_descendants(node) if c.tag == tag]


def escape_md_table_cell(value: str) -> str:
    # Keep pipe tables stable and avoid accidental table breaks.
    value = value.replace("|", "\\|")
    value = value.replace("\n", "<br>")
    return value.strip()


def fenced_code(language: str | None, code: str) -> list[str]:
    lang = (language or "").strip()
    fence = "```"
    if "```" in code:
        # Extremely unlikely in these docs, but keep it correct.
        fence = "````"
    header = f"{fence}{lang}".rstrip()
    return [header, code.rstrip("\n"), fence]


def _collapse_inline_whitespace(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


class MarkdownRenderer:
    def __init__(self) -> None:
        self._lines: list[str] = []

    def render_document(self, root: Node) -> str:
        body = self._find_body(root) or root
        self._render_blocks(body, list_indent=0)
        return _normalize_markdown("\n".join(self._lines))

    def _find_body(self, root: Node) -> Node | None:
        for n in iter_descendants(root):
            if n.tag == "body":
                return n
        return None

    def _emit(self, line: str = "") -> None:
        self._lines.append(line.rstrip())

    def _emit_blank(self) -> None:
        if not self._lines or self._lines[-1] != "":
            self._lines.append("")

    def _render_blocks(self, node: Node, *, list_indent: int) -> None:
        for child in node.children:
            if child.tag is None:
                if child.text.strip():
                    self._emit(_collapse_inline_whitespace(child.text))
                    self._emit_blank()
                continue

            if child.tag == "button" or (child.attr("role") == "button"):
                # Doc-site UI controls (tabs, expanders, copy buttons) should not appear in Markdown output.
                continue

            if child.tag in {"svg", "path"}:
                continue

            if child.tag in {"div", "span", "section"}:
                if "exclude-from-copy" in child.classes():
                    continue
                if "endpoint-text" in child.classes():
                    # Rendered explicitly by _render_endpoint().
                    continue
                if "param-table" in child.classes():
                    self._render_param_table(child)
                    continue
                if "code-sample" in child.classes():
                    self._render_code_sample(child)
                    continue
                if "endpoint" in child.classes():
                    self._render_endpoint(child)
                    continue

                self._render_blocks(child, list_indent=list_indent)
                continue

            if child.tag in {"h1", "h2", "h3", "h4", "h5", "h6"}:
                self._render_heading(child)
                continue

            if child.tag == "p":
                paragraph = self._render_inline(child)
                if paragraph:
                    self._emit(paragraph)
                    self._emit_blank()
                continue

            if child.tag in {"ul", "ol"}:
                self._render_list(child, list_indent=list_indent)
                self._emit_blank()
                continue

            if child.tag == "pre":
                self._render_pre(child)
                self._emit_blank()
                continue

            if child.tag == "hr":
                self._emit("---")
                self._emit_blank()
                continue

            # Fallback: descend.
            self._render_blocks(child, list_indent=list_indent)

    def _render_heading(self, heading: Node) -> None:
        tag_level = int(heading.tag[1]) if heading.tag and heading.tag.startswith("h") else 2
        raw_id = heading.attr("id") or ""
        title = self._render_inline(heading)
        if not title:
            return

        level = tag_level
        if heading.tag == "h2" and raw_id:
            # The docs page uses <h2> for most headings; infer hierarchy from the id.
            depth = len([p for p in raw_id.split("/") if p])
            level = max(1, min(6, depth))

        if raw_id:
            self._emit(f'<a id="{raw_id}"></a>')
        self._emit(f'{"#" * level} {title}')
        self._emit_blank()

    def _render_inline(self, node: Node) -> str:
        return self._render_inline_children(node.children)

    def _render_inline_children(self, nodes: Iterable[Node]) -> str:
        parts: list[str] = []

        for n in nodes:
            if n.tag is None:
                parts.append(n.text)
                continue

            if n.tag in {"svg", "path"}:
                continue

            if n.tag == "button" or (n.attr("role") == "button"):
                continue

            if n.tag == "br":
                parts.append("<br>")
                continue

            if n.tag == "code":
                code_text = node_text(n, preserve_whitespace=False)
                if not code_text:
                    continue
                parts.append(self._inline_code(code_text))
                continue

            if n.tag == "a":
                if "api-ref-anchor-link" in n.classes():
                    continue
                href = n.attr("href") or ""
                text = self._render_inline(n)
                if not href:
                    parts.append(text)
                    continue
                if not text:
                    continue
                parts.append(f"[{text}]({href})")
                continue

            if n.tag in {"strong", "b"}:
                inner = self._render_inline(n)
                parts.append(f"**{inner}**" if inner else "")
                continue

            if n.tag in {"em", "i"}:
                inner = self._render_inline(n)
                parts.append(f"*{inner}*" if inner else "")
                continue

            if n.tag == "span":
                if "react-syntax-highlighter-line-number" in n.classes():
                    continue
                parts.append(self._render_inline(n))
                continue

            # Fallback: inline-render children.
            parts.append(self._render_inline(n))

        return _collapse_inline_whitespace("".join(parts))

    def _inline_code(self, code_text: str) -> str:
        if "`" not in code_text:
            return f"`{code_text}`"
        # Use a longer fence for inline code that includes backticks.
        return f"`` {code_text} ``"

    def _render_list(self, node: Node, *, list_indent: int) -> None:
        ordered = node.tag == "ol"
        index = 1

        for li in [c for c in node.children if c.tag == "li"]:
            prefix = f"{index}." if ordered else "-"
            indent = "  " * list_indent
            item = self._render_list_item(li, list_indent=list_indent)
            if item:
                self._emit(f"{indent}{prefix} {item}")
            # Nested blocks inside <li> (e.g., nested lists)
            for child in li.children:
                if child.tag in {"ul", "ol"}:
                    self._render_list(child, list_indent=list_indent + 1)
            if ordered:
                index += 1

    def _render_list_item(self, li: Node, *, list_indent: int) -> str:
        # Render direct inline-ish content from <li>.
        inline_children = [c for c in li.children if c.tag not in {"ul", "ol"}]
        return self._render_inline_children(inline_children)

    def _render_pre(self, pre: Node) -> None:
        # Prefer the first descendant <code class="language-..."> for language and content.
        code_node = None
        language = None
        for c in iter_descendants(pre):
            if c.tag != "code":
                continue
            lang = _language_from_code_node(c)
            if lang:
                code_node = c
                language = lang
                break
        if code_node is None:
            # Fall back to raw pre text.
            raw = node_text(pre, preserve_whitespace=True).strip("\n")
            for line in fenced_code(None, raw):
                self._emit(line)
            return

        code = _extract_syntax_highlighted_code(code_node)
        for line in fenced_code(language, code):
            self._emit(line)

    def _render_code_sample(self, node: Node) -> None:
        title_node = first_descendant_with_class(node, "code-sample-title")
        title = node_text(title_node, preserve_whitespace=False) if title_node else ""
        title = title.strip()
        if title:
            self._emit(f"**{title}**")
            self._emit_blank()

        for pre in descendants_with_tag(node, "pre"):
            # Avoid duplicating the same code when nested <pre> exists (rare).
            self._render_pre(pre)
            self._emit_blank()

    def _render_endpoint(self, node: Node) -> None:
        endpoint_text = first_descendant_with_class(node, "endpoint-text")
        if endpoint_text:
            method_node = first_descendant_with_class(endpoint_text, "endpoint-method")
            path_node = first_descendant_with_class(endpoint_text, "endpoint-path")
            method = node_text(method_node, preserve_whitespace=False) if method_node else ""
            path = node_text(path_node, preserve_whitespace=False) if path_node else ""
            method = method.strip().upper()
            path = path.strip()
            if method and path:
                self._emit(f"**{method}** `{path}`")
                self._emit_blank()

        self._render_blocks(node, list_indent=0)

    def _render_param_table(self, table: Node) -> None:
        rows: list[dict[str, str]] = []
        for row_node in [c for c in table.children if c.tag == "div" and "param-row" in c.classes()]:
            row = _parse_param_row(row_node, renderer=self)
            if row is not None:
                rows.append(row)

        if not rows:
            # Sometimes the markup includes empty param tables.
            return

        headers = ["Name", "Type", "Required", "Default", "Description"]
        self._emit("| " + " | ".join(headers) + " |")
        self._emit("| " + " | ".join(["---"] * len(headers)) + " |")
        for r in rows:
            cells = [
                escape_md_table_cell(r.get("Name", "")),
                escape_md_table_cell(r.get("Type", "")),
                escape_md_table_cell(r.get("Required", "")),
                escape_md_table_cell(r.get("Default", "")),
                escape_md_table_cell(r.get("Description", "")),
            ]
            self._emit("| " + " | ".join(cells) + " |")
        self._emit_blank()


def _language_from_code_node(code_node: Node) -> str | None:
    cls = code_node.attr("class") or ""
    for part in cls.split():
        if part.startswith("language-"):
            return part[len("language-") :]
    return None


def _extract_syntax_highlighted_code(code_node: Node) -> str:
    parts: list[str] = []

    def walk(n: Node) -> None:
        if n.tag is None:
            parts.append(n.text)
            return

        if n.tag in {"svg", "path"}:
            return

        if "react-syntax-highlighter-line-number" in n.classes():
            return

        for ch in n.children:
            walk(ch)

    walk(code_node)
    code = "".join(parts)
    return code.strip("\n")


def _parse_param_row(row_node: Node, *, renderer: MarkdownRenderer) -> dict[str, str] | None:
    name_node = first_descendant_with_class(row_node, "param-name")
    if name_node is None:
        return None
    name = node_text(name_node, preserve_whitespace=False).strip()
    if not name:
        return None

    type_node = first_descendant_with_class(row_node, "param-type")
    type_text = ""
    if type_node:
        type_text = node_text(type_node, preserve_whitespace=False).strip()

    required_node = first_descendant_with_class(row_node, "param-optl")
    required = node_text(required_node, preserve_whitespace=False).strip() if required_node else "Required"

    default_node = first_descendant_with_class(row_node, "param-default")
    default = node_text(default_node, preserve_whitespace=False).strip() if default_node else ""

    deprecated = first_descendant_with_class(row_node, "param-depr")
    if deprecated is not None:
        name = f"{name} (deprecated)"

    desc_node = first_descendant_with_class(row_node, "param-desc")
    description = ""
    if desc_node is not None:
        # Render description as markdown-ish inline with explicit <br> to keep lists readable in a table cell.
        description = _render_param_description(desc_node, renderer=renderer)

    return {
        "Name": name,
        "Type": type_text,
        "Required": required,
        "Default": default,
        "Description": description,
    }


def _render_param_description(desc_node: Node, *, renderer: MarkdownRenderer) -> str:
    # The description is usually a <div class="docs-markdown-content"> with <p>, <ul>, etc.
    docs = first_descendant_with_class(desc_node, "docs-markdown-content") or desc_node

    chunks: list[str] = []
    for child in docs.children:
        if child.tag == "p":
            text = renderer._render_inline(child)  # noqa: SLF001 - intentional, keeps formatting consistent.
            if text:
                chunks.append(text)
        elif child.tag in {"ul", "ol"}:
            items = []
            for li in [c for c in child.children if c.tag == "li"]:
                item = renderer._render_inline(li)  # noqa: SLF001 - intentional.
                if item:
                    items.append(f"- {item}")
            if items:
                chunks.append("<br>".join(items))
        else:
            text = renderer._render_inline(child)  # noqa: SLF001 - intentional.
            if text:
                chunks.append(text)
    return "<br><br>".join(chunks).strip()


def _normalize_markdown(md: str) -> str:
    lines = [ln.rstrip() for ln in md.splitlines()]

    # Collapse 3+ blank lines down to 2.
    out: list[str] = []
    blank_run = 0
    for ln in lines:
        if ln == "":
            blank_run += 1
        else:
            blank_run = 0
        if blank_run <= 2:
            out.append(ln)

    # Trim leading/trailing blanks.
    while out and out[0] == "":
        out.pop(0)
    while out and out[-1] == "":
        out.pop()
    return "\n".join(out) + "\n"


def convert_file(input_path: Path) -> str:
    root = parse_html(input_path)
    renderer = MarkdownRenderer()
    return renderer.render_document(root)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Convert OpenAI docs HTML snippets to GitHub-flavored Markdown."
    )
    parser.add_argument(
        "inputs",
        nargs="*",
        type=Path,
        help="Input .html files (defaults: responses.html conversations.html streaming.html).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("."),
        help="Directory to write .md outputs to (default: current directory).",
    )
    args = parser.parse_args(argv)

    inputs = args.inputs or [
        Path("responses.html"),
        Path("conversations.html"),
        Path("streaming.html"),
    ]

    output_dir: Path = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)

    for input_path in inputs:
        if not input_path.exists():
            raise FileNotFoundError(f"Missing input file: {input_path}")
        md = convert_file(input_path)
        out_path = output_dir / (input_path.stem + ".md")
        out_path.write_text(md, encoding="utf-8")
        print(f"Wrote {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
