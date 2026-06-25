#!/usr/bin/env python3
"""Lightweight pull request body check.

Validates that a PR description follows the repository PR template:
  - all required section headings are present
  - both "Before Submitting" checkboxes are checked
  - exactly one "AI Assistance" checkbox is checked
  - if AI assistance was used, "Tools used:" and "How extensively:" are filled in

Offline and secret-free. In CI it reads the PR body from the event payload at
$GITHUB_EVENT_PATH (.pull_request.body). For local use, pass a file path as the first
argument, or pipe the body on stdin:

    .github/scripts/pr-body-check.py path/to/body.md
    cat body.md | .github/scripts/pr-body-check.py

Exits 0 if the body passes; otherwise prints each problem and exits 1.
"""
import json
import os
import re
import sys

REQUIRED_HEADINGS = [
    "## Before Submitting This PR",
    "## Human Written Description",
    "## Related Issues/Discussions",
    "## Community Feedback",
    "## Testing",
    "## Screenshots/Videos (if applicable)",
    "## AI Assistance",
]

BEFORE_SUBMITTING_LABELS = [
    "I have searched existing issues and pull requests, including closed ones, to ensure this is not a duplicate.",
    "I have read CONTRIBUTING.md.",
]

AI_NOT_USED_LABEL = "No AI assistance was used."
AI_USED_LABEL = "AI assistance was used."

CHECKBOX = re.compile(r"^\s*-\s*\[( |x|X)\]\s*(.*\S)\s*$")


def read_body():
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if event_path and os.path.exists(event_path):
        with open(event_path, encoding="utf-8") as handle:
            event = json.load(handle)
        return ((event.get("pull_request") or {}).get("body")) or ""
    if len(sys.argv) > 1:
        with open(sys.argv[1], encoding="utf-8") as handle:
            return handle.read()
    return sys.stdin.read()


def section(body, heading):
    """Return the lines from `heading` up to the next level-2 heading (or end)."""
    out, capturing = [], False
    for line in body.splitlines():
        if line.strip() == heading:
            capturing = True
            continue
        if capturing and line.startswith("## "):
            break
        if capturing:
            out.append(line)
    return "\n".join(out)


def checkbox_state(text):
    """Map each checkbox label in `text` to its checked state (later lines win on duplicates)."""
    state = {}
    for line in text.splitlines():
        match = CHECKBOX.match(line)
        if match:
            state[match.group(2).strip()] = match.group(1).lower() == "x"
    return state


def field_value(text, label):
    """Inline value after `label` (e.g. "Tools used:"), or None if the label is absent."""
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.lower().startswith(label.lower()):
            return stripped[len(label):].strip()
    return None


def main():
    body = read_body()
    problems = []

    # Headings must appear as exact heading lines, not merely as a substring somewhere.
    heading_lines = {line.strip() for line in body.splitlines()}
    for heading in REQUIRED_HEADINGS:
        if heading not in heading_lines:
            problems.append(f"missing required heading line: {heading}")

    # The two "Before Submitting" checkboxes must be present (exact labels) and checked.
    before = checkbox_state(section(body, "## Before Submitting This PR"))
    for label in BEFORE_SUBMITTING_LABELS:
        if label not in before:
            problems.append(f'missing "Before Submitting" checkbox: {label}')
        elif not before[label]:
            problems.append(f'"Before Submitting" checkbox must be checked: {label}')

    # Both AI checkboxes must be present (exact labels) and exactly one of them checked.
    ai_text = section(body, "## AI Assistance")
    ai = checkbox_state(ai_text)
    missing_ai = [label for label in (AI_NOT_USED_LABEL, AI_USED_LABEL) if label not in ai]
    for label in missing_ai:
        problems.append(f'missing "AI Assistance" checkbox: {label}')
    if not missing_ai:
        if sum(1 for label in (AI_NOT_USED_LABEL, AI_USED_LABEL) if ai[label]) != 1:
            problems.append('exactly one "AI Assistance" checkbox must be checked')
        elif ai[AI_USED_LABEL]:
            if not field_value(ai_text, "Tools used:"):
                problems.append('AI assistance was used: "Tools used:" must not be blank')
            if not field_value(ai_text, "How extensively:"):
                problems.append('AI assistance was used: "How extensively:" must not be blank')

    if problems:
        print("PR body check failed:")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print("PR body check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
