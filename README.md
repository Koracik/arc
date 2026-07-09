# Arc

Репозиторий Minecraft-мода **Real Radio** для NeoForge 1.21.1.

Мод добавляет реалистичную радиосвязь: передатчик и приёмник с AM/FM, дальностью, помехами и интеграцией с **Plasmo Voice 2.x**.

---

## Real Radio

Папка: [`real_radio/`](real_radio/)

### Возможности

- **Radio Transmitter** — ловит голос игроков в радиусе 4 блоков и вещает на частоте
- **Radio Receiver** — 3D-источник Plasmo Voice на позиции блока
- **AM / FM**
  - FM: 87.5–108.0 MHz, шаг 0.1, дальность ≈ base…base×1.5 (авто, ниже частота — дальше)
  - AM: 530–1600 kHz, шаг 10, дальность ≈ base×2…base×3 (авто), линейный спад
- Дальность **не выбирается игроком** — зависит от частоты; базовое значение в конфиге
- Конфиг: `config/real_radio-common.toml` → `baseRangeBlocks` (по умолчанию **2500**)
- Неточная настройка: голос тише, помех больше (резкая кривая совпадения)
- Белый шум (`real_radio:radio_static`) тихий, чуть громче при плохом сигнале
- GUI в стиле старого радио (частота, громкость, AM/FM, питание)
- Блоки **без** FE/RF — работают автономно

### Требования

| Компонент        | Версия              |
|------------------|---------------------|
| Minecraft        | 1.21.1              |
| NeoForge         | 21.1.x              |
| Java             | 21                  |
| Plasmo Voice     | 2.1.0+ (NeoForge)   |

### Сборка

```bash
cd real_radio
./gradlew build
```

Готовый jar: `real_radio/build/libs/real_radio-1.0.0.jar`

### Установка

1. Положи `real_radio-*.jar` в папку `mods`
2. Установи **Plasmo Voice** для NeoForge 1.21.1 (клиент и сервер)
3. Запусти игру

### Использование

```mcfunction
/give @s real_radio:radio_transmitter
/give @s real_radio:radio_receiver
```

Или скрафти (note block + redstone + iron; для приёмника ещё copper).

1. ПКМ по блоку — открыть GUI  
2. Включить питание, выбрать AM/FM, настроить частоту  
3. Говори рядом с включённым передатчиком  
4. Приёмник на той же волне воспроизводит голос + шипение  

### Структура пакетов

```
com.realradio
├── common.block / blockentity / menu / registry / util
├── client.gui / sound
├── network
└── integration.plasmovoice
```

### Лицензия

MIT — см. [`real_radio/LICENSE`](real_radio/LICENSE).
