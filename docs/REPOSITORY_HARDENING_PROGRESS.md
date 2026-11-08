# Repository hardening: current progress

No TLS/certificate changes are included: the original network behavior is retained. Existing user changes to the stabilization plan, mobile layouts, colors and theme were not rewritten. Current TV completion and CPU acceptance are recorded in [TV_COMPLETION.md](TV_COMPLETION.md). Catalog deletion based on 404 remains excluded from this work.

## Completed and verified

1. **Archive ingestion and cancellation**
   - ZIP path traversal, duplicate paths, entry-count and uncompressed-byte limits; isolated extraction directory.
   - Parse every selected JSON shard before changing the catalog; reject a malformed shard instead of silently skipping it.
   - Transactional catalog replacement; retain unavailable movies referenced by history, favorites or playback progress.
   - Prevent duplicate catalog operations, including local-file import; cancel the archive job explicitly.
   - Real-service tests use a fixture downloader, private Room DB and test-owned update timestamp. They do not replace the application's catalog.
2. **Catalog operation status**
   - One process-scoped tracker shared by service and settings: queued, downloading, unpacking, importing, scraping, success, failure, cancellation.
   - Settings shows progress, processed input-record count on archive success, and cancellation; repeated dispatch is blocked.
   - A new settings ViewModel immediately observes the existing operation/result. Status is not persisted across OS process death.
   - Scraper completion is determined by return, not by a nonexistent terminal Success event; per-item errors do not terminate scraping prematurely.
   - UpdateService uses `dataSync` with the matching foreground-service permission.
3. **Search restoration**
   - SavedStateHandle stores query, search type, source, filters and sort.
   - Draft text and last submitted query/type are separate; restore re-fetches the submitted search rather than submitting a draft.
   - Navigation defaults initialize once. RecyclerView waits for data before restoring its view state.
   - Full OS process-death restoration and long paginated-list scroll restoration are not verified on-device.
4. **Room migrations, partially complete**
   - Schemas 3–8 are available for Git and packaged as instrumentation assets.
   - MigrationTestHelper validates 3/4/5/6/7 → 8, user data, progress backfill, unique constraints and reopening through Room.
   - Versions 1–2 remain unverified. Local `1.json` already contains v3 columns, and git history has different structures with database version 1. It remains ignored; no speculative repair or destructive fallback was added.

## Verification

- Current normal-build unit suite: **288 tests, 0 failures/errors/skips**.
- Current TV CPU run: **9 Android media/track tests pass**, plus manual acceptance documented in TV_COMPLETION.md.
- Earlier API 30 Pixel 5 run: **18 targeted instrumentation tests, 0 failures/skips** (not rerun as part of TV completion):
  - ArchiveServiceIntegrationTest: 7;
  - CatalogAtomicityTest: 4;
  - DatabaseMigrationTest: 5;
  - CatalogCheckResponsivenessTest: 1;
  - SearchQueryIntegrationTest#queryAndSourceSurviveBackFromDetails: 1.
- UI-test setup grants the legacy storage permission on fresh installs and isolates live update checks, which otherwise display dialogs above the tested navigation.
- Final focused read-only review found no new blockers; it did not independently rerun the tests.
- **Lint is not green:** latest normal-build run has 343 errors, 362 warnings. Error IDs: UnsafeOptInUsageError (339), MissingLeanbackLauncher, ImpliedTouchscreenHardware, MissingLeanbackSupport, UseAppTint. No baseline was introduced to hide them; new TV RestrictedApi findings were fixed via the public Window.Callback API.
- Earlier phone-stage logs: `build/repository-hardening-final-tests.log`, `build/repository-hardening-lint.log`. Current TV/normal-build logs and APKs are listed in TV_COMPLETION.md. Logs are disposable outputs; commands and acceptance results are recorded in the documents.

```sh
./gradlew :app:testDebugUnitTest --rerun :app:connectedDebugAndroidTest \
  -PmobileUiTest=true \
  -Pandroid.testInstrumentationRunnerArguments.class=com.arny.mobilecinema.data.ArchiveServiceIntegrationTest,com.arny.mobilecinema.data.CatalogAtomicityTest,com.arny.mobilecinema.data.DatabaseMigrationTest,com.arny.mobilecinema.presentation.settings.CatalogCheckResponsivenessTest,com.arny.mobilecinema.presentation.sitesearch.SearchQueryIntegrationTest#queryAndSourceSurviveBackFromDetails \
  --offline --console=plain
./gradlew :app:lintDebug --offline --console=plain
```

## Remaining, in the agreed order

1. Obtain trustworthy v1/v2 databases before changing their migration path; do not infer them from the local v1 snapshot.
2. TV shared-link routing is implemented and checked cold/warm. Broader phone entry and redirect/network-destination validation remain separate work.
3. TV CPU core playback/navigation acceptance is complete for the tested fixtures. Physical devices, voice/CEC and the full external HLS/DASH/CDN matrix remain unverified; see TV_COMPLETION.md.
4. CI/release gates and documentation: resolve/classify lint failures, add instrumentation smoke and gate publishing on successful checks. Do not merely make current errors non-blocking.
5. Later: measured performance work and targeted responsibility extraction, not a blanket UI/architecture rewrite.
