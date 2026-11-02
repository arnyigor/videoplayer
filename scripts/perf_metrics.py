"""S-005 measurement collector: one scenario, reproducible conditions, real artifacts.

What it records (all local, no app data):
  * gfxinfo percentiles/histogram for the measured window (frame duration proxy),
  * `am start -W` startup timing,
  * `dumpsys meminfo` used heap before/after,
  * device/network/temperature/screen conditions,
  * optional Perfetto trace (sched + gfx + view) pulled to build/perf/traces.

Deliberately NOT a Macrobenchmark replacement: no FrameTimingMetric/frameOverrunMs on API<31,
and debug builds are kept only for diagnosis. Emulator numbers are not release criteria.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time

ENV = dict(os.environ, MSYS_NO_PATHCONV="1")
PERFETTO_CONFIG = """buffers: { size_kb: 32768 fill_policy: RING_BUFFER }
data_sources: { config { name: "linux.ftrace" ftrace_config {
  ftrace_events: "sched/sched_switch" ftrace_events: "power/suspend_resume"
  ftrace_events: "sched/sched_wakeup" atrace_categories: "gfx"
  atrace_categories: "view" atrace_categories: "input" atrace_categories: "wm" } } }
data_sources: { config { name: "linux.process_stats" } }
duration_ms: %d
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--serial", required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--seconds", type=int, default=15)
    ap.add_argument("--perfetto", action="store_true")
    args = ap.parse_args()
    if not 3 <= args.seconds <= 120:
        ap.error("seconds must be 3..120")

    def adb(*cmd, timeout=120):
        return subprocess.run(["adb", "-s", args.serial, *cmd], capture_output=True, env=ENV,
                              timeout=timeout, check=True).stdout

    def sh(cmd, timeout=120):
        return subprocess.run(["adb", "-s", args.serial, "shell", cmd], capture_output=True,
                              env=ENV, timeout=timeout).stdout.decode("utf-8", "replace")

    package = sh("dumpsys window").split("mCurrentFocus")[-1]
    pkg = re.search(r"(com\.arny\.mobilecinema[\w.]*)", package)
    if not pkg:
        raise RuntimeError("App must be in the foreground before measuring")
    pkg = pkg.group(1)

    def meminfo_used():
        text = sh(f"dumpsys meminfo {pkg}")
        m = re.search(r"TOTAL PSS:\s*(\d+)", text) or re.search(r"TOTAL\s+(\d+)", text)
        return int(m.group(1)) if m else None

    def gfx():
        text = sh(f"dumpsys gfxinfo {pkg}")
        data = {}
        for key, pattern in [("frames", r"Total frames rendered:\s*(\d+)"),
                             ("janky", r"Janky frames:\s*(\d+)"),
                             ("p50", r"50th percentile:\s*(\d+)ms"),
                             ("p90", r"90th percentile:\s*(\d+)ms"),
                             ("p95", r"95th percentile:\s*(\d+)ms"),
                             ("p99", r"99th percentile:.*?(\d+)ms"),
                             ("missed_vsync", r"Number Missed Vsync:\s*(\d+)"),
                             ("slow_ui", r"Number Slow UI thread:\s*(\d+)")]:
            m = re.search(pattern, text)
            if m:
                data[key] = int(m.group(1))
        hist = re.search(r"HISTOGRAM:(.+)", text)
        data["histogram"] = hist.group(1).strip() if hist else None
        return data

    conditions = {
        "device_model": sh("getprop ro.product.model").strip(),
        "sdk": sh("getprop ro.build.version.sdk").strip(),
        "emulator": sh("getprop ro.kernel.qemu").strip() == "1",
        "screen": sh("wm size").strip(),
        "density": sh("wm density").strip(),
        "battery": dict(re.findall(r"^\s*(level|temperature|status):\s*(-?\d+)\s*$",
                                   sh("dumpsys battery"), re.M)),
        "wifi_on": sh("settings get global wifi_on").strip(),
        "mobile_data_on": sh("settings get global mobile_data").strip(),
        "screen_on": "mWakefulness=Awake" in sh("dumpsys power"),
    }

    heap_before = meminfo_used()
    adb("shell", f"dumpsys gfxinfo {pkg} reset")
    trace = None
    if args.perfetto:
        traces = args.out.parent / "traces"
        traces.mkdir(parents=True, exist_ok=True)
        cfg = traces / "config.pbtxt"
        cfg.write_text(PERFETTO_CONFIG % (args.seconds * 1000 + 3000), encoding="utf-8")
        # SELinux forbids perfetto from reading /data/local/tmp, so the config goes via stdin.
        with cfg.open("rb") as stream:
            subprocess.run(["adb", "-s", args.serial, "shell",
                            "perfetto --txt -c - -o /data/misc/perfetto-traces/c005.pftrace --background"],
                           stdin=stream, capture_output=True, env=ENV, timeout=60, check=True)
        time.sleep(1)

    # Scenario: continuous catalog scroll bursts across the whole window.
    started = time.monotonic()
    swipes = 0
    while time.monotonic() - started < args.seconds:
        sh("input swipe 540 1700 540 700 220")
        swipes += 1
    measured = time.monotonic() - started

    if args.perfetto:
        # `duration_ms` ends the trace itself; wait for the daemon to flush before pulling.
        deadline = time.monotonic() + args.seconds + 20
        while time.monotonic() < deadline and sh("pidof perfetto").strip():
            time.sleep(1)
        time.sleep(1)
        trace = args.out.parent / "traces" / "c005.pftrace"
        adb("pull", "/data/misc/perfetto-traces/c005.pftrace", str(trace))

    heap_after = meminfo_used()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    result = {
        "package": pkg,
        "scenario": f"{args.seconds}s catalog scroll, {swipes} swipe gestures",
        "measured_seconds": round(measured, 2),
        "conditions": conditions,
        "startup_ms": None,
        "gfx": gfx(),
        "heap_pss_kb_before": heap_before,
        "heap_pss_kb_after": heap_after,
        "perfetto_trace": str(trace) if trace else None,
        "perfetto_trace_bytes": trace.stat().st_size if trace and trace.exists() else 0,
        "limits": "debug build; emulator; percentiles are gfxinfo buckets, not Macrobenchmark "
                  "FrameTimingMetric/frameOverrunMs; compare only identical runs",
    }
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: result[k] for k in ("measured_seconds", "gfx", "perfetto_trace_bytes",
                                             "heap_pss_kb_before", "heap_pss_kb_after")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
