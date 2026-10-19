# jpexs-and

Android viewer for SWF files built on the JPEXS Free Flash Decompiler library (`ffdec_lib`).
Open a `.swf` and browse its images, shapes, morphs, sprites (animated) and main timeline frames.

## How it works
`ffdec_lib` depends on `java.awt`, which Android lacks. `tools/build-lib.sh` compiles the lib at a pinned
JPEXS commit, generates a `java.awt` stand-in (`tools/ShimGen.java`, real OpenJDK geometry via
`tools/overlay.py`, `shim/jawt/Color.java`), and remaps bytecode with `tools/Remap.java`.
The app (`app/`) parses with the lib and draws with its own Android renderer (`Rnd.kt`).

## Build
GitHub Actions (`Build APK`) produces the debug APK as an artifact. Locally: JDK 21, then
`bash tools/build-lib.sh && gradle :app:assembleDebug`.

## Limits
Static text and buttons are listed but not previewed; filters and blend modes are ignored.

The lib is GPLv3, see https://github.com/jindrapetrik/jpexs-decompiler
