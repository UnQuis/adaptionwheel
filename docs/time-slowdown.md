# Замедление времени — как это устроено

Документ написан так, чтобы механику можно было воспроизвести в другом моде, **не читая исходник**.
Источник: `handofgod/tempo/*.java`, `handofgod/client/TempoCliente.java`,
`handofgod/client/CameraLentaCliente.java`, `handofgod/client/SonsTempo.java`,
`handofgod/mixin/**`, `handofgod/rede/Rede.java`, `handofgod/Config.java`.
Обфусцированные имена CFR (`m_46468_` = `getDayTime`, `m_8615_` = `setDayTime` и т.п.) оставлены
только в цитатах кода.

## 0. Важно: под «tempo» здесь три разные механики

| Механика | Португальское имя | Суть |
|---|---|---|
| **Замедление (то, что вы просили)** | `CameraLenta` / «Supervelocidade» | **Децимация тиков**: сервер и клиент *пропускают* тики сущностей внутри сферы, а `partialTick` подменяется так, чтобы движение шло плавно, но медленнее. Урон и взрывы буферизуются и применяются пачкой при выходе. |
| Ускорение | `Acelerar` | Сервер быстро крутит **dayTime** (не тики сущностей!), `randomTickSpeed` поднимается, `doFireTick` выключается. Небо клиента интерполируется своим пакетом. |
| Перемотка | `Volta` / `Marcador` | Полная запись: позиции/повороты сущностей каждый тик, изменения блоков и NBT-снапшоты; при rebobinar блоки восстанавливаются по бюджету, сущности телепортируются назад, траектория игрока проигрывается на клиенте. |

**Никакого переопределения `DimensionType` game speed, никакого replay ввода, никакого
замораживания чанков нет.** `DimensionTypeMixin` только подменяет яркость неба на рендере.

Ниже главный акцент — на первой механике; две другие описаны короче, чтобы вы знали, что это не одно и то же.

---

## 1. ЗАМЕДЛЕНИЕ (`CameraLenta`, «суперскорость»)

Идея игрока: **сам игрок двигается нормально, всё вокруг в радиусе — медленно.** Клавиша **X**
включает/выключает. По умолчанию скорость мира `0.2` (в 5 раз медленнее), радиус 64 блока,
длительность 120 с, перезарядка 10 с.

### 1.1 Логические часы (ядро)

На каждом тике сервера (`TickEvent.ServerTickEvent.START`):

```java
// меняем множества «которому тику дали тик в прошлый раз»
IntOpenHashSet troca = TICARAM_ANTES; TICARAM_ANTES = TICARAM; TICARAM = troca; TICARAM.clear();

boolean ativo = /* любой Estado.ativo */;
double alvo = ativo ? Config.LENTA_VELOCIDADE : 1.0;      // 0.2 или 1.0
double passo = (1.0 - Config.LENTA_VELOCIDADE) / 4.0;     // 0.2/tick при 0.2
velocidade = alvo < velocidade ? Math.max(alvo, velocidade - passo)
                               : Math.min(alvo, velocidade + passo);
long antes = relogio;
tickLento = (relogio += Math.round(velocidade * 1000.0)) / 1000L != antes / 1000L;
```

`relogio` — **логические часы в миллисекундах**. «Медленный тик» наступает, когда серверный тик
пересекает границу 1000 единиц. При `velocidade = 0.2` сущность реально тикает **1 раз в 5
серверных тиков**. Скорость плавно нарастает/спадает за ~4 тика (`/4.0`).

### 1.2 Пропуск тиков и синхронизация (`CameraLenta.pular` / `estadoEnvio`)

```java
public static boolean pular(Entity e) {
    if (!algum || !lenta(e)) return false;
    if (!tickLento) return true;          // пропустить этот тик
    TICARAM.add(e.getId());
    return false;
}
public static int estadoEnvio(Entity e) {
    if (!algum || !lenta(e)) return 0;
    return TICARAM_ANTES.contains(e.getId()) ? 1 : 2;
}
```

- `lenta(e)`: не игрок, не захвачен телекинезом, в радиусе `LENTA_RAIO` от активного (или
  выходящего) игрока, и не едет верхом на игроке.
- `estadoEnvio`: `1` = сущность тикала в прошлый раз (надо форсировать отправку обновления),
  `2` = не тикала (обновление надо задержать), `0` = не в зоне.

Подключение на сервере — `ServerLevelMixin`:

```java
@Inject(method="tickNonPassenger", at=@At("HEAD"), cancellable=true)
private void handofgod$tickLento(Entity e, CallbackInfo ci) {
    if (CameraLenta.pular(e)) ci.cancel();
}
```

`ServerEntityMixin` в `sendChanges()`:
- если `estado == 1`: `f_8521_ = (f_8521_/n + 1)*n`, где `n = max(1, updateInterval)` — форсировать
  отправку на следующем «настоящем» тике;
- если `estado == 2` и нет пассажира: `ci.cancel()` — задержать обновление.

Компенсация цикла дня (иначе при `doDaylightCycle=true` день пропускается):

```java
if (diaAntes != Long.MIN_VALUE && ow.doDaylightCycle() && ow.getDayTime()-diaAntes == 1L) {
    long l = (long)Math.floor(restoDia += velocidade);
    restoDia -= l;
    ow.setDayTime(diaAntes + l);          // дробный прогресс дня
}
```

### 1.3 Клиент: тот же дециматор + подмена `partialTick`

`LentaEstado` приходят в очередь `FILA`. На `ClientTickEvent.START`:

```java
velocidade = m.velocidade(); relogio = m.relogio();
if (m.tick()) pendentes = min(4, pendentes + 1);
...
tickAgora = pendentes > 0; if (tickAgora) pendentes--;   // локальный «медленный тик»
```

Клиентские миксы повторяют серверную логику:
- `ClientLevelMixin.tickNonPassenger` HEAD → `if (CameraLentaCliente.pular(e)) ci.cancel()`;
- `ParticleEngineMixin.tickParticle` HEAD → `if (pularParticula(p)) ci.cancel()`;
- `LevelRendererLentaMixin.renderEntity` HEAD: `p = parcialEntidade(e, parcial)`; если отличается —
  отменяем оригинальный вызов, ставим `parcialForcado = p`, вызываем рендер через `@Invoker`
  `AcessoLevelRenderer` и возвращаем прежнее значение;
- `MinecraftLentaMixin` на `getFrameTime` / `getPartialTick` (`remap=false`) возвращает
  `parcialForcado`, если он не NaN;
- подмена partialTick для рендера:
  ```java
  public static float parcial(float p) {
      return clamp((float)(relogio % 1000L)/1000.0f + p * velocidade, 0, 1);
  }
  ```
- `SoundEngineMixin` масштабирует громкость (`SonsSol.fator`) и высоту тона (`FiltroSom.tom`)
  и включает низкочастотный фильтр OpenAL EFX;
- `ExplosaoClienteMixin.finalizeExplosion` HEAD отменяет ванильные частицы/звук, если
  `ExplosaoLentaCliente.suprimir(x,y,z)`.

### 1.4 Отложенный урон

Пока зона активна, `LivingAttackEvent` **отменяется**, а урон копится в `ACUMULOS` по id сущности:
урон, нокбэк `0.4 * (1 - knockbackResistance)` на атакующего, время горения
`fireEnchant * 4`, флаг крита. При выходе `aplicarVolta()` применяет всю сумму разом, нокбэк
ограничивается `LENTA_EMPURRAO_MAXIMO` (2.5), и шлётся `LentaVolta` для звуков удара.
Флаг `aplicando` не даёт рекурсировать в те же обработчики.

### 1.5 Отложенные взрывы

`ExplosionEvent.Start` внутри зоны отменяется. Мод заранее считает точный список разрушаемых
блоков (лучи по оболочке 16³), шлёт `LentaExplosao`; `LENTA_REFLEXO=true` может автоматически
включить замедление, если взрыв в пределах `LENTA_DISTANCIA_REFLEXO` (8 блоков). При выходе
все отложенные взрывы детонируют вместе, ванильный explosion-пакет вручную рассылается игрокам
в радиусе 64 блока. `ServerLevelMixin` подавляет ванильный пакет через `Redirect` на
`ServerGamePacketListenerImpl.send`, пока `suprimirPacoteExplosao=true`.

### 1.6 Игроки и скорость

`LENTA_JOGADORES=false` по умолчанию (другие игроки не замедляются). Если true — на игрока внутри
чужой зоны вешается `AttributeModifier` типа `MULTIPLY_TOTAL` с величиной `velocidade - 1.0`
на `Attributes.MAX_PLAYER_SPEED` (fixed id), снимается при выходе. Сам владелец зоны исключён.

---

## 2. УСКОРЕНИЕ (`Acelerar`) — короче

Клавиша **H** (удержание). Ускоряется только `dayTime`, не `gameTime` и не тики сущностей.

```java
double vMax = Math.max(0, 24000.0 / (TEMPO_SEGUNDOS_POR_DIA * 20.0) - 1.0);  // 799 при 1.5 с
double passo = 0.05 / (segura ? TEMPO_ACELERACAO : TEMPO_DESACELERACAO);     // ~0.0333/tick
double quintica(double u) { u=clamp(u,0,1); return u*u*u*(u*(u*6-15)+10); }  // smootherstep
fracao = segura ? inicio + (1-inicio)*quintica(u)
               : (u>=1 ? 0 : inicio*(1-quintica(u)));
v = vMax * fracao;
resto += v; long passoDia = (long)Math.floor(resto); resto -= passoDia;
setDayTime(getDayTime() + passoDia);
randomTickSpeed = min(TEMPO_TETO_RANDOM_TICK, max(base, round(base*(1+v))));
```

`TempoCeu` шлёт `dia`, `porTick`, `variacao`, `fracao`, `pico`, `jogador`, причём `porTick`/`variacao`
считаются с предпросмотром на 1–2 тика вперёд. Клиент `TempoCliente.aoQuadro` интерполирует:

```java
el = clamp((now - pktNs)/5.0E7, 0, 3.0);                  // до 1.5 с предсказания
alvo = pktDia + pktPorTick*el + pktVariacao*(el*el - el)*0.5;
novo = seguir(mostrado, alvo, porTick*20, dt, max(6000, pktPorTick*10));
mostrado = max(mostrado + porTick*20*dt*0.5, novo);       // монотонность + коррекция
```

Небо подменяется через `HorarioRender.substitutoTempo` + `DimensionTypeMixin.timeOfDay` и
периодические `ClientboundSetTimePacket`. Облака сдвигаются `nuvensTicks += (ritmo-1)*dt*20`.
FOV: `FOV *= 1 + TEMPO_FOV_AUMENTO(0.06) * smootherstep(fracao)^2`.
Исходные `randomTickSpeed`/`doFireTick` хранятся в SavedData `hand_of_god_tempo` и восстанавливаются
при завершении/перезапуске/выходе. Только Overworld, только если нет другого активного time-режима.

---

## 3. ПЕРЕМОТКА (`Volta`) — короче

Клавиша **G**: создать маркер, ещё раз — перемотать к нему.

**Запись** (`Marcador.gravarTick`, каждый тик):
```java
if (retratos.isEmpty() || agora - retratos.peekLast().tick >= round(VOLTA_QUADRO_CHAVE*20))
    retratos.addLast(retratar(p, agora));                 // полный NBT раз в 5 с
for (Entity e : entidades())                              // сфера VOLTA_RAIO (48)
    ents.put(e.getUUID(), new float[]{x,y,z,yRot,xRot,onGround});
quadros.addLast(new Quadro(agora, getDayTime(), p.x,p.y,p.z,p.yRot,p.xRot, ents));
deslizar(agora);                                          // окно VOLTA_SEGUNDOS (60 с)
```
Изменения блоков перехватываются `LevelChunkMixin` HEAD → `Gravador.antesDeMudar`, с фильтрами:
только главный поток сервера, `pausas>0` во время replay не записывать, лимит жидкостей
2000/тик, общий лимит 2 000 000 изменений.

**Replay** (`Volta.Rebobinar`):
```java
alvoCursor = max(0, cursor - VOLTA_VELOCIDADE);            // 3.0 кадра/тик
tickAlvo = inicio + floor(alvoCursor);
orcamento = Perfil.orcamento(VOLTA_BLOCOS_POR_TICK);        // 8000
// идём по fila назад; группы >2000 сортируются по убыванию Y
por(level, pos, oldState, beTag);                          // восстановить блок
moverEntidades(q);  setDayTime(q.dia());  p.teleportTo(...);
send(new VoltaCursor(cursor, passo));
```
Если бюджет блоков кончился — `alvoCursor` ограничивается текущей группой, курсор **становится**
(траектория игрока тоже замирает — это специально). В конце `finalizar()` восстанавливает
block-entity NBT, entity NBT из первого портрета, инвентарь/здоровье/эффекты/опыт игрока,
время мира, состояние светил и переписанную реальность. AI мобов выключается через
персистентные теги `hand_of_god_ia_0/_1` и снимается при `EntityJoinLevelEvent`.

---

## 4. Пакеты (канал `hand_of_god:principal`, версия `18`)

| id | Пакет | Направление | Поля | Обработчик |
|---|---|---|---|---|
| 15 | `TempoTecla` | C→S | `byte acao` (0 удержать,1 отпустить,2 volta) | `Tempo.tecla` |
| 16 | `TempoCeu` | S→C | `double dia; float porTick,variacao,fracao,pico; int jogador` | `TempoCliente.ceu` |
| 17 | `VoltaEstado` | S→C | `byte estado` (0 net,1 запись,2 перемотка,3 конец), `int gravados,limite` | `TempoCliente.estado` |
| 18 | `VoltaTrajeto` | S→C | `float velocidade; int n; double[n*3] pos; float[n*2] rot; long[n] dia` | `TempoCliente.trajeto` |
| 19 | `VoltaCursor` | S→C | `float indice,porTick` | `TempoCliente.cursor` |
| 24 | `LentaTecla` | C→S | пустой | `CameraLenta.tecla` |
| 25 | `LentaEstado` | S→C | `float velocidade; long relogio; boolean tick; int[] centros; float raio; byte meu; float fracao; boolean jogadores` | `CameraLentaCliente.estado` |
| 26 | `LentaGolpe` | S→C | `int alvo; Vec3 ponto,direcao (float); boolean critico; int golpes; float dano` | `CameraLentaCliente.golpe` |
| 27 | `LentaExplosao` | S→C | `int id; Vec3 pos; float raio; long relogio; boolean fim; long[] blocos` | `CameraLentaCliente.explosao` |
| 28 | `LentaVolta` | S→C | `int[] ids; float[] pos; float[] danos` | `CameraLentaCliente.volta` |

Все регистрируются через `messageBuilder(...).encoder(...).decoder(...).consumerMainThread(...)`.
Дополнительно ванильный `ClientboundSetTimePacket` шлётся в трёх местах (ускорение, перемотка,
замедление при выключении).

---

## 5. Миксы (все из `hand_of_god.mixins.json`)

| MIXIN | Цель / точка | Эффект |
|---|---|---|
| `mixin/ServerLevelMixin` | `ServerLevel.tickNonPassenger` HEAD, cancellable | `CameraLenta.pular(e)` → `ci.cancel()` — ядро децимации тиков. |
| `mixin/ServerLevelMixin` | `ServerLevel.explode` Redirect на `send(Packet)` + RETURN | Подавить ванильный explosion-пакет, сбросить флаг. |
| `mixin/ServerEntityMixin` | `ServerEntity.sendChanges` HEAD, cancellable | Форсировать/задержать обновление сущности (см. §1.2). |
| `mixin/LevelChunkMixin` | `LevelChunk.setBlockState` HEAD | `Gravador.antesDeMudar(...)` — запись для перемотки. |
| `mixin/DimensionTypeMixin` | `DimensionType.timeOfDay` HEAD, cancellable | Подмена неба через `HorarioRender.substitutoTempo` (только рендер, потоко-защищено). |
| `mixin/LevelMixin` | `Level.updateSkyBrightness` TAIL | Принудительная ночь от `GerenteSol` (не tempo). |
| `client/ClientLevelMixin` | `ClientLevel.tickNonPassenger` HEAD | Клиентская децимация тиков. |
| `client/MinecraftLentaMixin` | `Minecraft.getFrameTime` / `getPartialTick` HEAD | Вернуть `parcialForcado`, если не NaN. |
| `client/LevelRendererLentaMixin` | `LevelRenderer.renderEntity` HEAD + re-entry guard | Подменить partialTick для одной сущности. |
| `client/ParticleEngineMixin` | `tickParticle` HEAD; `render` Redirect на `Particle.render` | Пропустить тик частицы / подменить её partialTick. |
| `client/SoundEngineMixin` | `play`/`calculateVolume`/`calculatePitch`/`tickNonPaused` | Громкость, тон и фильтр звука при замедлении. |
| `client/ExplosaoClienteMixin` | `Explosion.finalizeExplosion` HEAD, cancellable | Подавить ванильные частицы/звук отложенного взрыва. |
| `client/LevelRendererMixin` | `renderClouds`, `@ModifyVariable camX` | Сдвиг облаков при ускорении. |
| `client/LightTextureMixin` | `updateLightTexture` HEAD/TAIL | Форсировать обновление света при ускорении/перемотке. |
| `mixin/oculus/CelestialUniformsMixin` | Iris `CelestialUniforms` | Подать `T1..T4` в шейдер `assets/.../tempo.glsl`. |

**Ни один микс не меняет `Level.dayTime`, `Entity.tick` или `MinecraftServer.tickRate` напрямую.**
Ускорение — вызов `setDayTime` из Forge-события; замедление — отмена `tickNonPassenger`.

---

## 6. Клавиши и конфиг

Клавиши (все `IN_GAME`, категория `key.categories.hand_of_god`): **H** (ускорение, удержание),
**G** (маркер/перемотка), **X** (замедление). Все проверяются через `Entrada.pode(Poder.TEMPORAL /
SUPERVELOCIDADE)` и серверный `Desbloqueios.pode(...)`, плюс `motivoRecusa` (выключено в конфиге,
не живой/спектатор, неправильное измерение, светило вне неба, изменённая реальность, другой
time-режим активен, перезарядка).

```
tempo.acelerar=true       tempo.segundosPorDia=1.5   tempo.aceleracao=1.5   tempo.desaceleracao=1.5
tempo.tetoRandomTick=900  tempo.fogo=false
tempo.voltar=true         tempo.raio=48              tempo.segundos=60      tempo.velocidade=3
tempo.aoMorrer=true       tempo.limiteBlocos=2_000_000  tempo.limiteFluidosPorTick=2000
tempo.blocosPorTick=8000  tempo.quadroChave=5
supervelocidade.ligada=true  supervelocidade.duracao=120  supervelocidade.recarga=10
supervelocidade.velocidade=0.2  supervelocidade.raio=64  supervelocidade.jogadores=false
supervelocidade.reflexo=true   supervelocidade.distanciaReflexo=8  supervelocidade.empurraoMaximo=2.5
client: tempo.rastro/relogio/campoDeVisao=true, campoDeVisaoAumento=0.06, sepia=1, ondas, hud
client: supervelocidade.tomSons=0.6, filtro=0.18, cor=1, fantasmas, pulso
```

Команды: `/tempo acelerar|marcar|conferir|cratera|salvar|plantas|rebobinar`, `/lenta ligar|desligar`,
плюс алиасы в `ComandoConfig` (`acelerar_tempo`, `voltar_tempo`, `velocidade_lenta`, ...).

---

## 7. Чек-лист воспроизведения замедления

1. Логические часы в мс: `relogio += round(velocidade*1000)`; медленный тик при смене тысячи.
2. Сервер: отменить `tickNonPassenger` для сущностей в сфере, кроме «настоящих» тиков.
3. Задержать/форсировать `ServerEntity.sendChanges` так же, как тики.
4. Клиент: так же считать «медленные тики» из `LentaEstado` (`pendentes` до 4).
5. Подменить `partialTick`: `clamp((relogio%1000)/1000 + partial*velocidade, 0, 1)`.
6. Компенсировать день дробным `setDayTime`.
7. Буферизовать урон и взрывы, применить пачкой при выходе.
8. Звук: понизить тон и добавить low-pass; отложенные взрывы рисовать своим файрболом.

## 8. Подводные камни

1. **Только один time-режим одновременно.** Перемотка принудительно завершает замедление и
   сбрасывает отложенные удары/взрывы; ускорение отказывается при активной перемотке/замедлении.
2. **`dayTime` vs `gameTime`.** Ускорение крутит только первый; запись/перемотка индексируются
   вторым. Не верьте клиентскому `level.getDayTime()` — небо подменено.
3. SavedData `hand_of_god_tempo`: при краше посередине ускорения флаг `ativo` остаётся и оптимистично
   восстанавливается при загрузке.
4. Бюджет перемотки может остановить курсор — это специально, не баг.
5. Порядок восстановления блоков: сверху вниз (Y desc) для групп >2000.
6. `Gravador.pausar()/retomar()` вокруг replay; `poderInicio()/poderFim()` вокруг эффектов, чтобы
   не записывать разлитую жидкость от своей же способности. Только главный поток сервера.
7. Теги `hand_of_god_ia_0/_1` — персистентные; забыть снять → мобы навсегда с выключенным AI.
8. Смерть во время записи может быть отменена (`aoMorrer`), здоровье = 1; во время перемотки
   урон/смерть/подбор предметов отменяются.
9. Очередь `LentaEstado` надо темпировать `pendentes` (макс 4), иначе partialTick рассинхронизируется.
10. JVM-флаг `hand_of_god.lentaSemSuave` (`SEM_SUAVIZAR`) выключает интерполяцию для диагностики.
11. Шейдерный эффект требует OptiFine/Iris и `tempo.glsl`; `solNaMaoTempoRastro` вызывается вне `if`
    (внутри используется `dFdx/dFdy`).
12. `RAMPA = 4` объявлена, но в тике делитель написан литералом `4.0` — смена константы ничего не делает.

---

*Не проверял: полные реализации звука/частиц (`ExplosaoLentaCliente` после строки 160, вызовы
`tempo.glsl` сверх подключения uniform'ов), рантайм-трафик пакетов и работу мода в игре.
Имена ванильных методов восстановлены по использованию в CFR-декомпиляции, а не из оригинальных исходников.*
