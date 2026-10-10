#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""packai 真機驗收 driver（可重用）——一條命令跑完：閘 → 備份 → 部署 → 落 case → 開沙盒 →
等 status → 收證據 → 還原 → 出 summary.json。

為什麼存在：2026-10-10 之前每一輪真機驗收都要**由零手寫一份 300+ 行 driver**（當晚寫咗 3 份），
步驟靠人記，易漏（備份／還原／清 cases.json）。本檔把已驗證嘅步驟固化。

鐵則（違反＝唔准跑）
  1. 只郁沙盒 instance（`--instance`，預設 packai_sandbox）；**真 instance 永不部署**。
  2. 部署只准經 `mc_mod_deploy_jar.py`（自帶 backup ＋ sha 驗證）；**禁止 hot-copy**。
  3. SK 打機／用機（activity=playing/using）⇒ 拒絕開遊戲（`--force` 只可以喺 SK 明確批准先用）。
  4. 所有寫入前先備份；收尾必須還原 + 刪 `cases.json`（殘留＝下次開機會自動再跑付費 ask）。

用法
  python tools/packai_smoke.py --cases cases.json
  python tools/packai_smoke.py --cases cases.json --instance packai_sandbox_atm8 --wait-idle-min 60
  python tools/packai_smoke.py --dry-run --cases cases.json      # 只做檢查，唔部署唔開遊戲

cases.json 格式（同 harness 一致；bare list 亦可）
  {"world": "新的世界 (1)", "quitWhenDone": true, "cases": [{"id": "x", "item": "...", "question": "..."}]}

Exit code
  0 正常完成   2 被 gate 擋   3 等唔到 status／timeout   4 開遊戲或移窗失敗
  5 部署失敗   6 前置檢查失敗（沙盒唔乾淨）   9 內部錯誤
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import zipfile

TEMP = os.path.join(os.environ.get("LOCALAPPDATA", os.path.expanduser("~")), "Temp")
HERMES = os.path.join(os.environ.get("LOCALAPPDATA", ""), "hermes")
ACTIVITY = os.path.join(HERMES, "state", "sk_activity.json")
PEAK = os.path.join(HERMES, "scripts", "ds_peak_hours.py")
DEPLOY = os.path.join(HERMES, "scripts", "mc_mod_deploy_jar.py")
SKILL_SCRIPTS = os.path.join(
    HERMES, "skills", "software-development", "minecraft-mod-in-game-autotest", "scripts"
)
MOVE_WIN = os.path.join(SKILL_SCRIPTS, "move_win_by_pid.py")
DEFAULT_PRISM = (os.environ.get("PACKAI_PRISM")
                 or r"C:\Users\skps9\Documents\PrismLauncher-Windows-MinGW-w64-Portable-11.1.0")
REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_JAR = os.path.join(REPO, "forge", "1.19.2", "build", "libs", "autotest-dev-0.2.3.jar")
DST_JAR_NAME = "packai-autotest-dev.jar"
MAGIC = '{"packaiAutotest":1,'
MOD_CLASS_SUFFIX = "com/skps9/packai/logic/AskEngine.class"
CREATE_NO_WINDOW = 0x08000000
STAMP = time.strftime("%Y%m%d-%H%M%S")

_logfh = None


def log(msg: str) -> None:
    line = "%s %s" % (time.strftime("%H:%M:%S"), msg)
    print(line, flush=True)
    if _logfh:
        _logfh.write(line + "\n")
        _logfh.flush()


def run(cmd, **kw):
    kw.setdefault("capture_output", True)
    kw.setdefault("creationflags", CREATE_NO_WINDOW)
    r = subprocess.run(cmd, **kw)
    out = (r.stdout or b"").decode("utf-8", "replace")
    err = (r.stderr or b"").decode("utf-8", "replace")
    return r.returncode, out, err


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def ps(script: str) -> str:
    _, out, _ = run(["powershell", "-NoProfile", "-Command", script])
    return out.strip()


def ps_json(script: str):
    """PowerShell → JSON → Python（空／null 一律當 []）。"""
    out = ps(script)
    try:
        d = json.loads(out or "null")
    except Exception:
        return []
    if d is None:
        return []
    return [d] if isinstance(d, dict) else d


def game_pids(instance: str) -> list:
    """沙盒 java pid。⚠️ 一定要喺 Python 側用路徑**邊界**過濾：
    `packai_sandbox` 係 `packai_sandbox_atm8` 嘅前綴，用 PowerShell `-like '*<name>*'` 會撞錯 instance；
    而且 PS 單引號入面 `\\\\` 係兩個反斜線（`-like` 唔會當 escape）→ 實測兩個寫法都會亂 match。
    """
    procs = ps_json("Get-CimInstance Win32_Process -Filter \"Name='javaw.exe' or Name='java.exe'\" | "
                    "Select-Object ProcessId,CommandLine | ConvertTo-Json -Compress")
    pat = re.compile(r"instances[\\/]" + re.escape(instance) + r"[\\/]")
    return [p["ProcessId"] for p in procs if pat.search(p.get("CommandLine") or "")]


def fg_hwnd() -> int:
    v = ps("Add-Type -Namespace W -Name U -MemberDefinition '[DllImport(\"user32.dll\")] "
           "public static extern System.IntPtr GetForegroundWindow();'; "
           "[W.U]::GetForegroundWindow().ToString()")
    try:
        return int(v)
    except Exception:
        return 0


def fg_process() -> str:
    return ps("Add-Type -Namespace W -Name U -MemberDefinition '[DllImport(\"user32.dll\")] "
              "public static extern System.IntPtr GetForegroundWindow(); "
              "[DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId"
              "(System.IntPtr h, out uint p);'; "
              "$h=[W.U]::GetForegroundWindow(); $p=0; [void][W.U]::GetWindowThreadProcessId($h,[ref]$p); "
              "(Get-Process -Id $p -ErrorAction SilentlyContinue).ProcessName") or "?"


def restore_focus(hwnd: int) -> bool:
    if not hwnd:
        return False
    ps("Add-Type -Namespace W -Name U -MemberDefinition '[DllImport(\"user32.dll\")] "
       "public static extern bool SetForegroundWindow(System.IntPtr h);'; "
       "[W.U]::SetForegroundWindow([System.IntPtr]%d)" % hwnd)
    return True


def mod_class_jars(mods: str) -> list:
    hits = []
    for jar in glob.glob(os.path.join(mods, "*.jar")):
        try:
            with zipfile.ZipFile(jar) as z:
                if any(n.endswith(MOD_CLASS_SUFFIX) for n in z.namelist()):
                    hits.append((os.path.basename(jar), os.path.getsize(jar)))
        except Exception:
            continue
    return hits


def activity() -> tuple:
    try:
        with open(ACTIVITY, encoding="utf-8") as fh:
            d = json.load(fh)
        return str(d.get("state", "")).lower(), int(d.get("idle_seconds") or 0)
    except Exception:
        return "unknown", 0


def ds_off_peak() -> bool:
    return run([sys.executable, PEAK, "--quiet"])[0] == 0


def usage_snapshot(cfg: dict) -> dict:
    try:
        with open(os.path.join(cfg["game"], "config", "packai-usage.json"), encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def load_cases(path: str) -> dict:
    with open(path, encoding="utf-8") as fh:
        raw = fh.read()
    try:
        d = json.loads(raw)
    except Exception as exc:
        raise SystemExit("cases file 唔係合法 JSON: %s (%r)" % (path, exc))
    if isinstance(d, list):
        d = {"cases": d}
    if not isinstance(d, dict) or not d.get("cases"):
        raise SystemExit("cases file 內冇 'cases' 陣列: %s" % path)
    for c in d["cases"]:
        if not c.get("id") or not (c.get("item") or c.get("question")):
            raise SystemExit("每條 case 要有 id ＋ (item 或 question)：%r" % (c,))
    return d


def build_cfg(a) -> dict:
    inst = a.instance
    sbx = a.prism_root or DEFAULT_PRISM
    root = os.path.join(sbx, "instances", inst)
    game = os.path.join(root, "minecraft")
    return {
        "prism": sbx, "instance": inst, "root": root, "game": game,
        "mods": os.path.join(game, "mods"),
        "case_dir": os.path.join(game, "packai", "autotest"),
        "trace": os.path.join(game, "packai", "trace"),
        "logs": os.path.join(game, "logs"),
        "usage": os.path.join(game, "config", "packai-usage.json"),
        "jar": a.jar or DEFAULT_JAR,
        "world": a.world,
        "backup": os.path.join(TEMP, "packai_smoke_backup_%s_%s" % (inst, STAMP)),
        "out": a.out or os.path.join(TEMP, "packai_smoke_results_%s_%s" % (inst, STAMP)),
    }


def preflight(cfg: dict) -> str:
    if not os.path.isdir(cfg["game"]):
        return "沙盒路徑唔存在：%s" % cfg["game"]
    if not os.path.isfile(cfg["jar"]):
        return "jar 唔存在：%s（先 build：./gradlew jar -PpackaiAutotest）" % cfg["jar"]
    if not os.path.isfile(os.path.join(cfg["root"], "instance.cfg")):
        return "唔似 instance 目錄（冇 instance.cfg）：%s" % cfg["root"]
    leftover = os.path.join(cfg["case_dir"], "cases.json")
    if os.path.exists(leftover):
        return "沙盒已有殘留 cases.json（上次收尾失敗）：%s" % leftover
    world = os.path.join(cfg["game"], "saves", cfg["world"], "level.dat")
    if cfg["world"] and not os.path.isfile(world):
        return "世界冇 level.dat：%s" % world
    jars = mod_class_jars(cfg["mods"])
    if len(jars) != 1:
        return "沙盒 mods 內含本 mod class 嘅 jar 有 %d 支（必須 1）：%s" % (len(jars), jars)
    if game_pids(cfg["instance"]):
        return "沙盒已經開住（java pid=%s）——先關 game" % game_pids(cfg["instance"])
    return ""


def backup(cfg: dict) -> None:
    import shutil
    os.makedirs(cfg["backup"], exist_ok=True)
    for name in ("trace", "logs"):
        src = cfg[name]
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(cfg["backup"], name), dirs_exist_ok=True)
    jar = os.path.join(cfg["mods"], DST_JAR_NAME)
    if os.path.isfile(jar):
        shutil.copy2(jar, os.path.join(cfg["backup"], DST_JAR_NAME))
    n = len(glob.glob(os.path.join(cfg["trace"], "*.jsonl")))
    log("  backup -> %s (trace jsonl=%d)" % (cfg["backup"], n))


def rotate_status(cfg: dict) -> int:
    import shutil
    stale = glob.glob(os.path.join(cfg["case_dir"], "status-*.json"))
    if not stale:
        return 0
    stash = os.path.join(cfg["backup"], "oldstatus")
    os.makedirs(stash, exist_ok=True)
    for p in stale:
        try:
            shutil.move(p, os.path.join(stash, os.path.basename(p)))
        except Exception as exc:  # noqa: BLE001
            log("  rotate %s 失敗：%r" % (p, exc))
    return len(stale)


def stop_gradle(cfg: dict) -> None:
    gw = os.path.join(REPO, "forge", "1.19.2", "gradlew.bat")
    if not os.path.isfile(gw):
        return
    rc, out, _ = run(["cmd.exe", "/c", gw, "--stop"])
    log("  gradle --stop rc=%s %s" % (rc, out.strip()[:120]))
    time.sleep(3)


def deploy(cfg: dict, expect_classes) -> str:
    src_sha = sha256(cfg["jar"])
    log("  source jar sha256=%s size=%d" % (src_sha[:16], os.path.getsize(cfg["jar"])))
    rc, out, err = run([sys.executable, DEPLOY, "--jar", cfg["jar"],
                        "--mods", cfg["mods"], "--name", DST_JAR_NAME])
    log("  deploy rc=%s" % rc)
    for line in (out + err).splitlines():
        log("    " + line)
    if rc != 0:
        return "deploy rc=%s" % rc
    dst = os.path.join(cfg["mods"], DST_JAR_NAME)
    if not os.path.isfile(dst):
        return "deploy 之後找唔到 %s" % dst
    dst_sha = sha256(dst)
    if dst_sha != src_sha:
        return "sha 唔對 deployed=%s source=%s" % (dst_sha[:16], src_sha[:16])
    if expect_classes:
        with zipfile.ZipFile(dst) as z:
            names = z.namelist()
        missing = [c for c in expect_classes if not any(n.endswith(c) for n in names)]
        if missing:
            return "jar 缺少預期 class：%s" % missing
    jars = mod_class_jars(cfg["mods"])
    if len(jars) != 1:
        return "部署後本 mod class jar 數目=%d（必須 1）：%s" % (len(jars), jars)
    log("  deployed sha=%s（mod-class jar 恰 1 支）" % dst_sha[:16])
    return ""


def write_cases(cfg: dict, spec: dict) -> None:
    os.makedirs(cfg["case_dir"], exist_ok=True)
    body = {"packaiAutotest": 1, "world": cfg["world"],
            "quitWhenDone": bool(spec.get("quitWhenDone", True)), "cases": spec["cases"]}
    txt = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    assert txt.startswith(MAGIC), txt[:40]
    tmp = os.path.join(cfg["case_dir"], "cases.json.tmp")
    with open(tmp, "w", encoding="utf-8") as fh:
        fh.write(txt)
    os.replace(tmp, os.path.join(cfg["case_dir"], "cases.json"))   # 原子寫（harness 每秒 poll）
    log("  cases.json written（%d 條，world=%s，原子寫）" % (len(spec["cases"]), cfg["world"]))


def wait_idle(cfg: dict, minutes: int, need: int) -> bool:
    end = time.time() + minutes * 60
    while True:
        st, idle = activity()
        if st == "idle" and idle >= need:
            log("  idle gate PASS state=%s idle=%ss" % (st, idle))
            return True
        if time.time() >= end:
            log("  idle gate 等夠 %d 分鐘仍未見 idle（state=%s idle=%ss）" % (minutes, st, idle))
            return False
        time.sleep(30)


def launch(cfg: dict) -> tuple:
    hwnd0, proc0 = fg_hwnd(), fg_process()
    log("  foreground before: %s hwnd=%s" % (proc0, hwnd0))
    exe = os.path.join(cfg["prism"], "prismlauncher.exe")
    subprocess.Popen([exe, "-l", cfg["instance"]], cwd=cfg["prism"], creationflags=CREATE_NO_WINDOW)
    t0 = time.time()
    log("  launched %s" % cfg["instance"])
    time.sleep(50)                                # 等遊戲窗出現
    restore_focus(hwnd0)
    moved = False
    if os.path.isfile(MOVE_WIN):
        for _ in range(18):
            rc, out, _ = run([sys.executable, MOVE_WIN, cfg["instance"], "1"])
            if '"on_target_monitor": true' in (out or "").lower():
                moved = True
                log("  移窗去副螢幕 OK")
                break
            time.sleep(10)
        if not moved:
            log("  ⚠️ 移窗去副螢幕失敗（唔阻流程，但會遮住 SK 主螢幕）")
    return t0, hwnd0, proc0, moved


def wait_status(cfg: dict, since: float, timeout_s: int):
    end = time.time() + timeout_s
    while time.time() < end:
        for p in sorted(glob.glob(os.path.join(cfg["case_dir"], "status-*.json")),
                        key=os.path.getmtime):
            if os.path.getmtime(p) >= since:
                time.sleep(3)                     # 等寫完
                return p
        time.sleep(5)
    return None


def collect(cfg: dict, since: float) -> dict:
    import shutil
    os.makedirs(cfg["out"], exist_ok=True)
    n = 0
    for p in glob.glob(os.path.join(cfg["trace"], "ask-*.jsonl")):
        if os.path.getmtime(p) >= since - 60:
            shutil.copy2(p, cfg["out"])
            n += 1
    for name in ("latest.log",):
        p = os.path.join(cfg["logs"], name)
        if os.path.isfile(p):
            shutil.copy2(p, cfg["out"])
    log("  collected %d trace file(s) -> %s" % (n, cfg["out"]))
    return {"traces": n, "dir": cfg["out"]}


def kill_sandbox(cfg: dict, wait_exit_s: int = 60) -> dict:
    """harness quitWhenDone 會自己關；等唔到才硬殺（按 Python 側算出嘅 pid，唔用 PS pattern）。"""
    t0 = time.time()
    while time.time() - t0 < wait_exit_s:
        if not game_pids(cfg["instance"]):
            return {"killed": False, "waited_s": round(time.time() - t0, 1)}
        time.sleep(3)
    pids = game_pids(cfg["instance"])
    if pids:
        ps("Stop-Process -Id %s -Force -ErrorAction SilentlyContinue" % ",".join(str(p) for p in pids))
        log("  ⚠️ 等唔到自動關機，已硬殺 pid=%s" % pids)
        return {"killed": True, "pids": pids, "waited_s": round(time.time() - t0, 1)}
    return {"killed": False, "waited_s": round(time.time() - t0, 1)}


def restore(cfg: dict) -> None:
    import shutil
    for name in ("trace", "logs"):
        src = os.path.join(cfg["backup"], name)
        if not os.path.isdir(src):
            continue
        try:
            shutil.rmtree(cfg[name], ignore_errors=True)
            shutil.copytree(src, cfg[name])
            log("  restored %s" % cfg[name])
        except Exception as exc:  # noqa: BLE001
            log("  ⚠️ restore %s 失敗：%r（證據已 collect）" % (name, exc))
    jar = os.path.join(cfg["backup"], DST_JAR_NAME)
    if os.path.isfile(jar):
        try:
            shutil.copy2(jar, os.path.join(cfg["mods"], DST_JAR_NAME))
            log("  restored sandbox jar")
        except Exception as exc:  # noqa: BLE001
            log("  ⚠️ restore jar 失敗：%r" % exc)


def cleanup(cfg: dict) -> dict:
    info = {}
    info["kill"] = kill_sandbox(cfg)
    restore(cfg)
    cases = os.path.join(cfg["case_dir"], "cases.json")
    try:
        if os.path.exists(cases):
            os.remove(cases)
        info["cases_removed"] = not os.path.exists(cases)
    except Exception as exc:  # noqa: BLE001
        info["cases_removed"] = False
        info["cases_error"] = repr(exc)
        log("  ⚠️ cases.json 刪唔到：%r（下次開機會自動再跑付費 ask！）" % exc)
    for extra in ("cases.json.tmp",):
        p = os.path.join(cfg["case_dir"], extra)
        if os.path.exists(p):
            os.remove(p)
    log("  cases.json 收尾存在 = %s（必須 False）" % os.path.exists(cases))
    return info


def main() -> int:
    ap = argparse.ArgumentParser(description="packai 真機驗收 driver（可重用）")
    ap.add_argument("--cases", required=True, help="cases JSON（harness 格式）")
    ap.add_argument("--jar", default=None, help="autotest flagged jar（預設 build/libs/autotest-dev-0.2.3.jar）")
    ap.add_argument("--instance", default=os.environ.get("PACKAI_SANDBOX", "packai_sandbox"))
    ap.add_argument("--world", default=os.environ.get("PACKAI_WORLD", "新的世界 (1)"))
    ap.add_argument("--prism-root", default=None)
    ap.add_argument("--out", default=None, help="證據輸出夾（預設 %%TEMP%%\\packai_smoke_results_<stamp>）")
    ap.add_argument("--timeout-min", type=int, default=30)
    ap.add_argument("--wait-idle-min", type=int, default=0, help=">0 = 等 SK idle 才開遊戲（最多 N 分鐘）")
    ap.add_argument("--idle-seconds", type=int, default=120)
    ap.add_argument("--expect-class", action="append", default=[], help="部署後要存在嘅 class 尾綴（可重複）")
    ap.add_argument("--no-deploy", action="store_true")
    ap.add_argument("--no-gradle-stop", action="store_true")
    ap.add_argument("--no-launch", action="store_true", help="只做閘／備份／部署／落 case")
    ap.add_argument("--force", action="store_true", help="SK 明確批准時才可用：無視 activity gate")
    ap.add_argument("--dry-run", action="store_true", help="只做前置檢查，唔寫檔唔開遊戲")
    a = ap.parse_args()

    global _logfh
    cfg = build_cfg(a)
    os.makedirs(TEMP, exist_ok=True)
    _logfh = open(os.path.join(TEMP, "packai_smoke_driver_%s.log" % STAMP), "w", encoding="utf-8")
    summary = {"stamp": STAMP, "instance": cfg["instance"], "world": cfg["world"],
               "dry_run": a.dry_run, "ok": False}
    log("=== 0) 前置檢查 ===")
    problem = preflight(cfg)
    if problem:
        log("  ABORT: %s" % problem)
        summary["abort"] = problem
        _write_summary(cfg, summary)
        return 6
    log("  前置 OK（沙盒乾淨、jar 存在、世界有 level.dat、mod-class jar 1 支）")
    try:
        spec = load_cases(a.cases)
    except SystemExit as exc:
        log("  ABORT: %s" % exc)
        summary["abort"] = "bad cases file: %s" % exc
        _write_summary(cfg, summary)
        return 6
    log("  cases=%d 條" % len(spec["cases"]))

    if a.dry_run:
        log("  --dry-run：唔部署、唔開遊戲")
        summary["ok"] = True
        summary["dry_run_ok"] = True
        _write_summary(cfg, summary)
        return 0

    st, idle = activity()
    log("=== 1) activity gate：state=%s idle=%ss | DS off_peak=%s" % (st, idle, ds_off_peak()))
    summary["activity"] = {"state": st, "idle": idle, "ds_off_peak": ds_off_peak()}
    if st in ("playing", "using") and not a.force:
        log("  ABORT: SK 用緊機（state=%s）。要跑就等 idle 或 SK 明確批准後用 --force" % st)
        summary["abort"] = "activity=%s" % st
        _write_summary(cfg, summary)
        return 2

    try:
        log("=== 2) 備份 ===")
        backup(cfg)
        log("  rotated %d 個舊 status" % rotate_status(cfg))
        if a.no_deploy:
            log("=== 3) 部署：--no-deploy（沿用現有 jar）===")
        else:
            log("=== 3) 部署 ===")
            if not a.no_gradle_stop:
                stop_gradle(cfg)
            err = deploy(cfg, a.expect_class)
            if err:
                log("  ABORT: %s" % err)
                summary["abort"] = err
                return 5
            summary["jar_sha"] = sha256(os.path.join(cfg["mods"], DST_JAR_NAME))
        log("=== 4) 落 case ===")
        write_cases(cfg, spec)
        summary["case_count"] = len(spec["cases"])
        if a.wait_idle_min > 0:
            log("=== 5) 等 SK idle（最多 %d 分鐘）===" % a.wait_idle_min)
            if not wait_idle(cfg, a.wait_idle_min, a.idle_seconds):
                summary["abort"] = "idle gate timeout"
                return 2
        u0 = usage_snapshot(cfg)
        if a.no_launch:
            log("  --no-launch：準備完成，等 SK 批准才開")
            summary["ok"] = True
            _write_summary(cfg, summary)
            return 0
        log("=== 6) 開沙盒 ===")
        t0, hwnd0, proc0, moved = launch(cfg)
        summary["window_moved"] = moved
        log("=== 7) 等 status（最多 %d 分鐘）===" % a.timeout_min)
        sp = wait_status(cfg, t0, a.timeout_min * 60)
        if not sp:
            log("  TIMEOUT：冇 status json")
            summary["abort"] = "no status json"
            return 3
        with open(sp, encoding="utf-8", errors="replace") as fh:
            status = json.load(fh)
        log("  status=%s cases=%s ok=%s elapsed=%sms"
            % (status.get("status"), status.get("caseCount"), status.get("ok"), status.get("elapsedMs")))
        summary["status"] = status
        log("=== 8) 收證據 ===")
        summary["collect"] = collect(cfg, t0)
        u1 = usage_snapshot(cfg)
        proc1 = fg_process()
        summary["foreground"] = {"before": proc0, "after": proc1}
        summary["focus_stolen"] = proc1.lower() not in (proc0.lower(), "", "?")
        lday, uday = time.strftime("%Y-%m-%d"), time.strftime("%Y-%m-%d", time.gmtime())
        summary["tokens"] = {
            "local": u1.get(lday, 0) - u0.get(lday, 0),
            "utc": u1.get(uday, 0) - u0.get(uday, 0),
        }
        summary["wall_s"] = round(time.time() - t0, 1)
        summary["ok"] = True
        log("  tokens(%s)=%s tokens(%s)=%s | foreground %s -> %s"
            % (lday, summary["tokens"]["local"], uday, summary["tokens"]["utc"], proc0, proc1))
        return 0
    except Exception as exc:  # noqa: BLE001
        summary["abort"] = "exception: %r" % exc
        log("  ⚠️ 例外：%r" % exc)
        return 9
    finally:
        log("=== 9) 收尾 ===")
        summary["cleanup"] = cleanup(cfg)
        _write_summary(cfg, summary)
        log("  summary -> %s" % os.path.join(cfg["out"], "summary.json"))
        if _logfh:
            _logfh.close()


def _write_summary(cfg: dict, summary: dict) -> None:
    os.makedirs(cfg["out"], exist_ok=True)
    try:
        with open(os.path.join(cfg["out"], "summary.json"), "w", encoding="utf-8") as fh:
            json.dump(summary, fh, ensure_ascii=False, indent=2)
    except Exception as exc:  # noqa: BLE001
        log("  ⚠️ 寫 summary 失敗：%r" % exc)


if __name__ == "__main__":
    sys.exit(main())
