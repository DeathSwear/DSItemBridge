# DSItemBridge

**DSItemBridge** — это специализированный, высокопроизводительный Paper/Bukkit плагин для Minecraft 1.21+, разработанный для RPG-сервера DeathSwear. Он выступает в качестве связующего звена между **MythicMobs / Crucible**, кастомными контейнерами добычи (**LootContainers**) и системой достижений (**PremiumAdvancements**).

---

## 🌟 Основные возможности

### 1. Достижения за крафт (`craft-advancements`)
- **Проблема**: В связке MythicMobs/Crucible псевдо-триггер `~onCraft` в файлах предметов не вызывается ядром, из-за чего достижения за сборку кастомного снаряжения не работали.
- **Решение**: Плагин напрямую перехватывает событие `CraftItemEvent` (поддерживает как обычный клик, так и Shift-клик для массового крафта) и автоматически выдаёт игроку соответствующее достижение через `/padv give <player> <advancement> --silent`.
- **Защита от абуза**: Достижение выдаётся **только** при физическом крафте предмета. Передача из рук в руки, покупка или нахождение готового предмета в сундуке ачивку за крафт не активирует.

### 2. Достижения за получение предметов (`acquire-advancements`)
- Позволяет выдавать ачивки за сам факт появления предмета в инвентаре игрока любым путём (лут из босса, сундук данжа, торговля, подбор с пола, крафт).
- Идеально подходит для легендарного оружия и дропа боссов (например, Булава Тролля `weapon_troll_mace` или базовые руны).

### 3. Автоматическое открытие рецептов (`recipe-unlocks`)
- **Проблема**: При использовании кастомных сундуков с лутом (`LootContainers`) стандартный триггер подбора с пола `~onPickup` (`EntityPickupItemEvent`) не срабатывает, так как игрок забирает предметы из инвентаря.
- **Решение**: Плагин отслеживает любые способы получения ингредиентов:
  - Извлечение из `LootContainers` (контейнеры с лутом / головы / сундуки);
  - Извлечение из обычных сундуков, бочек и воронок;
  - Подбор выпавшего дропа с пола;
  - Создание промежуточного ингредиента в верстаке.
- При первом получении ингредиента нативно открывает привязанные рецепты крафта через Bukkit API `player.discoverRecipe(...)`.
- **Умные уведомления**: плагин строго проверяет `player.hasDiscoveredRecipe(...)`. Если игрок уже знает все рецепты, звук и уведомления не спамят. При открытии нового рецепта воспроизводится звук повышения уровня и сообщение в Action Bar.

### 4. Оптимизация и производительность
- **0% MSPT Overhead**: Никаких циклических таймеров или поллинга инвентарей. Вся логика построена исключительно на реактивных Bukkit Events (`CraftItemEvent`, `InventoryClickEvent`, `EntityPickupItemEvent`, `InventoryDragEvent`).
- Поиск типа предмета происходит через PDC / NBT MythicMobs (`getMythicTypeFromItem`) за наносекунды в оперативной памяти.

---

## 🛠 Команды и права

| Команда | Описание | Право |
|---|---|---|
| `/dsbridge reload` | Мгновенно перезагрузить конфигурацию плагина без рестарта сервера | `dsitembridge.admin` (OP) |
| `/dsbridge check` | Проверить предмет в руке: показывает точный Mythic ID / Material, привязанные ачивки и статус изученности рецептов | `dsitembridge.admin` (OP) |

---

## ⚙️ Конфигурация (`config.yml`)

```yaml
settings:
  debug: false
  notifications:
    sound:
      enabled: true
      type: "ENTITY_PLAYER_LEVELUP"
      volume: 0.5
      pitch: 2.0
    actionbar:
      enabled: true
      message: "&a✦ Открыт новый рецепт крафта!"
    chat:
      enabled: false
      message: "&a[+] Открыт новый рецепт: &f%recipe%"

# Выдаётся только при крафте в верстаке (CraftItemEvent)
craft-advancements:
  BerserkBoneAxe: "craft_berserk_axe"
  BoneSword: "craft_bone_sword"
  FrostSword: "craft_frost_sword"

# Выдаётся при любом получении предмета
acquire-advancements:
  TrollMace: "weapon_troll_mace"
  BasicRune:
    - "magic_root"
    - "rune_basic"

# Рецепты, открываемые при попадании ингредиента в инвентарь
recipe-unlocks:
  WeirdBone:
    - "mythiccrucible:recipe_bonesword_shaped_shaped"
    - "mythiccrucible:recipe_berserkboneaxe_shaped_shaped"
  FireSpiritPowder:
    - "mythiccrucible:recipe_firespiritpotion_shapeless_shapeless"
```

---

## 📦 Сборка из исходников

Для сборки используется автономный скрипт `build.py` на базе JDK 21 (Paper API 1.21.1 + MythicMobs API):

```bash
python build.py
```

Готовый JAR-файл автоматически сохраняется в папку `target/DSItemBridge.jar`.
