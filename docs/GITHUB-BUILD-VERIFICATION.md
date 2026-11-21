# GitHub Actions build verification — INCUID Chat v1.4.0

This repository uses `.github/workflows/build.yml` to run the same clean build on pushes to `main`, pull requests, and manual workflow dispatches.

## What GitHub verifies

- Ubuntu runner, Temurin Java 25, Gradle 9.2.1.
- `gradle --no-daemon clean build --stacktrace --console=plain` compiles source, compiles/runs Gradle-configured tests, and creates the JAR **only if Gradle succeeds**.
- The **Summary** page includes test counts from JUnit XML if the tests reached execution.
- `INCUIDChat-v1.4.0-build-reports` is uploaded **even when the build fails**; it contains the build log and any generated JUnit XML / HTML reports and Gradle problem reports.
- `INCUIDChat-v1.4.0-JAR` is uploaded **only when verification succeeds**.

## One-time setup / updating

1. Extract the source ZIP.
2. Upload or replace `.github/workflows/build.yml` and add `.github/scripts/ci_test_summary.py` in your repository. Preserve the leading dot in `.github` and the folder structure. If the browser upload hides `.github`, use **Add file → Create new file**, entering the full paths above, or use GitHub Desktop.
3. Commit to the `main` branch. The push triggers a new run automatically.
4. Go to **Actions → Build and Verify INCUID Chat → newest run → Summary**.
5. Download `INCUIDChat-v1.4.0-JAR` under **Artifacts** after a green run; unzip to obtain the plugin JAR.
6. If red, download `INCUIDChat-v1.4.0-build-reports`; provide `ci-logs/gradle-build.log` and the JUnit failure details for troubleshooting. You can also read the failed step in the GitHub Actions log.

**Do not rerun an old commit expecting new source fixes.** After editing code, commit the changes and use the newly triggered workflow run.

## What a green build does not prove

A passing build confirms Gradle compilation and the configured automated tests succeeded. It **does not** establish runtime compatibility on your Paper server or prove live chat behavior; still test with multiple players, including mentions, local/staff visibility, AFK, config migration, and personal health styles.

## Local build, if Java/Gradle are installed

```bash
gradle --no-daemon clean build --stacktrace --console=plain
```

The compiled JAR is written to `build/libs/`.
