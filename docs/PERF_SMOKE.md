# S-059 — profiling target and measurement smoke

## Build

```sh
./gradlew :app:assembleDebug :app:assembleBenchmark --offline
python scripts/perf_smoke.py --transport <adb-transport-id> --output build/perf/s059-smoke.json
```

`benchmark` inherits release code/minification, uses a debug signing key and the isolated
`com.arny.mobilecinema.benchmark` application ID. Its manifest overlay enables shell profiling.
Production release and TV sources are untouched. Both targets use the same main sources;
normal debug-vs-release differences (`BuildConfig.DEBUG`, logging, developer preferences,
R8) remain intentionally present. No new dependency or Gradle module is required: the smoke
runner uses Python's standard library and adb, not AndroidX Macrobenchmark.

The runner is syntax-checked with `py_compile` and runs the same explicit MainActivity launch
on each installed target, with one warmup, a process force-stop, a measured launch and an
8-second settle. This **bypasses the splash activity**: TotalTime is main-activity launch time,
not the app's complete launcher startup. It validates measurement plumbing only.

It never clears app data or records logcat, screenshots, raw gfxinfo, media URLs or history.
The JSON contains only allowlisted numeric metrics, API/model and scenario conditions.
Force-stopping the debug target interrupts any current debug session. Use a test device.
The script does not install APKs or alter production package data.

## Verified on 2026-10-22

- `assembleDebug` + `assembleBenchmark --offline`: BUILD SUCCESSFUL.
- Both APKs installed successfully on one selected adb transport (30), physical ONEPLUS A6013,
  API 30; `ro.kernel.qemu` empty. Transport IDs are transient: select anew with `adb devices -l`.
- Merged manifests: debug `debuggable=true`; benchmark `debuggable=false`,
  `profileable android:shell=true`.
- Smoke JSON: `build/perf/s059-smoke.json` (ignored build artifact), two nonempty runs:

| Target | am TotalTime ms | Frames | gfxinfo P50/P90/P95/P99 ms |
|---|---:|---:|---|
| debug | 2693 | 134 | 7 / 21 / 117 / 300 |
| benchmark | 546 | 6 | 18 / 150 / 150 / 150 |

These are **not comparable performance results**: existing app data differ between packages,
network and temperature are uncontrolled, and six benchmark frames are not a useful statistical
sample. No optimization decision or SLA conclusion follows from these numbers. Raw artifacts
stay under `build/`, outside Git. No Perfetto trace was collected in this smoke.

## Remaining for S-003 and subsequent PERF steps

Establish equal synthetic data and repeatable workload, record temperature/network/display
conditions, then compare repeated runs. API 30 gfxinfo percentiles are not Macrobenchmark's
FrameTimingMetric and do not provide frameOverrunMs (requires API 31+ for that metric).
This runner does not replace the broader scenario matrix, Perfetto bottleneck diagnosis,
Macrobenchmark or release sign-off.
