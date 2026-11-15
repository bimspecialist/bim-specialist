# BIM Measure 3D · v0.4.0 Beta

Experimental Android engineering measurement and modeling app by BIM Specialist.

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
- Projects and measurement history are local in Android SharedPreferences (100 most recent records). Point-cloud data are stored in memory until the user exports them; they are not retained when the process is terminated.
- No app backend or account; the app's own code does not upload measurement data. Android/Google AR services may have their own service behavior.
- If upgrading from a previously installed CI **debug APK**, the signatures are different from the new long-lived release. Android may require one final clean installation after you have exported important data. After that, keep the fixed signing key and install newer signed versions without uninstalling.
- Imported STL/OBJ models may require cleanup and independently verified units for production fabrication.

## Validation

The fixed-key signing workflow is separate: `.github/workflows/bim-measure-3d-release.yml`.
Release jobs are gated by an explicit `[signed-release]` commit marker or manual workflow dispatch. They fail safely if signing secrets are missing; they do not auto-generate another signing key. Production release artifacts are uploaded under a dedicated `bim-measure-3d-v...` tag to avoid changing unrelated product releases.


GitHub Actions workflow: `.github/workflows/bim-measure-3d-apk.yml`.
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
