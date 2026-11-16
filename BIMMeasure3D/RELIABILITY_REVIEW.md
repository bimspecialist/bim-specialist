# BIM Measure 3D — Reliability patch v0.5 Beta

## Fixed code paths

**Durable records**
- Replaced SharedPreferences list rewriting with a single SQLite database and ACID transactions. Measurements are inserted as individual rows; a malformed row cannot cause the entire archive to be overwritten with an empty list.
- Project selection, project creation, selected units, and backup restore are committed transactionally.
- One-time migration on first SQLite creation reads legacy `bim_measure_projects_v1`, `bim_measure_3d` and `bim_measure_display` preferences. **Legacy preferences are never erased**. Migration failure stops without creating an empty replacement database.
- Backup format remains `BIMMeasure3D-Backup`, schemaVersion 1. The parser rejects invalid references and duplicates before a restore transaction.
- Database I/O and backup import/export move to `Dispatchers.IO`; Compose renders a storage error if the database cannot be read, and does not silently create a blank history.
- Changing projects is not allowed to erase an unsaved measurement when the database write fails.

**AR tracking**
- Added anchor tracking stability checks across frames; save/export controls wait until all anchors are actively tracking with stable positions.
- Reset stabilizer when anchor count changes and app goes to background.
- Projection uses fresh anchor positions from the latest ARCore update, not a previously captured Compose coordinate list.
- ARCore results remain estimates. This is **not a calibrated tape-measure replacement**.

**CAD DXF**
- Explicit coordinate mode controls: `ARCORE_RAW` preserves old behavior, and `CAD_Z_UP` uses a right-handed rotation `(x, y, z) -> (x, -z, y)`.
- Both modes retain a local ARCore session origin, and DXF units remain millimetres.
- No site control system, geographic positioning, real survey coordinates, direct Revit family authoring or certified accuracy is provided.

## Verification

GitHub Actions on branch `bim-measure-3d-reliability-fixes` executes:
- existing pure geometry, STL, OBJ, point cloud, projection, update metadata tests;
- additional CAD transform, tracking stabilization, backup round-trip and malicious backup rejection tests;
- Robolectric tests for SQLite migration from legacy SharedPreferences and recovery from failed transactions;
- Android Lint, APK build, package metadata and debug signature checks.

A green pipeline proves that the specified code paths pass simulated tests; it does NOT demonstrate ARCore camera behavior, measurement error or accurate fabrication/import on a physical phone.

## Outstanding external acceptance requirements

1. Test on a physical ARCore-supported Android phone in low light, airplane mode, screen rotation, background/resume and denied camera permission.
2. Compare several known lengths with a physical tape and log repeatability and absolute errors.
3. Open representative DXF (both axes), OBJ, STL and PLY files in the intended CAD and Revit import pipeline.
4. Test an installed signed Release-to-Release upgrade while preserving a real project database. The stable signing key must be installed in GitHub Actions Secrets first; CI Debug APKs are not valid perpetual upgrade installers.
5. Do not uninstall the existing installed APK until the user's records are backed up and a separately signed upgrade path is verified.

## Rollback

The original branch `bim-measure-3d-android-build` is untouched. The new branch only carries experimental patches. The pre-migration SharedPreferences remain on-device, and the new database file is stored separately.

If the v0.5 data migration fails, do **not** clear app storage: export old DXF/CSV/backup records or contact technical support before changing app installations.

## Safety and privacy

No new remote upload of measurement data. The local backup JSON contains potentially sensitive engineering geometry. Save it only to user-chosen secure storage. Update checking remains the existing GitHub-hosted workflow.