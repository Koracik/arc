# Real Radio

Realistic radio system for **Minecraft 1.21.1 / NeoForge 21.1.x** with **Plasmo Voice 2.x**.

## Features

- **Radio Transmitter** — captures nearby player voice (4-block radius) and broadcasts on AM/FM
- **Radio Receiver** — plays received voice as a 3D Plasmo Voice static source
- **AM / FM** bands with realistic frequency-based range (not player-chosen power)
  - Lower frequency → farther reach; AM covers more ground than FM
- **Tuning tolerance** — slight detuning still works, but voice is much quieter (sharp curve)
- **Distance falloff** — linear for AM, cubic for FM
- **White-noise static** via Minecraft sound engine (`real_radio:radio_static`), kept quiet

## Requirements

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |
| Plasmo Voice | 2.1.0+ (NeoForge) |

## Build

```bash
./gradlew build
```

Output: `build/libs/real_radio-1.0.0.jar`

## Run (dev)

```bash
./gradlew runClient
./gradlew runServer
```

Install **Plasmo Voice** for NeoForge into the run mods folder (or as a runtime dependency).

## In-game

1. Craft or give blocks:
   - `/give @s real_radio:radio_transmitter`
   - `/give @s real_radio:radio_receiver`
2. Right-click to open the radio GUI.
3. Enable power, pick AM or FM, tune frequency (and volume on the receiver).
4. Speak near a powered transmitter; receivers on the same band/frequency play the voice with static based on signal quality.

## Packages

```
com.realradio
├── common.block / blockentity / menu / registry / util
├── client.gui / sound
├── network
└── integration.plasmovoice
```

## Notes

- Blocks are **self-powered** (no FE/RF).
- Server Plasmo addon id: `real_radio` (source line: `radio_line`).
- Voice loudness is scaled on the **client** via Plasmo Voice `AlSource` (server API has no `setVolume` on static sources).
- Place a looping OGG at `assets/real_radio/sounds/radio_static.ogg` if you want custom noise (a generated placeholder is included).
