# Нарезание мобов на части лазером — как это устроено

Документ написан так, чтобы механику можно было воспроизвести в другом моде, **не читая исходник**.
Источник: `handofgod/laser/Laser.java`, `handofgod/laser/Geometria.java`, `handofgod/client/LaserCliente.java`,
`handofgod/client/Metades.java`, `handofgod/client/render/CapturaCorpo.java`, `handofgod/rede/Rede.java`,
миксы `ModelPartMixin` / `EntityRenderDispatcherMixin` / `AcessoMorte` / `AcessoAldeao`.
Все имена `m_XXXX_` — обфусцированные идентификаторы из CFR-декомпиляции; в документе они оставлены только там,
где важно понимать вызов.

## 0. Сначала — чего НЕ надо копировать

В моде есть **три разные системы «кусков»**. Лазерное нарезание — только первая:

| Система | Файлы | Что это |
|---|---|---|
| **Лазерное нарезание (нужно нам)** | `Laser.java`, `LaserCliente.java`, `Metades.java`, `CapturaCorpo.java`, `Geometria.java`, `Rede.LaserCorte`, `ModelPartMixin` | Сервер решает разрез, клиент захватывает меш моба и рисует две половинки |
| Разчленение телекинезом | `client/Pedacos.java`, `client/TelecineseCliente.java` (кейсы 4/5) | ПКМ, пока держим моба → много «кусков», без плоскости разреза |
| Обломки Луны | `servidor/Fragmentos.java`, `entidade/FragmentoEntity.java`, `client/render/FragmentoRenderer.java` | Луна разлетается метеоритами, копает кратеры |

`Corpo.java` / `CorpoCliente.java` — перечисление Солнце/Луна, к мобам отношения не имеет.
`MobMixin`, `LivingEntityMixin`, `ServerEntityMixin`, `HumanoidModelMixin` — телекинез/замедление/позы, не разрез.

## 1. Главная идея (одним абзацем)

**Сервер авторитетен и «убирает» моба. Клиент «разрезает» его визуально.**
Сервер каждом тиком строит треугольник «глаз — конец луча в прошлом тике — конец луча сейчас»,
проверяет пересечение этого треугольника с hitbox мобов, вычисляет плоскость разреза и число `frac`
(где плоскость проходит относительно hitbox). Если `frac` в диапазоне — сервер шлёт клиентам пакет
`LaserCorte` с точкой, нормалью и направлением смахивания, выдаёт (по желанию) лут и XP,
освобождает POI у жителя и делает `discard()` оригиналу. Каждый клиент, получив пакет,
**перерендеривает ещё живого моба в буфер квадов**, отсекает квады плоскостью, строит «крышки»
среза, и симулирует две половинки как чистый клиентский эффект (не сущности!).

Ключевой момент для воспроизведения: **никакого NBT-клонирования и никаких серверных сущностей-половинок нет**.
Пакет несёт только геометрию; обе половинки каждый клиент строит сам.

## 2. Включение

- Клавиша **B** (keycode 66), `LaserCliente.TECLA`, категория `key.categories.hand_of_god`.
- Удержание → `Rede.LaserPedido(0)` (`LIGAR`), отпускание → `(1)` (`DESLIGAR`). Клиент ещё проверяет
  `Entrada.segurando(Poder.VISAO_TERMICA, TECLA)`: клавиша + разблокировка + выбранная способность,
  нет GUI, не спектатор, `VT_LIGADA`, нет Солнца/Луны вне неба, нет захваченного телекинезом моба.
- Сервер: `Desbloqueios.pode(p, Poder.VISAO_TERMICA)` (в выживании — разблокировка за уровни опыта).
- Зарядка `VT_CARGA` (по умолчанию 0.3 с), затем луч активен тик за тиком.
- Конфиг-команда: `handofgod config <ключ> <значение>`; алиасы `corte_min_laser`, `corte_max_laser`,
  `drops_mobs_laser`, `vida_metades`, `laser_jogadores` и т.д. Отдельной команды «разрежь» нет.

## 3. Сервер: геометрия разреза

### 3.1 Шаг луча (`Laser.passo`)

```java
Vec3 olho = p.getEyePosition();
BlockHitResult hit = level.clip(new ClipContext(olho,
    olho.add(dir.scale(VT_ALCANCE)), Block.OUTLINE, Fluid.NONE, p));
Vec3 b = hit.getType()==BLOCK ? hit.getLocation() : fimRaio;  // конец луча сейчас
Vec3 a = f.ultimo != null ? f.ultimo : b;                     // конец в прошлом тике
f.varredura = b.subtract(a);                                  // сдвиг луча за тик
cortarEntidades(f, olho, a, b, dir);
f.ultimo = b;
```

Луч за тик — это треугольник `(глаз, a, b)`. `varredura` нужна клиенту только для анимации скольжения
половинок, **не** для выбора плоскости.

### 3.2 Нормаль плоскости (`Laser.cortarEntidades`) — ядро математики

```java
Vec3 n = a.subtract(eye).cross(b.subtract(eye));        // нормаль треугольника луча
boolean parado = a.distanceToSqr(b) < 0.0064 || n.lengthSqr() < 1e-6;
if (parado) {
    n = dir.cross(new Vec3(0,1,0));                     // луч не двигался → вертикальная плоскость
    if (n.lengthSqr() < 1e-6) n = new Vec3(1,0,0);      // смотрим строго вверх/вниз
}
n = n.normalize();
```

**Плоскость проходит через глаз** (не через точку попадания!), её нормаль — векторное произведение
сторон треугольника. Поворот моба сам по себе нормаль не задаёт; моб влияет только своим AABB.

Подписи для отладки: `|n.y| > 0.8` → "horizontal" (разрез горизонтальный), `< 0.3` → "vertical",
иначе "diagonal".

### 3.3 Проверка пересечения (`Geometria.java`)

Треугольник отсекается по всем шести плоскостям AABB (алгоритм Сазерленда–Ходжмана):

```java
List<Vec3> pol = List.of(a, b, c);
for (axis = 0; axis < 3 && !pol.isEmpty(); axis++) {
    pol = clip(pol, axis, box.min(axis), true);
    pol = clip(pol, axis, box.max(axis), false);
}
return !pol.isEmpty();
```

`clip()` для каждой грани оставляет вершины с `coord >= lim` (или `<=`) и вставляет точку
пересечения ребра с плоскостью.

### 3.4 Где именно проходит разрез: `frac`

Берём 8 углов hitbox моба и проектируем их на нормаль `n`:

```java
d_i = (corner_i - eye).dot(n);          // min dmin, max dmax
frac = (dmax - dmin < 1e-6) ? 0.5 : -dmin / (dmax - dmin);
```

`frac` — нормализованная позиция плоскости внутри проекции hitbox на ось `n`, `0..1`.
Если `frac ∈ [VT_CORTE_MIN, VT_CORTE_MAX]` (по умолчанию `0.15..0.85`) — режем.
Иначе — только «царапина» (`raspar`): урон `VT_DANO_RASPAO` (4) и горение `VT_FOGO_RASPAO` (4 с),
с throttle `e.f_19802_ > 10` и множеством `raspados` на луч.

Режутся только `Mob`, не `Player`, не multipart (`EnderDragonPart`, `PartEntity`), не ID из
`DESINT_EXCLUSAO` (переиспользуется список из «Материи»). Игроки всегда только царапаются,
и только при `VT_JOGADORES=true` и не в креативе.

### 3.5 Сам разрез (`Laser.cortar`)

```java
Vec3 c = e.getBoundingBox().getCenter();
double d = c.subtract(ponto).dot(n);
Vec3 noPlano = c.subtract(n.scale(d));          // центр моба, спроецированный на плоскость
sendToAllWithin(level, e.position(),
    new LaserCorte(e.getId(), e.getUUID(), noPlano, n, f.varredura, p.getId()));
if (VT_DROPS_MOBS) {
    e.setLastHurtByPlayer(p);                    // m_6598_
    ((AcessoMorte)e).dropAllDeathLoot(level.getLootParams(p));
    ((AcessoMorte)e).dropExperience();
}
if (e instanceof Villager) ((AcessoAldeao)v).releaseAllPois();
e.discard();
```

Важен порядок: **лут/XP до `discard()`**, POI после. Рассылка — всем игрокам в радиусе 192 блока
(`dist² < 36864`), не только стрелявшему.

## 4. Сеть

Канал `hand_of_god:principal`, версия `"18"`, id пакетов присваиваются по порядку в `Rede.registrar()`:

```java
LaserPedido  id=..  PLAY_TO_SERVER   byte acao {LIGAR=0, DESLIGAR=1}
LaserEstado  id=..  PLAY_TO_CLIENT   int jogador, byte estado, float valor
                                     {CARREGANDO=0, LIGADO=1, DESLIGADO=2,
                                      RECUSADO=3, USO=4, RECARGA=5}
LaserCorte   id=..  PLAY_TO_CLIENT   int entidade, UUID uuid,
                                     Vec3 ponto, Vec3 normal, Vec3 varredura, int jogador
```

`LaserCorte` пишет 3 Vec3 как 9 double. `uuid` передаётся, но **клиентом не используется** —
поиск идёт по entity id (см. Gotchas). `LaserCorte.tratar` → `LaserCliente.corte(this)`.

## 5. Клиент: захват меша (`CapturaCorpo.java`)

```java
EntityRenderer r = dispatcher.getRenderer(e);
PoseStack ps = new PoseStack();
ps.translate(r.getRenderOffset(e, partial));
float yaw = Mth.lerp(partial, e.yRotO, e.getYRot());
int light = dispatcher.getPackedLightCoords(e, partial);
Fonte fonte = new Fonte(light);
capturando = true;
try { r.render(e, yaw, partial, ps, fonte, light); }   // ванильный рендер в наш буфер
catch (Throwable t) { /* logged */ } finally { capturando = false; }
Corpo c = fonte.corpo;   // если quads()==0 → null
```

`Fonte.novo(RenderType)` фильтрует слои: отбрасываются имена с `glint|shadow|text|lines|leash|lightning|
water_mask|debug` и всё, что не `QUADS`/`TRIANGLES`; `eyes|energy|emissive|beacon` помечается как
эмиссивное. Текстура достаётся цепочкой access-миксов `AcessoRenderType.state() →
AcessoEstadoRender.textureState() → AcessoTextura.cutoutTexture()`.

`Consumidor` собирает каждые 4 вершины (для `TRIANGLES` — 6, четвёртая дублируется) в квад и записывает:
позиции, UV, нормали, цвет (ARGB), свет, материал, `parte0/parte1` (идентификаторы `ModelPart`).
`Corpo` — параллельные `FloatArrayList pos/uv/normal` и `IntArrayList cor/luz/material/parte0/parte1`.
`ModelPartMixin` пушит/попает текущий `ModelPart` вокруг `ModelPart.render(...)`, чтобы тегировать квады.

## 6. Клиент: сам разрез (`Metades.java`)

### 6.1 Подготовка плоскости (`criar`)

```java
Corpo c = CapturaCorpo.capturar(e, parcial);
Vec3 origem = e.getEyePosition(partial);          // e.m_20318_
px = ponto.x - origem.x; ... ;  n = normalized(normal);
for (every vertex) d = (p - p0)·n;                 // dmin, dmax по всему мешу
float folga = 0.15f * (dmax - dmin);
if (dmin > -folga || dmax < folga) {              // плоскость почти не пересекает меш
    p0 += n * ((dmin + dmax) * 0.5f);             // сдвинуть в середину меша
}
halfA.n = -n;  halfB.n = +n;
```

Этот шаг критичен: AABB-плоскость из сервера может «скрести» меш по касательной; без подтягивания
на 15% либо одна половинка пустая, либо разрез не видно.

### 6.2 Отсечение квадов

Для каждого квада считаем `d[k]` 4 вершин:
- все `d > -1e-4` → целиком в половинку `+n`;
- все `d < 1e-4` → целиком в `-n`;
- иначе режем: `recortar(quad, +1, halfA)` и `recortar(quad, -1, halfB)`.

`recortar` обходит 4 ребра, сохраняет вершины с `d*sinal >= 0`, вставляет пересечение при
`t = di/(di−dj)` с интерполяцией позиции/UV/нормали/ARGB, удаляет соседние дубликаты (`<1e-6`),
триангулирует веером и записывает **граничные рёбра** `bordas`: по 12 float на ребро
`{x0,y0,z0, x1,y1,z1, normalFaceXYZ, normalEdgeXYZ}`, нормаль — к центроиду полигона.
Они дают светящийся контур среза.

### 6.3 Крышки среза (`tampas`)

1. Union-find по квадам, имеющим вершину, квантованную в `1/1024`
   (`key = round(x*1024)<<42 | round(y*1024)<<21 | round(z*1024)`) → связные оболочки.
2. Собираем точки, где ребро пересекает плоскость (`t = di/(di−dj)`, `p = pi+t(pj−pi)`).
3. Строим базис `u,v` в плоскости: `u = (nz,0,−nx)` если `|ny|<0.9`, иначе `(1,0,0)` спроецированный;
   `v = n × u`.
4. Проектируем точки, берём **выпуклую оболочку** (монотонная цепочка `casca`), если точек <3 или
   площадь <1e-4 — пропуск. Поднимаем в 3D и добавляем **одинаковый полигон в обе половинки**.

Ограничение: выпуклая оболочка закрывает вогнутые сечения (разведённые ноги) «мостом».

### 6.4 Центрирование, скорости, вращение (`montar`)

Для каждой половинки: AABB локальных вершин → центр `(cx,cy,cz)`, полуразмеры (`min 0.04`),
вычитаем центр из позиций/крышек/рёбер. Мировая позиция `h.x = origem.x + cx`.

```java
float sep = 0.045f;
boolean horizontal = |n.y| > 0.6f;
Vec3 desliza = normalize(varredura.x, 0, varredura.z);   // при ~0 — случайный
for (half h) {
    h.vx = velMob.x*0.5 + h.nx*(-sep);
    h.vy = max(0,velMob.y)*0.5 + h.ny*(-sep);
    h.vz = velMob.z*0.5 + h.nz*(-sep);
    if (horizontal) {
        if (h == верхняя && частей>1) {   // скользит по лучу
            h.vx += desliza.x*0.06; h.vz += desliza.z*0.06; h.vy += 0.03;
            h.giro = (desliza.z,0,-desliza.x) * (0.07 + 0.03*rand);
        } else h.giro = randUnitXZ * 0.012;
    } else {                              // кувырок вокруг горизонтальной оси ⊥ n
        h.giro = normalize(-h.nz, 0, h.nx) * (0.035 + 0.02*rand);
    }
}
```

### 6.5 Физика и время жизни (только клиент)

- Гравитация −0.045/tick, сопротивление ×0.985, выталкивание из блоков (до 6× +0.05 по Y).
- Коллизии через `Entity.getBoundingBox` с AABB, расширенным по кватерниону
  (`ex = |m00|hx+|m10|hy+|m20|hz`, ..., всё × `0.92*max(0.05, escala)`).
  Другая половинка добавляется как препятствие (`Shapes`), пока её `escala > 0.3`.
- Отскок: `vy = -vy*0.28` если `|vy|>0.12`; трение о землю ×0.72; отскок X/Z ×0.25;
  опрокидывание, если половинка высокая и наклонена.
- Вращение кватернионом `premul(axisAngle(ang, giro/ang))`.
- Живёт `vidaMetades` секунд = `VT_VIDA_METADES * max(0.3, desempenho.pedacos())` (по умолчанию 10 с).
  Затем 30 tick темнеет, 26 tick сжимается `escala = 1-u²` с частицами дыма/пепла → удаление.
- Температура среза: `temperatura = exp(-(idade+parcial)/20/1.7)` → оранжево-белое свечение
  крышек и кромок.

### 6.6 Рендер

`RenderType.entityCutout(textura)` (или исходный, если текстуры нет). Для каждого квада:
локальная вершина × scale → поворот интерполированным кватернионом → перенос на интерполированную
позицию относительно хоста → модельно-видео матрица; нормаль поворачивается тем же кватернионом;
дальше крышки (коричневые 0.11/0.095/0.085), светящиеся ленты рёбер и аддитивный проход свечения.

Хост — **клиентская entity** `PoeiraEntity` типа `Registro.EFEITO_LASER`, с отрицательным id
(от −4 000 000), стоит у камеры, AABB ±300. `EfeitoLaserRenderer.shouldRender()` всегда true,
`render()` вызывает `LaserCliente.desenhar()`, который идёт по списку `METADES`.
**Половинки не являются entity мира.**

### 6.7 Скрытие оригинала и уборка

```java
ESCONDIDAS.put(entityId, System.nanoTime());       // в corte()
EntityRenderDispatcherMixin.shouldRender → false, если id в ESCONDIDAS
```

Запись истекает через 3 с или сразу, если id больше не существует. Каждые 2 tick: если живая сущность
с тем же **UUID** появилась снова — группа `Metades` удаляется (защита от перемотки/респавна).
Лимит: `MAX_METADES=64`, `teto = max(4, round(64*desempenho.pedacos()))`; старые группы
растворяются досрочно. `limpar()` очищает всё при отключении.

## 7. Конфиг (все важные ключи)

```
visaoTermica.ligada=true          visaoTermica.alcance=64        visaoTermica.carga=0.3
visaoTermica.limiteUso=0          visaoTermica.recarga=5
visaoTermica.corteMin=0.15        visaoTermica.corteMax=0.85
visaoTermica.danoRaspao=4.0       visaoTermica.fogoRaspao=4
visaoTermica.dropsMobs=true       visaoTermica.jogadores=false
visaoTermica.vidaMetades=10.0     visaoTermica.marcas=true      (client)
desempenho.pedacos=1.0                                          (client)
```

## 8. Чек-лист воспроизведения

1. Сервер: луч как треугольник (глаз + прошлый/нынешний конец), нормаль `cross`.
2. Пересечение треугольника с AABB (Сазерленд–Ходжман), `frac` по 8 углам, окно `[0.15,0.85]`.
3. Пакет с точкой/нормалью/смахиванием; `discard()` оригинала; опционально лут и XP **до** discard.
4. Клиент: перерендер моба в буфер квадов с UV/нормалями/цветом/материалом.
5. Сдвиг плоскости к центру меша, отсечение квадов, интерполяция пересечений.
6. Крышки: выпуклая оболочка точек пересечения в 2D-базисе плоскости, в обе половинки.
7. Кинематика половинок: разведение по нормали + скольжение по лучу + вращение.
8. Клиентская физика, таймер, свечение, скрытие оригинала и лимиты.

## 9. Подводные камни

1. **Половинки — чистый клиентский FX.** Клиент без мода видит только исчезновение моба.
2. Захват меша должен произойти, пока сущность ещё есть на клиенте; иначе `e == null` → половинок нет.
3. `uuid` в пакете не читается, поиск по id; при переиспользовании id можно захватить не того моба.
4. Плоскость идёт через **глаз**, а не через точку попадания; без шага 6.1 разрез может быть невидим.
5. Толерантности: ±1e-4 (сторона квада), 1e-6 (дубли вершин), 1e-4 (площадь крышки), 1/1024 (сварка вершин).
6. Крышки предполагают выпуклое сечение — вогнутые формы заделываются «мостиком».
7. `TRIANGLES` превращается в квад с дублированной 4-й вершиной; все потребители ждут 4 вершины.
8. Фильтр `RenderType` выбрасывает текст/тени/glint/линии/поводок/молнию/воду; текстура достаётся
    тремя access-миксами — без них теряются UV.
9. Ошибка рендера моба глушится (`catch Throwable`), 0 квадов → тихий отказ.
10. Если луч не двигается, fallback `n = dir × up`; при взгляде строго вверх/вниз `n = (1,0,0)`.
11. Игроков никогда не режем, только царапаем.
12. Список исключений общий с «Материей» (`DESINT_EXCLUSAO`).
13. `frac` считается по углам hitbox, а не по мешу — тонкие/высокие мобы ведут себя иначе.
14. `ESCONDIDAS` живёт 3 с; при странном порядке пакетов original может мелькнуть поверх половинок.
15. При замедлении времени `CameraLentaCliente.pularPonto/parcialPonto` замораживает/интерполирует
    половинки; без этого они дёргаются.
16. В `Laser.java` есть ещё нагрев/разрушение блоков и валка деревьев (`Arvores`) — это отдельно
    от нарезания мобов.

---

*Не проверял: полные `TelecineseCliente`, `DesintegracaoCliente`, `CameraLentaCliente`, `AutoTeste`,
`Arvores`, `Impacto` — читал только пересекающиеся с разрезом части. Идентификатор ванильного метода
`e.m_6598_` выведен по форме вызова, а не по имени из исходника (декомпиляция CFR).*
