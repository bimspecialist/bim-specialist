# BIM Measure 3D Beta: Privacy and Quality Gate

## Data handling

The application saves measurement records (coordinates, lengths, areas, local project IDs, date/time) in private local Android app storage. It exports geometry only when the user chooses a destination with the Android document picker. The current 3D point cloud remains in memory until explicitly exported and is not preserved after the Android process is terminated.

The application implements no analytics, account login, or cloud upload. ARCore/Google Play Services may require their own installation or service connectivity and have separate behavior.

Uninstalling the application, clearing its storage, or changing debug APK signing keys can cause loss of local records. Export DXF/CSV before uninstalling. Project records are not automatically synchronized or backed up.

## Engineering limitations

Camera-only AR measurement is approximate and susceptible to tracking drift, moving objects, texture/lighting conditions, and device sensors. No numerical accuracy guarantee is made.

The 3D Scan function captures sparse tracked points, **not** a watertight mesh. STL and OBJ are produced only by extruding a user-measured polygon footprint with a user-defined height; they are not automatic 3D scans.

## Evidence required before release

- CI: unit tests, lint, APK build, signing verification.
- Hardware: Android test device model and OS, ARCore availability, permission flow.
- Precision: controlled tests against tape with sample size, repeatability and error report.
- Export: open representative DXF, CSV, PLY, XYZ, OBJ and STL in target CAD or 3D software, validate scale and orientation.
- Installation: new install and upgrade with persistent signing credentials.

Until these are complete, the app remains **Beta / UNVERIFIED for professional field accuracy**.
