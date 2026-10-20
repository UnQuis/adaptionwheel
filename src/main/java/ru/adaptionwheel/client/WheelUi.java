package ru.adaptionwheel.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import ru.adaptionwheel.AdaptionWheel;

import java.util.List;
import java.util.Locale;

/**
 * The small drawing vocabulary the white-space wheel menu is made of.
 *
 * <p>Every surface here is drawn with {@link GuiGraphics#fill} rather than a vanilla sprite. The
 * reference UI's soft cards and hairline rules have no equivalent in 1.21.1's GUI atlas, and
 * blitting a nine-slice at three different scales was the source of two bugs in the older panel.
 * Rounded rectangles are rasterised as rows, which is exactly what the GUI needs at 2x scale and
 * costs a handful of quads per card.
 *
 * <p>All colours are ARGB. The {@link #mix} and {@link #withAlpha} helpers deliberately keep those
 * two jobs apart: mixing is for an opaque accent, alpha is for a shadow or a fade.
 */
final class WheelUi {

    static final ResourceLocation MONO_8 = font("mono_8");
    static final ResourceLocation MONO_9 = font("mono_9");
    static final ResourceLocation MONO_11 = font("mono_11");
    static final ResourceLocation MONO_14 = font("mono_14");
    static final ResourceLocation MONO_20 = font("mono_20");
    static final ResourceLocation MONO_24 = font("mono_24");
    static final ResourceLocation MONO_30 = font("mono_30");

    static final int INK = 0xFFF2EFE7;
    static final int INK_SOFT = 0xFFCEC9BE;
    static final int MUTED = 0xFF9A968D;
    static final int FAINT = 0xFF6D6961;
    static final int HAIRLINE = 0x24FFFFFF;
    static final int CARD = 0xFF141416;
    static final int CARD_ALT = 0xFF0F0F11;
    static final int BORDER = 0xFF2B2B30;
    static final int GREEN = 0xFF83C48B;
    static final int GREEN_BG = 0xFF17301D;
    static final int ROSE = 0xFFE58AA5;
    static final int ROSE_BG = 0xFF371A24;
    static final int GOLD = 0xFFD8B972;
    static final int AMBER = 0xFFD9A45A;

    private WheelUi() {
    }

    static ResourceLocation font(String name) {
        return ResourceLocation.fromNamespaceAndPath(AdaptionWheel.MODID, name);
    }

    static Component mono(ResourceLocation font, String text) {
        return Component.literal(text).withStyle(style -> style.withFont(font));
    }

    static Component mono(ResourceLocation font, Component text) {
        return text.copy().withStyle(style -> style.withFont(font));
    }

    static void text(GuiGraphics graphics, Font font, ResourceLocation fontId, String text,
                     int x, int y, int color, boolean shadow) {
        graphics.drawString(font, mono(fontId, text), x, y, color, shadow);
    }

    static void text(GuiGraphics graphics, Font font, ResourceLocation fontId, Component text,
                     int x, int y, int color, boolean shadow) {
        graphics.drawString(font, mono(fontId, text), x, y, color, shadow);
    }

    static void centered(GuiGraphics graphics, Font font, ResourceLocation fontId, String text,
                         int centerX, int y, int color, boolean shadow) {
        Component component = mono(fontId, text);
        graphics.drawString(font, component, centerX - font.width(component) / 2, y, color, shadow);
    }

    static void centered(GuiGraphics graphics, Font font, ResourceLocation fontId, Component text,
                         int centerX, int y, int color, boolean shadow) {
        Component component = mono(fontId, text);
        graphics.drawString(font, component, centerX - font.width(component) / 2, y, color, shadow);
    }

    static int trackedWidth(Font font, ResourceLocation fontId, String text, float tracking) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int width = 0;
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += font.width(mono(fontId, new String(Character.toChars(cp))));
            i += Character.charCount(cp);
            count++;
        }
        return width + Math.round(tracking * Math.max(0, count - 1));
    }

    static void trackedLeft(GuiGraphics graphics, Font font, ResourceLocation fontId, String text,
                            int x, int y, int color, float tracking) {
        float cursor = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            Component glyph = mono(fontId, new String(Character.toChars(cp)));
            graphics.drawString(font, glyph, Math.round(cursor), y, color, false);
            cursor += font.width(glyph) + tracking;
            i += Character.charCount(cp);
        }
    }

    static void trackedCentered(GuiGraphics graphics, Font font, ResourceLocation fontId, String text,
                                int centerX, int y, int color, float tracking) {
        int width = trackedWidth(font, fontId, text, tracking);
        trackedLeft(graphics, font, fontId, text, centerX - width / 2, y, color, tracking);
    }

    /**
     * A string that fits, with a real ellipsis. JetBrains Mono is proportional in measurement even
     * though its advances are uniform, so this walks widths instead of dividing by a character
     * count; a translated string can contain any glyph, including one the fallback font draws.
     */
    static String fit(Font font, ResourceLocation fontId, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(mono(fontId, text)) <= maxWidth) {
            return text;
        }
        String ellipsis = "\u2026";
        if (font.width(mono(fontId, ellipsis)) > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            String candidate = text.substring(0, mid).stripTrailing() + ellipsis;
            if (font.width(mono(fontId, candidate)) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low).stripTrailing() + ellipsis;
    }

    /**
     * Draws wrapped text and returns the number of lines drawn.
     *
     * <p>The caller owns the line gap rather than the font's line height: JetBrains Mono at 8 px
     * has a taller natural line box than the editorial layout wants, and letting the font decide
     * made a three-line description collide with the toggle below it.
     */
    static int wrapped(GuiGraphics graphics, Font font, ResourceLocation fontId, Component text,
                       int x, int y, int maxWidth, int lineGap, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(mono(fontId, text), maxWidth);
        int count = Math.min(maxLines, lines.size());
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), x, y + i * lineGap, color, false);
        }
        return count;
    }

    static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height,
                            int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width / 2, height / 2)));
        if (r == 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        for (int row = 0; row < r; row++) {
            double dy = r - row - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy)));
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int row = 0; row < r; row++) {
            double dy = row + 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0.0, (double) r * r - dy * dy)));
            graphics.fill(x + inset, y + height - r + row, x + width - inset,
                    y + height - r + row + 1, color);
        }
    }

    static void outlineRounded(GuiGraphics graphics, int x, int y, int width, int height,
                               int radius, int border, int body) {
        fillRounded(graphics, x, y, width, height, radius, border);
        if (width > 2 && height > 2) {
            fillRounded(graphics, x + 1, y + 1, width - 2, height - 2,
                    Math.max(0, radius - 1), body);
        }
    }

    static void softShadow(GuiGraphics graphics, int x, int y, int width, int height, int radius) {
        fillRounded(graphics, x - 3, y + 5, width + 6, height + 1, radius + 3, 0x66000000);
        fillRounded(graphics, x - 2, y + 3, width + 4, height + 1, radius + 2, 0x4D000000);
        fillRounded(graphics, x - 1, y + 2, width + 2, height + 1, radius + 1, 0x33000000);
    }

    static void card(GuiGraphics graphics, int x, int y, int width, int height,
                     int radius, int body, int border) {
        softShadow(graphics, x, y, width, height, radius);
        outlineRounded(graphics, x, y, width, height, radius, border, body);
    }

    static void fillCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        for (int row = -radius; row <= radius; row++) {
            int dx = (int) Math.floor(Math.sqrt(Math.max(0.0, (double) radius * radius - (double) row * row)));
            graphics.fill(centerX - dx, centerY + row, centerX + dx + 1, centerY + row + 1, color);
        }
    }

    static void ringCircle(GuiGraphics graphics, int centerX, int centerY, int radius,
                           int color) {
        if (radius <= 0) {
            return;
        }
        int segments = Math.max(32, radius * 5);
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * 2.0 * i / segments;
            int x = centerX + (int) Math.round(Math.cos(a) * radius);
            int y = centerY + (int) Math.round(Math.sin(a) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    static void toggle(GuiGraphics graphics, int x, int y, boolean on, boolean hovered) {
        int width = 34;
        int height = 16;
        int track = on ? (hovered ? 0xFFA6DBAE : GREEN) : (hovered ? 0xFF35353B : 0xFF28282D);
        fillRounded(graphics, x, y, width, height, height / 2, 0x66000000);
        fillRounded(graphics, x, y + 1, width, height, height / 2, track);
        int knob = height - 4;
        int knobX = on ? x + width - knob - 2 : x + 2;
        fillRounded(graphics, knobX, y + 3, knob, knob, knob / 2, 0x66000000);
        fillRounded(graphics, knobX, y + 2, knob, knob, knob / 2, 0xFFF1EEE6);
    }

    static void itemIcon(GuiGraphics graphics, ItemStack stack, int x, int y, int size) {
        if (stack == null || stack.isEmpty() || size <= 0) {
            return;
        }
        float scale = size / 16f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 1f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    static void dashedVertical(GuiGraphics graphics, int x, int y, int height, int color) {
        for (int row = 0; row < height; row += 6) {
            graphics.fill(x, y + row, x + 2, y + Math.min(height, row + 3), color);
        }
    }

    static int withAlpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        float f = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) * (1f - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1f - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1f - f) + (b & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** A quiet, warm tile for an accent on the dark cards. */
    static int tint(int accent) {
        return mix(accent, CARD, 0.80f);
    }

    /** The washed-out accent used by an adaptation that has been switched off. */
    static int disabled(int accent) {
        return mix(accent, 0xFF4A4A50, 0.72f);
    }

    static int lighten(int rgb, float t) {
        return mix(rgb, 0xFFFFFF, t);
    }

    static int darken(int rgb, float t) {
        return mix(rgb, 0x000000, t);
    }

    static String upper(String text) {
        return text == null ? "" : text.toUpperCase(Locale.ROOT);
    }
}
