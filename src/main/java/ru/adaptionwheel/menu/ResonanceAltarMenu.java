package ru.adaptionwheel.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import ru.adaptionwheel.block.ModBlocks;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;
import ru.adaptionwheel.server.AltarOfferings;
import ru.adaptionwheel.server.DomainExchange;

import java.util.ArrayList;
import java.util.List;

/**
 * The Resonance Altar: the mod's one trading block, and the whole of what the Domain Stone used to
 * be as well.
 *
 * <p>Those were two blocks with the same two slots, the same list, the same price arithmetic and the
 * same screen, differing only in where they looked for what an offering item opens — the stone in a
 * hand-written price list, the altar in the mob loot tables. Two blocks to learn, two recipes, two
 * advancements, two of everything, for one set of rules. So there is one block now, and it asks
 * both questions of the same item and shows the union of the answers.</p>
 *
 * <p><b>One offering, up to three kinds of row.</b> An item on the price list buys the adaptation it
 * names; a mob's own drop buys that mob's two adaptations — the ability to hurt it and the ability
 * to take more from it — and a bone drops from five kinds of skeleton, so a bone buys ten. Splitting
 * a mob's two is the point: a player who wants a mob's loot is not asking for the ability to fight
 * it. An item can be both kinds at once (a bone is on the price list <em>and</em> drops from five
 * skeletons), and then it buys both, price-list rows first.</p>
 *
 * <p><b>Every row is one this particular wheel has not finished.</b> Bought here or adapted to by
 * suffering, both live in the fed stack's {@code wheel_data} and both are subtracted before the list
 * is drawn, so the list is a shopping list for the wheel in the slot rather than a catalogue of the
 * item. See {@link DomainExchange#candidates}.</p>
 *
 * <p>The altar also keeps its first job, the resonance aura in {@code RitualAuras}, because that is a
 * different question: one asks who is standing near you, this asks what you are willing to spend.</p>
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

    /**
     * What the offering opens: the price list first, then the mobs that drop it.
     *
     * <p>Both halves subtract what the fed wheel has already finished, and both go through
     * {@code DomainExchange.isFinished} rather than each having its own idea of "finished" — the
     * price list's version is the one the exchange re-checks with, so the two agreeing is a rule
     * rather than a coincidence.</p>
     *
     * <p>Mob rows come after the price-list rows and in mob-major order, so a mob's two sit together
     * rather than being interleaved with another mob's.</p>
     */
    @Override
    protected List<String> candidatesFor(ServerPlayer player, PlayerAdaption fed, ItemStack offering) {
        List<String> pool = new ArrayList<>();

        DomainExchange.Recipe recipe = DomainExchange.recipeFor(offering);
        if (recipe != null) {
            pool.addAll(DomainExchange.candidates(fed, WheelTier.forCount(fed.getAdaptCount()), recipe));
        }

        // 26.3's Entity has no getServer(); the level is what holds it, and this menu only ever
        // runs on the server (the player is a ServerPlayer to reach here at all).
        for (String mob : AltarOfferings.mobsFor(offering, serverOf(player))) {
            addIfUnfinished(pool, fed, Concepts.offense(mob));
            addIfUnfinished(pool, fed, Concepts.drop(mob));
        }
        return List.copyOf(pool);
    }

    private static net.minecraft.server.MinecraftServer serverOf(ServerPlayer player) {
        return player.level() instanceof net.minecraft.server.level.ServerLevel level
                ? level.getServer() : null;
    }

    /**
     * `fed`, not the player's attachment: "already have it" has to mean what <em>that</em> wheel has,
     * because the player's own attachment belongs to the wheel they took off to put this one in.
     */
    private static void addIfUnfinished(List<String> pool, PlayerAdaption fed, String concept) {
        if (!DomainExchange.isFinished(fed, concept)) {
            pool.add(concept);
        }
    }

    /**
     * How many of the item one trade costs: the price list's own number where there is one, and a
     * single item where the item is only a mob's drop.
     *
     * <p>Zero means the altar does not take this item at all, which is what keeps an unrelated item
     * out of the exchange rather than letting it buy a thing for nothing.</p>
     */
    @Override
    protected int itemPrice(ServerPlayer player, ItemStack offering) {
        DomainExchange.Recipe recipe = DomainExchange.recipeFor(offering);
        if (recipe != null) {
            return recipe.itemsPerTrade();
        }
        return AltarOfferings.mobsFor(offering, serverOf(player)).isEmpty() ? 0 : 1;
    }

    /**
     * Grants into the wheel being fed and writes it straight back onto that stack.
     *
     * <p>Both kinds go through one call because the difference between them — a drop-rate
     * adaptation is paid in kills, an offense at level — is a rule about the <em>concept</em>, and
     * it lives next to the grant it changes rather than here.</p>
     */
    @Override
    protected void grant(ServerPlayer player, PlayerAdaption fed, ItemStack wheel, String concept) {
        AdaptionEvents.grantToWheel(player, fed, wheel, concept, PlayerAdaption.MAX_LEVEL);
    }
}
