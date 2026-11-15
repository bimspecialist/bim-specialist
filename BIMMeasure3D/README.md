# BIM Measure 3D — Android AR prototype

**Experimental / not validated on a physical device.**

- Independent Length and Area modes using ARCore points on detected planes.
- Experimental 3D Scan collecting sparse ARCore feature points (not a printable mesh).
- DXF export of geometric line segments (mm), PLY export of 3D points (m).
- Arabic / English system language. Requires ARCore-supported Android phone.

## Build
GitHub Actions: open the Actions tab -> Android APK (experimental) -> latest successful run -> download `BIMMeasure3D-debug-apk` artifact; extract ZIP to install the APK.

This build is a **debug APK**, signed using the standard Android debug key. Not a Play Store release. No data is uploaded to an external server. DXF/PLY files are written only where the user chooses in the Android file picker.

The app has no calibrated accuracy guarantee. Do not use for construction setting-out, safety, fabrication or precise surveys without independent verification.

Android SDK 37, JDK 21, Gradle 9.6.0, AGP 9.4, Kotlin 2.4.10.
