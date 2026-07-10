# Real Radio — план и roadmap

Документ описывает **уже сделанное**, **текущую итерацию** и **будущие идеи**.

---

## Сделано (v1.x)

### База
- [x] Передатчик / приёмник (NeoForge 1.21.1 + Plasmo Voice)
- [x] AM / FM с реальными шкалами частот
- [x] Дальность от частоты (не ползунок игрока)
- [x] Конфиг `baseRangeBlocks` (default 2500)
- [x] Кривая расстройки (голос тише при «почти» совпадении)
- [x] Тихий static + tickable volume slider
- [x] GUI без наложений текста

### UI polish
- [x] LCD-дисплей частоты
- [x] Fine-tune `±`, деления шкалы, колёсико мыши
- [x] S-метр + словесная оценка сигнала
- [x] ON AIR / В ЭФИРЕ
- [x] Клики UI при переключении

### Реализм (первая волна)
- [x] Adjacent-channel interference
- [x] FM capture
- [x] AM night boost (`amNightMultiplier`)
- [x] Squelch (`squelchThreshold`)
- [x] `staticVolumeScale` в конфиге

---

## Сделано (v1.1 realism pack)

| # | Фича | Статус |
|---|------|--------|
| 1 | **FM line-of-sight** — затухание по блокам / рельефу | done |
| 2 | **Antenna height bonus** — выше блок → дальше | done |
| 3 | **Channel presets** — M1–M3 (ЛКМ load / ПКМ save) | done |
| 4 | **Spectrum peaks** — пики станций на шкале RX | done |
| 5 | **Weather** — дождь/гроза бьют по FM | done |
| 6 | **GUI chrome** — procedural wooden panel + grill | done |
| 7 | **TX mic activity** — индикатор эфира | done |
| 8 | **Soft AGC** — `enableAgc` / `agcExponent` | done |
| 9 | **Always-on quiet hiss** while RX powered (squelch only mutes voice) | done |
| 10 | **`realismMode`** — hide spectrum / S-meter / signal % (find by ear) | done |

---

## Сделано (v1.2)

| # | Фича | Статус |
|---|------|--------|
| 1 | **LOS cache** — grid quantize, TTL/size config, half-evict | done |
| 2 | **Texture atlas** — wooden block/item/GUI art pass | done |
| 3 | **Coverage map** — creative/debug overlay (key `H` / mapper item) | done |
| 4 | **Radio relay** — RX→TX bridge with hop limit | done |
| 5 | **Channel key** — closed channel (0 = open) | done |
| 6 | ~~FE/RF power~~ | **out of scope** (blocks stay self-powered) |
| 7 | **Air recording** — REC on RX → `radio_tape` playback | done |
| 8 | **Handheld radio** — item + PTT hotkey (`V`) | done |

Конфиг (`config/real_radio-common.toml`):
- `[gameplay]` **`realismMode`**
- `[range]` `baseRangeBlocks`, `amNightMultiplier`, `antennaHeightBonus`
- `[audio]` `squelchThreshold`, `staticVolumeScale`, `enableAgc`, `agcExponent`
- `[propagation]` `enableLineOfSight`, `losSampleStep`, `losMaxPenalty`, `losCacheGrid`, `losCacheTtlMs`, `losCacheMax`, weather factors
- `[features]` `requireMatchingKey`, `maxRelayHops`, `enableCoverageOverlay`, `maxRecordingSeconds`, `handheldRangeFactor`
- Пример: [`config-example.toml`](config-example.toml)

---

## Сделано (совместимость)

- [x] **pv-addon-discs** — музыка с проигрывателя / goat horn в зоне захвата TX идёт в эфир (`DiscsRadioBridge`)

---

## Будущее (после v1.2)

1. **Интеграция с FE/RF** (опциональное питание) — отложено по запросу; блоки self-powered
2. Полный raycast occlusion с учётом материалов (железо сильнее дерева)
3. Ionosphere sim (только AM, фазы луны / погода мира)
4. Multi-language TTS station IDs
5. Compatibility: Simple Voice Chat fallback (без Plasmo)
6. Fabric port

---

## Как править этот файл

При мерже фичи: перенеси пункт из «Текущая» в «Сделано».  
Новые идеи — в «Будущее» с кратким обоснованием.
