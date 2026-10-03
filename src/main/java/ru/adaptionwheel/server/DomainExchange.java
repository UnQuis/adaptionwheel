package ru.adaptionwheel.server;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationDomain;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.category.WheelTier;
import ru.adaptionwheel.data.PlayerAdaption;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Resonance Altar's price list: what an offering item buys, and what it will not buy.
 *
 * <p>This used to be the Domain Stone's, back when the stone and the altar were two blocks with the
 * same two slots and the same arithmetic. They are one block now — see
 * {@link ru.adaptionwheel.menu.ResonanceAltarMenu}, which asks this list <em>and</em> the mob-drop
 * index for the same item — so the name is the one thing here that outlived its block.</p>
 *
 * <p>The stone originally handed an adaptation over for free, on a cooldown, drawn at random from
 * whatever the wheel had not finished. That made it the one place in the mod where progress cost
 * nothing, which quietly made the other fifty-odd adaptations — each bought with suffering and
 * time — optional. It is now an <em>exchange</em>: an item goes in, adaptations come out, and the
 * item is the price.</p>
 *
 * <h2>What the item does</h2>
 *
 * <p>An offering item does not name an adaptation. It narrows the pool. A recipe is a list of
 * selectors, and a selector is either an exact concept or a family written with a trailing
 * {@code *}:</p>
 *
 * <pre>
 *   FEATHER     -&gt; Debuff_levitation     one adaptation, always
 *   ENDER_EYE   -&gt; Env_Void               idem
 *   NETHER_STAR -&gt; Type_*                 sixteen damage types, pick one
 * </pre>
 *
 * <p>Naming families rather than enumerating keeps the recipes short and, more usefully, lets the
 * pool be <em>derived</em> instead of fixed: a recipe that says {@code Type_*} needs no editing
 * when a damage type is added.</p>
 *
 * <h2>Exact selectors are the escape hatch, and they are load-bearing</h2>
 *
 * <p>The pool is walked out of {@link AdaptationRegistry#allDefinitions()}, and that registry does
 * <b>not</b> contain the dynamically-built families. {@code Debuff_*} keys are minted on the fly
 * from whatever effect a player happens to be under, and {@code Contact_} / {@code Offense_NPC_} /
 * {@code Drop_NPC_} are minted per mob — so a recipe naming either would offer nothing at all,
 * silently, with no error and no log line. Since a feather for levitation is the example the whole
 * block was sketched around, an <b>exact</b> selector is therefore resolved directly rather than
 * looked up in the registry: it names the concept, and the concept is offered.</p>
 *
 * <p>The consequence worth knowing: an exact selector can sell something the registry cannot
 * describe, and therefore something that sorts last by domain name, because
 * {@link AdaptationRegistry#get} returns {@code null} for it. That is only a cosmetic ordering
 * effect, and paying it is much cheaper than a recipe that quietly trades nothing.</p>
 *
 * <h2>The pool is what the item opens MINUS what this wheel has finished</h2>
 *
 * <p>Both halves are load-bearing and they are not the same rule.</p>
 *
 * <p>The <b>subtraction</b> is the point of the block: an adaptation the wheel being fed has already
 * finished — bought here, or adapted to by standing in the thing until it stopped mattering — is not
 * on offer. Paying experience levels for something the wheel already has is the worst outcome
 * available, because the item would be gone and nothing would have changed, so the row is removed
 * before it can be clicked rather than refused after. This also means <b>which wheel</b> is in the
 * slot decides the list, and that is deliberate: the list is a shopping list for that wheel, not a
 * catalogue of the item.</p>
 *
 * <p>It is also why the wheel slot is not optional: with the slot empty there is no wheel to
 * subtract anything from, so nothing is offered. The block has no answer that does not depend on
 * the wheel in it, which is what makes it a ritual rather than a shop.</p>
 *
 * <p>The <b>tier</b> does not filter. Tiers <em>reveal</em>, they never restrict, because the wheel
 * is omnipotent so a later tier is only ever a larger one. A block that refused to sell a concept
 * until the wheel could analyse it would impose the one rule the tier system does not have, and would
 * make the price of the item irrelevant to what it buys. The tier is read here for one thing only:
 * to put revealed families at the top of the list.</p>
 *
 * <h2>Ordering is total</h2>
 *
 * <p>Revealed families first, then domain, then concept name. The first clause keeps the shortcut
 * towards what comes next. The rest exists because the client selects a candidate by its
 * <em>index</em> in this list: two builds of the same pool in a different order is an altar that
 * grants whatever the server's index happened to point at, which is not what the player clicked.
 * {@code TradeMenu.exchange()} is where that index is turned back into a concept, and it resolves it
 * by name against a freshly built pool for exactly this reason.</p>
 */
public final class DomainExchange {

    private DomainExchange() {
    }

    /**
     * One clause of a recipe: an exact concept, or a family when it ends in {@code *}.
     *
     * <p>An exact selector is <em>not</em> validated against the registry, and deliberately so —
     * see the class comment. Validating it would make {@code Debuff_levitation} a startup crash for
     * a concept that is real and reachable, just not registered.</p>
     */
    public record Selector(String text) {

        public static Selector of(String text) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("a domain exchange selector cannot be blank");
            }
            return new Selector(text);
        }

        public boolean matches(String concept) {
            return isExact() ? concept.equals(text) : concept.startsWith(prefix());
        }

        /** Whether this selector names one specific adaptation rather than a family. */
        public boolean isExact() {
            return !text.endsWith("*");
        }

        /** The family part of a {@code *}-selector, i.e. {@code text} minus its star. */
        public String prefix() {
            return text.substring(0, text.length() - 1);
        }
    }

    /**
     * What one item buys into, and how many of it one trade costs.
     *
     * <p>The item is <b>consumed</b> by the trade, so this is a price rather than a key. It is a
     * plain count rather than a per-level rate because there is no longer a level count to buy: an
     * exchange grants the adaptation whole, and what varies is how much it costs.</p>
     *
     * @param itemsPerTrade how many of the item one exchange consumes
     */
    public record Recipe(Item item, List<Selector> selectors, int itemsPerTrade) {

        public Recipe {
            itemsPerTrade = Math.max(1, itemsPerTrade);
        }
    }

    private static final Map<Item, Recipe> BY_ITEM = new LinkedHashMap<>();

    /**
     * Fills the registry, once.
     *
     * <p>Called from common setup because that is the earliest point where both the vanilla and the
     * mod's own {@link Item}s are resolved. Registering during class init would capture
     * unregistered holders; looking everything up by id later would work but would lose the
     * compile-time check that a typo in {@code ModItems} is a typo.</p>
     *
     * <p>No recipe names a mutation, {@code Dimension_Destroy} or {@code Self_Damage}. Those three
     * are milestones with unlock conditions of their own — a combo mutation exists because the
     * wheel has already learned both halves of it — and selling one for an item would replace that
     * condition with a shopping list.</p>
     */
    public static void bootstrap() {
        if (!BY_ITEM.isEmpty()) {
            return;
        }
        // The two the block was sketched around. Both name a single adaptation, so the first thing
        // a player does here is a one-row choice and the list is not the first thing they learn.
        register(Items.FEATHER, 1, "Debuff_levitation");
        register(Items.ENDER_EYE, 1, "Env_Void");
        register(Items.ENDER_PEARL, 1, "Env_Void");

        // Environments, at one per level: they are one-time, so one is all there is.
        register(Items.WATER_BUCKET, 1, "Env_Liquid");
        register(Items.LAVA_BUCKET, 2, "Env_Lava");
        register(Items.COD, 1, "Env_Drowning");
        register(Items.SLIME_BALL, 1, "Env_Slime");
        register(Items.COBWEB, 1, "Env_Cobweb");
        register(Items.PACKED_ICE, 1, "Env_Ice");
        register(Items.BONE, 1, "Env_Suffocate", "Type_SUFFOCATE");

        // Movement, also one-time.
        register(Items.HONEY_BOTTLE, 1, "Move_Honey");
        register(Items.SNOWBALL, 1, "Move_PowderSnow");
        register(Items.SWEET_BERRIES, 1, "Move_BerryBush");
        register(Items.SOUL_SAND, 1, "Move_SoulSand");

        // A second debuff, to show the escape hatch earns its keep.
        register(Items.GHAST_TEAR, 1, "Debuff_wither");

        // Damage types are leveled to eight, so they are worth more of an item than a
        // one-time is. The real price of a leveled one is experience; see priceFor.
        register(Items.NETHER_WART, 1, "Type_WITHER");
        register(Items.BLAZE_POWDER, 2, "Type_FIRE");
        register(Items.SNOW_BLOCK, 2, "Type_FREEZE");
        // The catch-all: every damage type there is, for four of the most valuable item in the
        // game. Two experience levels on top of that, for the privilege of not being shot at.
        register(Items.NETHER_STAR, 4, "Type_*");
    }

    /**
     * Registers one offering item. A later registration for the same item replaces an earlier one,
     * so a pack or another mod can retune a recipe without removing the built-in first.
     */
    public static void register(Item item, int itemsPerTrade, String... selectors) {
        List<Selector> parsed = new ArrayList<>(selectors.length);
        for (String text : selectors) {
            parsed.add(Selector.of(text));
        }
        BY_ITEM.put(item, new Recipe(item, List.copyOf(parsed), itemsPerTrade));
    }

    /**
     * Removes a recipe.
     *
     * <p>Exists for tests and for a pack that wants to withdraw an offering it dislikes. Nothing in
     * the mod calls it, which is exactly why it is here rather than left out: a registry that can
     * only grow cannot be tested in isolation, because every test that registers something would
     * leave it registered for the next one.</p>
     */
    public static void unregister(Item item) {
        BY_ITEM.remove(item);
    }

    /** The recipe an item stack buys into, or {@code null} if the price list does not take it. */
    public static Recipe recipeFor(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BY_ITEM.get(stack.getItem());
    }

    /** The recipe for a bare item, for tooltips and tests. */
    public static Recipe recipeFor(Item item) {
        return BY_ITEM.get(item);
    }

    public static List<Item> offerings() {
        return List.copyOf(BY_ITEM.keySet());
    }

    /**
     * What the altar would offer for this recipe: every adaptation a selector reaches that this
     * wheel has not finished.
     *
     * <p>Exact selectors are resolved directly, family selectors by walking the registry. See the
     * class comment for why the two cannot be treated alike.</p>
     *
     * <p>Never offers {@code ADVERSITY}. It is a survival challenge rather than an adaptation, and
     * being handed one would start a timed event the player neither asked for nor could have paid
     * for — the price would be an item, and what it buys would be a fight.</p>
     */
    public static List<String> candidates(PlayerAdaption data, int tier, Recipe recipe) {
        if (recipe == null) {
            return List.of();
        }
        List<String> pool = new ArrayList<>();
        for (Selector selector : recipe.selectors()) {
            if (selector.isExact()) {
                if (!isFinished(data, selector.text())) {
                    pool.add(selector.text());
                }
                continue;
            }
            for (AdaptationDefinition definition : AdaptationRegistry.allDefinitions()) {
                String concept = definition.concept();
                if (selector.matches(concept) && !isFinished(data, concept) && !pool.contains(concept)) {
                    pool.add(concept);
                }
            }
        }
        pool.remove(Concepts.ADVERSITY);
        pool.sort(orderingFor(tier));
        return List.copyOf(pool);
    }

    /**
     * Whether this wheel is already done with a concept.
     *
     * <p>Either kind of "done": a one-time adaptation is in the wheel's {@code adapted} set, and a
     * leveled one has a level at all. Both are written to the wheel's own {@code wheel_data}, whether
     * the adaptation was bought here or earned by suffering through it, which is why reading this
     * off the fed stack covers both cases — the block cannot tell them apart and does not need to.</p>
     */
    public static boolean isFinished(PlayerAdaption data, String concept) {
        return data.isAdapted(concept) || data.level(concept) > 0;
    }

    /**
     * The pool order. Revealed families first, then domain, then concept name — see the class
     * comment; the trailing clauses exist for the client's index-based selection, not for taste.
     */
    private static Comparator<String> orderingFor(int tier) {
        return Comparator
                .<String>comparingInt(concept -> WheelTier.familyUnlocked(concept, tier) ? 0 : 1)
                .thenComparing(DomainExchange::domainNameOf)
                .thenComparing(Comparator.naturalOrder());
    }

    /** Empty for an unregistered concept, which is how exact-selector entries sort last. */
    private static String domainNameOf(String concept) {
        AdaptationDefinition definition = AdaptationRegistry.get(concept);
        return definition == null ? "" : definition.domain().name();
    }

    /**
     * What an adaptation costs in whole levels of the player's own experience.
     *
     * <p>Four prices, and the shape of them is the whole design: the price tracks how much of the
     * wheel's progression the thing is worth, so an adaptation the wheel would have spent minutes
     * on costs more than one it grants outright.</p>
     *
     * <pre>
     *   1  a one-time adaptation -- an environment, a movement discomfort, a debuff
     *   2  a leveled adaptation  -- a damage type, a fist tier, Mine_Labor, Combat_Cooldown
     *   3  Drop_NPC_&lt;mob&gt;       -- what a mob leaves behind, which is earned by killing it
     *   4  Existence_ / Mutation_ / Dimension_Destroy -- a boss, or a milestone
     * </pre>
     *
     * <p><b>Nothing is ever free.</b> The floor is one level, which is the rounded-up form of the
     * half-level minimum: a price that could reach zero would make the altar a place to stand
     * rather than a trade, which is the thing it stopped being.</p>
     *
     * <p>Whole levels rather than fractions because vanilla experience is an integer and spending
     * part of a level means keeping fractional progress the game has nowhere to store.</p>
     */
    public static int priceFor(String concept) {
        if (concept == null) {
            return 0;
        }
        if (concept.startsWith(Concepts.EXISTENCE_PREFIX)
                || concept.startsWith(Concepts.MUTATION_PREFIX)
                || concept.equals(Concepts.DIMENSION_DESTROY)) {
            return 4;
        }
        if (concept.startsWith(Concepts.DROP_PREFIX)) {
            return 3;
        }
        return Concepts.isLevelBased(concept) ? 2 : 1;
    }

    /** The most expensive thing on offer, so the screen can scale its readout against something. */
    public static int dearestPrice(List<String> pool) {
        int dearest = 0;
        for (String concept : pool) {
            dearest = Math.max(dearest, priceFor(concept));
        }
        return dearest;
    }

    /** The domain of an adaptation, or {@link AdaptationDomain#SPECIAL} for an unregistered one. */
    public static AdaptationDomain domainOf(String concept) {
        AdaptationDefinition definition = AdaptationRegistry.get(concept);
        return definition == null ? AdaptationDomain.SPECIAL : definition.domain();
    }
}