#!/usr/bin/env python3
"""In-game autotest driver for THIS SERVER (Linux). Windows twin: tools/packai_smoke.py.

Same contract as the Windows script, minus the things that only make sense on SK's
desktop:
  * no foreground-window juggling / monitor moving (unattended server, nobody watching)
  * no idle gate (that reads the Windows activity file)
  * process handling via `pgrep -f instances/<name>/` instead of PowerShell

Kept identical on purpose:
  * deploy only through the jar guard rules: backup + sha256 verify + exactly ONE jar in
    mods containing com/skps9/packai/logic/AskEngine.class
  * cases.json must start with {"packaiAutotest":1,  (atomic write)
  * cleanup MUST delete cases.json (a leftover re-runs paid asks on the next launch)

Usage (from the repo root):
  python3 tools/packai_smoke_linux.py --cases /tmp/cases.json --instance packai_sandbox_startech
  python3 tools/packai_smoke_linux.py --cases /tmp/cases.json --dry-run
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import time
import zipfile

DEFAULT_PRISM_BIN = os.path.expanduser("~/opt/prismlauncher/PrismLauncher")
DEFAULT_PRISM_ROOT = os.path.expanduser("~/opt/prism-data")
DEFAULT_JAR = os.path.join("forge", "1.19.2", "build", "libs", "autotest-dev-0.2.3.jar")
DST_JAR_NAME = "packai-autotest-dev.jar"
MAGIC = '{"packaiAutotest":1,'
MOD_CLASS_SUFFIX = "com/skps9/packai/logic/AskEngine.class"
STAMP = time.strftime("%Y%m%d-%H%M%S")
DISPLAY = os.environ.get("PACKAI_DISPLAY", ":0")

_logfh = None


HERE = os.path.dirname(os.path.abspath(__file__))
X11_GUI = os.path.join(HERE, "x11_gui.py")
X11_SHOT = os.path.join(HERE, "x11_shot.py")
DEFAULT_X_PY = os.path.expanduser("~/opt/py-x/bin/python")


def log(msg: str) -> None:
    line = "%s %s" % (time.strftime("%H:%M:%S"), msg)
    print(line, flush=True)
    if _logfh:
        _logfh.write(line + "\n")
        _logfh.flush()


def run(cmd, timeout=600, **kw):
    """Run a command with DISPLAY set.

    Every child needs a display here: the Prism CLI talks to X even for a plain
    "launch this instance" request, and without DISPLAY it dies before forwarding the
    message to the running launcher (symptom: Prism never logs the request at all).
    """
    env = dict(os.environ)
    env["DISPLAY"] = DISPLAY
    kw.setdefault("env", env)
    cp = subprocess.run(cmd, capture_output=True, timeout=timeout, **kw)
    out = (cp.stdout or b"").decode("utf-8", "replace")
    err = (cp.stderr or b"").decode("utf-8", "replace")
    return cp.returncode, out, err


def sha256(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def game_pids(instance: str) -> list:
    """Sandbox java processes, matched on /proc/<pid>/cmdline.

    NOT `pgrep -f`: that also matches our own scp/bash lines that merely mention the
    instance name (a tarball is called packai_sandbox_e9e.tgz and my own shell wrapper
    carries the whole command). We therefore require BOTH:
      * the spawned executable is java/javaw (argv[0])
      * the command line references instances/<instance>/ with a real path boundary
    """
    needle = "instances/%s/" % instance
    pids = []
    for entry in glob.glob("/proc/[0-9]*"):
        pid = os.path.basename(entry)
        try:
            with open(os.path.join(entry, "cmdline"), "rb") as fh:
                raw = fh.read().split(b"\0")
        except Exception:
            continue
        argv = [x.decode("utf-8", "replace") for x in raw if x]
        if not argv:
            continue
        if os.path.basename(argv[0]) not in ("java", "javaw", "java.exe", "javaw.exe"):
            continue
        if needle in " ".join(argv):
            pids.append(int(pid))
    return pids


def mod_class_jars(mods: str) -> list:
    hits = []
    for jar in glob.glob(os.path.join(mods, "*.jar")):
        try:
            with zipfile.ZipFile(jar) as z:
                if MOD_CLASS_SUFFIX in z.namelist():
                    hits.append((os.path.basename(jar), os.path.getsize(jar)))
        except Exception:
            continue
    return hits


def load_cases(path: str) -> dict:
    with open(path, encoding="utf-8") as fh:
        d = json.load(fh)
    if isinstance(d, list):
        d = {"cases": d}
    if not isinstance(d, dict) or not d.get("cases"):
        raise SystemExit("cases file 內冇 'cases' 陣列：%s" % path)
    return d


def build_cfg(a) -> dict:
    root = os.path.join(a.prism_root, "instances", a.instance) if a.prism_root else \
        os.path.join(DEFAULT_PRISM_ROOT, "instances", a.instance)
    game = os.path.join(root, "minecraft")
    out = a.out or "/tmp/packai_smoke_results_%s_%s" % (a.instance, STAMP)
    return {
        "prism_bin": a.prism_bin, "prism_root": a.prism_root, "instance": a.instance,
        "root": root, "game": game,
        "mods": os.path.join(game, "mods"),
        "case_dir": os.path.join(game, "packai", "autotest"),
        "trace": os.path.join(game, "packai", "trace"),
        "logs": os.path.join(game, "logs"),
        "usage": os.path.join(game, "config", "packai-usage.json"),
        "jar": a.jar or DEFAULT_JAR, "world": a.world,
        "backup": "/tmp/packai_smoke_backup_%s_%s" % (a.instance, STAMP),
        "out": out, "offline": a.offline_name, "x_py": a.x_py, "gui_assist": a.gui_assist, "runlog": "/tmp/packai_smoke_prism_%s_%s.log" % (a.instance, STAMP),
    }


def preflight(cfg: dict) -> str:
    if not os.path.isdir(cfg["game"]):
        return "沙盒路徑唔存在：%s" % cfg["game"]
    if not os.path.isfile(cfg["jar"]):
        return "jar 唔存在：%s（先 build：./gradlew jar -PpackaiAutotest）" % cfg["jar"]
    if not os.path.isfile(os.path.join(cfg["root"], "instance.cfg")):
        return "唔似 instance 目錄（冇 instance.cfg）：%s" % cfg["root"]
    if not os.path.isfile(cfg["prism_bin"]):
        return "找唔到 PrismLauncher：%s" % cfg["prism_bin"]
    leftover = os.path.join(cfg["case_dir"], "cases.json")
    if os.path.exists(leftover):
        return "沙盒已有殘留 cases.json（上次收尾失敗）：%s" % leftover
    if cfg["world"]:
        world = os.path.join(cfg["game"], "saves", cfg["world"], "level.dat")
        if not os.path.isfile(world):
            return "世界冇 level.dat：%s" % world
    jars = mod_class_jars(cfg["mods"])
    if len(jars) > 1:
        return "沙盒 mods 內含本 mod class 嘅 jar 有 %d 支（多過 1，唔知用邊支）：%s" % (len(jars), jars)
    if len(jars) == 0:
        log("  preflight：沙盒未有本 mod jar（新裝 pack 正常）→ deploy 會放入去")
    pids = game_pids(cfg["instance"])
    if pids:
        return "沙盒已經開住（java pid=%s）——先關 game" % pids
    return ""


def backup(cfg: dict) -> None:
    os.makedirs(cfg["backup"], exist_ok=True)
    for name in ("trace", "logs"):
        src = cfg[name]
        if os.path.isdir(src):
            shutil.copytree(src, os.path.join(cfg["backup"], name), dirs_exist_ok=True)
    jar = os.path.join(cfg["mods"], DST_JAR_NAME)
    if os.path.isfile(jar):
        shutil.copy2(jar, os.path.join(cfg["backup"], DST_JAR_NAME))
    log("  backup -> %s" % cfg["backup"])


def rotate_status(cfg: dict) -> int:
    stale = glob.glob(os.path.join(cfg["case_dir"], "status-*.json"))
    if not stale:
        return 0
    stash = os.path.join(cfg["backup"], "oldstatus")
    os.makedirs(stash, exist_ok=True)
    for p in stale:
        shutil.move(p, os.path.join(stash, os.path.basename(p)))
    return len(stale)


def deploy(cfg: dict) -> str:
    src_sha = sha256(cfg["jar"])
    log("  source jar sha256=%s size=%d" % (src_sha[:16], os.path.getsize(cfg["jar"])))
    pids = game_pids(cfg["instance"])
    if pids:
        return "game 仲開住（pid=%s）——deploy 前必須關" % pids
    dst = os.path.join(cfg["mods"], DST_JAR_NAME)
    shutil.copy2(cfg["jar"], dst)
    if sha256(dst) != src_sha:
        return "sha 唔對 deployed=%s source=%s" % (sha256(dst)[:16], src_sha[:16])
    if len(mod_class_jars(cfg["mods"])) != 1:
        return "部署後本 mod class jar 數目=%d（必須 1）：%s" % (len(mod_class_jars(cfg["mods"])), mod_class_jars(cfg["mods"]))
    log("  deployed sha=%s（mod-class jar 恰 1 支）" % sha256(dst)[:16])
    return ""


def write_cases(cfg: dict, spec: dict) -> None:
    os.makedirs(cfg["case_dir"], exist_ok=True)
    body = {"packaiAutotest": 1, "world": cfg["world"],
            "quitWhenDone": bool(spec.get("quitWhenDone", True)), "cases": spec["cases"]}
    if spec.get("routeMarks"):
        body["routeMarks"] = True
    if spec.get("openRouteLibrary"):
        body["openRouteLibrary"] = True
    screens = spec.get("openScreens")
    if screens:
        body["openScreens"] = screens
    if spec.get("routeId"):
        body["routeId"] = str(spec["routeId"])
    if spec.get("skipGraphWait"):
        body["skipGraphWait"] = True
    if "scrollY" in spec:
        body["scrollY"] = int(spec["scrollY"])
    if spec.get("forceTreeTruncated"):
        body["forceTreeTruncated"] = True
    if spec.get("jeiCoverage"):
        body["jeiCoverage"] = True
    if spec.get("indexBuild"):
        body["indexBuild"] = True
    if spec.get("assistantScrollTop"):
        body["assistantScrollTop"] = True
    txt = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
    assert txt.startswith(MAGIC), txt[:40]
    tmp = os.path.join(cfg["case_dir"], "cases.json.tmp")
    with open(tmp, "w", encoding="utf-8") as fh:
        fh.write(txt)
    os.replace(tmp, os.path.join(cfg["case_dir"], "cases.json"))
    log("  cases.json written（%d 條，world=%s，原子寫）" % (len(spec["cases"]), cfg["world"]))


def _prism_cmd(cfg: dict, with_launch: bool) -> list:
    args = [cfg["prism_bin"], "-d", cfg["prism_root"]]
    if with_launch:
        args += ["-l", cfg["instance"]]
        if cfg["offline"]:
            args += ["-o", cfg["offline"]]
    args.append("--alive")
    return args


def preselect_instance(cfg: dict) -> None:
    """Point Prism at the instance we want BEFORE it starts.

    Prism's single-instance IPC is unreliable here (a second CLI invocation prints
    "QLocalSocket::setServerName() called while not in unconnected state" and never
    forwards the launch), so the driver selects the instance by config and then clicks
    the Launch button instead. Prism reads these keys at startup.
    """
    cfg_path = os.path.join(cfg["prism_root"], "prismlauncher.cfg")
    if not os.path.isfile(cfg_path):
        return
    if not os.path.isfile(cfg_path + ".bak"):
        shutil.copy2(cfg_path, cfg_path + ".bak")
    raw = io.open(cfg_path, "rb").read().decode("utf-8", "replace")

    def setkey(text, key, value):
        rx = r"(?mi)^%s=.*$" % re.escape(key)
        if re.search(rx, text):
            return re.sub(rx, "%s=%s" % (key, value), text)
        return text.rstrip("\r\n") + "\r\n%s=%s\r\n" % (key, value)

    raw = setkey(raw, "SelectedInstance", cfg["instance"])
    raw = setkey(raw, "LastOfflinePlayerName", cfg["offline"] or "packai_autotest")
    io.open(cfg_path, "wb").write(raw.encode("utf-8"))
    log("  preselect：SelectedInstance=%s" % cfg["instance"])


def launch(cfg: dict) -> float:
    """Start Prism only. The launch REQUEST goes out later, from assist().

    Two hard-won facts about Prism 11.1.1 on this box:
      * a `-l` request that arrives while it is still initialising is DROPPED
        ("Received message ... while still initializing. It will be ignored."), and
        initialising can take minutes (managed-pack update checks); so we re-send the
        request every ~25 s until the game process appears;
      * on a fresh data dir it shows the Quick Setup wizard, which must be walked
        first (see assist()).
    """
    preselect_instance(cfg)
    env = dict(os.environ)
    env["DISPLAY"] = DISPLAY
    with open(cfg["runlog"], "w") as fh:
        subprocess.Popen(["setsid", cfg["prism_bin"], "-d", cfg["prism_root"], "--alive"],
                         stdout=fh, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                         env=env, start_new_session=True)
    log("  started Prism (launch request comes later), log=%s" % cfg["runlog"])
    t0 = time.time()
    while time.time() - t0 < 120:
        rc, out, _ = gui(cfg, "windows")
        if rc == 0 and any(w.split("|")[1].startswith("Prism Launcher") for w in out.splitlines()
                           if w.count("|") >= 2):
            break
        time.sleep(4)
    return time.time()


def request_launch(cfg: dict) -> int:
    cmd = [cfg["prism_bin"], "-d", cfg["prism_root"], "-l", cfg["instance"]]
    if cfg["offline"]:
        cmd += ["-o", cfg["offline"]]
    cmd.append("--alive")
    return run(cmd, timeout=120)[0]


def wait_status(cfg: dict, since: float, timeout_s: int):
    end = time.time() + timeout_s
    while time.time() < end:
        for p in sorted(glob.glob(os.path.join(cfg["case_dir"], "status-*.json")), key=os.path.getmtime):
            if os.path.getmtime(p) >= since:
                time.sleep(3)
                return p
        time.sleep(10)
        alive = game_pids(cfg["instance"])
        log("  … waiting status (game pids=%s, elapsed=%ds)" % (alive, int(time.time() - since)))
        if not alive:
            # the game may have just written the status and quit: re-check before giving up
            for p in sorted(glob.glob(os.path.join(cfg["case_dir"], "status-*.json")), key=os.path.getmtime):
                if os.path.getmtime(p) >= since:
                    log("  status 出現（遊戲已關）：%s" % os.path.basename(p))
                    return p
            if time.time() - since > 240:
                log("  ⚠️ 開機 4 分鐘都未見遊戲進程（可能 Prism 卡住／要設定）")
                return None
    return None


def raise_mc(cfg: dict) -> None:
    """Bring the Minecraft window forward so the evidence screenshot shows the game."""
    for needle in ("Minecraft", "GLFW"):
        rc, out, _ = gui(cfg, "raise", needle)
        if rc == 0:
            time.sleep(2)
            return


def screenshot(cfg: dict) -> str:
    """Server-side X11 screenshot (GNOME's D-Bus screenshot API refuses this session)."""
    path = os.path.join(cfg["out"], "screen.png")
    if cfg.get("gui_assist"):
        raise_mc(cfg)
    rc, out, err = run([cfg["x_py"], X11_SHOT, path], timeout=180)
    if rc == 0 and os.path.isfile(path):
        return path
    return ""


def inject_keys(cfg: dict, a) -> list:
    """收完 status 之後，用 X11（x11_gui.py key）打真鍵。窄版 C8／C9 真鍵測試用。

    a.x11_keys：逗號分隔嘅 keysym（例 `y,],y`）；每個鍵之前先 raise 遊戲窗（x11 鍵只去 focus 窗），
    鍵之間 sleep a.x11_keys_gap 秒，每個鍵之後截圖一張（keys-NN-<key>.png）。
    """
    seq = [s.strip() for s in (a.x11_keys or "").split(",") if s.strip()]
    clicks = [s.strip() for s in (getattr(a, "x11_clicks", "") or "").split(",") if s.strip()]
    out = []
    for c in clicks:
        try:
            x, y = [int(v) for v in c.split("x")]
            rc, sout, serr = gui(cfg, "click", x, y)
            log("  x11 click %d,%d rc=%s" % (x, y, rc))
        except Exception as exc:
            log("  x11 click %r 失敗：%r" % (c, exc))
    for i, k in enumerate(seq):
        time.sleep(max(0.0, float(a.x11_keys_gap)))
        item = {"key": k}
        try:
            if cfg.get("gui_assist") and getattr(a, "x11_keys_raise", False):
                raise_mc(cfg)
            rc, sout, serr = gui(cfg, "key", k)
            item.update({"rc": rc, "out": (sout or "").strip()[:200], "err": (serr or "").strip()[:200]})
        except Exception as exc:
            item.update({"rc": -1, "err": repr(exc)})
        p = os.path.join(cfg["out"], "keys-%02d-%s.png" % (i + 1, k))
        try:
            rc2, _o, _e = run([cfg["x_py"], X11_SHOT, p], timeout=180)
            item["shot"] = p if (rc2 == 0 and os.path.isfile(p)) else ""
        except Exception as exc:
            item["shot"], item["shot_err"] = "", repr(exc)
        log("  x11 key %r rc=%s shot=%s" % (k, item.get("rc"), item.get("shot")))
        out.append(item)
    return out


def gui(cfg: dict, *args, timeout=120):
    return run([cfg["x_py"], X11_GUI] + [str(a) for a in args], timeout=timeout)


def _geom_xy(geom: str):
    m = re.match(r"\d+x\d+([+-]\d+)([+-]\d+)", geom.strip())
    return (int(m.group(1)), int(m.group(2))) if m else (0, 0)


def assist(cfg: dict, budget_s: int = 900) -> str:
    """Drive Prism's dialogs headlessly and keep re-sending the launch request."""
    if not cfg["gui_assist"]:
        return "off"
    start, end = time.time(), time.time() + budget_s
    last_req, steps, misses = time.time(), [], 0
    last_click = 0.0

    def geom(w):
        return _geom_xy(w.split("|")[2])

    def click(off_x, off_y, w):
        x, y = geom(w)
        gui(cfg, "click", x + off_x, y + off_y)

    while time.time() < end:
        if game_pids(cfg["instance"]):
            return "game-started after: %s" % (", ".join(steps) or "nothing")
        rc, out, _ = gui(cfg, "windows")
        if rc != 0:
            misses += 1
            if misses >= 6:
                return "x11 unavailable (%d misses)" % misses
            time.sleep(3)
            continue
        misses = 0
        wins = [w for w in out.splitlines() if w.count("|") >= 2]

        def find(sub):
            for w in wins:
                if sub.lower() in w.split("|")[1].lower():
                    return w
            return None

        hit = find("Quick Setup")
        if hit:
            gui(cfg, "wizard", timeout=240)
            steps.append("wizard")
            continue
        hit = find("Play demo?")
        if hit:
            click(390, 156, hit)
            steps.append("play-demo")
            time.sleep(5)
            continue
        hit = find("Player name")
        if hit:
            x, y = geom(hit)
            gui(cfg, "click", x + 215, y + 100)
            gui(cfg, "type", cfg["offline"] or "packai_autotest")
            gui(cfg, "click", x + 351, y + 173)
            steps.append("player-name")
            time.sleep(4)
            continue
        hit = find("No Accounts")
        if hit:
            click(367, 142, hit)          # "No" = dismiss; a launch then offers demo mode
            steps.append("no-accounts-no")
            time.sleep(3)
            continue
        hit = find("Settings")
        if hit:
            # Prism sometimes keeps a Settings page in front of the Launch button.
            # Press OK; if it refuses to go away, Escape out of it.
            if steps.count("settings-ok") >= 2:
                gui(cfg, "close", "Settings")
                steps.append("settings-close")
            else:
                click(600, 719, hit)      # OK
                steps.append("settings-ok")
            time.sleep(3)
            continue
        hit = find("News")
        if hit:
            gui(cfg, "close", "News")     # the news window covers the Launch button
            steps.append("news-close")
            time.sleep(3)
            continue
        # No dialog: press Launch (the target instance is preselected in the config).
        mains = sorted([w for w in wins if w.split("|")[1].startswith("Prism Launcher")
                        and int(w.split("|")[2].split("x")[0]) >= 400],
                       key=lambda w: -int(w.split("|")[2].split("x")[0]))
        if mains and time.time() - last_req > 25:
            # Prism 11.1.1 IPC launch request (-l <instance>), retried until the game
            # process appears. This is the reliable path: GUI clicks depend on the
            # window's absolute position, which the WM may place partly off-screen
            # (observed 828x666-14-49) → the calibrated offsets then miss the button.
            rc = request_launch(cfg)
            steps.append("launch-cli(rc=%d)" % rc)
            last_req = time.time()
        if mains and time.time() - last_click > 90:
            x, y = _geom_xy(mains[0].split("|")[2])
            gui(cfg, "click", x + 722, y + 234)          # the Launch button
            last_click = time.time()
            steps.append("launch-click")
        time.sleep(5)
    return "gave up after: %s" % (", ".join(steps) or "no windows seen")


def collect(cfg: dict, since: float) -> dict:
    os.makedirs(cfg["out"], exist_ok=True)
    n = 0
    for p in glob.glob(os.path.join(cfg["trace"], "ask-*.jsonl")):
        if os.path.getmtime(p) >= since - 60:
            shutil.copy2(p, cfg["out"])
            n += 1
    for p2 in glob.glob(os.path.join(cfg["case_dir"], "status-*.json")):
        shutil.copy2(p2, cfg["out"])
    for name in ("latest.log",):
        p = os.path.join(cfg["logs"], name)
        if os.path.isfile(p):
            shutil.copy2(p, cfg["out"])
    shot = screenshot(cfg)
    log("  collected %d trace file(s) -> %s (screenshot=%s)" % (n, cfg["out"], bool(shot)))
    return {"traces": n, "dir": cfg["out"], "screenshot": shot}


def kill_sandbox(cfg: dict, wait_exit_s: int = 90) -> dict:
    t0 = time.time()
    while time.time() - t0 < wait_exit_s:
        if not game_pids(cfg["instance"]):
            return {"killed": False, "waited_s": round(time.time() - t0, 1)}
        time.sleep(3)
    pids = game_pids(cfg["instance"])
    if pids:
        run(["kill", "-TERM"] + [str(p) for p in pids], timeout=60)
        time.sleep(8)
        pids2 = game_pids(cfg["instance"])
        if pids2:
            run(["kill", "-KILL"] + [str(p) for p in pids2], timeout=60)
        log("  ⚠️ 等唔到自動關機，已殺 pid=%s" % (pids + pids2))
        return {"killed": True, "pids": pids, "waited_s": round(time.time() - t0, 1)}
    return {"killed": False, "waited_s": round(time.time() - t0, 1)}


def main() -> int:
    global _logfh
    ap = argparse.ArgumentParser()
    ap.add_argument("--cases", required=True)
    ap.add_argument("--instance", default="packai_sandbox_startech")
    ap.add_argument("--world", default="New World")
    ap.add_argument("--jar", default="")
    ap.add_argument("--prism-bin", default=DEFAULT_PRISM_BIN)
    ap.add_argument("--prism-root", default=DEFAULT_PRISM_ROOT)
    ap.add_argument("--offline-name", default="packai_autotest")
    ap.add_argument("--timeout-min", type=int, default=25)
    ap.add_argument("--out", default="")
    ap.add_argument("--x-py", default=DEFAULT_X_PY, help="python with python-xlib (X11 driving)")
    ap.add_argument("--no-gui-assist", dest="gui_assist", action="store_false",
                    help="唔自動撳 Prism 彈窗（有人坐喺 console 前先好用）")
    ap.add_argument("--force-clean", action="store_true",
                    help="清走上次收尾失敗留低嘅 cases.json（move 去 backup，唔會靜靜刪）")
    ap.add_argument("--keep-cases", action="store_true", help="收尾保留 cases.json（唔建議）")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--x11-keys", default="",
                    help="收完 status 之後用 X11 打嘅真鍵，逗號分隔（例 'y,],y'）")
    ap.add_argument("--x11-keys-gap", type=float, default=8.0,
                    help="每個鍵之間嘅秒數（預設 8）")
    ap.add_argument("--x11-clicks", default="",
                    help="打鍵前先做嘅滑鼠點擊，逗號分隔 `xXy`（例 '960x420'）")
    ap.add_argument("--x11-keys-raise", action="store_true",
                    help="打鍵前先 raise 遊戲窗（注意：raise 會令 MC 失焦自動暫停，預設唔開）")
    a = ap.parse_args()

    spec = load_cases(a.cases)
    cfg = build_cfg(a)
    os.makedirs(cfg["out"], exist_ok=True)
    _logfh = open(os.path.join(cfg["out"], "smoke.log"), "w", encoding="utf-8")

    log("instance=%s world=%s cases=%d" % (cfg["instance"], cfg["world"], len(spec["cases"])))
    err = preflight(cfg)
    if err and a.force_clean and err.startswith("沙盒已有殘留 cases.json"):
        leftover = os.path.join(cfg["case_dir"], "cases.json")
        os.makedirs(cfg["backup"], exist_ok=True)
        shutil.move(leftover, os.path.join(cfg["backup"], "cases.json.leftover"))
        log("  --force-clean：殘留 cases.json 已移去 %s" % cfg["backup"])
        err = preflight(cfg)
    if err:
        log("PREFLIGHT FAIL: %s" % err)
        return 2
    log("preflight OK")
    if a.dry_run:
        log("dry-run：唔部署、唔開遊戲")
        return 0

    usage_before = {}
    try:
        with open(cfg["usage"], encoding="utf-8") as fh:
            usage_before = json.load(fh)
    except Exception:
        pass

    backup(cfg)
    log("  rotated %d stale status file(s)" % rotate_status(cfg))
    err = deploy(cfg)
    if err:
        log("DEPLOY FAIL: %s" % err)
        return 3
    write_cases(cfg, spec)

    t0 = launch(cfg)
    log("  GUI assist: %s" % assist(cfg))
    status_path = None
    keys = []
    try:
        status_path = wait_status(cfg, t0, a.timeout_min * 60)
        keys = inject_keys(cfg, a)
        res = collect(cfg, t0)
        kill = kill_sandbox(cfg)
    finally:
        if not a.keep_cases:
            cj = os.path.join(cfg["case_dir"], "cases.json")
            if os.path.exists(cj):
                os.remove(cj)
                log("  cases.json 已刪（收尾必須）")

    summary = {"instance": cfg["instance"], "status_file": status_path, "collected": res,
               "kill": kill, "out": cfg["out"], "x11_keys": keys}
    if status_path:
        try:
            with open(status_path, encoding="utf-8") as fh:
                st = json.load(fh)
            summary["status"] = st
            log("STATUS: %s" % json.dumps(st, ensure_ascii=False)[:1500])
        except Exception as exc:
            log("status 讀取失敗：%r" % exc)
    try:
        with open(cfg["usage"], encoding="utf-8") as fh:
            summary["usage_after"] = json.load(fh)
    except Exception:
        pass
    summary["usage_before"] = usage_before
    with open(os.path.join(cfg["out"], "summary.json"), "w", encoding="utf-8") as fh:
        json.dump(summary, fh, ensure_ascii=False, indent=2)
    log("結果資料夾：%s" % cfg["out"])
    return 0 if status_path else 4


if __name__ == "__main__":
    sys.exit(main())
