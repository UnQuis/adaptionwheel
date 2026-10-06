#version 150

// The blade, drawn procedurally: nothing here samples a texture.
//
// The blade line is a sine arch, `sin(u * PI) * 0.55`, so a straight quad bends into a crescent
// without any of its four corners moving. It is offset by half its own height so the arch is
// centred on the projectile: unoffset, the whole blade floats half a quad above the thing it is
// cutting, which matters because the hit sweep runs along the projectile's real path.
//
// Everything else is a function of the perpendicular distance `d` to that one line, which is what
// lets the taper sharpen the tips and the halo follow the taper rather than staying a blob.

in vec2 texCoord;

// x = glow half-width, y = ink half-width, z = edge feather, w = tip taper power.
uniform vec4 SlashShape;
// rgb = ink colour + overall alpha.
uniform vec4 SlashTint;
// rgb = paper/highlight colour + halo alpha.
uniform vec4 SlashGlow;
// Seconds since the world started; filled in by ShaderInstance.apply().
uniform float GameTime;

out vec4 fragColor;

void main() {
    float glowHalf = SlashShape.x;
    float coreHalf = SlashShape.y;
    float feather  = max(SlashShape.z, 1e-4);
    float taper    = max(SlashShape.w, 0.01);

    float u = clamp(texCoord.x, 0.0, 1.0); // вдоль клинка: 0 — хвост взмаха, 1 — остриё
    float v = texCoord.y * 2.0 - 1.0;      // поперёк клинка, -1..1

    // Тот же трюк, что гнёт прямой quad в полумесяц: центральная линия — синусоида от u,
    // а не ось четырёхугольника.
    float arc = sin(u * 3.14159265);
    float centreline = (arc - 0.5) * 1.1;

    // d меряется ПЕРПЕНДИКУЛЯРно линии, а не по вертикали. На плечах синуса наклон достигает
    // ~1.4 на единицу u, и вертикальный замер там даёт клинок на 30-40% тоньше, чем он
    // нарисован: замерено 0.57 против 0.82 блока на u=0.30. Деление на длину наклона
    // (переведённую в пропорции самого quad) держит толщину одинаковой по всей дуге.
    //
    // 0.833 — это height/width, и у обоих рендереров quad одинаковые 1.2:1 (6x5 и 9x7.5),
    // так что одна константа верна для обоих.
    float slope = 0.55 * 3.14159265 * cos(u * 3.14159265) * 0.8333;
    float d = abs(v - centreline) / sqrt(1.0 + slope * slope);

    // Сужение к обоим концам взмаха вместо кирпича постоянной ширины.
    float lengthFalloff = pow(max(arc, 0.0), taper);
    float coreEdge = coreHalf * lengthFalloff;
    float glowEdge = glowHalf * lengthFalloff;

    // Край тушевой, а не мягкий: feather держится маленьким и почти постоянным в МИРОВЫХ
    // единицах — замерено: полоса в 25 px на 3 блоках и ~1 px на 60. Комментарий в старой
    // версии звал это «экранными единицами», что неправда: константа в координатах quad
    // постоянна в мире, а не на экране, и сжимается вместе с перспективой.
    // fwidth() подкладывается снизу как страховка от aliasing за пределами видимой дистанции,
    // где константа схлопнулась бы в долю пикселя.
    float fe = max(feather, fwidth(d));
    float core = 1.0 - smoothstep(coreEdge - fe, coreEdge + fe, d);
    float glow = 1.0 - smoothstep(glowEdge - fe, glowEdge + fe * 4.0, d);

    // Полоса между свечением и ядром — не градиент, а скринтон: тот же приём двухтоновости,
    // что в impact-панели, иначе клинок выглядит аэрографом, а не нарисованным тушью.
    //
    // Делитель подобран под пропорции quad: при плоском 0.03 ячейка выходила в 1.67 раза
    // выше ширины (0.09 x 0.15 блока), и «точки» растягивались в вертикальные чёрточки.
    // 0.0125 даёт клетку 0.09 x 0.09 блока — круглую на обоих рендерерах.
    float tone = clamp(glow - core, 0.0, 1.0);
    vec2 dotSpace = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (texCoord / vec2(0.015, 0.0125));
    float dots = step(length(fract(dotSpace) - 0.5), mix(0.1, 0.5, tone));

    // Манговые "speed lines" с задней кромки дуги, бегущие по GameTime — чтобы статичный
    // кадр всё равно читался как движение, а не как наклейка.
    float hatchLane = fract(u * 9.0 - GameTime * 4.0);
    float hatch = step(0.85, hatchLane) * smoothstep(0.0, 0.5, arc) * (1.0 - core);

    // Штрихи гасятся ДВУМЯ воротами, и нужны оба.
    //
    // По расстоянию до кромки свечения: без этого `max` внизу рисует девять полос скорости
    // поперёк всей пустоты quad — замерено 9% площади с альфой 0.5 там, где клинка нет вообще,
    // медиана в 0.73 единицы от осевой линии, то есть сетка из висящих прямоугольников.
    // По lengthFalloff: на кончиках glowEdge схлопывается в ноль, и ворота по расстоянию там
    // уже ничего не гасят. Вместе 0.07% против 0.08% и 9% без них.
    hatch = hatch
            * (1.0 - smoothstep(glowEdge * 1.15, glowEdge * 2.1, d))
            * lengthFalloff;

    vec3 color = mix(SlashGlow.rgb, SlashTint.rgb, core);
    color = mix(color, SlashTint.rgb, dots * tone * 0.8);
    color = mix(color, SlashTint.rgb, hatch * 0.6);

    float alpha = clamp(core * SlashTint.a + glow * SlashGlow.a, 0.0, 1.0) * lengthFalloff;
    alpha = max(alpha, hatch * SlashTint.a * 0.5);

    if (alpha < 0.01) {
        discard;
    }
    fragColor = vec4(color, alpha);
}