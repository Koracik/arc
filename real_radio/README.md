# Real Radio

Realistic radio system for **Minecraft 1.21.1 / NeoForge 21.1.x** with **Plasmo Voice 2.x**.

**Version: 1.2.0**

## Features

- **Radio Transmitter** — captures nearby player voice (4-block radius) and broadcasts on AM/FM
- **Radio Receiver** — plays received voice as a 3D Plasmo Voice static source
- **Radio Relay** — retransmits on another frequency/band/key (hop-limited)
- **Handheld Radio** — portable RX/TX with PTT hotkey (`V` by default)
- **Channel key** — closed channel (matching key required; `0` = open)
- **Air recording** — REC on receiver drops a **Radio Tape**; use tape to rebroadcast
- **Coverage map** — creative/debug overlay (`H` or Coverage Mapper item)
- **AM / FM** with frequency-based range (not player power slider)
  - Config `baseRangeBlocks` (default **2500**), night AM boost, antenna height
- **Propagation**: FM line-of-sight / terrain (cached), rain & thunder attenuation
- **Tuning / interference / FM capture**, squelch, soft AGC
- **Presets M1–M3** (left-click load, right-click save)
- **Spectrum peaks** on the receiver dial for nearby stations
- **Vintage GUI**: wooden chrome, LCD, ±, S-meter, mic meter, ON AIR
- **pv-addon-discs** — music from a jukebox (or goat horn) inside the TX capture radius is relayed over radio
- Full plan: [`ROADMAP.md`](ROADMAP.md) · config: `config/real_radio-common.toml`
  - **Realism mode:** `[gameplay] realismMode = true` (or `false` for assist UI)
  - Example: [`config-example.toml`](config-example.toml)

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |
| Plasmo Voice | 2.1.0+ (NeoForge) |
| pv-addon-discs (optional) | 0.1.0+ NeoForge — jukebox music over radio |

## Build

```bash
./gradlew build
```

Output: `build/libs/real_radio-1.2.0.jar`

## Run (dev)

```bash
./gradlew runClient
./gradlew runServer
```

Install **Plasmo Voice** for NeoForge into the run mods folder (or as a runtime dependency).

## In-game

1. Craft or give blocks/items:
   - `/give @s real_radio:radio_transmitter`
   - `/give @s real_radio:radio_receiver`
   - `/give @s real_radio:radio_relay`
   - `/give @s real_radio:handheld_radio`
   - `/give @s real_radio:radio_tape`
   - `/give @s real_radio:coverage_mapper` (creative/debug)
2. Right-click to open the radio GUI.
3. Enable power, pick AM or FM, tune frequency (and volume on the receiver).
4. Optional **Key** (0–9999): matching key on TX and RX for a closed channel.
5. Speak near a powered transmitter; receivers on the same band/frequency/key play the voice with static based on signal quality.
6. **REC** on the receiver captures air to a tape item; use the tape to play back.
7. Handheld: power on, tune, hold **PTT** (`V`) to transmit; receive when powered.
8. Coverage overlay: key **H** or use Coverage Mapper (disabled in realism for survival).

## Packages

```
com.realradio
├── common.block / blockentity / menu / registry / util / item
├── client.gui / sound / coverage
├── network
└── integration.plasmovoice   # voice + optional discs bridge
```

## Notes

- Blocks are **self-powered** (no FE/RF).
- **Antenna:** stack **copper lightning rods** (`minecraft:lightning_rod`) above the TX/RX/relay (3×3 column by default). Each rod boosts TX range / RX quality; more rods = stronger signal (config: `antennaRodBonusPerRod`, `antennaMaxRods`). Placement height still gives a soft bonus.
- Server Plasmo addon id: `real_radio` (source line: `radio_line`).
- Voice loudness is scaled on the **client** via Plasmo Voice `AlSource` (server API has no `setVolume` on static sources).
- Place a looping OGG at `assets/real_radio/sounds/radio_static.ogg` if you want custom noise (a generated placeholder is included).
- **Discs bridge** listens to Plasmo `ServerSourceAudioPacketEvent` on the `discs` source line (no hard dependency).
