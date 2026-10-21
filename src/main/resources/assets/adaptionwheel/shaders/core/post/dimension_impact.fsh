#version 150

uniform sampler2D Sampler0;
uniform sampler2D ImpactSampler;

uniform vec2 ImpactSize;
// x strength, y chromatic aberration, z flash, w edge gain.
uniform vec4 ImpactParams;
// x panel age 0..1. y reserved for future use. zw impact centre, in screen uv.
uniform vec4 ImpactProgress;
// x shockwave gain, y speed-line gain, z screentone crawl rate, w ink darkness.
uniform vec4 ImpactBurst;
uniform vec4 ImpactInk;
uniform vec4 ImpactPaper;
// Hot core/burst colour. The palette selector in Java drives ink, paper and accent together so
// the frame cannot end up with a crimson paper and a white core from two different styles.
uniform vec4 ImpactAccent;
// x use texture mask, y smooth invert, z stretch to screen, w reserved.
uniform vec4 ImpactMaskOptions;
// xy frame_N.png size in pixels, for the aspect-preserving crop.
uniform vec2 ImpactMaskSize;

in vec2 texCoord;
out vec4 fragColor;

const float TAU = 6.28318531;

float lum(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 scene(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;
}

float hash11(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

void main() {
    // The copy pass runs this very shader with strength 0, purely to move the frame into the
    // scratch target before anything here reads its neighbours. Has to come first.
    if (ImpactParams.x <= 0.0005) {
        fragColor = vec4(scene(texCoord), 1.0);
        return;
    }

    vec2 uv = texCoord;
    vec2 texel = 1.5 / ImpactSize;
    float aspect = ImpactSize.x / ImpactSize.y;
    vec2 centre = ImpactProgress.zw;

    // Chromatic aberration growing with the square of the radius from the impact centre, so the
    // middle of the frame holds still and only the edges tear.
    vec2 radial = (uv - centre) * vec2(aspect, 1.0);
    float r2 = dot(radial, radial);
    float split = ImpactParams.y * r2 * 0.08;
    vec3 col = vec3(scene(uv + radial * split).r, scene(uv).g, scene(uv - radial * split).b);

    // Radial zoom blur, strongest at the opening instant and unwinding with the panel. It samples
    // along the same radius as the aberration, so the two lens effects agree about where the
    // frame is being pulled toward. Four taps is the cheap version from TheShootingStar's
    // ZoomBlur setting; more taps buy very little once the frame is already two-toned.
    float zoom = ImpactProgress.y;
    if (zoom > 0.0005) {
        vec2 zdir = (uv - centre) * zoom;
        vec3 z = scene(uv + zdir * 0.25);
        z += scene(uv + zdir * 0.55);
        z += scene(uv + zdir * 0.85);
        z += scene(uv + zdir * 1.15);
        col = mix(col, z * 0.25, 0.7);
    }
    float l = lum(col);

    // Ink outline off a full 3x3 Sobel rather than a central difference. The extra taps are what
    // make fine silhouettes survive the threshold step; a 3x3 kernel also gives the ink a
    // direction, which is why the result reads as a drawn outline and not a blurred edge.
    float tl = lum(scene(uv + vec2(-texel.x, -texel.y)));
    float tc = lum(scene(uv + vec2(0.0, -texel.y)));
    float tr = lum(scene(uv + vec2(texel.x, -texel.y)));
    float ml = lum(scene(uv + vec2(-texel.x, 0.0)));
    float mr = lum(scene(uv + vec2(texel.x, 0.0)));
    float bl = lum(scene(uv + vec2(-texel.x, texel.y)));
    float bc = lum(scene(uv + vec2(0.0, texel.y)));
    float br = lum(scene(uv + vec2(texel.x, texel.y)));
    float gx = (tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl);
    float gy = (bl + 2.0 * bc + br) - (tl + 2.0 * tc + tr);
    float ink = smoothstep(0.18, 0.55, length(vec2(gx, gy))) * ImpactParams.w;

    // Eight-tap ring average. Thresholding against each pixel's own neighbourhood instead of a
    // fixed brightness is what lets the same two tones read over a noon sky and a black cave
    // alike, and survive the white flash sitting underneath this whole pass.
    float avg = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * (TAU / 8.0);
        avg += lum(scene(uv + vec2(cos(a) / aspect, sin(a)) * 0.045));
    }
    avg *= 0.125;

    float two = step(avg + 0.004, l);
    float core = step(avg + 0.18, l);

    // Screentone: a 45-degree dot grid, the dots growing as the tone darkens, crawling outward on
    // the panel's own clock so the frame is never a still image.
    float crawl = ImpactProgress.x * ImpactBurst.z;
    vec2 tp = mat2(0.7071, -0.7071, 0.7071, 0.7071) * (uv * ImpactSize) / 9.0
              - vec2(crawl * 2.0, crawl * 1.2);
    // Keyed off absolute brightness, not off local contrast: a flat region (sky, a wall in
    // shadow) matches its own ring average exactly, so a contrast-driven tone has no reading
    // there at all and every flat pixel lands in dirty midtone dots instead of clean paper.
    float tone = 1.0 - smoothstep(0.36, 0.96, l);
    float dots = step(length(fract(tp) - 0.5), sqrt(clamp(tone, 0.0, 1.0)) * 0.46);
    float mid = smoothstep(0.28, 0.44, tone) * (1.0 - smoothstep(0.62, 0.80, tone));

    vec3 frame = ImpactPaper.rgb * two;
    frame = mix(frame, ImpactInk.rgb * dots, mid * 0.8);
    frame = mix(frame, ImpactInk.rgb * 0.25, ink * ImpactBurst.w);
    frame = mix(frame, ImpactAccent.rgb, core);

    // Structural modes, independent of the palette. Options.w was reserved; it now carries the
    // mode so no extra uniform or JSON entry is needed.
    float mode = ImpactMaskOptions.w;
    if (mode > 0.5 && mode < 1.5) {
        // Line art: clean paper, silhouettes drawn only by the Sobel ink and the hot core. The
        // midtone dots are deliberately absent, which is what makes the line-art variant read as
        // a drawing rather than as a thresholded photograph.
        float contour = smoothstep(0.10, 0.38, length(vec2(gx, gy)));
        frame = ImpactPaper.rgb;
        frame = mix(frame, ImpactInk.rgb, clamp(ink + contour * 0.45, 0.0, 1.0));
        frame = mix(frame, ImpactAccent.rgb, core);
    } else if (mode > 1.5) {
        // Halftone: the screentone becomes the image instead of a midtone overlay. The dot cells
        // are larger and the coverage follows absolute brightness, so a flat sky still resolves
        // into clean paper rather than a noise field.
        float bigDots = step(length(fract(tp * 0.55) - 0.5),
                            sqrt(clamp(tone, 0.0, 1.0)) * 0.58);
        frame = mix(ImpactPaper.rgb, ImpactInk.rgb, bigDots);
        frame = mix(frame, ImpactInk.rgb, ink * 0.35);
        frame = mix(frame, ImpactAccent.rgb, core * 0.6);
    }

    // Optional frame_N.png masks. The mask is a black/white image: white keeps the
    // threshold-converted scene, black selects its inverse. This is how the original mod
    // composed its frames by hand, and it folds its textured-invert pass in without a second
    // program. Options.x is set false on the copy pass and when no mask resolves, in which case
    // the procedural two-tone above stands.
    if (ImpactMaskOptions.x > 0.5) {
        vec3 baseConvertedColor = mix(ImpactInk.rgb, ImpactPaper.rgb, step(vec3(0.5), col));
        vec3 targetColor = mix(ImpactPaper.rgb, ImpactInk.rgb, step(vec3(0.5), col));
        vec2 maskUv = uv;

        if (ImpactMaskOptions.z < 0.5) {
            // Preserve the mask's aspect ratio and centre-crop it to the screen; uv runs
            // outside [0, 1] where the mask does not reach and is left as the plain frame.
            //
            // No vertical flip here, unlike the 26.3 port: this branch samples a plain texture
            // through Sampler0's own convention, where v = 0 is the image's top row (the same
            // convention GuiGraphics.blit uses), and the quad's uv.y = 0 already lands on the top
            // of the screen. 26.3's mask sampler goes through its own pipeline and needed the
            // flip; copying it here would draw the mask upside down.
            vec2 inputSize = max(ImpactSize, vec2(1.0));
            vec2 frameSize = max(ImpactMaskSize, vec2(1.0));
            vec2 ratio = frameSize / inputSize;
            ratio /= max(ratio.x, ratio.y);
            vec2 overflow = 1.0 - ratio;
            maskUv = (uv - overflow * 0.5) / ratio;
        }

        vec3 textureFrame = baseConvertedColor;
        bool insideMask = all(greaterThanEqual(maskUv, vec2(0.0)))
                && all(lessThanEqual(maskUv, vec2(1.0)));
        if (insideMask) {
            vec4 mask = texture(ImpactSampler, maskUv);
            float invertAmount = dot(mask.rgb, vec3(1.0 / 3.0));
            vec3 maskedColor;
            if (ImpactMaskOptions.y > 0.5) {
                maskedColor = mix(baseConvertedColor, targetColor, 1.0 - invertAmount);
            } else {
                maskedColor = invertAmount < 0.5 ? targetColor : baseConvertedColor;
            }
            textureFrame = mix(baseConvertedColor, maskedColor, mask.a);
        }
        frame = textureFrame;
    }

    // The drawn burst: one hard ring leaving the impact centre, sized to reach the edges as its
    // own short window ends, plus radial speed lines. Both decay on their own fast clock
    // (ImpactProgress.x against a ~0.28 window, about 125ms) independently of the panel's own
    // slower fade, so the burst reads as a single instant rather than fading with the panel.
    float radius = length(radial);
    float angle = atan(radial.y, radial.x);

    float bp = clamp(ImpactProgress.x / 0.28, 0.0, 1.0);
    float burst = pow(1.0 - bp, 0.6);

    float ringR = 0.7 * pow(bp, 0.6);
    float ringW = 0.024 + 0.018 * ImpactProgress.x;
    float ring = smoothstep(ringW, ringW * 0.3, abs(radius - ringR)) * burst * ImpactBurst.x;

    float spokes = 32.0;
    float wedgeId = floor(angle / TAU * spokes);
    float pick = step(0.5, hash11(wedgeId));
    float wedge = abs(fract(angle / TAU * spokes) - 0.5);
    float ray = 1.0 - smoothstep(0.05, 0.095, wedge);
    float taper = 0.25 + 0.75 * clamp(radius / 0.6, 0.0, 1.0);
    float band = smoothstep(0.04, 0.28, radius) * (1.0 - smoothstep(0.6, 1.05, radius));
    float lines = pick * ray * taper * band * burst * ImpactBurst.y;

    vec3 harsh = clamp(mix(frame * 0.12, ImpactAccent.rgb * 0.8, 0.55),
                       vec3(0.02), vec3(1.0));
    float hard = clamp(smoothstep(0.05, 0.45, ring) + smoothstep(0.05, 0.5, lines), 0.0, 1.0) * 0.8;
    frame = mix(frame, harsh, hard);

    // Void vignette: the edges of the screen are visibly being consumed, closing in as the
    // panel's own life runs out - this is what makes it read as a dimension failing rather than
    // as a colour grade laid over an otherwise untouched frame.
    float edgeRadius = length((uv - 0.5) * vec2(aspect, 1.0));
    float vignette = smoothstep(0.55, 1.05 - ImpactProgress.x * 0.35, edgeRadius);
    frame = mix(frame, ImpactInk.rgb, vignette * 0.85);

    vec3 result = mix(col, frame, clamp(ImpactParams.x, 0.0, 1.0));
    result += ImpactPaper.rgb * ImpactParams.z;

    fragColor = vec4(result, 1.0);
}