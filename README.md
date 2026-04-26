# QuickJS + Java FFM

Minimal example that integrates QuickJS with Java via FFM (no JNI), including cross-platform native artifact tasks.

## Requirements

- macOS host
- Zig (`zig` on `PATH`) with `zig cc` available
- Internet access to download QuickJS source

## Native build targets

Artifacts are staged in:

- `build/native/macos-aarch64/libquickjs.dylib`
- `build/native/linux-x86_64/libquickjs.so`
- `build/native/windows-x86_64/quickjs.dll`

Build commands:

```bash
./gradlew buildNativeMac
./gradlew buildNativeLinux
./gradlew buildNativeWindows
./gradlew buildNativeAll
./gradlew assembleNativeDist
```

## Run and test

By default, `run`, `compileJava`, and `test` use host target `macos-aarch64`.

```bash
./gradlew run
./gradlew test
```

You can override host target selection:

```bash
./gradlew run -PhostNativeTarget=macos-aarch64
```

## Notes

- Override Zig executable path if needed: `-PzigCommand=/path/to/zig`
- If Zig is missing, native build tasks fail with a clear process start error.
- Source QuickJS archive: `https://github.com/quickjs-ng/quickjs/archive/refs/tags/v0.14.0.tar.gz`

## GitHub Actions: Build and release JAR

This repository includes two workflows:

- `.github/workflows/ci.yml`: runs on push and pull request and executes `./gradlew test`
- `.github/workflows/release.yml`: runs on version tag push (`v*`), builds JARs, uploads them as GitHub Release assets, and publishes to GitHub Packages (Maven)

### Release process

1. Create and push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

2. The release workflow will:
   - derive version `1.0.0` from tag `v1.0.0`
   - build with `-PreleaseVersion=1.0.0`
   - upload `build/libs/*.jar` to the GitHub Release
   - publish package `io.github.julia_script:javaquickjs:1.0.0` to GitHub Packages

### GitHub Packages auth

- Uses default workflow credentials:
  - `GITHUB_ACTOR`
  - `GITHUB_TOKEN` (automatic `secrets.GITHUB_TOKEN`)
- Workflow permissions include:
  - `contents: write`
  - `packages: write`
