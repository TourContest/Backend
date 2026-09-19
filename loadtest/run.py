#!/usr/bin/env python3
"""부하테스트 실행/분석 드라이버.

  python3 loadtest/run.py mixed   # 50 TPS 혼합 + 2분 지점 리마인더 실행, 구간별 p95 / outbox 배수 시간 / 원장 감사
  python3 loadtest/run.py limit   # ramping-arrival-rate 로 에러율 1% 초과 지점(한계 TPS) 탐색
  python3 loadtest/run.py probe   # 트래픽 없이 리마인더 개선 전/후 쿼리 수만 측정 (유저 수는 DB 기준)
  python3 loadtest/run.py drain   # 트래픽 없이 리마인더 outbox 7,000건 적체 해소 시간만 측정
  python3 loadtest/run.py idem    # 3분 혼합 부하(같은 requestId 재전송 10%) 후 원장 감사만 수행

전제: loadtest/up.sh 로 스택이 떠 있어야 한다. 결과는 loadtest/results/<mode>-<시각>/ (git 무시).
"""
import json, os, re, signal, statistics, subprocess, sys, threading, time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BASE = os.environ.get("BASE_URL", "http://localhost:8081")
MYSQL = "jejuday-lt-mysql-1"
MODE = sys.argv[1] if len(sys.argv) > 1 else "mixed"
TRIGGER_AT = int(os.environ.get("TRIGGER_AT", "120"))     # k6 측정 시작 후 리마인더 실행 시각(초)
POST_SECS = int(os.environ.get("POST_SECS", "180"))       # 배수 완료 후 관찰 시간
WARMUP = 15                                               # 분석에서 제외할 초기 구간(초)
STEP, MAX_TPS, HOLD = int(os.environ.get("STEP", "50")), int(os.environ.get("MAX_TPS", "600")), int(os.environ.get("HOLD", "30"))
START = int(os.environ.get("START", str(STEP)))            # limit: 첫 단계 TPS
SAMPLE_S = int(os.environ.get("SAMPLE_S", "15"))           # docker stats 샘플 간격

OUT = ROOT / "results" / f"{MODE}-{datetime.now():%Y%m%d-%H%M%S}"
OUT.mkdir(parents=True, exist_ok=True)
JWT = next(l.split("=", 1)[1].strip() for l in (ROOT / ".env").read_text().splitlines() if l.startswith("JWT_SECRET="))


def sh(cmd, **kw):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, **kw).stdout


def sql(q):
    r = subprocess.run(["docker", "exec", MYSQL, "mysql", "-uroot", "-ploadtest", "-N", "-B", "jejuday_loadtest", "-e", q],
                       capture_output=True, text=True)
    return "\n".join(l for l in r.stdout.splitlines() if l.strip())


def post(path):
    t = time.time()
    body = sh(f"curl -s -m 600 -X POST '{BASE}{path}'")
    return body, (time.time() - t) * 1000


def reset_state():
    sql("SET FOREIGN_KEY_CHECKS=0; TRUNCATE notification_entity; TRUNCATE notification_outbox; TRUNCATE point_ledger; "
        "TRUNCATE step_daily; DELETE FROM shedlock; UPDATE users SET hallabong=0, total_steps=0; SET FOREIGN_KEY_CHECKS=1;")
    sql("DELETE FROM user_mood_rewards")
    sh("docker exec jejuday-lt-redis-1 redis-cli flushall")


def epoch(ts):  # k6 JSON 의 ISO 시각 → epoch
    return datetime.fromisoformat(ts).timestamp()


def pctl(vals, p):
    if not vals:
        return None
    vals = sorted(vals)
    return round(vals[min(len(vals) - 1, int(len(vals) * p))], 1)


def load_k6(path, keep=("http_req_duration", "http_req_failed", "dropped_iterations", "convert_fresh", "convert_replay", "convert_ok", "convert_rejected")):
    """k6 --out json 스트림에서 필요한 메트릭만 읽는다. (name, time, value)"""
    rows = {k: [] for k in keep}
    pat = re.compile(r'"metric":"(%s)"' % "|".join(keep))
    with open(path) as f:
        for line in f:
            m = pat.search(line)
            if not m or '"type":"Point"' not in line:
                continue
            d = json.loads(line)["data"]
            if d["tags"].get("name") == "setup":
                continue
            rows[m.group(1)].append((epoch(d["time"]), d["value"], d["tags"].get("name", "")))
    return rows


def window_stats(rows, lo, hi):
    dur = [v for t, v, _ in rows["http_req_duration"] if lo <= t < hi]
    fail = [v for t, v, _ in rows["http_req_failed"] if lo <= t < hi]
    secs = max(hi - lo, 1e-9)
    return {"requests": len(dur), "req_per_s": round(len(dur) / secs, 1), "error_rate_pct": round(100 * sum(fail) / len(fail), 3) if fail else None,
            "avg_ms": round(statistics.mean(dur), 1) if dur else None, "p50_ms": pctl(dur, .5), "p95_ms": pctl(dur, .95), "p99_ms": pctl(dur, .99), "max_ms": round(max(dur), 1) if dur else None,
            "window_s": round(secs)}


class K6:
    def __init__(self, extra_env):
        self.t0 = None
        self.ready = threading.Event()
        env = dict(os.environ, **extra_env)
        self.p = subprocess.Popen(["k6", "run", "--out", f"json={OUT / 'k6.json'}", "--summary-export", str(OUT / "k6-summary.json"),
                                   "-e", f"SCENARIO={MODE}", "-e", f"JWT_SECRET={JWT}", str(ROOT / "jejuday.js")],
                                  stdout=open(OUT / "k6.stdout.log", "w"), stderr=subprocess.PIPE, text=True, env=env)
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        with open(OUT / "k6.stderr.log", "w") as log:
            for line in self.p.stderr:
                log.write(line); log.flush()
                if "SETUP_DONE" in line and self.t0 is None:
                    self.t0 = time.time(); self.ready.set()

    def stop(self):
        if self.p.poll() is None:
            self.p.send_signal(signal.SIGINT)
        try:
            self.p.wait(timeout=90)
        except subprocess.TimeoutExpired:
            self.p.kill()


def sample_stats(stop_evt, path):
    with open(path, "w") as f:
        while not stop_evt.is_set():
            out = sh("docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' jejuday-lt-app-1 jejuday-lt-mysql-1 jejuday-lt-redis-1")
            for l in out.strip().splitlines():
                f.write(f"{time.time():.0f},{l}\n")
            f.flush(); stop_evt.wait(SAMPLE_S)


def health_snapshot():
    return {n: sh(f"docker inspect -f 'restarts={{{{.RestartCount}}}} oom={{{{.State.OOMKilled}}}} status={{{{.State.Status}}}} started={{{{.State.StartedAt}}}}' {n}").strip() for n in ("jejuday-lt-app-1", "jejuday-lt-mysql-1", "jejuday-lt-redis-1")}


# ───────────────────────── mixed ─────────────────────────
def probe_attendance():
    """리마인더 개선 전/후 쿼리 수 — 트래픽이 없는 상태에서 3회씩 교차 측정 (Hibernate statistics)."""
    post("/v1/test-notification/attendance/seed-cache?count=3000")
    res = {"before": [], "after": []}
    for _ in range(3):
        for k in ("before", "after"):
            body, ms = post(f"/v1/test-notification/attendance/{k}")
            m = re.search(r"DB쿼리=(\d+)회, 처리시간=(\d+)ms", body)
            res[k].append({"queries": int(m.group(1)), "server_ms": int(m.group(2)), "http_ms": round(ms)} if m else {"raw": body[:200]})
    return res


def audit():
    q = lambda s: sql(s)
    r = {}
    r["ledger_rows"] = q("SELECT COUNT(*) FROM point_ledger")
    r["ledger_by_reason"] = q("SELECT CONCAT(reason,':',COUNT(*),':',SUM(amount)) FROM point_ledger GROUP BY reason").replace("\n", " | ")
    r["ledger_sum"] = q("SELECT COALESCE(SUM(amount),0) FROM point_ledger")
    r["balance_sum"] = q("SELECT COALESCE(SUM(hallabong),0) FROM users")
    r["balance_mismatch_users"] = q("SELECT COUNT(*) FROM users u LEFT JOIN (SELECT user_id, SUM(amount) s FROM point_ledger GROUP BY user_id) l "
                                    "ON l.user_id=u.user_id WHERE u.hallabong <> COALESCE(l.s,0)")
    r["duplicate_idempotency_keys"] = q("SELECT COUNT(*) FROM (SELECT idempotency_key FROM point_ledger GROUP BY idempotency_key HAVING COUNT(*)>1) x")
    r["step_convert_ledger_rows"] = q("SELECT COUNT(*) FROM point_ledger WHERE reason='STEP_CONVERT'")
    r["step_convert_ledger_points"] = q("SELECT COALESCE(SUM(amount),0) FROM point_ledger WHERE reason='STEP_CONVERT'")
    r["step_daily_exchange_count_sum"] = q("SELECT COALESCE(SUM(exchange_count),0) FROM step_daily")
    r["step_daily_converted_points_sum"] = q("SELECT COALESCE(SUM(converted_points),0) FROM step_daily")
    # 한도(쿼터)는 소진됐는데 원장에는 없는 전환 = 재전송이 쿼터만 소진시킨 건수
    r["users_quota_ne_ledger"] = q("SELECT COUNT(*) FROM step_daily s LEFT JOIN (SELECT user_id, COUNT(*) c, SUM(amount) a FROM point_ledger WHERE reason='STEP_CONVERT' GROUP BY user_id) l "
                                   "ON l.user_id=s.user_id WHERE s.exchange_count <> COALESCE(l.c,0) OR s.converted_points <> COALESCE(l.a,0)")
    r["outbox_by_type_status"] = q("SELECT CONCAT(type,'/',status,':',COUNT(*)) FROM notification_outbox GROUP BY type,status").replace("\n", " | ")
    return r


def run_mixed():
    reset_state()
    (OUT / "probe.json").write_text(json.dumps(probe_attendance(), ensure_ascii=False, indent=1))
    stop = threading.Event()
    threading.Thread(target=sample_stats, args=(stop, OUT / "docker-stats.csv"), daemon=True).start()
    k6 = K6({"DURATION": os.environ.get("DURATION", "30m")})
    assert k6.ready.wait(900), "k6 setup 이 끝나지 않음"
    t0 = k6.t0
    print(f"[t0] 측정 시작, 리마인더는 +{TRIGGER_AT}s", flush=True)

    while time.time() < t0 + TRIGGER_AT:
        time.sleep(0.5)
    sql("DELETE FROM shedlock")
    t_trig = time.time()
    body, trig_ms = post("/v1/test-notification/attendance")
    t_trig_end = time.time()
    print(f"[trigger] {trig_ms:.0f}ms {body[:80]}", flush=True)

    # outbox 배수 폴링: ATTENDANCE 행이 전부 SENT 가 될 때까지
    poll = open(OUT / "outbox-drain.csv", "w"); poll.write("epoch,elapsed_since_trigger_s,pending,processing,sent,total\n")
    t_drained, total = None, 0
    while True:
        row = sql("SELECT COALESCE(SUM(status='PENDING'),0), COALESCE(SUM(status='PROCESSING'),0), COALESCE(SUM(status='SENT'),0), COUNT(*) FROM notification_outbox WHERE type='ATTENDANCE'").split("\t")
        now = time.time()
        if len(row) == 4:
            pend, proc, sent, total = map(int, row)
            poll.write(f"{now:.1f},{now - t_trig:.1f},{pend},{proc},{sent},{total}\n"); poll.flush()
            if total > 0 and pend == 0 and proc == 0 and t_drained is None:
                t_drained = now
                print(f"[drained] {t_drained - t_trig:.1f}s (total={total})", flush=True)
        if t_drained and now > t_drained + POST_SECS:
            break
        if k6.p.poll() is not None:
            break
        time.sleep(2)
    t_end = time.time()
    k6.stop(); stop.set()

    rows = load_k6(OUT / "k6.json")
    lo = t0 + WARMUP
    phases = {"before(전)": window_stats(rows, lo, t_trig),
              "during(중: 트리거~배수완료)": window_stats(rows, t_trig, t_drained or t_end),
              "during_first_30s(트리거 직후)": window_stats(rows, t_trig, t_trig + 30),
              "after(후: 배수완료~종료)": window_stats(rows, t_drained, t_end) if t_drained else None}
    by_name = {}
    for n in sorted({x[2] for x in rows["http_req_duration"]}):
        sub = {k: [r for r in v if r[2] == n or k.startswith("convert") or k == "dropped_iterations"] for k, v in rows.items()}
        by_name[n] = {p: window_stats(sub, a, b) for p, (a, b) in {"before": (lo, t_trig), "during": (t_trig, t_drained or t_end), "after": (t_drained or t_end, t_end)}.items()}
    counters = {k: int(sum(v for _, v, _ in rows[k])) for k in ("convert_fresh", "convert_replay", "convert_ok", "convert_rejected", "dropped_iterations")}
    result = {"trigger": {"http_ms": round(trig_ms), "response": body[:120], "attendance_rows": total},
              "outbox_drain_s": round(t_drained - t_trig, 1) if t_drained else None,
              "outbox_drain_note": "폴러: fixedDelay 10s, 배치 100건, 스텁 FCM 200ms",
              "phases": phases, "phases_by_endpoint": by_name, "counters": counters, "audit": audit(), "containers": health_snapshot(),
              "timeline": {"t0": t0, "trigger_at": t_trig - t0, "drained_at": (t_drained - t0) if t_drained else None, "end": t_end - t0}}
    (OUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=1))
    print(json.dumps({k: result[k] for k in ("trigger", "outbox_drain_s", "phases", "counters", "audit", "containers")}, ensure_ascii=False, indent=1))


# ───────────────────────── limit ─────────────────────────
def run_limit():
    reset_state()
    stop = threading.Event()
    threading.Thread(target=sample_stats, args=(stop, OUT / "docker-stats.csv"), daemon=True).start()
    before = health_snapshot()
    k6 = K6({"STEP": str(STEP), "START": str(START), "MAX_TPS": str(MAX_TPS), "HOLD": f"{HOLD}s"})
    assert k6.ready.wait(900), "k6 setup 이 끝나지 않음"
    t0 = k6.t0
    n = len(range(START, MAX_TPS + 1, STEP)); block = 5 + HOLD
    print(f"[t0] {n} 단계 × {block}s", flush=True)
    k6.p.wait(timeout=n * block + 300); stop.set()
    rows = load_k6(OUT / "k6.json")
    stages, first_bad, last_ok = [], None, None
    for i, tps in enumerate(range(START, MAX_TPS + 1, STEP)):
        lo = t0 + i * block + 5; hi = t0 + (i + 1) * block           # 램프 5초 제외, 유지 구간만
        st = window_stats(rows, lo, hi)
        st["offered_tps"] = tps
        st["dropped_iterations"] = int(sum(v for t, v, _ in rows["dropped_iterations"] if lo <= t < hi))
        stages.append(st)
        bad = (st["error_rate_pct"] or 0) > 1.0
        if bad and first_bad is None:
            first_bad = tps
        if not bad and first_bad is None:
            last_ok = tps
    result = {"stages": stages, "last_ok_tps": last_ok, "first_tps_over_1pct_error": first_bad, "containers_before": before, "containers": health_snapshot()}
    (OUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=1))
    print(f"{'offered':>8} {'achieved':>9} {'err%':>7} {'p95ms':>8} {'p99ms':>8} {'dropped':>8}")
    for s in stages:
        print(f"{s['offered_tps']:>8} {s['req_per_s']:>9} {s['error_rate_pct']!s:>7} {s['p95_ms']!s:>8} {s['p99_ms']!s:>8} {s['dropped_iterations']:>8}")
    print(f"last_ok_tps={last_ok} first_over_1pct={first_bad}\nbefore={before}\nafter ={result['containers']}")


def run_probe():
    reset_state()
    users = sql("SELECT COUNT(*) FROM users")
    res = probe_attendance(); res["users"] = int(users)
    (OUT / "probe.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    print(json.dumps(res, ensure_ascii=False, indent=1))


def run_drain():
    """트래픽 없이 리마인더만 실행해 outbox 적체가 해소되는 시간을 잰다 (수정 전후 비교용)."""
    reset_state()
    post("/v1/test-notification/attendance/seed-cache?count=3000")
    sql("DELETE FROM shedlock")
    t_trig = time.time()
    body, trig_ms = post("/v1/test-notification/attendance")
    poll = open(OUT / "outbox-drain.csv", "w"); poll.write("epoch,elapsed_since_trigger_s,pending,processing,sent,total\n")
    t_drained, total = None, 0
    while time.time() - t_trig < 1800:
        row = sql("SELECT COALESCE(SUM(status='PENDING'),0), COALESCE(SUM(status='PROCESSING'),0), COALESCE(SUM(status='SENT'),0), COUNT(*) FROM notification_outbox WHERE type='ATTENDANCE'").split("\t")
        now = time.time()
        if len(row) == 4:
            pend, proc, sent, total = map(int, row)
            poll.write(f"{now:.1f},{now - t_trig:.1f},{pend},{proc},{sent},{total}\n"); poll.flush()
            if total > 0 and pend == 0 and proc == 0:
                t_drained = now; break
        time.sleep(1)
    res = {"trigger_ms": round(trig_ms), "attendance_rows": total, "drain_s": round(t_drained - t_trig, 1) if t_drained else None,
           "duplicate_sends": stub_stats()}
    (OUT / "result.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    print(json.dumps(res, ensure_ascii=False, indent=1))


def stub_stats():
    """StubFcmGateway 가 센 (token,contextKey)별 전송 횟수 — 중복 선점(=중복 전송) 검출용. 엔드포인트가 없으면 None."""
    out = sh(f"curl -s '{BASE}/v1/test-notification/outbox/stub-stats'")
    try:
        return json.loads(out).get("data")
    except Exception:
        return None


def run_idem():
    reset_state()
    k6 = K6({"DURATION": os.environ.get("DURATION", "180s")})
    assert k6.ready.wait(900), "k6 setup 이 끝나지 않음"
    k6.p.wait(timeout=900)
    rows = load_k6(OUT / "k6.json")
    counters = {k: int(sum(v for _, v, _ in rows[k])) for k in ("convert_fresh", "convert_replay", "convert_ok", "convert_rejected")}
    a = audit()
    checks = {"exchange_count_sum == ledger_rows": a["step_daily_exchange_count_sum"] == a["step_convert_ledger_rows"],
              "converted_points_sum == ledger_sum": a["step_daily_converted_points_sum"] == a["step_convert_ledger_points"],
              "users_quota_ne_ledger == 0": a["users_quota_ne_ledger"] == "0",
              "balance_mismatch_users == 0": a["balance_mismatch_users"] == "0",
              "duplicate_idempotency_keys == 0": a["duplicate_idempotency_keys"] == "0"}
    res = {"counters": counters, "audit": a, "checks": checks}
    (OUT / "result.json").write_text(json.dumps(res, ensure_ascii=False, indent=1))
    print(json.dumps(res, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    print(f"결과 디렉터리: {OUT}")
    {"mixed": run_mixed, "limit": run_limit, "probe": run_probe, "drain": run_drain, "idem": run_idem}[MODE]()
