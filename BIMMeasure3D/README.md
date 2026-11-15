# BIM Measure 3D · 0.2.0 Beta

Android (Kotlin + Compose + ARCore/SceneView). Experimental engineering measurement and point-cloud capture.

## Capabilities
- Separate camera workflows for **Length** and **Area**.
- Live AR visual markers, connecting lines and shaded polygon.
- Choose metres/centimetres/millimetres; show **area + perimeter**.
- Reject some self-intersecting, duplicated, collinear or nonplanar polygons.
- Undo/clear; local history (up to 100 measurements); export history.
- Length/area export to **DXF** (local coordinates **in mm**) and **CSV** (SI units).
- Experimental sparse 3D point-cloud capture with pause/resume, rotating preview, export **PLY** and **XYZ** (metres).
- Android Storage Access Framework lets the user choose export destination.
- No external account or remote upload required. History remains on device unless app is uninstalled/cleared.
- Arabic/English text follows system language.

## Important limitations
Camera/ARCore gives **estimated** measurements. No professional accuracy certification, no measured accuracy claims.
- A sparse ARCore feature-point cloud is **not a closed mesh**, STL, nor a Revit-native Family.
- Local AR tracking coordinates are **not survey control coordinates**.
- ARCore-supporting Android phone and camera permission required.
- No device test yet: UI positioning, lighting, model tracking, orientation behavior and real-world accuracy must be verified on hardware.
- Android debug APK is signed with an ephemeral CI debug key. It **may not update a prior debug install**; uninstalling an old APK will delete its local history. Production release signing should use a persistent keystore stored securely in GitHub Actions secrets.

## Build
GitHub Actions: `BIM Measure 3D - Android APK` on branch `bim-measure-3d-android-build`.
The workflow runs 15 JVM unit tests then `assembleDebug`, and uploads an APK artifact if both succeed.

Build requirements: JDK 21; Gradle 8.13; Android SDK 36; Android Gradle Plugin 8.13.2; Kotlin 2.3.21; SceneView 4.18.0.

## Field verification checklist
1. Fresh install on an ARCore-approved Android phone; camera permission grant and denial.
2. Detect a flat surface; tap two points a known distance apart and compare with a tape measure.
3. Create a 4-point rectangle; check perimeter and area against measured dimensions.
4. Test self-intersecting/nonplanar polygon feedback, undo and clear.
5. Save a measurement, force-stop and reopen; verify history and export to DXF/CSV.
6. Pause/resume point-cloud capture; rotate preview; export PLY/XYZ.
7. Check airplane mode, device rotation, large text and interrupted Android file-picker.
8. Validate generated DXF and PLY in CAD/3D tools **before** claiming interoperability.
