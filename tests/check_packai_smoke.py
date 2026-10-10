#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static gate: `tools/packai_smoke.py`（真機驗收 driver）必須守嘅鐵則。

點解要有呢個閘：真機 driver 會部署 jar 去沙盒、開遊戲、寫檔。呢啲步驟 2026-10-10 之前係
每次手寫（當晚 3 份），靠人記 → 易漏「備份／還原／清 cases.json／唔准 hot-copy」。
本閘把「鐵則」變成機械檢查，改壞就即刻紅。
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "packai_smoke.py"


def fail(msg: str) -> None:
    print(msg)
    raise SystemExit(1)


def main() -> None:
    if not TOOL.is_file():
        fail("missing tools/packai_smoke.py")
    src = TOOL.read_text(encoding="utf-8")
    compile(src, str(TOOL), "exec")          # 真語法檢查（唔靠 py_compile 子進程）

    # 1) 部署只准經官方工具（唔准 hot-copy 入 mods）
    if "mc_mod_deploy_jar.py" not in src:
        fail("driver 冇引用 mc_mod_deploy_jar.py（唔准自寫部署）")
    if "run([sys.executable, DEPLOY" not in src:
        fail("driver 冇真正呼叫 DEPLOY（mc_mod_deploy_jar.py）")
    # 唯一合法嘅 mods 內寫入 = restore() 函數體內（backup() 係由 mods 抄出去，唔算）。
    # 用 AST 判「喺邊個函數內」，唔用 regex／位置比大小（兩者都試過誤判）。
    import ast

    tree = ast.parse(src)
    parents = {}
    for node in ast.walk(tree):
        for child in ast.iter_child_nodes(node):
            parents[child] = node

    def enclosing_func(node):
        cur = node
        while cur is not None:
            cur = parents.get(cur)
            if isinstance(cur, (ast.FunctionDef, ast.AsyncFunctionDef)):
                return cur.name
        return "<module>"

    if not any(isinstance(n, ast.FunctionDef) and n.name == "restore" for n in ast.walk(tree)):
        fail("driver 揾唔到 restore()（還原路徑必須存在）")
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        f = node.func
        if not isinstance(f, ast.Attribute) or f.attr not in ("copy", "copy2", "copyfile"):
            continue
        seg = ast.get_source_segment(src, node) or ""
        if 'cfg["mods"]' not in seg:
            continue
        owner = enclosing_func(node)
        if owner != "restore":
            fail("restore() 以外有 copy 入 mods（%s）：%s" % (owner, " ".join(seg.split())[:110]))

    # 2) activity gate：SK 用緊機必須拒跑（RC=2）
    if '("playing", "using")' not in src:
        fail("driver 冇睇 activity gate 嘅 playing/using")
    if 'return 2' not in src:
        fail("driver gate 拒絕時冇 return 2")

    # 3) 前置檢查：殘留 cases.json ／ 沙盒已開住 ／ 多過一支 mod-class jar 都要擋
    for needle, why in (
        ("已有殘留 cases.json", "冇擋殘留 cases.json"),
        ("沙盒已經開住", "冇擋沙盒已開住"),
        ("必須 1", "冇檢查 mod-class jar 數目"),
        ("level.dat", "冇檢查世界 level.dat"),
    ):
        if needle not in src:
            fail("前置檢查缺：%s" % why)

    # 4) cases.json 必須原子寫（harness 每秒 poll）
    if "os.replace(" not in src or "cases.json.tmp" not in src:
        fail("cases.json 唔係原子寫（無 tmp + os.replace）")

    # 5) 收尾：還原 + 刪 cases.json + 硬殺前先等自動關機
    if "cleanup(cfg)" not in src:
        fail("冇 cleanup() 收尾")
    if "def restore(cfg: dict)" not in src or "restore(cfg)" not in src:
        fail("cleanup 冇還原")
    if "wait_exit_s" not in src:
        fail("kill_sandbox 冇「先等 harness 自動關機」")
    if 'os.remove(cases)' not in src:
        fail("冇刪 cases.json（殘留＝下次開機再跑付費 ask）")

    # 6) 次序：備份一定要喺部署之前
    i_backup, i_deploy = src.find("        backup(cfg)"), src.find("            err = deploy(cfg")
    if i_backup < 0 or i_deploy < 0 or i_backup > i_deploy:
        fail("次序錯：backup() 必須喺 deploy() 之前")

    # 7) 開窗：要移去副螢幕（唔准擋主螢幕）—— 要見到真呼叫（唔止見到常數名）
    if "move_win_by_pid.py" not in src:
        fail("開窗冇用 move_win_by_pid.py")
    if 'os.path.isfile(MOVE_WIN)' not in src or 'MOVE_WIN, cfg["instance"], "1"' not in src:
        fail("冇真呼叫移窗去副螢幕（move_win_by_pid + monitor 1）")

    # 8) 收證據 + summary.json（每次 run 都要有統一輸出）
    if "summary.json" not in src or "_write_summary" not in src:
        fail("冇 summary.json 輸出")

    # 9) instance 邊界：唔准用 PowerShell `-like` 淨前綴 match instance（packai_sandbox ⊂ _atm8）
    if re.search(r"-like '\*[^']*%s[^']*\*'", src):
        fail("用咗 PS `-like` pattern 認 instance（會撞 packai_sandbox_atm8）")

    print("check_packai_smoke: OK")


if __name__ == "__main__":
    main()
