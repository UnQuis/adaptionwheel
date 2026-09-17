package ru.adaptionwheel.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import ru.adaptionwheel.AdaptionWheel;

/**
 * Adversity overlay, ported from the original mod's DrawAdversityTimer +
 * giant-wheel layer:
 * - fullscreen dark-red vignette with a heartbeat pulse
 * - black cinematic bars top/bottom
 * - "ADAPTING TO ADVERSITY" title + [ SS : CS ] countdown
 * - progress bar that shrinks symmetrically toward zero as the run completes
 * - cooldown note after the run ends
 * - on trigger the wheel sprite flies at the player's face (totem-style flash),
 *   then stays as a faint gray heartbeat wheel while adversity is active.
 */
@EventBusSubscriber(modid = AdaptionWheel.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class AdversityOverlay {

    private static final int ADVERSITY_TOTAL_TICKS = 480;

    private AdversityOverlay() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !ClientAdaption.wearingWheel || mc.player.isDeadOrDying()) {
            return;
        }
        boolean active = ClientAdaption.adversityActive;
        boolean cooling = ClientAdaption.adversityCooldownTimer > 0 && !active;
        if (!active && !cooling) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = ClientAdaption.gameTime() + partialTick;
        float pulse = Mth.abs(Mth.sin(time * 0.2f));
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        PoseStack pose = graphics.pose();

        // Totem-style face flash right after the trigger, then faint gray heartbeat wheel.
        drawWheel(graphics, time, screenW, screenH);

        if (!active) {
            // Cooldown note only.
            String cdText = Component.translatable("adaptionwheel.hud.adversity_cooldown",
                    ClientAdaption.adversityCooldownTimer / 20).getString();
            int w = font.width(cdText);
            graphics.drawString(font, cdText, (screenW - w) / 2, 50, 0xFF999999, true);
            return;
        }

        // Fullscreen dark red vignette.
        graphics.fill(0, 0, screenW, screenH,
                ((int) ((0.15f + pulse * 0.10f) * 255f) << 24) | 0x8B0000);

        // Cinematic black bars.
        int barH = Math.max(20, screenH / 12);
        graphics.fill(0, 0, screenW, barH, ((int) (0.7f * 255f) << 24));
        graphics.fill(0, screenH - barH, screenW, screenH, ((int) (0.7f * 255f) << 24));

        int cx = screenW / 2;
        int cy = barH / 2;
        float themeLerp = pulse * 0.5f;
        int themeColor = lerpColor(0xFF0000, 0xFFFFFF, themeLerp);
        float titleScale = 1.5f + pulse * 0.1f;

        // Title.
        pose.pushPose();
        pose.translate(cx, cy - 14, 0);
        pose.scale(titleScale, titleScale, 1f);
        String title = Component.translatable("adaptionwheel.hud.adversity_title").getString();
        graphics.drawString(font, title, -font.width(title) / 2, -font.lineHeight / 2, 0xFFFF0000, true);
        pose.popPose();

        // Countdown [ SS : CS ].
        int remainingTicks = ClientAdaption.smoothedAdversityTimer();
        int seconds = remainingTicks / 20;
        int centis = (int) (remainingTicks % 20 / 20f * 100f);
        String timeStr = "[ " + String.format("%02d : %02d", seconds, centis) + " ]";
        pose.pushPose();
        pose.translate(cx, cy + 16, 0);
        pose.scale(0.9f, 0.9f, 1f);
        graphics.drawString(font, timeStr, -font.width(timeStr) / 2, -font.lineHeight / 2, themeColor, true);
        pose.popPose();

        // Progress bar shrinking symmetrically toward zero.
        float progress = Mth.clamp(remainingTicks / (float) ADVERSITY_TOTAL_TICKS, 0f, 1f);
        int barW = Math.min(240, screenW / 3);
        int barH2 = 4;
        int barX = cx - barW / 2;
        int fill = (int) (barW * progress);
        graphics.fill(cx - barW / 2, cy + 34, cx + barW / 2, cy + 34 + barH2, 0x808B0000);
        if (fill > 0) {
            graphics.fill(cx - fill / 2, cy + 34, cx + fill / 2, cy + 34 + barH2, themeColor);
        }
    }

    /**
     * The wheel flying at the player's face on the trigger tick window (white, growing,
     * fading out over 60 ticks like the original AdversitySpinTimer), then a subtle
     * gray heartbeat wheel for the rest of the active run.
     */
    private static void drawWheel(GuiGraphics graphics, float time, int screenW, int screenH) {
        long triggeredAt = ClientAdaption.adversityTriggeredAtGameTime;
        long now = ClientAdaption.gameTime();
        float alpha;
        float sizeFrac;
        boolean white;
        if (triggeredAt != Long.MIN_VALUE && now >= triggeredAt && now < triggeredAt + 70
                && ClientAdaption.adversityActive) {
            float t = (now - triggeredAt) / 70f;
            white = true;
            alpha = 0.85f * (1f - t);
            sizeFrac = 1.3f + 1.9f * t;
        } else if (ClientAdaption.adversityActive) {
            float heartbeat = (Mth.sin(time * 3.5f) + 1f) / 2f;
            white = false;
            alpha = 0.08f + heartbeat * 0.25f;
            sizeFrac = 1.6f;
        } else {
            return;
        }

        int size = (int) (screenH * sizeFrac);
        int argb = (int) (alpha * 255f) << 24 | (white ? 0xFFFFFF : 0x777777);
        drawTexturedQuad(graphics.pose(), screenW / 2 - size / 2, screenH / 2 - size / 2, size, argb);
    }

    private static void drawTexturedQuad(PoseStack pose, int x, int y, int size, int argb) {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, AdaptionHud.WHEEL_TEXTURE);
        Matrix4f matrix = pose.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x, y, 0).setUv(0f, 0f).setColor(argb);
        buffer.addVertex(matrix, x, y + size, 0).setUv(0f, 1f).setColor(argb);
        buffer.addVertex(matrix, x + size, y + size, 0).setUv(1f, 1f).setColor(argb);
        buffer.addVertex(matrix, x + size, y, 0).setUv(1f, 0f).setColor(argb);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
