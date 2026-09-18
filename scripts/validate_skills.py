#!/usr/bin/env python3
"""Skill 结构校验：frontmatter 规范 + references 链接有效性 + 内部相对链接。

用法：python scripts/validate_skills.py  （CI 与本地共用，退出码非 0 即失败）
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SKILLS_DIR = ROOT / "skills"
MAX_DESC = 1024
failures = []


def fail(msg: str) -> None:
    failures.append(msg)


def check_skill_dir(skill_dir: Path) -> None:
    skill_md = skill_dir / "SKILL.md"
    name = skill_dir.name
    if not skill_md.is_file():
        fail(f"{name}: 缺少 SKILL.md")
        return
    text = skill_md.read_text(encoding="utf-8")

    # frontmatter：--- 包裹、恰含 name 与 description 两键
    m = re.match(r"^---\r?\n(.*?)\r?\n---\r?\n", text, re.DOTALL)
    if not m:
        fail(f"{name}: SKILL.md 缺少 frontmatter（--- 包裹）")
        return
    entries = {
        line.split(":", 1)[0].strip(): line.split(":", 1)[1].strip()
        for line in m.group(1).splitlines()
        if line.strip() and not line.strip().startswith("#")
    }
    if set(entries) != {"name", "description"}:
        fail(f"{name}: frontmatter 键应为恰含 name/description，实际 {sorted(entries)}")
        return
    if entries["name"] != name:
        fail(f"{name}: frontmatter name={entries['name']!r} 与目录名不一致")
    if len(entries["description"]) > MAX_DESC:
        fail(f"{name}: description {len(entries['description'])} 字符超过 {MAX_DESC}")

    # references 链接：正文引用的 references/x.md 必须存在
    for ref in set(re.findall(r"references/[\w.-]+\.md", text)):
        if not (skill_dir / ref).is_file():
            fail(f"{name}: 引用的 {ref} 不存在")

    # references 目录内未被引用的孤儿文件（提示级，不算失败则注释掉）
    ref_dir = skill_dir / "references"
    if ref_dir.is_dir():
        referenced = set(re.findall(r"references/[\w.-]+\.md", text))
        for f in ref_dir.glob("*.md"):
            if f"references/{f.name}" not in referenced:
                print(f"  提示: {name}/references/{f.name} 未被 SKILL.md 引用")


def main() -> int:
    if not SKILLS_DIR.is_dir():
        fail("skills/ 目录不存在")
    else:
        for skill_dir in sorted(p for p in SKILLS_DIR.iterdir() if p.is_dir()):
            check_skill_dir(skill_dir)

    if failures:
        print("Skill 校验失败：")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("Skill 校验通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
