# AGENTS.md

## Build Check

Use this from the repository root to do a Java/Gradle compile check on Windows PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jre'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
./gradlew.bat assembleDebug
```

Notes:
- `java` is not reliably available on `PATH` in this environment, so set `JAVA_HOME` first.
- `assembleDebug` is the fast sanity check used here after UI/code changes.

## Editing Notes

- Prefer Windows PowerShell for edits in this repo. Git Bash is installed, but in this Codex environment `bash -lc` returned `Access is denied` and `C:\Program Files\Git\bin\bash.exe` failed with an MSYS `couldn't create signal pipe` error.
- When writing Android XML/resource files from shell commands, save as UTF-8 **without BOM**. A BOM in `res/layout/activity_main.xml` caused Android resource/data binding parsing failures (`mismatched input '?'` / `root is null`).
- PowerShell string replacement is brittle when the text contains Android resource syntax like `@string/...` or `@font/...`. Prefer single-quoted here-strings, line-based edits, or full-file rewrites over heavily escaped inline replacements.
- After editing layout/resource files, run `assembleDebug` immediately. This catches XML/resource merger issues quickly.

## File Guide

- `app/src/main/java/io/github/simonhalvdansson/flux/`: main app Java source, activities, widgets, price logic.
- `app/src/main/res/layout/`: Android layouts for activities and widgets.
- `app/src/main/res/drawable/`: shapes, icons, and widget/app UI drawables.
- `app/src/main/res/values/`: strings, styles, colors, dimens.
- `app/src/main/AndroidManifest.xml`: app manifest, launcher activity, services, widget receivers.
- `app/build.gradle`: app module build config and dependencies.
- `build.gradle` and `settings.gradle`: top-level Gradle configuration.