"""FINAL: per-case layout verdict = cardplace truth seq  x  trace body tokens."""
import io, os, re, json, datetime, sys

D = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.environ["LOCALAPPDATA"], "Temp", "autotest_results_20260919-211621")
TOK = re.compile(r"\[card:(\d+)\]")
LINEPAT = re.compile(r"^\[(\d{2}\w{3}\d{4} [\d:.]+)\].*Pack AI cardplace: (.*)$", re.M)


def recs_of(p):
    out = []
    for ln in io.open(p, encoding="utf-8", errors="replace"):
        ln = ln.strip()
        if ln:
            try:
                out.append(json.loads(ln))
            except Exception:
                pass
    return out


def parse_ms(ts):
    b = datetime.datetime.strptime(ts.split(".")[0], "%d%b%Y %H:%M:%S")
    return b + datetime.timedelta(milliseconds=int(ts.split(".")[1]) if "." in ts else 0)


# cardplace lines (log text lives in the jsonl 'content' of render events? -> read raw file too)
lines = []
for f in sorted(os.listdir(D)):
    p = os.path.join(D, f)
    txt = io.open(p, encoding="utf-8", errors="replace").read()
    for m in LINEPAT.finditer(txt):
        lines.append((parse_ms(m.group(1)), m.group(2)))
lines.sort()

cases = []
for f in sorted(os.listdir(D)):
    m = re.match(r"ask-(\d{8})-(\d{6})-(.+)\.jsonl$", f)
    if not m:
        continue
    when = datetime.datetime.strptime(m.group(1) + m.group(2), "%Y%m%d%H%M%S")
    item = m.group(3).replace("_", ":", 1)
    cases.append((when, item, f))

print("%-40s %5s %5s %5s %8s %6s %6s %-9s %s" %
      ("item", "tok", "grp", "cards", "adjPairs", "afterS", "lastC", "verdict", "final seq"))
stats = {"design": 0, "real": 0, "clean": 0}
for i, (t0, item, f) in enumerate(cases):
    t1 = cases[i + 1][0] if i + 1 < len(cases) else t0 + datetime.timedelta(seconds=120)
    recs = recs_of(os.path.join(D, f))
    body = ""
    for r in recs:
        if r.get("event") == "display.body.final":
            body = r.get("body") or ""
    toks = [int(x.group(1)) for x in TOK.finditer(body)]
    groups = [g for g in ([int(x.group(1)) for x in TOK.finditer(l)] for l in body.split("\n")) if len(g) > 1]
    ncards = sum(1 for r in recs if r.get("event") == "check.cards")
    seg = [p for ts, p in lines if t0 <= ts < t1 and p.startswith("cards=")]
    seqs = [p for ts, p in lines if t0 <= ts < t1 and p.startswith("n=")]
    adj = max([int(re.search(r"adjacentCardPairs=(\d+)", p).group(1)) for p in seg] or [0])
    afts = max([int(re.search(r"afterSrcStart=(\d+)", p).group(1)) for p in seg] or [0])
    lastc = any("lastIsCard=true" in p for p in seg)
    seq = seqs[-1].split("seq=", 1)[1][:34] if seqs else "-"
    if adj or afts or lastc:
        v = "BY-DESIGN" if groups else "REAL?"
        stats["design" if groups else "real"] += 1
    else:
        v = "clean"
        stats["clean"] += 1
    print("%-40s %5d %5s %5d %8d %6d %6s %-9s %s" %
          (item[:40], len(toks), bool(groups) if groups else "-", ncards, adj, afts, lastc, v, seq))

print("\n=== 結論統計 ===")
print("有相鄰卡 + LLM 同行多引用（設計行為）: %d" % stats["design"])
print("有相鄰卡 + 冇同行引用（疑似真症狀）  : %d" % stats["real"])
print("完全乾淨（冇相鄰／冇去最尾）          : %d" % stats["clean"])
