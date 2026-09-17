# Adaption Wheel — Code Audit (NeoForge 1.21.1)

Ветка: `arena/01a0af61-adaptionwheel`. Статус: **готово** (ожидаю указаний по исправлениям).

## Метод и ограничения

- Прочитан весь исходный код мода (~60 Java-файлов, конфиг, миксины, ресурсы, линги, рецепты, curios-файлы).
- Поведение vanilla проверено по фактическим источникам 1.21.1: патч `LivingEntity.java` из официального репозитория NeoForge (ветка `1.21.1`), а также декомпилированные `LivingEntity`/`Player`/`DamageSources`/`Projectile`/`Item` Mojang-маппингов 1.21.1.
- **Сборка/запуск в песочнице невозможны** (нет JDK и доступа к maven-репозиториям), поэтому compile-level претензий нет: все API-вызовы сверены с сигнатурами 1.21.1 по тем же источникам, но финальная проверка компиляцией остаётся за `./gradlew build --no-daemon`.

## Сводка

| # | Критичность | Найденное |
|---|---|---|
| B1 | Высокая | Рефлекс существовой адаптации — мёртвый код: урон от адаптированных боссов нулевой, но обещанное ×3-отражение с откидыванием **никогда не срабатывает** |
| B2 | Средняя | «Восстановление доли HP после смерти» — мёртвая логика: доля всегда 0, восстановление не происходит |
| B3 | Средняя | Dimension Destroy: двойное начисление kill-статистики + моб, «перерезанный» рифтом, **не даёт опыта** (bypass `hurt()`) |
| B4 | Средняя | `grantAllAdaptations` дублирует концепты в `adapted` И `levels` → `adaptCount` (и бонусы от него) раздуты на ~30-45 |
| B5 | Средняя | `grantAllAdaptations` создаёт неограниченный по размеру `levels` (~1500-3000+ записей) → утяжеление предмета, сейвов и пакета синка (каждую секунду) |
| M1 | Средняя | Защита Lv8 даёт 120 тиков i-frames после каждого попадания — 6 секунд почти полного иммунитета |
| M2 | Низкая | Lifesteal лечит даже при полностью заблокированном щитом ударе |
| M3 | Низкая | `onMobDrops`: до 100-200 повторных лут-роллов и ItemEntity на убийство; NPE-риск для сущностей без лут-таблицы |
| M4 | Низкая | `CursedSlashProjectile` без лимита времени жизни (у рифта есть, у slash'а нет) |
| M5 | Низкая | All Adaption предмет consumes'ится даже без надетого колеса (пустая еда за красное сообщение) |
| R1 | Низкая | Кодеки `WheelData`/`PlayerAdaption` без валидации размера: сфабрикованный предмет с огромным `WHEEL_DATA` → packet > 2 MiB → кик (self-DoS) |
| R2 | Косметика | Двойной `super.tick()` в `SpatialRiftProjectile` на клиенте |
| R3 | Косметика | `/adaptionwheel reset` не работает из консоли; опечатка ключа `ADBERSITY` (намеренно портирована) |

Безопасность клиент→сервер в целом **в порядке** (подробнее ниже). Ниже — детали.

---

## Bugs

### B1. Рефлекс existence-адаптации недостижим (mrtv code) — `server/AdaptionEvents.java`

Документированное поведение (AGENTS.md, конфиг `existenceReflection.reflectMultiplier`): «контактный урон от адаптированного босса отражается с множителем (по умолчанию ×3, как в оригинале) и откидыванием».

Что происходит на самом деле:

1. `onAttack(LivingIncomingDamageEvent)` — [AdaptionEvents.java:260](src/main/java/ru/adaptionwheel/server/AdaptionEvents.java#L260):
   ```java
   if (adaptedExistenceTarget(data, source) != null) {
       event.setCanceled(true);   // весь удар отменён до hurt-пайплайна
   }
   ```
2. `onDamage(LivingDamageEvent.Pre)` — [AdaptionEvents.java:289-294](src/main/java/ru/adaptionwheel/server/AdaptionEvents.java#L289):
   ```java
   String reflectedBoss = adaptedExistenceTarget(data, source);
   if (reflectedBoss != null) {
       Entity reflectSource = ...;
       reflectAttack(player, reflectSource, newDamage);
       event.setNewDamage(0);
       return;
   }
   ```

Порядок в NeoForge 1.21.1 (см. `patches/net/minecraft/world/entity/LivingEntity.java.patch`, ветка `1.21.1`): `hurt()` сначала вызывает `CommonHooks.onEntityIncomingDamage` (то есть `LivingIncomingDamageEvent`) и **возвращает `false` ещё до `actuallyHurt()`**, если событие отменено. `LivingDamageEvent.Pre` шлётся только внутри `actuallyHurt()`. Значит: если `adaptedExistenceTarget(...) != null` (условие одинаковое в обоих хендлерах, между ними состояние не меняется), `Pre` **никогда не долетает** до ветки рефлекса. `reflectAttack()` недостижим ни при каких обстоятельствах, конфиг `reflectMultiplier` читается только из мёртвого кода.

Реальный геймплей: плоский иммунитет к боссу (0 урона, без красной вспышки) вместо «урон отражается в 3 раза». Лазер Хранителя Хаоса (`handleDirectHealthReduction`) для адаптированного игрока тоже просто `return` — без рефлекса.

Как чинить: перенести отражение в `onAttack` (место, где удар реально отменяется) либо не отменять `Incoming` для контакта с боссом и обрабатывать отражение в `Pre`.

### B2. `PENDING_RESPAWN_HEALTH` всегда 0 — «восстановление доли HP» не работает — `server/AdaptionEvents.java`

[AdaptionEvents.java:782](src/main/java/ru/adaptionwheel/server/AdaptionEvents.java#L782) (`onPlayerDeath`, `LivingDeathEvent`):
```java
PENDING_RESPAWN_HEALTH.put(player.getUUID(), player.getHealth() / maxHealth);
```

Порядок в vanilla 1.21.1: `hurt()` → `actuallyHurt()` → `setHealth(health - f)` (здоровье упирается в 0) → `isDeadOrDying()` → `die(source)` → **первой строкой** `CommonHooks.onLivingDeath` → `LivingDeathEvent`. То есть к моменту нашего хендлера `getHealth()` **всегда 0**, доля всегда `0f`.

Дальше [AdaptionEvents.java:1018-1026](src/main/java/ru/adaptionwheel/server/AdaptionEvents.java#L1018):
```java
float target = Mth.clamp(max * fraction, 1f, max);   // всегда 1f
if (player.getHealth() < target - 0.01f) {
    player.setHealth(target);
}
```
После респавна игрок полностью залечен, условие `health < 0.99` не выполняется — запись просто молча выбрасывается. Единственный наблюдаемый эффект: если до переэквипа колеса здоровье окажется < 0.99 (дробные значения возможны), оно поднимется до 1.

Javadoc поля и метода («Health fraction at the moment of death», «restore the death-time fraction») описывают поведение, которое физически невозможно в `LivingDeathEvent`. Чинить: брать долю раньше (в `LivingDamageEvent.Pre/Post` или вести «последнее известное HP») либо убрать механизм и комменты.

### B3. Dimension Destroy: двойная kill-статистика и отсутствие опыта — `entity/SpatialRiftProjectile.java`

[SpatialRiftProjectile.java:146-156](src/main/java/ru/adaptionwheel/entity/SpatialRiftProjectile.java#L146):
```java
target.invulnerableTime = 0;
target.setHealth(0f);
target.die(source);                                   // source = playerAttack(owner)
if (owner instanceof ServerPlayer killer && target.getHealth() <= 0f) {
    killer.killedEntity(serverLevel, target);         // (1)
}
```

1. **Двойное начисление статистики.** Vanilla 1.21.1 `LivingEntity#die()` уже вызывает `source.getEntity().killedEntity(serverlevel, target)`, а `Player#killedEntity` начисляет `Stats.ENTITY_KILLED` (`minecraft:killed_<entity>`) и возвращает `true`. Явный вызов (1) начисляет ту же статистику **второй раз** за каждое «перерезанное» существо. (Двойного XP нет — орбы падают один раз, в `dropExperience`.)
2. **Опыт не падает.** Килл обходит `hurt()`, поэтому `lastHurtByPlayerTime` цели остаётся 0, а vanilla-условие `dropExperience` — `lastHurtByPlayerTime > 0 && shouldDropExperience()` (плюс хук `LivingExperienceDropEvent` в NeoForge) — не выполняется: **рифт-килы не дают XP-орбов** (если только игрок не бивал моба в последние 5 секунд). При этом лут-дупликация (`LivingDropsEvent` в `onMobDrops`) и счётчик убийств (`onMobDeath`) срабатывают — мультипликатор опыта от Drop-адаптации на рифт-килах молча не применяется.
3. Граница `target.getHealth() <= 0f` после `die()` всегда истинна — защита не защищает от ничего.

Как чинить: убрать явный `killedEntity` (vanilla уже учитывает киллера из `source.getEntity()`), и при необходимости выставлять `lastHurtByPlayerTime`/`lastHurtByPlayer` перед `die()`, чтобы XP и его мультипликатор работали.

### B4. `grantAllAdaptations` дублирует концепты → раздутый `adaptCount` — `server/AdaptionEvents.java`

[AdaptionEvents.java:1556-1600](src/main/java/ru/adaptionwheel/server/AdaptionEvents.java#L1556): для `envConcepts`, `Debuff_*` и `ADVERSITY` одновременно выполняется
```java
data.adapted.add(concept);
data.levels.put(concept, PlayerAdaption.MAX_LEVEL);
```
В обычном пути каждый концепт живёт **либо** в `levels`, **либо** в `adapted` (`completeTask` кладёт в одно из двух), а `getAdaptCount()` суммирует оба множества:
```java
cachedAdaptCount = adapted.size() + (int) levels.values().stream().filter(l -> l > 0).count();
```
Итог: «All Adaption»-колесо даёт на ~13 (env) + ~15-20 (debuffs) + 1 (ADVERSITY) ≈ 30-45 «призрачных» адаптаций больше, чем эквивалентное заработанное колесо. На это число завязаны: cumulative-бонусы (`BONUS_HP_PCT` 0.5%/шт, `BONUS_ARMOR_FLAT` 0.5/шт, damage/crit %), урон/скорость/количество проклятых slash'ей меча (`adaptCount`), порог `DIMENSION_DESTROY_REQUIRED` (450). То есть трансцендентный игрок получает заметно больше пассивных бонусов, чем тот, кто «добил» те же адаптации штатным путём.

Как чинить: для one-time концептов не писать `levels` (и наоборот) — привести к инварианту «концепт в одном из множеств».

### B5. `grantAllAdaptations` — необъёмный `levels`-мап, каждый секунда летит в синк — `server/AdaptionEvents.java`

[AdaptionEvents.java:1611-1640](src/main/java/ru/adaptionwheel/server/AdaptionEvents.java#L1611): цикл по **всем** зарегистрированным `EntityType` (~500 в vanilla 1.21.1, тысячи в моде-паках) пишет 3 уровня (Contact_/Offense_NPC_/Drop_NPC_) + запись в history. Итог:

- `WheelData` (компонент предмета): ~1500-3000+ строк в NBT/codec — утяжеляет предметы в мире и сейвы;
- каждый `AdaptionSyncPayload` (шлётся **каждые 20 тиков** и при каждом изменении задач — [AdaptionEvents.java:1007-1009](src/main/java/ru/adaptionwheel/server/AdaptionEvents.java#L1007)) несёт весь `levels`-мап целиком → десятки-сотни КБ трафика в секунду на одного трансцендентного игрока;
- сериализация attachment в player.dat — аналогично.

Аналогичный (управляемый, но тоже неконтролируемый) путь — `/adaptionwheel grant` (опы, неограниченное число произвольных концептов).

Как чинить: либо хранить «все типы макс» отдельным флагом/маркером (проверка `level(concept)` при запросе), либо жёстко ограничить размер мапов и вырезать дубликаты в синке (отправлять только уровни > 0, которых здесь все 8 — не поможет), либо ограничить grantAll списком реально существующих в мире/доступных типов.

---

## Balance / вторичные проблемы

### M1. Lv8-защита = 6 секунд i-frames после любого попадания — `server/AdaptionEvents.java:381`

```java
if (bestLevel >= 8) {
    player.invulnerableTime = Math.max(player.invulnerableTime, 120);
}
```
В 1.21.1 `invulnerableTime > 10 && !BYPASSES_COOLDOWN` → следующее попадание с уроном ≤ `lastHurt` **полностью** игнорируется, с уроном больше — уменьшается на `lastHurt`. То есть одинокий скрач при Lv8 даёт 120 тиков (6 с) сильного иммунитета: фактически урон принимается не чаще раза в 6 секунд при любом DPS. Оригинальный i-frame окно — порядка 10 тиков. Выглядит как опечатка (120 вместо 10-20); если намеренно — задокументировать.

### M2. Lifesteal лечит заблокированный щитом урон — `server/AdaptionEvents.java:374-379`

```java
if (bestLevel >= 5) {
    double ratio = AdaptionConfig.defenseHealRatio(bestLevel) / 100.0;
    if (ratio > 0) player.heal(original * (float) ratio);   // original = DO shield
}
```
`event.getOriginalDamage()` — значение **до** щита. Если `LivingShieldBlockEvent`/щит заблокировал весь урон (`newDamage == 0`), игрок всё равно получает до 50% (Lv8) заблокированного урона в виде лечения. Бесплатный хил за «щит + колесо». Лечить нужно от фактически применённого урона (или от разницы health).

### M3. `onMobDrops`: цена лут-дупликации — `server/AdaptionEvents.java:690-750`

- `LOOT_BONUS_LEVELS` на Lv8 = 10000% → `extraRolls` = 100 (кап 200) **полных** повторных роллов лут-таблицы на убийство, каждый ролл может дать несколько `ItemEntity`. На ферме это сотни entity-объектов в секунду — серьёзный удар по TPS. Кап 200 только ограничивает худший случай.
- Fallback для пустых таблиц дублирует весь базовый дроп ещё раз (однократно) — по-хорошу, но при 100+ роллах базового дропа не будет (таблица не пустая) — fallback срабатывает только для «таблицы без дропа».
- `reloadableRegistries().getLootTable(dead.getLootTable())`: `LivingEntity#getLootTable()` возвращает `@Nullable` (у кастомных living-типов без loot_table в JSON ключ `null`) → `getRandomItems` по `null` → NPE на сервере. Vanilla свой `dropLoot` зовёт там же, но мод повторно заходит в этот путь при каждом килле с `drop level > 0`.

### M4. `CursedSlashProjectile` без тайм-аута — `entity/CursedSlashProjectile.java`

У `SpatialRiftProjectile` есть `LIFETIME_TICKS = 240`; у slash'а выхода только из: блок, `maxHits`, `!level().isLoaded(blockPosition())` (base `Projectile#tick`). В бесстеновых полностью загрузившихся пространствах (void-карты, длинные туннели) снаряд живёт десятки секунд впустую. Дешёвая защита: `if (tickCount > N) discard()`.

### M5. All Adaption без колеса — предмет съедается — `item/AllAdaptionItem.java`

`finishUsingItem` в 1.21.1 идёт через `DataComponents.CONSUMABLE` → стек уменьшится даже если `grantAllAdaptations` отработал как no-op (колесо не надето — только красное сообщение). Игрок теряет предмет за пустое действие. Варианты: не consum'ить без колеса (вернуть стек) или съесть только при успехе.

---

## Robustness

### R1. Отсутствие валидации размера данных колеса

`WheelData.CODEC`/`PlayerAdaption.CODEC` принимают мапы и списки любого размера. Сфабрикованный предмет (creative/`/give` с `data`, кастомный мир, или «данный с другого сервера» предмет) с гигантским `WHEEL_DATA`:

- при экипировке/логине данные грузятся в attachment;
- `onPlayerLogin` сразу шлёт `AdaptionSyncPayload` со всем этим содержимым;
- если пакет > 2 MiB (лимит vanilla custom payload) — **соединение игрока разбивается** (self-DoS; на мультиплеере — «почему меня кикает»).

Рекомендация: на загрузке (`loadInto`/конструктор attachment) ограничивать размеры (например, ≤ 512 записей на коллекцию) и логировать отбрасывание.

### R2. Двойной `super.tick()` — `entity/SpatialRiftProjectile.java:88,100`

В `tick()` вызов `super.tick()` в начале метода и повторный вызов в клиентской ветке. На клиенте локальные счётчики (`age`/`noActionTime`) идут ×2 — функционально безвредно, но лишний; убрать второй.

### R3. Мелочи

- `/adaptionwheel reset` (без target) через `self(ctx)` падает из консоли («not a player») — консольный оп может только `reset <player>`.
- Ключ концепта `ADBERSITY` — опечатка, намеренно перенесённая из оригинального мода (AGENTS.md так и говорит). Используется консистентно, переводы есть — чисто косметика, но при `/adaptionwheel grant` пользователь вводит «ADBERSITY».
- `AdversityOverlay`/`AdaptionHud` используют deprecated `Tesselator`/`BufferBuilder` API — на 1.21.1 работает.

---

## Безопасность (клиент → сервер)

Что проверял и что в порядке:

- **`FireSlashPayload`** (единственный c2s пейлоад): серверная `SwordOfExterminationHandler.tryFireSlashes` повторно валидирует — меч в главной руке, `SWORD_MODE` (cursed), кулдаун 10 тиков на UUID (по game-time уровня, переживает респавн). Спам пейлоадом не даёт больше slash'ей, чем физические замахивания (ту же частоту даёт и `AttackEntityEvent`). Переключать режим меча можно только через `use` на сервере. **Эксплойта нет.**
- **`AdaptionSyncPayload`** — только s2c, клиент менять своё состояние «от сервера» может лишь в рамках того, что уже синкается.
- **Команды**: все мутирующие подкоманды `/adaptionwheel` (grant/analyze/reset/registry) — `requires(hasPermission(2))`; read-only — на себя. Консольные сценарии учтены (`selfOrTarget`).
- **Состояние только на сервере**: `wearingWheel`, уровни, адаптации — читаются из attachment; клиентские зеркала (`ClientAdaption`) влияют только на визуал и клиентские миксины (см. ниже).
- **PvP по дизайну**: slash'и и рифты не целят игроков (`canHitEntity`/`canSever`), урон slash'а идёт через `target.hurt(...)` — полный урон-пайплайн, чужое колесо может резать входящий урон.
- **Хитрый сценарий**: «колесо в контейнере + death-reset» — `onPlayerDeath` чистит только компонент на **надетом** колесе и attachment; колесо, лежавшее в инвентаре/сундуке, данные сохранит — это соответствует документированному «wipe … when the wearer dies» (носитель). Не баг.
- **Клиентские миксины** (`FrictionMixin`, `SpeedFactorMixin`, `ShieldDisableMixin` и пр.) на клиенте читают синк-зеркало `ClientAdaption` — сфабрикованный клиент может «видеть» себе адаптации в локальном симуле, но на сервере все проверки идут от attachment; `AttackCooldownMixin` тоже читает `SurfaceAdaptations.conceptLevel` → на клиенте зеркало, на сервере attachment. **Серверу доверия клиенту не требуется.**

Единственная «эксплуатируемая» поверхность — самоделкинство с предметом (R1) и creative-контент (B5) — оба self-DoS/баланс, не RCE и не чужие аккаунты.

---

## Что выглядит хорошо

- Чистое разделение: все server-проверки на сервере, клиент только зеркало; common-код через `SurfaceAdaptations`/`ClientChecks` без загрузок client-классов на сервере.
- `@EventBusSubscriber`-подписчики везде с `isClientSide`-гардами; сеть через `enqueueWork` (main thread).
- Персистентность на предмете + attachment с `copyOnDeath`/`serialize`; переходы equip/unequip/swap-в-слоте обработаны (включая смену колеса в слоте — сравнение по ссылке `equippedStack`).
- Миксины: точечные, `require=1`, Draconic-миксин гатится `IMixinConfigPlugin` по наличию мода; `LivingEntityTickerAccessor` вместо хрупкого `@Shadow` на поле базового класса.
- Кэши в горячих путях (per-tick `WEARING_CACHE`, `NEARBY_BOSS_CACHE`, `PROXIMITY_HEAT_CACHE`, memo `AdaptionCategory.MATCH_CACHE`) — продумано.
- Все статические UUID-карты очищаются в `PlayerLoggedOutEvent` (нет утечек на перелогинах).
- API (`AdaptionWheelAPI`) серверно-авторитарен; `AdaptationCompleteEvent` не cancelable, шлётся на game bus.
- Ресурсы: линги полные (55 ключей, en + ru, динамические домены/концепты на месте), текстуры слота и модели предметов на месте, curios-валидатор по тегу.
- Лазер Хранителя (bypass через `setHealth`) обработан через `GuardianDirectDamage` с тем же редьюшеном + adversity — хорошая работа по обходу «невидимых» атак.

## Не проверено / вопросы

1. **Компиляция и рантайм** — в песочнице нет JDK/сети до maven; собрать и прогнать сервер `./gradlew build --no-daemon` + `./gradlew runServer` нужно на машине разработчика (особенно B1-B3: поведение лучше подтвердить в игре).
2. Точные имена методов Curios 9.5 (`isEquipped`, `findFirstCurio`/`IEquippedResult#stack`) сверены по докам 9.5.x, но не компилировались — если сборка падает, смотреть сюда в первую очередь.
3. `GuardianLaserMixin` зависит от байткода Draconic Evolution (`LaserBeamPhase.serverTick` → `setHealth(F)V`); при обновлении DE миксин может сломаться (заведомо, `require=1` + plugin-gate).
4. B5: реальный размер `levels` в паке с сотнями модов может быть в разы больше vanilla-оценки.
