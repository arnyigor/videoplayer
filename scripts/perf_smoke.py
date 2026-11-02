"""S-059: identical main-activity cold-launch smoke for debug and benchmark.

No dependencies, data clearing, screenshots, logcat or raw dumps persisted.
Only allowlisted numeric gfxinfo/startup metrics and non-identifying conditions.
This checks measurement plumbing, not a release performance benchmark.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--transport", required=True, type=int)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--settle-seconds", type=int, default=8)
    parser.add_argument("--isolated", action="store_true",
                        help="Use APKs built with -PperfIsolated=true (never clear user data)")
    parser.add_argument("--iterations", type=int, default=1)
    parser.add_argument("--allow-emulator", action="store_true",
                        help="Diagnostic emulator run only; not physical-device performance evidence")
    args = parser.parse_args()
    if not 1 <= args.iterations <= 10:
        parser.error("iterations must be in 1..10")
    if not 1 <= args.settle_seconds <= 60:
        parser.error("settle-seconds must be in 1..60")
    env = dict(os.environ, MSYS_NO_PATHCONV="1")

    def adb(*command):
        result = subprocess.run(
            ["adb", "-t", str(args.transport), *command],
            capture_output=True, text=True, check=True, timeout=90, env=env,
        )
        return result.stdout.strip()

    sdk = int(adb("shell", "getprop", "ro.build.version.sdk"))
    is_emulator = adb("shell", "getprop", "ro.kernel.qemu") == "1"
    if is_emulator and not args.allow_emulator:
        raise RuntimeError("Physical device required unless --allow-emulator is explicit")
    def conditions():
        battery = adb("shell", "dumpsys", "battery")
        values = {key: int(value) for key, value in re.findall(
            r"^\s*(level|temperature|status):\s*(-?\d+)\s*$", battery, re.M
        )}
        # Only numeric/allowlisted fields; no SSID, device serial or user content.
        values["display_on"] = "mWakefulness=Awake" in adb("shell", "dumpsys", "power")
        policy = adb("shell", "dumpsys", "window", "policy")
        values["unlocked"] = "mIsShowing=false" in policy
        values["wifi_enabled"] = adb("shell", "settings", "get", "global", "wifi_on")
        values["mobile_data_enabled"] = adb("shell", "settings", "get", "global", "mobile_data")
        return values

    results = {
        "device_kind": "emulator" if is_emulator else "physical",
        "release_performance_evidence": False,
        "isolated_packages": args.isolated,
        "iterations": args.iterations,
        "display_size": adb("shell", "wm", "size"),
        "scenario": "force-stop -> explicit MainActivity -> settle (no interaction)",
        "sdk": sdk,
        "model": adb("shell", "getprop", "ro.product.model"),
        "settle_seconds": args.settle_seconds,
        "conditions": "Existing app data retained; network/temperature uncontrolled; no comparison or SLA claim",
        "frame_overrun_available": False,
        "runs": [],
    }
    # Alternating AB/BA order limits systematic order bias. Each target gets a warmup.
    order = []
    for iteration in range(args.iterations):
        pair = ("debug", "benchmark") if iteration % 2 == 0 else ("benchmark", "debug")
        order.extend((iteration + 1, variant) for variant in pair)
    for iteration, variant in order:
        package = "com.arny.mobilecinema." + ("perf" if args.isolated else "") + variant
        before = conditions()
        if not before["display_on"] or not before["unlocked"]:
            raise RuntimeError("Device must be awake and unlocked for measurements")
        adb("shell", "am", "force-stop", package)
        # Warm up code/data once; the subsequent force-stop discards process frame stats.
        component = package + "/com.arny.mobilecinema.presentation.MainActivity"
        warmup = adb("shell", "am", "start", "-W", "-n", component)
        if "Status: ok" not in warmup:
            raise RuntimeError("Activity warmup failed: " + variant)
        time.sleep(args.settle_seconds)
        adb("shell", "am", "force-stop", package)
        startup = adb("shell", "am", "start", "-W", "-n", component)
        if "Status: ok" not in startup:
            raise RuntimeError("Activity launch failed: " + variant)
        time.sleep(args.settle_seconds)
        # Fresh process: gfxinfo contains only this launch, not a prior user session.
        gfx = adb("shell", "dumpsys", "gfxinfo", package)
        startup_metrics = {
            key: int(value) for key, value in
            re.findall(r"^(ThisTime|TotalTime|WaitTime):\s*(\d+)$", startup, re.M)
        }
        patterns = {
            "total_frames": r"Total frames rendered:\s*(\d+)",
            "janky_frames": r"Janky frames:\s*(\d+)",
            "frame_p50_ms": r"50th percentile:\s*(\d+)ms",
            "frame_p90_ms": r"90th percentile:\s*(\d+)ms",
            "frame_p95_ms": r"95th percentile:\s*(\d+)ms",
            "frame_p99_ms": r"99th percentile:\s*(\d+)ms",
        }
        frame_metrics = {}
        for key, pattern in patterns.items():
            match = re.search(pattern, gfx)
            if match:
                frame_metrics[key] = int(match.group(1))
        if not startup_metrics or frame_metrics.get("total_frames", 0) <= 0:
            raise RuntimeError(
                f"No usable numeric measurements for {variant}: "
                f"startup={startup_metrics}, frames={frame_metrics.get('total_frames', 0)}"
            )
        after = conditions()
        if not after["display_on"] or not after["unlocked"]:
            raise RuntimeError("Device slept or locked during measurement")
        results["runs"].append({
            "iteration": iteration, "variant": variant,
            "conditions_before": before, "conditions_after": after,
            "startup_ms": startup_metrics, "gfxinfo": frame_metrics,
        })
        adb("shell", "am", "force-stop", package)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
