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

## Текущая итерация (v1.1 realism pack)

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

Конфиг (`config/real_radio-common.toml`):
- `[range]` `baseRangeBlocks`, `amNightMultiplier`, `antennaHeightBonus`
- `[audio]` `squelchThreshold`, `staticVolumeScale`, `enableAgc`, `agcExponent`
- `[propagation]` `enableLineOfSight`, `losSampleStep`, `losMaxPenalty`, `fmRainFactor`, `fmThunderFactor`

---

## Ближайшее будущее (v1.2+)

1. **Кэширование LOS** на multi-chunk дистанциях (если нагрузка)
2. **Полноценная texture atlas** деревянного радио (art pass)
3. **Карта покрытия** (debug / creative overlay)
4. **Релейные ретрансляторы** (RX→TX bridge)
5. **Шифрование / «закрытый канал»** (общий ключ на блоках)
6. **Интеграция с FE/RF** (опциональное питание)
7. **Запись эфира** (server-side buffer, playback)
8. **Мобильная рация** (item + hotkey)

---

## Дальние идеи / research

- Полный raycast occlusion с учётом материалов (железо сильнее дерева)
- Ionosphere sim (только AM, фазы луны / погода мира)
- Multi-language TTS station IDs
- Compatibility: Simple Voice Chat fallback (без Plasmo)
- Fabric port

---

## Как править этот файл

При мерже фичи: перенеси пункт из «Текущая» в «Сделано».  
Новые идеи — в «Будущее» с кратким обоснованием.
