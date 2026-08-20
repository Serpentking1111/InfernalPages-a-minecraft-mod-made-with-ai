# Infernal Pages — Development Handoff

This document is the complete technical handoff for the **Infernal Pages** Fabric mod (formerly
"Permanent Death"). It is written so a fresh developer/AI can take over development without the
original conversation context.

**Project root:** the directory this file lives in.
**Version:** 1.13.7 — Minecraft **1.21.11**, Yarn **1.21.11+build.6**, Fabric Loader **0.19.3**,
Fabric API **0.141.6+1.21.11**, Java **21**, GeckoLib **5.4.5**.

---

## 1. How to build

```bash
chmod +x gradlew
./gradlew remapJar     # produces build/libs/infernalpages-<version>.jar
```

**Requirements / gotchas discovered during development:**

- **GeckoLib** is required at compile time and runtime. It is NOT pulled from a maven; it is a
  local jar at `libs/geckolib-fabric-1.21.11-5.4.5.jar`, wired in `build.gradle` as
  `modImplementation files("libs/...jar")`. It must be copied into `libs/` to compile, and into the
  user's `mods/` folder to run.
- **JDK 21 is required.** If the sandbox/host JDK disappears, reinstall:
  `sudo apt-get install -y openjdk-21-jdk-headless`.
- The full `./gradlew build` can OOM/remap-slow; `remapJar` is the reliable task.
- Version is set in **two** places (keep in sync):
  - `gradle.properties` → `mod_version=`
  - `src/main/resources/fabric.mod.json` → `"name": "Infernal Pages X.Y.Z"`

---

## 2. Package layout (source)

All code is under `src/main/java/net/infernalpages/`.

| Path | Purpose |
|---|---|
| `InfernalPagesMod.java` | `ModInitializer`; wires registries, handlers, managers, commands. Holds `MOD_ID`, `CONFIG`, `BANS`, `BROKEN`. |
| `InfernalPagesClient.java` | Client entrypoint (renderer registration). |
| `ModConfig.java` | JSON config (`config/infernalpages/config.json`): explosion power, calamity base power, ritual-ingredient map. |
| `ban/BanManager.java` | Persistent ban list (`infernalpages/banned_players.json`). Banish = permanent death. |
| `contract/BrokenContracts.java` | Persistent set of severed pact ids (`infernalpages/broken_contracts.json`). |
| `contract/ContractBreakHandler.java` | Purity-Seal-on-B-in-ritual → break pact, invalidate items, both lose 1 max HP. |
| `death/KillHandler.java` | On-death banish logic for Scripture + Sealer of Fates (only these banish). |
| `death/ContractProtectionHandler.java` | B can't harm A (nullifies damage + plays `contract_block` sound); broken swords deal no damage. |
| `effect/ScriptureCalamity.java` | Escalating explosion sequence for Scripture kills. |
| `health/HealthPenaltyManager.java` | Permanent max-health penalty (ritual cost). |
| `item/*.java` | The items (see below). |
| `entity/*.java` | The Mould of Souls entity + its goals + mode/ability enums. |
| `recipe/MouldCraftingSerializer.java` | Custom crafting serializer (see §7). |
| `registry/ModItems.java`, `ModEntities.java`, `ModComponents.java`, `ModSounds.java` | Registries. |
| `rei/` | REI integration (ritual recipe display). |
| `revive/ReviveChatHandler.java` | Chat-based charm actions (revive + unholy kill). |
| `ritual/RitualHandler.java` | Candle ritual reward logic. |
| `command/ModCommands.java` | `/revive` and `/setowner` admin commands. |
| `util/EffectUtil.java`, `ItemUtil.java` | Helpers (lightning, etc.). |
| `client/*.java` | GeckoLib renderer/model/render-state for the Mould of Souls. |

### Items (`item/`)
- `ScriptureItem` — permanent banish.
- `RevivalCharmItem` — revive in chat; smash on hard block → Purity Seal.
- `ContractItem` — two-player signing.
- `UnholyCharmItem` — A kills B in chat.
- `ContractSwordItem` — Sealer of Fates; smashable when broken → 3 Tainted Shards.
- `PuritySealItem` — reset health; break pact.
- `MouldOfSoulsItem` — summon guardian.
- `TaintedRemainsItem` — the sword/hoe hybrid (nested `Shard` subclass).

---

## 3. The Contract system

1. **Signing** (`ContractItem.use`): first signer = **A** (`CONTRACT_SIGNER`), second signer = **B**.
   A unique `CONTRACT_ID` (UUID) ties all items of a pact together.
2. **Rewards**: A gets an **Unholy Charm** (holder=A, target=B); B gets the **Sealer of Fates**
   (owner=B, forbidden=A).
3. **Breaking**: `ContractBreakHandler` — A right-clicks B (in a ritual) with a Purity Seal.
   Both lose 1 max HP; the pact is recorded as broken and both items are invalidated.
4. **Invalidation** is two-layered:
   - Per-item `CONTRACT_BROKEN` boolean component.
   - Global `BrokenContracts` registry (by `CONTRACT_ID`), so even items stashed in chests or held
     by offline players are inert. This closed a chest-stash exploit.

### Data components (`ModComponents`)
`CONTRACT_SIGNER`, `CONTRACT_ID`, `CONTRACT_SIGNER_NAME`, `UNHOLY_OWNER`, `UNHOLY_OWNER_NAME`,
`UNHOLY_TARGET`, `UNHOLY_TARGET_NAME`, `SWORD_OWNER`, `SWORD_OWNER_NAME`, `SWORD_FORBIDDEN`,
`CONTRACT_BROKEN` (bool), `TAINTED_MODE` (string `"sword"`/`"hoe"`).

### Admin commands (`/setowner`, `/revive`)
- `/setowner <player>` (operator) sets the owner component on the held item (Contract maker,
  Unholy Charm holder, or Sealer of Fates owner).
- `/revive <player>` unbans a player.

---

## 4. The Mould of Souls entity

**Files:** `entity/MouldOfSoulsEntity.java`, `entity/GuardMode.java`, `entity/GuardAbility.java`,
and goals `MouldAbilityGoal`, `MouldTeleportGoal`, `MouldFlightGoal`, `MouldFollowOwnerGoal`,
`MouldReturnHomeGoal`, `MouldReviveGoal`, `MouldWanderGoal`; plus `client/MouldGeoModel`,
`client/MouldOfSoulsRenderer`, `client/MouldGeoRenderState`.

### Modes (`GuardMode`)
- **PASSIVE** — AI fully disabled; stands still, doesn't move/look; plays static.
- **ACTIVE** — only engages enemies within **10 blocks**. When an enemy dies it **teleports home**
  and deactivates (static). **Home = where the owner switched it to ACTIVE.**
- **HUNT** — behaves like the original "on": attacks anything except owner, follows owner, wanders.

The ACTIVE flow is a deterministic state machine in `MouldOfSoulsEntity.tick()`:
```
at home + no enemy  → dormant (static)
enemy enters range  → wake, run to & kill enemy
enemy dies          → TELEPORT straight home (onto solid ground)
back home           → dormant again (static)
```

### Abilities (`GuardAbility`) — fed by right-clicking the guard with an item
| Item fed | Ability | Texture | Effect |
|---|---|---|---|
| (none) | NONE | `soulbound_neut` | — |
| Wind Charge | WIND | `soulbound_white` | shoots a wind charge (breeze-style) |
| Heart of the Sea | GUARDIAN | `soulbound_blue` | guardian-style beam (magic damage) |
| Fire Charge | FIRE | `soulbound_yellow` | shoots a fireball (ghast-style) |
| Dirt | STRENGTH | `soulbound_brown` | +attack damage, +attack speed, +move speed |
| Ender Pearl | TELEPORT | `soulbound_purple` | teleport to nearest enemy & strike |
| Dragon Egg | DRAGON | `soulbound_purpleandblack` | flight + dragon fireballs |

- **Right-click empty hand** cycles the mode (passive→active→hunt) and shows it on the action bar.
  Feeding an ability also shows on the action bar. Empty-hand with an ability equipped **removes the
  ability and returns the item**.
- Ability + guarding state are synced via the **data tracker** so the client render matches.
- **Death**: the mould drops its summon item + the equipped ability's item. Allied moulds (same
  owner) don't fight each other; other players' moulds are enemies. A mould will pick up a dropped
  mould item and resummon an ally with the same owner.

### Animation (GeckoLib)
- **`static`** (loop=hold_on_last_frame) plays when dormant. The dormant state is **synced to the
  client** via a `DORMANT_DATA` tracked boolean so the static pose always matches the server.
- **`running`** while chasing an enemy; **`walk`** while following/wandering; **`ability`** one-shot
  while a special attack is fired; **`punch`** available.
- Head-tracking is done in `MouldOfSoulsRenderer.adjustModelBonesForRender` (rotates the `head`
  bone from the render state's `relativeHeadYaw`/`pitch`).
- Model scaled 2x in the renderer to ~1.5 blocks tall.

### GeckoLib resource layout (IMPORTANT)
GeckoLib 5 only scans `assets/<ns>/geckolib/models/` and `assets/<ns>/geckolib/animations/`. The
files are:
- `assets/infernalpages/geckolib/models/soulmould.geo.json`
- `assets/infernalpages/geckolib/animations/soulmould.animation.json`

`MouldGeoModel` points at these with `GeckoLibResources.stripPrefixAndSuffix(...)` so the lookup
key matches GeckoLib's baked cache key (`infernalpages:soulmould`). **Do not** move them back to
`geo/entity/` or `animations/entity/` — that caused `Unable to find animation file` crashes.

`MouldGeoRenderState` must override `addGeckolibData`/`hasGeckolibData`/`getDataMap` to use one
consistent map (GeckoLib mixes a private map into vanilla render states; using a custom subclass
requires this).

---

## 4b. The Calling Horn
- Crafted **shapeless** with a **goat horn** + a **Mould of Souls** item.
- Right-click summons **all of the user's soul moulds** to them (256-block range), teleporting each
  mould near the player with portal/soul particles and a horn sound, setting their new home to the
  player's position. 10s cooldown (reusable).
- Uses a new shapeless recipe serializer (`infernalpages:mould_shapeless`).

## 4b2. Tainted-reinforced armour (RE-ENABLED 1.12.1)
- The old smithing reinforce recipe was removed in 1.10.13 because its static-initializer tag lookup
  crashed (`NoClassDefFoundError` / `Missing tag trimmable_armor`).
- Re-enabled in **1.12.1** via a runtime interaction (hold a **Tainted Shard** + an armour piece and
  right-click) — still works as a convenience.
- In **1.12.2** it is also a proper **smithing table recipe**: `recipe/ReinforceArmorSmithingRecipe`
  (a custom `SmithingRecipe` + `ReinforceArmorSmithingRecipeSerializer`, registered as
  `infernalpages:reinforce_armor_smithing`). Put any armour piece in the smithing **base** slot and a
  Tainted Shard in the **addition** slot (no template); the output is that same armour with the
  `TAINTED` component (one-hit shield via `TaintedArmorHandler`). A custom recipe is required because
  the result must preserve whatever armour was used as the base — a fixed-result
  `smithing_transform` cannot do that.

## 4c. Soul Mould improvements (1.10.0)
- **Punch animation**: the mould now plays the `punch` animation when it lands a melee attack
  (triggered in `tryAttack`). Fixed the long-standing bug where the punch animation never played.
- **Water buff**: `getBaseWaterMovementSpeedMultiplier()` returns `0.5` (vanilla is `0.8`), so moulds
  swim noticeably faster through water.

## 5. The Remains of a Tainted Past (sword/hoe weapon)

**File:** `item/TaintedRemainsItem.java` (+ nested `Shard`).

- **Obtaining:** smash a **broken** Sealer of Fates on a block tougher than stone → **3 Tainted
  Shards**; craft the weapon (recipe in `data/infernalpages/recipe/tainted_remains.json`:
  shards + string + stick + purity seal).
- **Sword mode:** 13 attack damage, 1.8 attack speed. Right-click = **riptide-style launch** along
  look direction (works in air & water), **1s cooldown** (20 ticks), damages durability.
- **Hoe/sickle mode:** tills dirt/grass like a hoe. If **Farmers' Delight** is installed, right-click
  a mature crop **reaps** it (collects drops + replants). Otherwise it's a plain hoe.
- **Shift-right-click** toggles mode (sets `TAINTED_MODE` + `custom_model_data`).
- **Cannot permanently kill players** — only the Scripture and Sealer of Fates banish.
- **Model switching** uses the same proven pattern as the Contract: `minecraft:range_dispatch` on
  `custom_model_data` (sword=0 → `tainted_remains_sword`, hoe=1 → `tainted_remains_hoe`).

### Custom model gotcha (seen in dev)
In Blockbench item models, every **face key** must be `"texture": "#0"` (a reference to the `"0"`
texture slot) — NOT a texture path. If the face keys are accidentally replaced with a texture path,
the model fails to load and renders as the purple/black missing-model cube.

---

## 5b. The Tainted Mould (mining automaton) — 1.11.0

**Files:** `entity/TaintedMouldEntity.java`, `entity/TaintedOreType.java`, `item/TaintedMouldItem.java`,
`client/TaintedMouldGeoModel.java`, `client/TaintedMouldGeoRenderState.java`,
`client/TaintedMouldRenderer.java`, recipe `data/infernalpages/recipe/tainted_mould.json`.

- **Crafting (shapeless):** 1 `mould_of_souls` + 1 `tainted_shard` + 4 `netherite_ingot`
  → 1 `tainted_mould`. Deploy by right-clicking a block; owned by the placer.
- **Feed it a resource** (right-click) to send it mining. Mapping (see `TaintedOreType`):
  | Feed item | Ore it mines | Item it collects (stack of 64) |
  |---|---|---|
  | Coal | Coal Ore / Deepslate Coal Ore | Coal |
  | Iron Ingot | Iron Ore / Deepslate Iron Ore | Raw Iron |
  | Copper Ingot | Copper Ore / Deepslate Copper Ore | Raw Copper |
  | Gold Ingot | Gold Ore / Deepslate Gold Ore | Raw Gold |
  | Redstone | Redstone Ore / Deepslate Redstone Ore | Redstone |
  | Lapis Lazuli | Lapis Ore / Deepslate Lapis Ore | Lapis Lazuli |
  | Diamond | Diamond Ore / Deepslate Diamond Ore | Diamond |
  | Emerald | Emerald Ore / Deepslate Emerald Ore | Emerald |
  | Netherite Ingot | Ancient Debris | Ancient Debris |
- **The fed item counts as the first of the stack** — the mould only mines the remaining
  `STACK_SIZE - 1` items (so it needs 63 more) before returning home.
- **Behaviour (rewritten in 1.12.6):** the mould is now constrained to act like a physical digger.
  - **Line of sight is required to mine.** `hasLineOfSight` steps a ray (`SIGHT_STEP = 0.15`) from
    the mould's eye to the block centre; any opaque full cube in between hides the ore.
  - **5-block break radius.** `BREAK_RADIUS = 5.0` is a hard cap — `withinBreakRadius` gates every
    break, so distant ores must be walked/tunnelled to first.
  - **If it can't see the target it digs to see it.** `findSightObstruction` returns exactly the
    block obscuring the sightline and that block is broken.
  - **If the target is out of range it tunnels towards it.** It tries pathfinding first; when
    walled in, `findDigStepToward` carves a corridor along the straight line to the ore.
  - **Give-up list.** An ore that can be neither seen, pathed to nor dug towards (bedrock, a
    protected block, a block entity) goes into `skippedOres` so the mould moves to the next ore
    instead of jamming. Cleared on every successful break, on feeding, and when everything has
    been skipped (one retry before reporting "no ore found").
  Only ore drops are collected — blocks broken while tunnelling drop nothing. When it holds **64** of the result item it **teleports back to its owner** and deposits
  the haul into their inventory (overflow dropped).
- **Rendering (1.11.2):** uses the **custom mining-bot model** `geckolib/models/taintedmould.geo.json`
  and its own **texture** `textures/entity/tainted_mould.png` (32×32, matching the model's texture
  size). Animation file `geckolib/animations/taintedmould.animation.json` provides 5 named
  animations wired in `TaintedMouldEntity.registerControllers`:
  - `static` — idle pose (played & held when IDLE)
  - `scan` — spinny-bit scan, looped while standing still searching for ore
  - `run` — looped while navigating toward the ore
  - `mine` — one-shot mining swing, played when an ore block is broken
  - `teliport` — one-shot, played when the mould teleports back to its owner
  Mode, mine and teleport flags are synced to the client via data tracker so the client plays the
  right animation. Soul moulds will **not** attack it (see `MouldOfSoulsEntity.isEnemyOf`).
- **Persistence:** owner UUID, mode, ore type, collected count and target ore pos are saved to NBT.
- On death it drops the `tainted_mould` item so it can be redeployed.
- **Shift-punch** (owner, sneaking) stops it: the mould and all collected materials are dropped on
  the ground as item entities (see `TaintedMouldPickupHandler`).

---

## 5c. The Sharpening Stone (weapon sharpening) — 1.12.0

**Files:** `item/Sharpening.java`, `item/SharpenerItem.java`, `item/SharpeningHandler.java`,
`mixin/SharpeningDamageMixin.java`, recipe `data/infernalpages/recipe/sharpener.json`.

- **Crafting (shaped):** rows `AAA` / `BBB` / empty, where `A` = Tainted Shard and `B` = any slab
  (`#minecraft:slabs` — the custom shaped serializer now supports `#tag` key values).
- **Use:** hold the Sharpening Stone (either hand) with a weapon in the other hand and right-click.
  Costs **3 experience levels** and applies a **random sharpening**; using it again **rerolls** the
  effect. Effects and weights:
  | Sharpening | Effect | Weight |
  |---|---|---|
  | Sharp at Range | +1.5 blocks attack reach (`ENTITY_INTERACTION_RANGE`) | 12.5 |
  | Sharp When Close | 1.5× damage when target within 1 block | 12.5 |
  | Sharp With Speed | +1 damage per block/s moving | 12.5 |
  | Sharp as the Wind | +0.2 attack speed | 12.5 |
  | Perfectly Sharp | 2× attack damage | 5 |
  | Blunt | weapon deals no damage | 10 |
- **Static effects** (RANGE/WIND/PERFECT) are applied as item **attribute modifiers** and stripped
  by modifier id on reroll. **Conditional effects** (CLOSE/SPEED/BLUNT) are applied at damage time in
  `mixin/SharpeningDamageMixin` (modifies the incoming `float amount` in `LivingEntity#damage`).
- Sharpening is stored in the `SHARPENING` data component and shown on the weapon tooltip
  (client-side via `ItemTooltipCallback`).
- **Note:** the effect weights sum to 65 and are treated as *relative weights* — an effect is always
  rolled (rarer effects are less common). There is no "no effect" outcome.

---

## 6. Sounds

- Custom sound `infernalpages:contract_block` (a bass hit) plays when a pact nullifies B's attack on A.
- Defined in `assets/infernalpages/sounds.json` → `assets/infernalpages/sounds/contract_block.ogg`,
  registered in `registry/ModSounds.java`.
- Other effects use vanilla sound events (e.g. `SoundEvents.ITEM_TRIDENT_RIPTIDE_3`,
  `BLOCK_CROP_BREAK`, `ENTITY_BREEZE_SHOOT`, `ENTITY_GHAST_SHOOT`, `ENTITY_GUARDIAN_ATTACK`,
  `ENTITY_ENDER_DRAGON_GROWL`, `ENTITY_ENDERMAN_TELEPORT`, `BLOCK_GLASS_BREAK`).

---

## 7. Recipes & the custom serializer

Many other mods in a large modpack override the vanilla `Ingredient` codec, breaking standard
`{"item": "..."}` recipe JSON with `No key fabric:type`. To be immune, the mod uses a **custom recipe
serializer** (`recipe/MouldCraftingSerializer`, registered as `infernalpages:mould_crafting`).

Recipe files (in `data/infernalpages/recipe/`) use this type with a compact format:
```json
{
  "type": "infernalpages:mould_crafting",
  "pattern": [" AA", " BA", "CD "],
  "keys": { "A": "infernalpages:tainted_shard", "B": "minecraft:string",
            "C": "minecraft:stick", "D": "infernalpages:purity_seal" },
  "result": "infernalpages:tainted_remains"
}
```
The serializer reads pattern/keys/result directly and builds ingredients with `Ingredient.ofItem(...)`,
bypassing the broken codec. It reuses the vanilla shaped-recipe **packet codec** so recipes sync to
clients/REI.

---

## 8. Known issues / open items

1. **ACTIVE-mode guard** has been iterated on heavily. The current design (teleport home on kill,
   sync dormant to client, home = activation spot, horizontal-only "at home" check) is the intended
   final behaviour. If it still misbehaves, add/check the `[Mould ACTIVE]` diagnostic log lines in
   `tick()` (pos/home/target/dormant) to see the runtime state.
2. **Farmers' Delight knife** is implemented as a self-contained "reap" of mature vanilla crops
   (gated behind `FabricLoader.isModLoaded("farmersdelight")`). It does NOT integrate with FD
   cutting boards (needs the FD jar to compile against).
3. **REI recipe display** for custom crafting worked via the default plugin once items were
   registered; if it ever doesn't show, it's usually a modpack recipe-codec conflict (see §7).
4. `en_us.json.tmp` exists in `lang/` (leftover — safe to delete).
5. The old `MouldEntityModel.java` / `MouldRenderState.java` / `MouldReturnHomeGoal.java` may be
   unused remnants from earlier iterations — verify before relying on them.

---

## 9. Version history (release notes)

- **1.8.x** — Foundation: Scripture, Ritual, Revival Charm, Contract, Unholy Charm, Sealer of Fates,
  Purity Seal, Mould of Souls. Fixed GeckoLib resource path (files moved to `geckolib/`), render-state
  data-map, strength-NPE on load. Added head-tracking, ability behaviours, guard modes, follow-owner,
  revive-ally, item drops, action-bar messages, custom sound, contract invalidation, broken-sword
  no-damage, `/setowner`, `/revive`.
- **1.9.x** — Tainted Remains weapon (sword/hoe hybrid), Tainted Shards, smash mechanic, custom
  recipe serializer, models/textures integration, mode-based model switching, sword stats
  (13 dmg / 1.8 speed), no-permanent-kill, FD knife behaviour, and the final ACTIVE-mode guard fix
  (teleport-home + synced dormant).
- **1.10.0** — The Calling Horn (summons your moulds), shapeless recipe serializer, mould punch
  animation fix, and mould water-movement buff.
- **1.10.2** — Enchancement compatibility for Tainted Remains (string material) + swords tag.
- **1.10.3** — Soul moulds ignore passive mobs and creative players.
- **1.10.5** — Tainted-reinforced armour (smithing), Tainted one-hit shield with 15s cooldown.
- **1.10.15** — The Remains of a Tainted Past (both sword and hoe modes) now renders **2× bigger** when
  held in a player's hand (first- and third-person, left & right hand) via a per-model `display`
  transform that doubles the vanilla `item/handheld` scale. No texture/UI change — GUI, ground,
  fixed and head contexts are untouched.
- **1.10.16** — Raised the third-person right-hand translation to `[0, 14.5, 0.5]` (in-hand vertical lift) for both modes.
- **1.10.17** — Applied Blockbench-finished hand positions to both modes: third-person right-hand
  `[0, 9.75, -0.5]`, third-person left-hand `[0, 12, -0.75]`; first-person unchanged
  (`[1.13, 3.2, 1.13]`), all scales still 2× (1.7 / 1.36).
- **1.10.18** — Buffed the sword-mode riptide dash: `LAUNCH_POWER` increased **1.6 → 2.5**
  (~56% faster boost). Cooldown, damage and hop unchanged.
- **1.10.19** — Boosted the sword dash again: `LAUNCH_POWER` **2.5 → 3.5** (~2.2× the original speed).
- **1.11.0** — New **Tainted Mould** mining entity (see below).
- **1.11.1** — Expanded Tainted Mould ores: added gold, lapis, redstone, emerald and coal. The fed
  item now counts as the first of the 64-stack (mould mines 63 more before returning). On return it
  stops and waits for a new feed item.
- **1.11.2** — Gave the Tainted Mould its own mining-bot model + texture and implemented the 5
  animations (static/scan/run/mine/teliport) per the animation file.
- **1.11.3** — Tainted Mould now always targets the **closest** matching ore (removed the exposed-ore
  preference). Added a brief `SCAN_PAUSE_TICKS` (30) pause after each ore break so the **scan**
  animation visibly plays before it moves on.
- **1.11.4** — Shift-punching a Tainted Mould (owner, sneaking) **stops it**: it drops its own
  deploy item plus all collected materials (full count of the ore result) on the ground, then is
  removed. Handled in `entity/TaintedMouldPickupHandler.java`.
- **1.12.0** — New **Sharpening Stone** mechanic (see below).
- **1.12.1** — Fixed the sharpener crafting recipe (tags are now resolved through the recipe codec's
  `RegistryOps` entry lookup instead of the static registry, so `#minecraft:slabs` resolves) and
  **re-enabled Tainted-reinforced armour** via a right-click interaction (Tainted Shard + armour piece).
- **1.12.2** — Made Tainted-reinforced armour a **real smithing table recipe**: a custom
  `SmithingRecipe` (`ReinforceArmorSmithingRecipe`) accepts any armour piece as the **base** slot and
  a Tainted Shard as the **addition** (no template), and outputs the same armour with the `TAINTED`
  component applied. Serializer registered as `infernalpages:reinforce_armor_smithing`; the
  right-click interaction from 1.12.1 is kept as a convenience.
- **1.12.3** — Reinforced armour now shows a **tooltip** ("Reinforced with a Tainted Shard" /
  "Blocks the next hit, then recharges over 15s") via the client `ItemTooltipCallback`.
- **1.12.4** — **Fixed the reinforced-armour shield never recharging.** The cooldown compared
  `ServerWorld.getTimeOfDay()`, the day/night clock, which is frozen by the `advance_time` gamerule
  (renamed from `doDaylightCycle` in 1.21.11) and rewritten by `/time set` — so on most worlds the
  shield blocked exactly one hit and never came back. Now uses `ServerWorld.getTime()` (monotonic
  world age) plus a `now >= last` guard against backwards clocks. Also **rebalanced** the cooldown
  so the reduction applies to pieces *beyond the first*: one reinforced piece now gives the
  documented 15s instead of 12s, for a 15/12/9/6s table at 1/2/3/4 pieces (extracted into
  `cooldownTicks(int)`). Also fixed a **memory leak** — `LAST_BLOCKED` was never cleaned up on
  logout or death and grew for the server's lifetime; recharged entries are now pruned every 64
  blocks. Tooltip and README reworded, as both described the shield as one-use.
- **1.12.5** — **Tainted Mould mining fixes.** (1) It kept **breaking blocks while playing the
  "scan" animation**: the `searchCooldown` pause was checked only inside the "no valid target"
  branch of `doMining()`, so once a target was held the mould carried on tunnelling during the
  pause. The cooldown is now checked at the top of the mining step and stops navigation too, so
  the scan pause is a real pause. (2) It **ignored the ore it was sent to collect**: `findBlocker()`
  deliberately skips target-ore blocks, but ores generate in *veins*, so the blocks between the
  mould and its target are usually more of the same ore — it tunnelled around its own vein instead
  of mining it. Added `findAdjacentTargetOre()`, checked before `findBlocker()`, so reachable ore
  is mined with `breakOre()` (drops collected). (3) `isBreakable()` now rejects blocks with a
  **block entity**, so tunnelling no longer destroys chests, shulker boxes, spawners or furnaces
  and their contents. (4) The idle message listed only 3 of the 8 accepted items; it is now
  generated from `TaintedOreType` via `feedItemList()` so it cannot drift.
- **1.12.6** — **Tainted Mould line-of-sight mining.** The mould now has to actually *see* an ore
  before it can mine it, and can only break blocks within **5 blocks** of itself.
  - `hasLineOfSight(BlockPos)` ray-marches from the mould's eye to the block centre in
    `SIGHT_STEP = 0.15` increments; any opaque full cube along the way blocks the view. Ore
    selection (`findVisibleTargetOre`, which replaces `findAdjacentTargetOre`) now requires both
    line of sight and range, so the mould can no longer reach through solid rock to delete ore.
  - `withinBreakRadius` replaces the old `canReach`/`REACH = 3.2` check and gates **every** break.
  - `findSightObstruction` returns the exact block interrupting the sightline; that block is what
    gets broken, so "if it can't see the ore it breaks the blocks it needs to" is literal.
  - `findDigStepToward` tunnels along the straight line to an out-of-range target when
    pathfinding fails, so buried ores are still reachable.
  - `skippedOres` records ores it can neither see, path to nor dig towards, so it moves on
    instead of jamming; the list is cleared on every successful break and on feeding.
  - Removed the now-unused `findBlocker()`.
- **1.13.0** — **Copper support + release folder.**
  - **The Tainted Mould can now see and mine copper.** New `TaintedOreType.COPPER`: feed it a
    **copper ingot** and it targets Copper Ore / Deepslate Copper Ore and collects **raw copper**.
    Everything else — line of sight, the 5-block break radius, vein mining, the idle-message item
    list — picks copper up automatically, because they all derive from the enum.
  - **Fixed oversized-stack delivery.** `returnToOwner()` built one `ItemStack` with the entire
    haul as its count. Multi-drop ores overshoot `STACK_SIZE` (copper drops 2-5 raw copper per
    block, redstone 4-5, lapis 4-9), so the count could exceed the item's max stack size and the
    excess was silently destroyed on delivery. The haul is now split into correctly-sized stacks,
    each inserted individually and dropped on the floor only if the inventory is full. This was
    reachable before copper, but copper makes it routine.
  - **`latest/` folder** added at the repo root, always holding the current release jar plus a
    short README. `releases/` still keeps the full version history.
  - **`.gitignore` reordered.** The blanket `*.jar` rule sat *after* the `!releases/...`
    whitelists and silently overrode them, so every release jar needed `git add -f`. The blanket
    rule now comes first and the un-ignore rules follow it, matching `releases/*.jar` and
    `latest/*.jar` by pattern so new versions no longer need per-file entries.

- **1.13.1** — **Contract Sword now renders like the Tainted Sword in hand.** Copied the Tainted
  Remains sword model's setup onto `contract_sword.json`: it is now a `minecraft:item/handheld`
  model whose `display` transform makes it render **larger and moved in hand** (third-person scale
  1.7, first-person 1.36, with the same translations/rotations as `tainted_remains_sword`), exactly
  matching how the Tainted Sword looks when held. The model points `layer0` at
  `item/contract_sword`, so the (updated) contract sword texture is the one shown. No code change —
  purely a resource/model tweak, so this is a PATCH bump.
  - **Note (texture asset):** the updated `contract_sword.png` texture was not available in the
    working session, so the repo still carries the previous 3D-atlas texture. The new flat handheld
    model expects a clean single-sword sprite (as `tainted_remains_sword.png` is); commit the
    updated `contract_sword.png` to make the in-hand look correct. **Resolved in 1.13.2.**

- **1.13.2** — **Added the updated item/entity textures (the 1.13.1 model change was shipped
  without them).** The new `contract_sword.png` (a clean 32×32 single-sword sprite) replaces the old
  3D-atlas texture, so the flat handheld model from 1.13.1 now renders correctly in hand. The new
  `tainted_mould.png` was supplied at 16×16 but the mould's GeckoLib model is `texture_width/height:
  32` with UVs up to ~19, so it was upscaled 2× (nearest-neighbour) to 32×32 to keep the pixel art
  aligned — the mould **model** was left untouched (only its texture changed), per request. Pure
  resource change, so this is a PATCH bump. `latest/` now points at 1.13.2.

- **1.13.3** — **Fixed the Tainted Mould textures (1.13.2 placed them wrong).** The uploaded
  `tainted_mould.png` is a 16×16 **2D item sprite** for the deploy item, not the 32×32 GeckoLib
  entity texture. 1.13.2 wrongly overwrote the 3D entity texture with it (and upscaled it), so the
  mould rendered with the wrong skin. Now: the 3D entity texture is **restored** to its original
  32×32, and the 2D sprite is used correctly on the **spawn item** — `models/item/tainted_mould.json`
  now points `layer0` at `item/tainted_mould` (previously it reused `item/mould_of_souls`). Pure
  resource fix, so this is a PATCH bump. `latest/` now points at 1.13.3.

- **1.13.4** — **Tainted Mould target/path detection is now much faster + refreshed contract sword
  texture.** `findNearestOre()` used to sweep the whole 65×65×25 volume (~105k block reads) on every
  target re-selection (which fires each time the mould consumes its target ore while mining a vein).
  It now walks outward in shells of increasing radius and stops at the first shell that contains an
  ore, returning the Euclidean-closest block in it — so selection is unchanged (still the nearest
  ore) but the common case stops after a few hundred blocks instead of ~105k, and the mould "sees"
  where to go and starts moving much sooner. `findVisibleTargetOre()` also early-outs once it finds
  an ore face-adjacent to the target (the closest a distinct block can be) instead of finishing its
  11×11×11 scan, keeping the per-tick detection cheap while standing in a vein. Also swapped in the
  updated `contract_sword.png` (a new 32×32 sprite for the Sealer of Fates). Pure perf/AI +
  resource change, no behaviour change, so this is a PATCH bump. `latest/` now points at 1.13.4.

- **1.13.5** — **16×16 inventory mini-icons for the Sealer of Fates + The Remains of a Tainted Past
  (sword mode), in-hand model unchanged.** The two swords now show user-supplied 16×16 sprites in
  `gui` / `ground` / `fixed` / `on_shelf` display contexts — matching how a regular item icon looks
  in the inventory — while every held-in-hand context (third-person, first-person, both hands)
  keeps the existing **2×-scaled Blockbench model** so the weapons still look large when equipped.
  Implemented with `minecraft:select` on `minecraft:display_context` in
  `items/contract_sword.json` and (nested inside the existing `custom_model_data` dispatch) in
  `items/tainted_remains.json`, the same proven pattern Reap uses for its scythe. No code change,
  no model change, no class file change — the bytecode jar is rebuilt only because the assets
  inside it must be replaced. Pure resource tweak, so this is a PATCH bump. `latest/` now points at
  1.13.5.

- **1.13.6** — **BLUNT sharpening now actually zeroes the weapon's attack-damage attribute.** Until
  1.13.5, BLUNT was patched in at damage time inside `SharpeningDamageMixin`
  (`case BLUNT -> amount = 0.0f;`), which kept the weapon's `ATTACK_DAMAGE` attribute at its full
  value so the tooltip lied (`"+15 Attack Damage"` on a BLUNT-ed netherite sword) and the
  reroll-strip code in `Sharpening.withoutSharpening` had no `sharp_blunt` modifier id to remove
  when rolling on top of an existing BLUNT. From 1.13.6 BLUNT is a real
  `ATTACK_DAMAGE` attribute modifier: `EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL`
  with value `-1.0`, which (being the exact inverse of PERFECT's `+1.0` `ADD_MULTIPLIED_TOTAL`)
  drives the running attack-damage total to 0 for any weapon — the tooltip now reads `0 Attack
  Damage`, the value flows through the normal attribute pipeline (same as RANGE / WIND / PERFECT),
  and the new `sharp_blunt` modifier id is correctly stripped when rerolling away. The corresponding
  `case BLUNT -> amount = 0.0f` line has been removed from `SharpeningDamageMixin`, which now only
  handles the genuinely dynamic effects (`CLOSE` and `SPEED`). Same .gitignore / four-file version
  bump convention; no model / texture / data changes. PATCH bump per §11. `latest/` now points at
  1.13.6.

---

## 10. Where to continue

- Fix any remaining ACTIVE-mode guard edge cases (use the `[Mould ACTIVE]` logs).
- Wire Farmers' Delight cutting-board integration (needs the FD jar).
- Polish models/textures or add more items/abilities.
- Keep `gradle.properties` `mod_version` and `fabric.mod.json` `"name"` in sync on every bump.

---

## 11. Versioning

Versions are `1.MINOR.PATCH`:

- **MINOR** (`1.11.x` → `1.12.0`) — a **major update**: a new mechanic, item, entity, or system.
  Reset PATCH to 0.
- **PATCH** (`1.12.3` → `1.12.4`) — a **minor update**: bug fixes, balance tweaks, tooltip/text
  changes, refactors. No new feature.

Four files must be updated together on every bump:

1. `gradle.properties` → `mod_version`
2. `src/main/resources/fabric.mod.json` → `"name": "Infernal Pages <version>"`
3. `README.md` → the `Current version:` line near the top
4. This file → a new bullet in the changelog above describing the change
