#!/usr/bin/env python3
"""Create a PACK-NATIVE world for a Prism instance (server-side, no GUI).

Why: the in-game autotest loads a save and quits with `WORLD_TIMEOUT` if it cannot.
Worlds copied from another pack are not portable (different mods/dimension data), which
is what happened to the freshly installed Modrinth packs. So: run the pack's own Forge
server once — it generates the world with exactly the pack's mods/config — then copy
`world/` into the instance's `saves/`.

Usage:
  python3 tools/packai_make_world_linux.py --instance packai_sandbox_createplus
  python3 tools/packai_make_world_linux.py --instance X --mem-mb 3072 --timeout-min 20

Outputs the world name and copies it to <instance>/minecraft/saves/<name>/.
"""
from __future__ import annotations

import argparse
import glob
import io
import json
import os
import queue
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile

UA = {"User-Agent": "packai-server-test/1.0 (skps952608@gmail.com)"}
DEFAULT_PRISM_ROOT = os.path.expanduser("~/opt/prism-data")
DEFAULT_JAVA = os.path.expanduser("~/opt/jdk-17.0.20.1+1/bin/java")
FORGE_MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"
COPY_DIRS = ("mods", "config", "defaultconfigs", "kubejs", "scripts", "datapacks", "resources")
OWN_JAR = "packai-autotest-dev.jar"   # client-side mod: never belongs on the server
STALL_S = 240                        # no server output for this long = hung → kill
STOP_S = 900                         # grace period for a clean 'stop' (world save)


def log(msg: str) -> None:
    print("%s %s" % (time.strftime("%H:%M:%S"), msg), flush=True)


def fetch(url: str, dest: str) -> bool:
    try:
        req = urllib.request.Request(url, headers=UA)
        with urllib.request.urlopen(req, timeout=600) as r, open(dest, "wb") as fh:
            shutil.copyfileobj(r, fh)
        return True
    except Exception as exc:  # noqa: BLE001
        log("  download failed: %r" % exc)
        return False


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--instance", required=True)
    ap.add_argument("--prism-root", default=DEFAULT_PRISM_ROOT)
    ap.add_argument("--java", default=DEFAULT_JAVA)
    ap.add_argument("--mem-mb", type=int, default=4096)
    ap.add_argument("--timeout-min", type=int, default=20)
    ap.add_argument("--workdir", default="")
    a = ap.parse_args()

    inst = os.path.join(a.prism_root, "instances", a.instance)
    game = os.path.join(inst, "minecraft")
    pack_json = os.path.join(inst, "mmc-pack.json")
    if not os.path.isfile(pack_json):
        log("唔似 Prism instance：%s" % inst)
        return 2
    comps = {c["uid"]: c.get("version") for c in json.load(open(pack_json))["components"]}
    mc = comps.get("net.minecraft")
    forge = comps.get("net.minecraftforge", "")
    if not mc or not forge:
        log("mmc-pack.json 冇 minecraft／forge 版本：%s" % comps)
        return 2
    work = a.workdir or ("/tmp/packai_worldgen_%s" % a.instance)
    os.makedirs(work, exist_ok=True)
    log("instance=%s mc=%s forge=%s work=%s" % (a.instance, mc, forge, work))

    installer = os.path.join(work, "forge-installer.jar")
    # Forge 1.19.2 uses the old coordinate layout: forge-<mc>-<forge>-installer.jar
    urls = ["%s/%s-%s/forge-%s-%s-installer.jar" % (FORGE_MAVEN, mc, forge, mc, forge),
            "%s/%s/forge-%s-installer.jar" % (FORGE_MAVEN, forge, forge)]
    if not os.path.isfile(installer):
        for u in urls:
            log("  try %s" % u)
            if fetch(u, installer) and os.path.getsize(installer) > 100_000:
                break
        else:
            log("抓唔到 Forge installer")
            return 3
    log("installer %.1f MB" % (os.path.getsize(installer) / 1e6))

    # server-side copy of the pack files (do not touch the instance itself)
    for name in COPY_DIRS:
        src = os.path.join(game, name)
        if os.path.isdir(src):
            dst = os.path.join(work, name)
            if not os.path.isdir(dst):
                shutil.copytree(src, dst)
            log("  copied %s" % name)

    env = dict(os.environ)
    env["PATH"] = os.path.dirname(a.java) + os.pathsep + env.get("PATH", "")
    log("running Forge installer --installServer …")
    cp = subprocess.run([a.java, "-jar", installer, "--installServer"], cwd=work,
                        env=env, capture_output=True, timeout=1800)
    log("  installer rc=%d %s" % (cp.returncode, (cp.stdout or b"")[-160:]))

    io.open(os.path.join(work, "eula.txt"), "w").write("eula=true\n")
    srv_props = os.path.join(work, "server.properties")
    # Unique port per instance: two worldgens running at once must not fight over 25565
    # (that failure looks like a pack problem but is just a bind error).
    port = 25565 + (sum(ord(c) for c in a.instance) % 2000)
    io.open(srv_props, "w").write("level-name=packaiworld\nlevel-type=minecraft\\:normal\n"
                                  "online-mode=false\nspawn-protection=0\n"
                                  "max-players=1\nview-distance=6\nserver-port=%d\n" % port)
    # JVM heap for the server (run.sh reads user_jvm_args.txt).
    io.open(os.path.join(work, "user_jvm_args.txt"), "w").write(
        "# generated by packai_make_world_linux.py\n-Xms1024M\n-Xmx%dM\n" % a.mem_mb)
    # Forge's 1.19.2 installer writes run.sh / run.bat (classpath launcher) and the
    # server jar(s). Prefer the script: `java -jar run.sh` is NOT valid.
    launch = None
    if os.path.isfile(os.path.join(work, "run.sh")):
        launch = ["bash", "run.sh", "nogui"]
    else:
        jars = sorted(glob.glob(os.path.join(work, "forge-*-shim.jar")) +
                      glob.glob(os.path.join(work, "forge-*.jar")))
        if jars:
            launch = [a.java, "-Xms1024m", "-Xmx%dm" % a.mem_mb, "-jar", jars[0], "nogui"]
    if launch is None:
        log("installer 冇整出 server 啟動檔（睇 %s）" % work)
        return 4
    log("booting the server to generate the world (timeout %d min/attempt) …" % a.timeout_min)
    # A stale world dir would be *loaded* instead of generated (and a previous failed run
    # can leave one with empty region files) → always start from nothing.
    stale_world = os.path.join(work, "packaiworld")
    if os.path.isdir(stale_world):
        shutil.rmtree(stale_world)
        log("  清走舊 work 世界目錄（重新生成）")
    env2 = dict(env)
    env2["JAVA"] = a.java
    mods_dir = os.path.join(work, "mods")
    disabled_dir = os.path.join(work, "mods-disabled")
    # Our own mod is client-side (screens) → it aborts a DEDICATED_SERVER load.
    os.makedirs(disabled_dir, exist_ok=True)
    own = os.path.join(mods_dir, OWN_JAR)
    if os.path.isfile(own):
        shutil.move(own, os.path.join(disabled_dir, OWN_JAR))
        log("  （世界生成唔需要本 mod）已移走 %s" % OWN_JAR)

    def kill_tree(proc):
        """run.sh runs java as a child — kill the whole process group, not just bash
        (killing bash leaves an orphan JVM that never flushes its region files)."""
        try:
            os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
        except Exception:  # noqa: BLE001
            try:
                proc.kill()
            except Exception:  # noqa: BLE001
                pass

    def boot(attempt: int, t0: float):
        """Run the server once; True when it reaches 'Done ('. Output -> server-boot-<n>.log.

        A reader thread keeps draining stdout for the whole run (Forge blocks forever if
        nobody reads the pipe) and a queue lets us spot a hung server (no output for
        STALL_S) instead of waiting out the full timeout.
        """
        log_file = os.path.join(work, "server-boot-%d.log" % attempt)
        proc = subprocess.Popen(launch, cwd=work, env=env2,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                stdin=subprocess.PIPE, text=True, bufsize=1,
                                start_new_session=True)
        q: "queue.Queue" = queue.Queue()

        def pump():
            try:
                for ln in proc.stdout:
                    q.put(ln)
            except Exception:  # noqa: BLE001
                pass
            q.put(None)

        threading.Thread(target=pump, daemon=True).start()
        deadline = time.time() + a.timeout_min * 60
        lines, up, stalled = [], False, False
        with io.open(log_file, "w", encoding="utf-8", errors="replace") as fh:
            last_data = time.time()
            while time.time() < deadline:
                try:
                    line = q.get(timeout=5)
                except queue.Empty:
                    if proc.poll() is not None:
                        break
                    if time.time() - last_data > STALL_S:
                        stalled = True
                        break
                    continue
                if line is None:
                    break
                last_data = time.time()
                fh.write(line)
                fh.flush()
                lines.append(line.rstrip())
                if "Done (" in line and "For help" in line:
                    up = True
                    break
            # The pump thread keeps draining, so 'stop' can actually be processed.
            if up:
                time.sleep(5)
                try:
                    proc.stdin.write("stop\n")
                    proc.stdin.flush()
                except Exception:  # noqa: BLE001
                    pass
                try:
                    proc.wait(timeout=STOP_S)
                except subprocess.TimeoutExpired:
                    log("  attempt %d：stop 後 %d 秒未收工 → 強殺" % (attempt, STOP_S))
                    kill_tree(proc)
            else:
                if stalled:
                    log("  attempt %d：%d 秒冇輸出（server 掛住）→ 殺" % (attempt, STALL_S))
                kill_tree(proc)
        return up, lines, log_file

    def jar_for_config(cfg: str):
        for jar in sorted(glob.glob(os.path.join(mods_dir, "*.jar"))):
            try:
                with zipfile.ZipFile(jar) as z:
                    if any(n.endswith(cfg) for n in z.namelist()):
                        return os.path.basename(jar)
            except Exception:  # noqa: BLE001
                continue
        return None

    def crash_report_mods(since: float):
        """Jars named in a crash report written during this attempt (hard crash = no mod row)."""
        out = []
        reports = [p for p in glob.glob(os.path.join(work, "crash-reports", "*.txt"))
                   if os.path.getmtime(p) >= since]
        for rep in sorted(reports, key=os.path.getmtime):
            try:
                txt = io.open(rep, encoding="utf-8", errors="replace").read()
            except Exception:  # noqa: BLE001
                continue
            for m in re.finditer(r"Mod File:\s*\S*[/\\]mods[/\\]([^/\s\\]+\.jar)", txt):
                if m.group(1) not in out:
                    out.append(m.group(1))
        return out

    def crashed_mods(lines, since: float):
        """Jars the server choked on:
        (a) mod-table |ERROR rows, (b) jars whose mixins target client-only classes
        (Forge aborts hard instead of listing the mod), (c) jars named in a new crash report.
        """
        bad = []
        for ln in lines:
            if "|ERROR" not in ln or ln.count("|") < 3:
                continue
            name = ln.split("|")[0].strip()
            if name.endswith(".jar") and name not in bad:
                bad.append(name)
        # (b) e.g. "@Mixin target net.minecraft.client.Minecraft was not found citresewn.mixins.json:..."
        cfgs = []
        for ln in lines:
            if "net.minecraft.client" not in ln and "net/minecraft/client" not in ln:
                continue
            m = re.search(r"was not found ([\w.\-]+\.json)", ln)
            if m and m.group(1) not in cfgs:
                cfgs.append(m.group(1))
        for cfg in cfgs:
            jar = jar_for_config(cfg)
            if jar and jar not in bad:
                bad.append(jar)
        for jar in crash_report_mods(since):
            if jar not in bad:
                bad.append(jar)
        return bad

    done, disabled = False, []
    for attempt in range(1, 9):
        t0 = time.time()
        up, lines, log_file = boot(attempt, t0)
        if up:
            done = True
            log("server up: %s" % (lines[-1][:120] if lines else "?"))
            break
        bad = [b for b in crashed_mods(lines, t0) if os.path.isfile(os.path.join(mods_dir, b))]
        if not bad:
            log("server 未起到（attempt %d，冇辨識到可停用嘅 mod）：%s"
                % (attempt, " | ".join(lines[-5:])[:400]))
            log("  完整輸出：%s" % log_file)
            break
        os.makedirs(disabled_dir, exist_ok=True)
        for b in bad:
            shutil.move(os.path.join(mods_dir, b), os.path.join(disabled_dir, b))
            disabled.append(b)
        log("  attempt %d：server 唔相容（client-only）→ 停用 %s" % (attempt, ", ".join(bad)))
    if disabled:
        log("  共停用 %d 支（世界生成唔受影響嘅渲染／client mod）：%s"
            % (len(disabled), ", ".join(disabled)))
    if not done:
        log("伺服器冇起到 → 唔會 copy 半製成品世界（睇 server-boot-*.log）")
        return 5

    world = os.path.join(work, "packaiworld")
    if not os.path.isfile(os.path.join(world, "level.dat")):
        log("冇 level.dat → 世界未生成")
        return 5
    saves = os.path.join(game, "saves")
    os.makedirs(saves, exist_ok=True)
    dst = os.path.join(saves, "packaiworld")
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    shutil.copytree(world, dst)
    log("世界已放入：%s（%.0f MB）" % (dst, sum(
        os.path.getsize(os.path.join(r, f)) for r, _, fs in os.walk(dst) for f in fs) / 1e6))
    log("下一步：packai_smoke_linux.py --instance %s --world packaiworld" % a.instance)
    return 0


if __name__ == "__main__":
    sys.exit(main())
