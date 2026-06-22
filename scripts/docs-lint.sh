#!/usr/bin/env bash
set -euo pipefail

ROOT_DOCS="docs"

declare -a REQUIRED_DIRS=(
  "indexes"
  "standards"
  "architecture"
  "domains"
  "runbooks"
  "decisions"
  "archive"
  "superpowers"
)

declare -a REQUIRED_INDEXES=(
  "architecture-index.md"
  "standards-index.md"
  "domains-index.md"
  "runbooks-index.md"
  "decisions-index.md"
  "archive-index.md"
  "working-docs-index.md"
)

fail() {
  echo "docs-lint: $1" >&2
  exit 1
}

[[ -f "${ROOT_DOCS}/README.md" ]] || fail "missing docs/README.md"

for dir in "${REQUIRED_DIRS[@]}"; do
  [[ -d "${ROOT_DOCS}/${dir}" ]] || fail "missing docs/${dir}"
done

for index_file in "${REQUIRED_INDEXES[@]}"; do
  [[ -f "${ROOT_DOCS}/indexes/${index_file}" ]] || fail "missing docs/indexes/${index_file}"
done

while IFS= read -r file; do
  [[ "$(basename "$file")" == ".gitkeep" ]] && continue
  [[ -s "$file" ]] || fail "empty official docs file: $file"
  grep -q "Status:" "$file" || fail "missing Status metadata: $file"
  grep -q "Audience:" "$file" || fail "missing Audience metadata: $file"
  grep -q "Source of Truth:" "$file" || fail "missing Source of Truth metadata: $file"
  grep -q "Last Reviewed:" "$file" || fail "missing Last Reviewed metadata: $file"
done < <(
  {
    printf '%s\n' "${ROOT_DOCS}/README.md"
    find "${ROOT_DOCS}/indexes" "${ROOT_DOCS}/standards" "${ROOT_DOCS}/architecture" "${ROOT_DOCS}/domains" "${ROOT_DOCS}/runbooks" "${ROOT_DOCS}/decisions" "${ROOT_DOCS}/archive" -type f | sort
  } | sort -u
)

python3 - "$PWD" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
known_files = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()}
known_dirs = {p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_dir()}
link_pattern = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
failures = []

official_roots = [
    root / "docs" / "indexes",
    root / "docs" / "standards",
    root / "docs" / "architecture",
    root / "docs" / "domains",
    root / "docs" / "runbooks",
    root / "docs" / "decisions",
    root / "docs" / "archive",
]

indexed_roots = [
    root / "docs" / "standards",
    root / "docs" / "architecture",
    root / "docs" / "domains",
    root / "docs" / "runbooks",
    root / "docs" / "decisions",
    root / "docs" / "archive",
]

markdown_files = [root / "docs" / "README.md"]
for official_root in official_roots:
    if official_root.exists():
        markdown_files.extend(official_root.rglob("*.md"))

agents = root / "AGENTS.md"
if agents.exists():
    markdown_files.append(agents)

for file in sorted(markdown_files):
    rel_file = file.relative_to(root).as_posix()
    for lineno, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
        for match in link_pattern.finditer(line):
            raw_target = match.group(1).strip()
            target = raw_target.split("#", 1)[0].strip()
            if not target:
                continue
            if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target):
                continue
            if target.startswith("/"):
                failures.append(f"{rel_file}:{lineno}: absolute local link is not allowed: {raw_target}")
                continue

            resolved = (file.parent / target).resolve()
            try:
                rel_target = resolved.relative_to(root).as_posix()
            except ValueError:
                failures.append(f"{rel_file}:{lineno}: link escapes repository: {raw_target}")
                continue

            if rel_target not in known_files and rel_target not in known_dirs:
                failures.append(f"{rel_file}:{lineno}: missing local link target: {raw_target}")

indexed_targets = set()
for index_file in sorted((root / "docs" / "indexes").glob("*.md")):
    for line in index_file.read_text(encoding="utf-8").splitlines():
        for match in link_pattern.finditer(line):
            raw_target = match.group(1).strip()
            target = raw_target.split("#", 1)[0].strip()
            if not target or re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target) or target.startswith("/"):
                continue

            resolved = (index_file.parent / target).resolve()
            try:
                rel_target = resolved.relative_to(root).as_posix()
            except ValueError:
                continue

            if rel_target in known_files:
                indexed_targets.add(rel_target)

for official_root in indexed_roots:
    if not official_root.exists():
        continue
    for official_file in sorted(official_root.rglob("*")):
        if not official_file.is_file() or official_file.name == ".gitkeep":
            continue
        rel_file = official_file.relative_to(root).as_posix()
        if rel_file not in indexed_targets:
            failures.append(f"{rel_file}: official docs file is not linked from docs/indexes/*.md")

if failures:
    for failure in failures:
        print(f"docs-lint: {failure}", file=sys.stderr)
    sys.exit(1)
PY

echo "docs-lint: OK"
