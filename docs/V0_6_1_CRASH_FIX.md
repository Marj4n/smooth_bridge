# v0.6.2: preLaunch MixinTargetAlreadyLoadedException

## Source evidence
`latest(1).log`, around 05:09:05–05:09:45:
- Forge remapping finished for `lionfishapi`, `skarrier_mobs`, `netherman`,
  and `cataclysm`.
- `mixins.mm_kilt.json` failed while preparing
  `org.marj4n.smoothbridge.runtime.TargetJars`.
- The exception was `MixinTargetAlreadyLoadedException`.

## Root cause in Kilt 1.20.1
`KiltHelper.getForgeClassNodesInternal()` collects all `.class` entries
from its own JAR. `KiltEarlyRiser.processForgeClasses()` registers
transformations through `ClassTinkerers.addTransformation` for scanned
classes, except those matching `ignoredKeywords`, including `kilt`.

v0.6.0 injected ordinary Java classes into Kilt's JAR with a package name
that did not contain any ignored keyword. One of those classes was already
loaded early because the patched Forge discovery method called it.

## Change
Move `TargetJars`, `TargetJars$ModInfo`, and `EmbeddedLionfish` into the
`org.marj4n.smoothbridge.kilt_runtime` namespace and update generated
bytecode call owners to match.
The Java patcher fails fast if any emitted helper leaves that namespace.

No gameplay Forge JAR is modified, disabled, or redistributed.

## Test boundary
Static package check and javac compilation can be done offline.
A full Minecraft client launch, registry inspection, and gameplay testing
are still needed to prove runtime behavior.
