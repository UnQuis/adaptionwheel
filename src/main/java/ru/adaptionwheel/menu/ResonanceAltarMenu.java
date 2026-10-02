package ru.adaptionwheel.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.block.ModBlocks;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.AltarOfferings;

import java.util.ArrayList;
import java.util.List;

/**
 * The Resonance Altar's second face: feed it a mob's own loot, and it sells that mob's adaptations.
 *
 * <p>The altar keeps its first job — the resonance aura in {@code RitualAuras} still runs — because
 * that is a different question. One asks who is standing near you; this one asks what you are willing
 * to spend on something that fights back.</p>
 *
 * <p><b>One offering, two choices per mob.</b> A bone drops from five kinds of skeleton, so a bone
 * offers ten things: for each of those five, the adaptation to hurting it and the adaptation to its
 * loot. Splitting them is the whole point — a player who wants a mob's drops is not asking for the
 * ability to fight it, and the two are worth different prices to different people.</p>
 *
 * <p>The order is mob-major, so a mob's two rows sit together rather than being interleaved with
 * another mob's.</p>
 */
public class ResonanceAltarMenu extends TradeMenu {

    public ResonanceAltarMenu(int windowId, Inventory playerInv, BlockPos pos) {
        super(windowId, playerInv, pos, ModMenus.RESONANCE_ALTAR_TRADE.get());
    }

    public ResonanceAltarMenu(int windowId, Inventory playerInv, FriendlyByteBuf extra) {
        super(windowId, playerInv, extra, ModMenus.RESONANCE_ALTAR_TRADE.get());
    }

    @Override
    protected boolean isTradeBlock(BlockState state) {
        return state.is(ModBlocks.RESONANCE_ALTAR.get());
    }

    @Override
    public String titleKey() {
        return "container.adaptionwheel.resonance_altar";
    }

    @Override
    protected List<String> candidatesFor(ServerPlayer player, PlayerAdaption fed, ItemStack offering) {
        ServerPlayer server = player;
        List<String> mobs = AltarOfferings.mobsFor(offering, server.getServer());
        if (mobs.isEmpty()) {
            return List.of();
        }
        List<String> pool = new ArrayList<>(mobs.size() * 2);
        for (String mob : mobs) {
            addIfUnfinished(pool, fed, Concepts.offense(mob));
            addIfUnfinished(pool, fed, Concepts.drop(mob));
        }
        return List.copyOf(pool);
    }

    private static void addIfUnfinished(List<String> pool, PlayerAdaption fed, String concept) {
        // `fed` is the wheel being fed, so "already have it" has to mean what THAT wheel has --
        // the player's own attachment belongs to the wheel they took off to put this one in.
        if (!fed.isAdapted(concept) && fed.level(concept) <= 0) {
            pool.add(concept);
        }
    }

    @Override
    protected int itemPrice(ServerPlayer player, ItemStack offering) {
        return AltarOfferings.mobsFor(offering, player.getServer()).isEmpty() ? 0 : 1;
    }

    /**
     * Grants into the wheel being fed and writes it straight back onto that stack.
     *
     * <p>Both kinds go through one call because the difference between them — a drop-rate
     * adaptation is paid in kills, an offense at level — is a rule about the <em>concept</em>, and
     * it lives next to the grant it changes rather than in each block that sells one.</p>
     */
    @Override
    protected void grant(ServerPlayer player, PlayerAdaption fed, ItemStack wheel, String concept) {
        AdaptionEvents.grantToWheel(player, fed, wheel, concept, PlayerAdaption.MAX_LEVEL);
    }

    /**
     * Which mob a concept is about, or {@code null} for anything that is not one of these two
     * families. Used by the screen to print whose adaptation is on offer.
     */
    public static String mobOf(String concept) {
        if (concept == null) {
            return null;
        }
        if (concept.startsWith(Concepts.OFFENSE_PREFIX)) {
            return concept.substring(Concepts.OFFENSE_PREFIX.length());
        }
        if (concept.startsWith(Concepts.DROP_PREFIX)) {
            return concept.substring(Concepts.DROP_PREFIX.length());
        }
        return null;
    }
}