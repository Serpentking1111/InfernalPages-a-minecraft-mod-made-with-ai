# Agent Handoff — Build and Ship `infernalpages-1.13.7.jar`

You are an AI agent picking up this handoff. Goal: produce
`latest/infernalpages-1.13.7.jar` and `releases/infernalpages-1.13.7.jar`
on the `master` branch of the `Serpentking1111/InfernalPages-a-minecraft-mod-made-with-ai`
GitHub repo, and push them. Source is already designed, reviewed and
merged; you do *not* need to write, read or design any Java.

---

## TL;DR

1. `git clone https://github.com/Serpentking1111/InfernalPages-a-minecraft-mod-made-with-ai.git`
2. `cd InfernalPages-a-minecraft-mod-made-with-ai && git checkout a8143a0`
3. `export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))`
4. `./gradlew remapJar`
5. `cp build/libs/infernalpages-1.13.7.jar latest/`
6. `cp build/libs/infernalpages-1.13.7.jar releases/`
7. `git checkout master`
8. `git checkout a8143a0 -- latest/ releases/ src/main/resources/fabric.mod.json gradle.properties`
   *(or just copy the 4 files a8143a0 already touched into the existing tracked tree — see step 8 expanded below)*
9. `git add latest/infernalpages-1.13.7.jar releases/infernalpages-1.13.7.jar`
10. `git commit -m "Ship 1.13.7 jar"`
11. `git push`

If `./gradlew remapJar` works first try, the rest is `cp` + `git add` + `commit` + `push`.

---

## Background — what is this update

**Version bump 1.13.6 → 1.13.7 (PATCH bump per §11 of `DEVELOPMENT_HANDOFF.md`):**
BLUNT was already zeroing attack damage (1.13.6, see `Sharpening.attribute()`,
`Sharpening.modifier()`), but its enchantments were untouched — a BLUNT-ed
netherite sword with Fire Aspect would still set mobs on fire. 1.13.7
fixes that by snapshotting enchantments on BLUNT-entry and restoring them
on reroll-away-from-BLUNT.

**Modifications are entirely source-and-resource code; no class-shape change
beyond what's already in 1.13.6.** Diff vs `master` is +67/-13 across 7
files:

```
M DEVELOPMENT_HANDOFF.md                            (header version + changelog bullet)
M README.md                                         (Current version line)
M gradle.properties                                 (mod_version 1.13.6 -> 1.13.7)
M latest/README.md                                  (points at 1.13.7 jar)
M src/main/java/net/infernalpages/item/Sharpening.java       (the actual fix)
M src/main/java/net/infernalpages/registry/ModComponents.java (new SAVED_ENCHANTMENTS data component)
M src/main/resources/fabric.mod.json                ("Infernal Pages 1.13.6" -> "1.13.7")
```

No .png/.json assets touched. No new mixin. The fix is:

```
ModComponents.SAVED_ENCHANTMENTS : ComponentType<ItemEnchantments>
                                  = register("saved_enchantments",
                                            ItemEnchantments.CODEC,
                                            ItemEnchantments.PACKET_CODEC);

Sharpening.applyToStack(stack)
  1. previous = fromStack(stack)
  2. compute result attribute modifier (unchanged behaviour for RANGE/WIND/PERFECT)
  3. if previous == BLUNT:
       restore stack.ENCHANTMENTS from SAVED_ENCHANTMENTS (or remove if empty)
       remove SAVED_ENCHANTMENTS
  4. write new attribute modifier; write SHARPENING id
  5. if this == BLUNT:
       snapshot current ENCHANTMENTS into SAVED_ENCHANTMENTS (only if non-empty)
       remove ENCHANTMENTS
```

BLUNT → BLUNT hits step 3 then step 5, so it's idempotent across re-rolls.

---

## Step-by-step

### 0. Prereqs

- **JDK 21** with `javac` on `PATH`. The project sets
  `sourceCompatibility = JavaVersion.VERSION_21` (build.gradle line 56-57).
  JDK 11 / 17 will fail compilation. JDK 22+ may work but is not tested.
- **Gradle 9.x** is not needed — `./gradlew` wrapper downloads it.
- **At least 4 GB of free RAM**. Loom remap's Gradle daemon OOMs at ~2 GB
  with `Gradle build daemon disappeared unexpectedly` during the
  `rebuilding loom cache` phase. On a 16 GB box the build takes ~2 minutes;
  warm-cache builds are faster.
- **Network access** to download Yarn mappings (~150 MB), Fabric API
  (~5 MB), GeckoLib (already vendored in `libs/`).
- **Disk**: ~1 GB free for the Loom cache (`~/.gradle/caches/fabric-loom/`).

Linux or macOS. Windows works with WSL; native PowerShell may not — the
`gradlew` script is a POSIX shell wrapper.

### 1. Clone and check out the source commit

```bash
git clone https://github.com/Serpentking1111/InfernalPages-a-minecraft-mod-made-with-ai.git
cd InfernalPages-a-minecraft-mod-made-with-ai
# Checkout the commit that contains the 1.13.7 source change.
# This is `master` as of 2026-08-20 but pinning is safer in case
# more commits land. If `git log` shows a different HEAD on master,
# cherry-pick a8143a0 instead.
git checkout a8143a0
git log -1    # should show: 1.13.7: BLUNT now also strips the weapon's enchantments
```

Verify the source content:
```bash
grep -n "SAVED_ENCHANTMENTS" src/main/java/net/infernalpages/registry/ModComponents.java
grep -n "this == BLUNT\|previous == BLUNT" src/main/java/net/infernalpages/item/Sharpening.java
grep -n "mod_version=1.13.7" gradle.properties
```
All three greps should produce non-empty output.

### 2. Confirm JDK 21

```bash
javac -version
# expect: javac 21.x
```

If wrong, point `JAVA_HOME` at JDK 21:
```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

Common locations:
- macOS Homebrew: `JAVA_HOME=$(brew --prefix openjdk@21)`
- Linux apt: `/usr/lib/jvm/java-21-openjdk-amd64`
- asdf: `JAVA_HOME=$(asdf where java)`

### 3. Build the jar

```bash
./gradlew remapJar
```

This runs:
- `:compileJava` (compile Yarn-mapped sources)
- `:processResources` (substitute `${version}` into `fabric.mod.json`)
- `:jar` (initial yarn-named jar)
- `:remapJar` (Loom remaps to Mojang names + applies the GeckoLib jar-in-jar)

Expected output: `BUILD SUCCESSFUL in 2m` followed by the Loom `remapJar`
task confirmation. The jar lands at:

```
/path/to/repo/build/libs/infernalpages-1.13.7.jar
```

### 4. Verify the jar

Size will be ~217 KB (the bytecode is identical to 1.13.6's jar + the new
SAVED_ENCHANTMENTS component code path, so the difference is just the few
extra bytecode bytes).

```bash
ls -la build/libs/infernalpages-1.13.7.jar
file build/libs/infernalpages-1.13.7.jar
# expect: Zip archive data, at least v3.0 to extract

# Check the version metadata is correct
unzip -p build/libs/infernalpages-1.13.7.jar fabric.mod.json | python3 -m json.tool
# expect: "name": "Infernal Pages 1.13.7", "version": "1.13.7"
```

Spot-check the bytecode change:
```bash
mkdir -p /tmp/verify-1.13.7
cd /tmp/verify-1.13.7
unzip -o /path/to/repo/build/libs/infernalpages-1.13.7.jar \
    net/infernalpages/item/Sharpening.class \
    net/infernalpages/registry/ModComponents.class \
    net/infernalpages/mixin/SharpeningDamageMixin.class

javap -p net/infernalpages/item/Sharpening.class | grep -E "attribute|modifier|modifierId"
# expect to see them cleanly listed

javap -c -p net/infernalpages/item/Sharpening.class | sed -n '/public.*modifier/,/^$/p' | head -50
# expect: tableswitch 1 to 6 (RANGE/CLOSE/SPEED/WIND/PERFECT/BLUNT)
# case 6 emits -1.0d (BLUNT's ADD_MULTIPLIED_TOTAL modifier)

javap -p net/infernalpages/registry/ModComponents.class | grep SAVED_ENCHANTMENTS
# expect: public static final ComponentType SAVED_ENCHANTMENTS
```

### 5. Place the jar in `latest/` (the always-current copy)

```bash
cp build/libs/infernalpages-1.13.7.jar latest/
```

The `latest/README.md` is already on `a8143a0` and points at
`infernalpages-1.13.7.jar`. Verify it didn't drift:

```bash
head -10 latest/README.md
# expect:
#   **Current version: 1.13.7**
#   Click infernalpages-1.13.7.jar
#   ...raw/master/latest/infernalpages-1.13.7.jar
```

If the working tree was a clean checkout of `a8143a0` (which it will be
since we just checked out that commit), `latest/README.md` already matches.

### 6. Place the jar in `releases/` (the historical archive)

```bash
cp build/libs/infernalpages-1.13.7.jar releases/
```

Per §11 of the handoff doc, every release is permanently archived in
`releases/`. Older jars (1.12.3 through 1.13.6) are already committed.

### 7. Switch to master and bring the jar + the source files together

```bash
git checkout master
```

`master` is currently `32a7aa3` (1.13.6's tip, before the 1.13.7 commit).
Step 8 puts `a8143a0`'s source changes + your newly-built jar onto master.

### 8. Bring over the four-file version bump + the .java changes

`latest/README.md` etc. are different between `a8143a0` and `32a7aa3`.
Two equivalent ways:

```bash
# (a) cherry-pick a8143a0 onto master, then add the jars
git cherry-pick a8143a0
# expect: "1.13.7: BLUNT now also strips..." lands cleanly on 32a7aa3
# If cherry-pick produces conflicts (extremely unlikely since the only
# difference between 32a7aa3 and a8143a0 is the 1.13.7 files and adding
# the jars, not editing any tracked-on-master file), resolve by hand and
# `git cherry-pick --continue`.
git add latest/infernalpages-1.13.7.jar
git add releases/infernalpages-1.13.7.jar
git commit --amend --no-edit
```

…or simpler if `master` is still pointing at `32a7aa3`:

```bash
# (b) treat jars as separate semantics from the source commit
git checkout a8143a0 -- \
    src/main/java/net/infernalpages/ \
    src/main/resources/fabric.mod.json \
    gradle.properties \
    DEVELOPMENT_HANDOFF.md \
    README.md \
    latest/README.md
git add latest/infernalpages-1.13.7.jar releases/infernalpages-1.13.7.jar
git commit -m "1.13.7: build and ship jar"
```

Either path is fine. Pick (a) if you want a single squashed commit with the
full history; pick (b) if the existing 1.13.7 source commit is already
canonical and the jar is a follow-up (this is what the project tends to do
for asset-only changes — but for a source change it's the same commit
in practice).

### 9. Push

```bash
git push origin master
```

If you have 2FA: use a PAT or SSH key. Sandbox forks will need either.

### 10. Verify the push

```bash
git fetch origin master
git ls-remote origin master
# expect: a8143a081eda96aa82ee5385471232045a290c79 refs/heads/master
# (or the new head SHA of the ship-the-jar commit, which will be a child of a8143a0)
curl -sL -o /tmp/infernalpages-1.13.7.jar https://github.com/Serpentking1111/InfernalPages-a-minecraft-mod-made-with-ai/raw/master/latest/infernalpages-1.13.7.jar
sha256sum \
    /tmp/infernalpages-1.13.7.jar \
    build/libs/infernalpages-1.13.7.jar
# expect two identical lines
```

---

## Failure modes you might hit

| Symptom | Cause | Fix |
|---|---|---|
| `error: JAVA_HOME is set to an invalid directory` | Gradle wrapper can't find JDK | `export JAVA_HOME=/path/to/jdk-21`, ensure `JAVA_HOME/bin/java -version` works |
| `Could not initialize class sun.util.calendar.ZoneInfoFile` (malformed `/usr/share/zoneinfo/Etc/UTC`, only seen in some sandboxes) | System's `Etc/UTC` zoneinfo file is corrupt | `sudo cp /usr/share/zoneinfo/Asia/Amman /usr/share/zoneinfo/Etc/UTC` (any valid TZif2 file works) |
| `Gradle build daemon disappeared unexpectedly` mid-build | OOM kill of the Loom daemon (sandbox has < 4 GB usable RAM) | Free up RAM, or build on a host with at least 6 GB free |
| `Cannot remap modifiers because it does not exist in any of the targets []` | Wrong Loom or yarn version mismatch | Always use the project's `./gradlew` wrapper, not a bare `gradle` |
| Cherry-pick conflict | `master` has moved past 32a7aa3 since this handoff was written | `git fetch origin && git rebase origin/master`, then re-cherry-pick |

---

## What you should *not* do

- Do not push to a different branch than `master`. The other AI agents
  studying this repo track files at `master/<path>`, not at feature branches.
- Do not rewrite the version history (no force-pushes). The changelog in
  `DEVELOPMENT_HANDOFF.md` §9 lists every release in chronological order
  — rewriting history makes that lie.
- Do not bump to 1.13.8 unless explicitly told. The VERSION bump scale is
  PATCH for tweaks like this one; 1.13.7 is the right number for this change.
- Do not change `INTERNAL` data component identifiers (e.g.
  `infernalpages:saved_enchantments`) without considering migration.
  Renaming it after the jar ships will break existing BLUNT-ed weapons.

---

## Verification checklist (run before declaring success)

- [ ] `git ls-remote origin master` returns an `a8143a0`-descendant SHA
- [ ] The `latest/` direct link from `latest/README.md` returns HTTP 200
- [ ] `sha256sum` of the jar in `latest/` matches the local `build/libs/` copy
- [ ] `latest/README.md` says "Current version: 1.13.7"
- [ ] `fabric.mod.json` inside `latest/infernalpages-1.13.7.jar` has
      `"name": "Infernal Pages 1.13.7"` and `"version": "1.13.7"`
- [ ] `releases/infernalpages-1.13.7.jar` is committed and tracked
- [ ] Github commits page shows a clean new commit on master with the
      new jars

If all seven are true, the handoff is complete.
