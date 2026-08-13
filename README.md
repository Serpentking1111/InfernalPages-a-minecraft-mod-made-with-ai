# Infernal Pages

A Fabric mod for **Minecraft 1.21.11** about **permanent death**, soul contracts, a summonable
guardian, and tainted gear. Built with **Yarn 1.21.11+build.6**, **Fabric Loader 0.19.3**,
**Fabric API 0.141.6+1.21.11**, and **GeckoLib 5.4.5** for the animated guardian model.

> Current version: **1.13.4** — see [`DEVELOPMENT_HANDOFF.md`](DEVELOPMENT_HANDOFF.md) for the full
> architecture, file map, known issues, and how to continue development.

## Download

**➡ [Get the latest build](latest/)** — the [`latest/`](latest) folder always holds the current
release jar, so the link never goes out of date. Older builds are kept in
[`releases/`](releases).

Requires **Fabric API 0.141.6+** and **GeckoLib 5.4.5+** installed separately; neither is bundled.

---

## Features

### The Scripture (permanent banish)
- A single-use item. When held (main or off hand) while you **kill another player**, it triggers a
  large escalating explosion ("calamity") and **permanently banishes** (bans) the victim — they are
  kicked and refused entry on reconnect. Killing non-players triggers the explosion but no ban.
- Obtained via the candle **Ritual**.

### The Ritual
- Place **four candles in a cross** (N/S/E/W), then **throw/drop an item** into the centre.
- The thrower **permanently loses 1 max health** (managed by a persistent health-penalty system).
- Depending on the item, grants a reward (see config / `ModConfig`):
  - `writable_book` → The Scripture
  - `enchanted_golden_apple` → Revival Charm
  - `scripture` → Contract
- If the thrower is at half a heart (can't pay the cost), it **backfires** — a non-block-destroying
  explosion + the thrower is banished ("got to greedy").

### Revival Charm
- Hold it and **type a banished player's name in chat** to revive (unban) them. Single-use.
- Can be **smashed** on a block tougher than stone to forge a **Purity Seal**.

### The Contract (two-player pact)
- Two different players sign an unsigned Contract (right-click). First signer = **A**, second = **B**.
- On completion: **A** receives an **Unholy Charm** (targeted at B), and **B** receives the
  **Sealer of Fates** sword (bound to B, unable to harm A).

### Unholy Charm
- Bound to A as holder, B as the doomed target. A types **B's name in chat** to permanently kill B:
  lightning + an ender-crystal-sized explosion + banish. Consumed on use.
- Only the pact's maker (A) can use it.

### Sealer of Fates (Contract Sword)
- Powerful, reusable, owner-bound. Killing a player **banishes** them (lightning). It **cannot harm
  the other signer (A)**.
- **Broken swords**: if the contract is broken, a broken sword **deals no damage**, stops banishing,
  and grants no protection. Smashing a broken sword yields **7 Tainted Shards**.

### Purity Seal
- Right-click to **reset your max health** to normal (removes the ritual health penalty). Single-use.
- Right-click a player standing in a ritual while holding it to **break the pact** between you and
  them (both lose 1 max health; the charm + sword are invalidated).

### Contract invalidation / breaking
- Breaking a contract records the pact id in a per-world file (`infernalpages/broken_contracts.json`)
  and marks the charm/sword `broken`. Even items stashed in chests stop working.

### Mould of Souls (animated guardian)
- An item that summons a soul guardian (animated via GeckoLib) that attacks intruders but never its
  owner, ignores **passive mobs** and **creative players**, and ignores allied moulds (same owner).
- **Modes** (right-click empty hand cycles): **passive** (stands still), **active** (only engages
  enemies within 10 blocks, teleports home after a kill), **hunt** (free-roaming).
- **Abilities** (feed an item by right-clicking): wind charge, guardian beam, fireball, strength,
  ender-pearl teleport, and dragon (flight + dragon fireballs).
- **Controls**: shift-punch removes the ability / picks up the mould; right-click feeds an ability
  or cycles modes. See [`DEVELOPMENT_HANDOFF.md`](DEVELOPMENT_HANDOFF.md) for full behaviour.
- Moulds **swim fast** through water and play a **punch animation** when attacking.

### The Calling Horn
- Crafted **shapeless** with a goat horn + a Mould of Souls.
- Right-click **summons all of your soul moulds** to you (256-block range), setting their home to
  your position. Reusable (10s cooldown).

### The Remains of a Tainted Past
- A hybrid **sword-and-hoe** weapon forged from a broken Sealer of Fates. Rendered as a **2D flat
  item** (sword blade or sickle sprite depending on mode).
  - **Sword mode**: 13 attack damage, 1.8 attack speed, right-click **riptide-style launch** (works
    in air & water, 1s cooldown).
  - **Hoe/sickle mode**: tills like a hoe; if **Farmers' Delight** is installed it can also reap
    mature crops like an FD knife.
  - Shift-right-click to switch modes (model switches automatically).
  - **Cannot permanently kill players**. Enchancement-compatible (uses string as the material).
- To obtain: smash a broken Sealer of Fates into **7 Tainted Shards**, then craft the weapon.
- **2× in-hand model** — the sword/hoe now renders twice as large when held (a `display` transform
  that doubles the hand scale without touching the texture resolution).

### The Tainted Mould (mining automaton)
- A mining automaton forged **shapelessly** from a **Mould of Souls + a Tainted Shard + 4 Netherite
  Ingots**. Right-click a block to deploy it.
- **Feed it an ore resource** to send it mining; it finds and breaks the nearest matching ore and
  collects only that ore's drops (blocks it breaks while tunnelling are not picked up).
  - Coal → Coal, Copper Ingot → Raw Copper, Iron Ingot → Raw Iron, Gold Ingot → Raw Gold,
    Redstone → Redstone, Lapis Lazuli → Lapis Lazuli, Diamond → Diamond, Emerald → Emerald,
    Netherite Ingot → Ancient Debris.
- It must have **line of sight** to an ore before it can mine it, and can only break blocks within
  **5 blocks** of itself. If it can't see its target it breaks the blocks obstructing its view, and
  if the target is out of range it tunnels towards it. If it can't safely reach a target at all it
  picks a new one.
- Once it holds a **full stack (64)** of the item it was sent for, it **teleports back to its owner**
  and deposits the haul. The fed item counts as the first of the stack, so it only mines 63 more.
- After returning it **stops and waits** for a new item.
- **Shift-punch it** (owner, sneaking) to **stop it** — it drops its deploy item plus all collected
  materials on the ground.
- Uses its own mining-bot model + texture with **5 animations**: `static`, `scan`, `run`, `mine`,
  and `teliport`. Soul moulds won't attack it.

### The Sharpening Stone
- Crafted **shaped** with 3 Tainted Shards (`AAA`) over any slabs (`BBB`, `#minecraft:slabs`).
- Hold it with a weapon in your other hand and right-click to **sharpen** the weapon with a random
  effect. Costs **3 experience levels**; use again to **reroll**.
- Effects (weighted by rarity): Sharp at Range (+1.5 reach), Sharp When Close (1.5× damage within
  1 block), Sharp With Speed (+1 dmg per block/s moving), Sharp as the Wind (+0.2 attack speed),
  Perfectly Sharp (2× damage), Blunt (deals no damage).

### Tainted-reinforced armour
- Reinforce any armour piece in the **smithing table**: put the armour in the **base** slot and a
  **Tainted Shard** in the **addition** slot (no template needed). Or hold the shard + armour and
  right-click.
- Reinforced armour gains a **recharging shield** that completely blocks an incoming hit, no matter
  how large, then goes on cooldown and recharges. It is not one-use — it keeps blocking for as long
  as the armour is worn.
- The cooldown is **15 seconds** with one reinforced piece equipped, reduced by **3 seconds for each
  additional** piece: 15s / 12s / 9s / 6s for 1 / 2 / 3 / 4 pieces.
- The armour's tooltip shows **"Reinforced with a Tainted Shard"**.

### Commands (operator/admin)
- `/setowner <player>` — sets the owner on the held contract item (Contract, Unholy Charm, or
  Sealer of Fates). Supports Minecraft player selectors (`@p`, `@a`, names).
- `/revive <player>` — unban a player (selector support).

### Custom sound
- A custom bass-hit sound (`contract_block`) plays when a pact nullifies B's attack on A.

---

## Requirements
- Minecraft 1.21.11 (Fabric)
- Fabric Loader >= 0.19.3
- Fabric API 0.141.6+1.21.11
- **GeckoLib 5.4.5** (required — animated Mould of Souls)
- Java 21
- *(Optional)* Roughly Enough Items (REI) for ritual-recipe display
- *(Optional)* Farmers' Delight for the sickle's reap behaviour
- *(Optional)* Enchancement for the Tainted Remains material compatibility

## Building
```bash
chmod +x gradlew
./gradlew remapJar
```
The compiled jar is at `build/libs/infernalpages-<version>.jar`. GeckoLib must be present at
`libs/geckolib-fabric-1.21.11-5.4.5.jar` to compile.

## Config
`config/infernalpages/config.json` — controls `explosionPower`, `calamityBasePower`, and the
`ritualIngredients` map.

## Persistence
Per-world files under `<world>/infernalpages/`:
- `banned_players.json` — the ban list.
- `broken_contracts.json` — severed pact ids.

## Recipes
Standard crafting recipes (see `data/infernalpages/recipe/`):
- Mould of Souls (shaped)
- Calling Horn (shapeless: goat horn + mould)
- Tainted Remains (shards + string + stick + purity seal)
- Tainted Mould (shapeless: mould of souls + tainted shard + 4 netherite ingots)
- Sharpening Stone (shaped: `AAA` / `BBB`, A = tainted shards, B = any slab)
- Reinforced armour (smithing: any armour + tainted shard — custom `reinforce_armor_smithing` recipe)
