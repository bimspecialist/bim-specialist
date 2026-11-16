# BIM Measure 3D · v0.7.0 Beta

Experimental Android engineering measurement and modeling app by BIM Specialist.

## ARCore device compatibility and manual measurement (v0.7)

The app now supports **AR Optional** mode and can open the home dashboard, project history, and an offline tape-measure entry tool even on a phone where Google Play Services for AR is unavailable.

- A non-blocking Google ARCore availability check runs on application resume. Camera modes are gated until Google reports `SUPPORTED_INSTALLED`; unsupported and indeterminate devices get accurate guidance and a retry action.
- Android camera permission is requested **only if the user launches a camera feature**. The offline measurement calculator needs no camera permission.
- The manual tool supports length and rectangular area with entered metre/cm/mm measurements, including Arabic-Indic digits. It saves records to the current project's SQLite database with `source=MANUAL`. History marks the record as manual, never as a camera measurement. CSV exports include the manual source tag.
- SQLite schema upgrades from version 1 to version 2 transactionally using `ALTER TABLE ... ADD COLUMN source TEXT NOT NULL DEFAULT 'ARCORE'` without dropping original records. Backup imports without a source attribute retain the legacy default `ARCORE`.
- ARCore sessions that fail report an error rather than pretending a tracked measurement is valid. The app can still access saved records and manual input.

**Important:** Manual input uses measurements typed from a physical tape. It does **not** make the phone camera measure distance without ARCore. GT 20 Pro hardware compatibility with ARCore must be confirmed on the actual device, independent of passing JVM tests.

**Update caveat:** CI debug builds use temporary signing keys. They may not install as updates to already installed versions; do not uninstall an app holding important local data until it is backed up. Persistent release signing still requires the repository owner to configure a private keystore.

## GT 20 Pro responsive interface (v0.6.1 beta)

Hardware reference: Infinix GT 20 Pro has a 6.78-inch screen, 1080 × 2436 physical pixels and a 20.3:9 aspect ratio. Android display size, logical dp, font scale and gesture-navigation insets vary by user setting. **The UI detects its available dp bounds at runtime, not a hardcoded device model or pixel density.**

- The landing page uses safe drawing insets for the top camera cutout and gesture navigation.
- Compact cards and a smaller heading respond to font scaling, small screens and display-size settings without cutting off labels.
- The capture screen reserves a substantial central camera region by capping the bottom sheet using the height available in dp.
- The camera screen places its controls in a **right/left side panel (using layout alignment)** in landscape, retaining the camera view.
- Measurement units have larger tap targets, tracking status truncates safely, and primary buttons are at least 48dp tall.
- The advanced controls respect Android safe drawing insets and have bounded scrollable regions.
- Added Android Studio design previews for GT20-like 393 × 880dp, Arabic home, Arabic capture and 135% font size.
- Added pure unit tests for tall portrait, 135%-150% font scale, narrower displays, landscape and invalid layout inputs.

**These changes are UI layout adaptations, not proof of physical Infinix hardware compatibility.** Actual dp dimensions may differ; verify on the device and capture a screenshot with Android settings (display size, font size and gesture nav) noted. The phone's **ARCore support must be checked independently** before camera-measurement functionality can be assumed to work.

## Redesigned camera interface in v0.6

This update replaces the default crowded AR screen with an approachable **start dashboard** and a dedicated minimal **camera screen**, following the original blue/navy visual concept. This is implemented using live Jetpack Compose UI, not a static image.

1. Launch app → choose **Length**, **Area**, or **3D Point Scan** from large labelled cards.
2. Camera opens only after choosing a tool. Watch tracking status, tap points on the camera, and read the prominent result.
3. Press **Save measurement**. Units can be switched without opening another page.
4. Use **Undo**, **Reset** or **Export** as needed. Choose DXF/CSV or PLY/XYZ in an uncluttered export dialog.
5. Tap **More** for professional tools: DXF axes, reference error, STL/OBJ extrusion, advanced geometry and cloud controls.
6. Tap **Home** (or Android Back) to leave. If unsaved points exist, a confirmation prevents accidental data loss.
7. **Projects**, **History**, and **Updates** are available from the start dashboard. Project backup/restore remain available within Projects.

**Note:** Screen design follows the earlier **concept illustrations** but is not pixel-identical; the camera preview and AR tracking require a supported Android phone. UI camera positioning, real-world usability and accessibility still require device tests.

## Reliability improvements in 0.5.0-beta

- The app now writes measurement records to a **single SQLite database**. SQLite transactions keep project selection, units, measurements and backup restores consistent. The old preferences are only read once for a non-destructive migration and remain untouched as a safety fallback.
- Corrupt legacy records fail closed without overwriting them with a new empty list. The UI reports a storage error and offers backup import/export when possible.
- SQL/database reads, writes, archive import and export now run off the main UI thread.
- The camera requires a short period of stable anchor tracking before saving or exporting measurements. This reduces preventable transient errors but **does not certify physical measurement accuracy**.
- DXF adds explicit `AR raw (Y-up)` and `CAD Z-up` modes. Neither mode has real-world survey control coordinates.
- The CI includes Robolectric database tests (legacy migration, corrupt records, rollback on injected write failure) in addition to pure geometry and security tests.
- For failure modes and rollback criteria see [RELIABILITY_REVIEW.md](RELIABILITY_REVIEW.md).

## Upgrade without uninstalling

The **installed release signing certificate must remain identical for the entire lifetime of this Android application**. Versions 0.2/0.3 were CI-signed with different temporary debug keys, which prevents seamless upgrades. Starting in v0.4, the production release workflow requires a permanent PKCS12 signing key stored exclusively in GitHub Actions Secrets. The source code and repository never contain that private key.

**No production-signed APK exists until the owner completes the one-time GitHub Actions secrets setup and launches a signed-release build.** See [SIGNING_SETUP_AR.md](SIGNING_SETUP_AR.md). The normal CI debug APK remains a test artifact only, **not the permanent-install version**.

After the first signed release is installed, the app checks a pinned HTTPS update manifest on app launch and on demand. When a newer signed release exists, the user can download it in-app; SHA-256 and file length are checked before Android's installer prompts for approval. Updates preserve project data when the signing certificate and package name match. Android does not permit unconsented silent installs for ordinary sideloaded apps.

**Backup / restore:** Open the Projects dialog and export all project/measurement records to a JSON archive. Restoring an archive validates its contents and requests confirmation before replacing local records. Backup files contain potentially sensitive site coordinates: store them securely and do not post them publicly.

## Three independent work modes

| Mode | Implementation | Export |
| --- | --- | --- |
| Length | Two detected-plane points, AR overlay, metre/cm/mm readout, reference tape comparison | DXF, CSV |
| Area | 3–24 polygon vertices, area, perimeter, overlays, self-intersection/nonplanar checks | DXF, CSV |
| 3D Scan | Sparse ARCore point-cloud capture, pause/resume, bounds, rotatable point-cloud preview | PLY, XYZ |

### New: solid model from a measured outline

In **Area** mode, after selecting a valid polygon, specify **Extrusion height (metres)**. The app triangulates the outline, creates a closed solid with side walls, and allows a rotatable wireframe preview. Export to **binary STL** or **OBJ**.

**This is not object scanning.** A solid extruded from an outline is **not** a photogrammetric reconstruction of an object. The independent 3D Scan mode still returns a **sparse point cloud**. STL/OBJ from an area has no texture or inferred shape beyond the selected boundary and height.

### Exports and coordinates

- DXF coordinates: local ARCore points multiplied by 1000, header declares **millimetres**.
- STL binary: unitless file format; numeric values written according to the **millimetre** convention.
- OBJ: coordinates written in **millimetres**, file header documents the units.
- CSV: metre, square metre and individual point coordinates in metres.
- PLY/XYZ: local session point-cloud coordinates **in metres**.
- ARCore coordinates are **relative to the tracking session**, not site survey/control or BIM world coordinates. Check import units and orientation in target CAD software.

## Project workflow

1. From the top bar choose the current **Project**, or create a new one (stored on device).
2. Choose **Length**, **Area**, or **3D Scan**. Changing modes prompts before discarding unsaved live points.
3. Place AR points on detected planes and verify measurements with a physical reference.
4. Save measurements to the project's local history and export as necessary.
5. For Area, enter extrusion height, preview the generated mesh, then export STL/OBJ.
6. For 3D Scan, move around a textured, well-lit object, pause, review the point-cloud bounds, then export PLY/XYZ.

## Limitations and safety

- **Beta software**; no guaranteed measurement accuracy, no professional survey certification.
- Device camera intrinsics, lighting, motion, ARCore drift and surface detection limit reliability.
- No direct STL mesh from point cloud; no automatically generated Revit walls/families or IFC.
- This version is limited to supported Android ARCore devices. Runtime camera permission is necessary.
- Projects and measurement history are stored in an on-device SQLite database. Legacy SharedPreferences are left untouched after migration. Point-cloud data stay in memory until exported and are not retained after process termination.
- No app backend or account; the app's own code does not upload measurement data. Android/Google AR services may have their own service behavior.
- If upgrading from a previously installed CI **debug APK**, the signatures are different from the new long-lived release. Android may require one final clean installation after you have exported important data. After that, keep the fixed signing key and install newer signed versions without uninstalling.
- Imported STL/OBJ models may require cleanup and independently verified units for production fabrication.

## Validation

The fixed-key signing workflow is separate: `.github/workflows/bim-measure-3d-release.yml`.
Release jobs are gated by an explicit `[signed-release]` commit marker or manual workflow dispatch. They fail safely if signing secrets are missing; they do not auto-generate another signing key. Production release artifacts are uploaded under a dedicated `bim-measure-3d-v...` tag to avoid changing unrelated product releases.


GitHub Actions workflow: `.github/workflows/bim-measure-3d-apk.yml` (also checks `bim-measure-3d-reliability-fixes`).
On `bim-measure-3d-android-build` it runs:
1. Android 36 + Java 21 + Gradle 8.13 installation.
2. All JVM unit tests: geometry, winding, manifold edges, STL, OBJ, DXF, CSV, point-sampling, projection and reference error.
3. Android Lint (`:app:lintDebug`).
4. Debug APK compilation, ZIP integrity, Android APK signature, and application identifier checks.
5. Upload APK and test report artifacts.

**Passing this pipeline does not verify AR tracking, runtime performance, camera access or precision on a real phone.** On-device test is mandatory before production use.

### Field acceptance test (manual; not yet conducted)

- Cold install and runtime permission grant/denial; unavailable-ARCore phone behavior.
- Measure reference lengths of 0.5, 1, 2 and 3 m in multiple orientations; compare absolute and relative error with calibrated tape.
- Measure rectangular and concave polygons; check calculations and polygon hints.
- Close/relaunch app and verify local project history; export DXF/CSV and inspect them in CAD.
- Extrude a known polygon, inspect STL/OBJ in a mesh viewer, verify millimetre dimensions and watertight geometry.
- Scan a textured object from different angles; verify PLY/XYZ file point count and bounds; never infer print readiness.
- Test airplane mode, rotation, low light, large fonts, background/resume, full point buffer and file-picker cancellation.

## Toolchain

JDK 21, Gradle 8.13, Android SDK 36, Android Gradle Plugin 8.13.2, Kotlin 2.3.21, Jetpack Compose, SceneView 4.18.0, ARCore. Minimum API 26; target SDK 36.

## Version history

- `0.1.0-alpha`: ARCore prototype, DXF/PLY
- `0.2.0-beta`: overlays, area perimeter, units, local history, CSV, XYZ and tests
- `0.3.0-beta`: named local projects, tape-reference error comparison, real watertight extrusion STL/OBJ from area, interactive solid preview, cloud bounding dimensions, adaptive icon, lifecycle cleanup, extra tests and lint
- `0.4.0-beta`: persistent release-signing workflow (requires owner secrets), in-app version checks and verified package installation with Android user consent, full JSON backup/restore, update validation tests and clearer CI debug labeling.
- `0.5.0-beta`: SQLite transactional persistence, legacy migration, IO dispatch, AR anchor stability, explicit CAD axes and simulated database fault tests.

- `0.6.0-beta`: new home dashboard, simplified camera controls, accessible navigation, collapsible advanced tools, export choices and minimal-screen state tests.

- `0.6.1-beta`: runtime sizing and insets for GT 20 Pro class screens; landscape side panel, accessible tap targets, compact home cards and UI layout tests.

- `0.7.0-beta`: AR Optional device checks, offline manual tape dimensions, record provenance and non-destructive SQLite schema migration.
