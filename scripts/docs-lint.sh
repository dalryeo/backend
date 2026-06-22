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
done < <(
  {
    printf '%s\n' "${ROOT_DOCS}/README.md"
    find "${ROOT_DOCS}/indexes" "${ROOT_DOCS}/standards" "${ROOT_DOCS}/architecture" "${ROOT_DOCS}/domains" "${ROOT_DOCS}/runbooks" "${ROOT_DOCS}/decisions" "${ROOT_DOCS}/archive" -type f | sort
  } | sort -u
)

python3 - "$PWD" <<'PY'
from datetime import date
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
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

index_targets = {
    root / "docs" / "indexes" / "standards-index.md": root / "docs" / "standards",
    root / "docs" / "indexes" / "architecture-index.md": root / "docs" / "architecture",
    root / "docs" / "indexes" / "domains-index.md": root / "docs" / "domains",
    root / "docs" / "indexes" / "runbooks-index.md": root / "docs" / "runbooks",
    root / "docs" / "indexes" / "decisions-index.md": root / "docs" / "decisions",
    root / "docs" / "indexes" / "archive-index.md": root / "docs" / "archive",
}

metadata_keys = ("Status", "Audience", "Source of Truth", "Last Reviewed")
allowed_statuses = {"Active", "Draft", "Archived"}
allowed_sources = {"Yes", "No"}
active_non_source_exceptions = {"docs/indexes/working-docs-index.md"}


def rel(path):
    return path.relative_to(root).as_posix()


def is_relative_to(path, parent):
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def official_files():
    files = [root / "docs" / "README.md"]
    for official_root in official_roots:
        if official_root.exists():
            files.extend(
                path
                for path in official_root.rglob("*")
                if path.is_file() and path.name != ".gitkeep"
            )
    return sorted(set(files))


def markdown_files():
    files = [path for path in official_files() if path.suffix == ".md"]
    agents = root / "AGENTS.md"
    if agents.exists():
        files.append(agents)
    return sorted(set(files))


def parse_markdown_metadata(file, lines):
    if not lines or not lines[0].startswith("# "):
        failures.append(f"{rel(file)}: missing top-level title before metadata")
        return {}

    index = 1
    while index < len(lines) and not lines[index].strip():
        index += 1

    metadata = {}
    for key in metadata_keys:
        if index >= len(lines):
            failures.append(f"{rel(file)}: missing {key} metadata near top of file")
            return metadata
        match = re.match(rf"^- {re.escape(key)}:\s*(.+?)\s*$", lines[index])
        if not match:
            failures.append(
                f"{rel(file)}:{index + 1}: invalid metadata block, expected '- {key}: ...'"
            )
            return metadata
        metadata[key] = match.group(1).strip()
        index += 1
    return metadata


def parse_sql_metadata(file, lines):
    metadata = {}
    for index, key in enumerate(metadata_keys):
        if index >= len(lines):
            failures.append(f"{rel(file)}: missing {key} metadata near top of file")
            return metadata
        match = re.match(rf"^-- {re.escape(key)}:\s*(.+?)\s*$", lines[index])
        if not match:
            failures.append(
                f"{rel(file)}:{index + 1}: invalid metadata block, expected '-- {key}: ...'"
            )
            return metadata
        metadata[key] = match.group(1).strip()
    return metadata


def validate_metadata(file):
    rel_file = rel(file)
    lines = file.read_text(encoding="utf-8").splitlines()
    if file.suffix == ".md":
        metadata = parse_markdown_metadata(file, lines)
    elif file.suffix == ".sql":
        metadata = parse_sql_metadata(file, lines)
    else:
        return

    if set(metadata) != set(metadata_keys):
        return

    status = metadata["Status"]
    source = metadata["Source of Truth"]

    if status not in allowed_statuses:
        failures.append(f"{rel_file}: invalid Status metadata: {status}")
    if source not in allowed_sources:
        failures.append(f"{rel_file}: invalid Source of Truth metadata: {source}")
    if not metadata["Audience"]:
        failures.append(f"{rel_file}: empty Audience metadata")

    try:
        date.fromisoformat(metadata["Last Reviewed"])
    except ValueError:
        failures.append(f"{rel_file}: Last Reviewed must use YYYY-MM-DD")

    if status in {"Draft", "Archived"} and source != "No":
        failures.append(f"{rel_file}: {status} documents must use Source of Truth: No")
    if status == "Active" and source == "No" and rel_file not in active_non_source_exceptions:
        failures.append(f"{rel_file}: Active documents must use Source of Truth: Yes")
    if rel_file.startswith("docs/archive/") and status != "Archived":
        failures.append(f"{rel_file}: archive files must use Status: Archived")


for official_file in official_files():
    validate_metadata(official_file)

for file in markdown_files():
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

            if not resolved.exists():
                failures.append(f"{rel_file}:{lineno}: missing local link target: {raw_target}")

indexed_targets = set()
for index_file, target_root in sorted(index_targets.items()):
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

            if resolved.is_file() and is_relative_to(resolved, target_root):
                indexed_targets.add(rel_target)

for index_file, official_root in sorted(index_targets.items()):
    if not official_root.exists():
        continue
    for official_file in sorted(official_root.rglob("*")):
        if not official_file.is_file() or official_file.name == ".gitkeep":
            continue
        rel_file = official_file.relative_to(root).as_posix()
        if rel_file not in indexed_targets:
            failures.append(f"{rel_file}: official docs file is not linked from {rel(index_file)}")

if failures:
    for failure in failures:
        print(f"docs-lint: {failure}", file=sys.stderr)
    sys.exit(1)
PY

echo "docs-lint: OK"
