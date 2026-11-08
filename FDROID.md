# Publishing Cadence on F-Droid

The public source repository and bilingual Fastlane descriptions are prepared. Submission is a source-build review, not an APK upload.

1. Licence: MIT, see `LICENSE`. The Exo 2 font in the preserved iOS project is under the SIL Open Font License; the Android app does not ship it.
2. Make a release commit and add the matching tag, for example `v1.0.0` for `versionName = "1.0.0"` and `versionCode = 1`.
3. Fork [fdroiddata](https://gitlab.com/fdroid/fdroiddata), copy `fdroid/io.github.professorengineergit.cadence.yml` to `metadata/io.github.professorengineergit.cadence.yml`, and submit a merge request. Alternatively, start an inclusion request through F-Droid's request-for-packaging process.
4. Point the build at the `android` subdirectory and its release variant. Run the F-Droid metadata lint and build checks before submitting.

Build details:

```text
Application ID: io.github.professorengineergit.cadence
Source: https://github.com/ProfessorEngineergit/Cadence
Subdirectory: android
Gradle task: assembleRelease
Version name: 1.0.0
Version code: 1
Compile/target SDK: 36
Java: 21
```

Release builds deliberately contain no signing configuration or keystore. Local SDK/cache paths are excluded through `.gitignore`; F-Droid uses its own build environment. No Google Play Services or Firebase is required.

F-Droid normally signs the app with its own key. A locally signed test installation therefore may not accept it as an update. Export practice data before changing distribution channels; CSV export is available, but CSV import is not currently implemented. Developer-signed reproducible builds are a separate option and require a stable release signing setup.

Official references:

- [Submission quick start](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/)
- [Inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)
- [Descriptions, graphics and screenshots](https://f-droid.org/docs/All_About_Descriptions_Graphics_and_Screenshots/)
- [Signing process](https://f-droid.org/docs/Signing_Process/)
