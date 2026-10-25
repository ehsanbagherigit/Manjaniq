#!/usr/bin/env python3
"""Resource-string gate: catches the mistakes aapt2 rejects, before Gradle runs.

`mergeDebugResources` fails the whole build on a malformed `<string>`, and its
message names one line of a *merged* file, which is a slow way to find a typo in
one of hundreds of strings. This checks the sources instead:

  * every `values*/**.xml` file parses as XML;
  * inside a `<string>` the only escapes are the ones aapt2 accepts
    (`\\n`, `\\t`, `\\\\`, `\\'`, `\\"`, `\\uXXXX`, `\\@`, `\\?`);
  * an apostrophe or a double quote inside a string is escaped or sits inside a
    quoted span (`"don't"`) - an unescaped one is the classic aapt2 failure;
  * `%1$s`-style placeholders are numbered, in order, and never mix numbered and
    positional forms (that throws `IllegalFormatException` at runtime);
  * every string this repository declares has the same placeholder signature in
    `values/` and `values-fa/`, so a translation cannot drop or reorder one.

Exit status is non-zero on any finding, and each finding is printed as a GitHub
annotation with the file and line so it is visible without the build log.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"

VALID_ESCAPES = set("nt\\'\"@?")
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?([sdfx])")


def line_of(text: str, index: int) -> int:
    return text.count("\n", 0, index) + 1


def check_escapes(path: Path, text: str) -> list[str]:
    problems: list[str] = []
    for match in re.finditer(r"\\(.)", text, re.DOTALL):
        nxt = match.group(1)
        if nxt == "u":
            hexits = text[match.end():match.end() + 4]
            if len(hexits) != 4 or any(c not in "0123456789abcdefABCDEF" for c in hexits):
                problems.append(
                    f"::error file={path},line={line_of(text, match.start())}::"
                    f"'\\u' must be followed by four hex digits, found '{hexits}'"
                )
        elif nxt not in VALID_ESCAPES:
            problems.append(
                f"::error file={path},line={line_of(text, match.start())}::"
                f"'\\{nxt}' is not an escape aapt2 accepts"
            )
    return problems


def check_apostrophes(path: Path, element: ET.Element, text: str) -> list[str]:
    """An unescaped `'` or `"` outside a quoted span is what aapt2 rejects."""
    problems: list[str] = []
    body = element.text or ""
    in_quotes = False
    escaped = False
    for char in body:
        if escaped:
            escaped = False
            continue
        if char == "\\":
            escaped = True
            continue
        if char == '"':
            in_quotes = not in_quotes
            continue
        if char in "'\"" and not in_quotes:
            problems.append(
                f"::error file={path}::string '{element.get('name')}' contains an "
                f"unescaped {char!r}: escape it as \\{char} or wrap it in \"double quotes\""
            )
            break
    return problems


def placeholder_signature(body: str) -> tuple[str, ...]:
    return tuple(
        f"{m.group(1) or '?'}{m.group(2)}" for m in PLACEHOLDER.finditer(body)
    )


def main() -> int:
    problems: list[str] = []
    by_name: dict[str, dict[str, tuple[str, ...]]] = {}

    for path in sorted(RES.glob("values*/**/*.xml")):
        text = path.read_text(encoding="utf-8")
        try:
            tree = ET.fromstring(text)
        except ET.ParseError as error:
            problems.append(f"::error file={path}::not well-formed XML: {error}")
            continue
        problems.extend(check_escapes(path, text))
        for element in tree.iter("string"):
            body = element.text or ""
            problems.extend(check_apostrophes(path, element, text))
            signature = placeholder_signature(body)
            numbers = [p for p in signature if p[0] != "?"]
            if numbers and len(numbers) != len(signature):
                problems.append(
                    f"::error file={path}::string '{element.get('name')}' mixes numbered "
                    f"and positional placeholders: {signature}"
                )
            by_name.setdefault(element.get("name", ""), {})[path.parent.name] = signature

    # A translation must keep the same placeholders as the default strings.
    for name, variants in sorted(by_name.items()):
        default = variants.get("values")
        if default is None:
            continue
        for qualifier, signature in variants.items():
            if qualifier == "values":
                continue
            if signature != default:
                problems.append(
                    f"::error::string '{name}' has placeholders {signature} in "
                    f"{qualifier}, but {default} in values/"
                )

    for problem in problems:
        print(problem)
    if problems:
        return 1
    print(f"Checked {len(by_name)} string resources: formatting and escapes are valid.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
