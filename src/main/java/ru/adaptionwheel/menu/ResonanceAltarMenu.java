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
    protected List<String> candidatesFor(ServerPlayer player, PlayerAdaption data, ItemStack offering) {
        // 26.3's Entity has no getServer(); the level is what holds it, and this menu only ever
        // runs on the server (the player is a ServerPlayer to reach here at all).
        List<String> mobs = AltarOfferings.mobsFor(offering, serverOf(player));
        if (mobs.isEmpty()) {
            return List.of();
        }
        List<String> pool = new ArrayList<>(mobs.size() * 2);
        for (String mob : mobs) {
            addIfUnfinished(pool, data, Concepts.offense(mob));
            addIfUnfinished(pool, data, Concepts.drop(mob));
        }
        return List.copyOf(pool);
    }

    private static net.minecraft.server.MinecraftServer serverOf(ServerPlayer player) {
        return player.level() instanceof net.minecraft.server.level.ServerLevel level
                ? level.getServer() : null;
    }

    private static void addIfUnfinished(List<String> pool, PlayerAdaption data, String concept) {
        if (!data.isAdapted(concept) && data.level(concept) <= 0) {
            pool.add(concept);
        }
    }

    @Override
    protected int itemPrice(ServerPlayer player, ItemStack offering) {
        return AltarOfferings.mobsFor(offering, serverOf(player)).isEmpty() ? 0 : 1;
    }

    /**
     * Grants the chosen adaptation, and the two kinds are not the same thing.
     *
     * <p>An offense is bought at level, like anything else. A <b>drop-rate adaptation is not</b>:
     * its level is derived from how many of that mob the player has killed, everywhere else in the
     * mod. Setting the level here would leave that counter lying — a player would hold an eighth
     * level of loot-luck having killed one chicken. So the altar pays in <em>kills</em> instead, and
     * the existing rule turns those into the level. Same table, same outcome, no second way to
     * reach the same number.</p>
     */
    @Override
    protected void grant(ServerPlayer player, PlayerAdaption data, String concept) {
        if (Concepts.isDrop(concept)) {
            AdaptionEvents.grantKillsToward(player, data, concept, PlayerAdaption.MAX_LEVEL);
            return;
        }
        AdaptionEvents.completeTaskUpTo(player, data, concept, PlayerAdaption.MAX_LEVEL);
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