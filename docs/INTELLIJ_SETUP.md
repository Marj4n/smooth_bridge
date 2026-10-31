# IntelliJ IDEA — no Python or command-line scripts

1. Extract all files in the ZIP into a new folder.
2. Open IntelliJ IDEA → **File → Open** → select the folder containing `build.gradle` → **Trust Project**.
3. If prompted, **Load as Gradle project**. In **Settings → Build Tools → Gradle**, select **Gradle JVM: JDK 21**.
4. If there is no system Gradle, let IntelliJ download a compatible Gradle distribution (8.14 or newer) via its project import settings. There is deliberately no broken wrapper script or missing wrapper JAR in this ZIP.
5. Wait for the IDE to import Maven dependencies (the first import needs internet).
6. In the Gradle window on the right, open **Tasks → build → build**.
7. If `BUILD SUCCESSFUL`, inspect `build/libs/smooth_bridge-0.6.2+1.20.1-EXPERIMENTAL.jar`.
8. Read `docs/VALIDATION.md` and test only a **copied** 1.20.1 instance and new world. Never place the original audit v0.3 JAR beside the new candidate.

You can edit the project code in `src/main/java/org/marj4n/smoothbridge/`. No `.py`, `.kt`, or external scripts need to be run. Gradle is the build tool, not a gameplay mod and not a Python replacement.
