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
 * What the Domain Stone will trade for, and what it will not.
 *
 * <p>The stone used to hand an adaptation over for free, on a cooldown, drawn at random from
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
 * looked up in the registry: it names the concept, and the concept is offered if the wheel has
 * not finished it.</p>
 *
 * <p>The consequence worth knowing: an exact selector can sell something the registry cannot
 * describe, and therefore something that sorts last by domain name, because
 * {@link AdaptationRegistry#get} returns {@code null} for it. That is only a cosmetic ordering
 * effect, and paying it is much cheaper than a recipe that quietly trades nothing.</p>
 *
 * <h2>The tier does not filter</h2>
 *
 * <p>Everything the wheel has not already finished is fair game, whatever the wheel has revealed.
 * That is the original behaviour — the stone preferred a family the wheel had <em>not</em> reached
 * yet, which was the whole reason the block existed — and it also keeps the subsystem honest about
 * the rest of the mod: tiers <em>reveal</em>, they never restrict, because the wheel is omnipotent
 * so a later tier is only ever a larger one. A block that refused to sell a concept until the
 * wheel could analyse it would impose the one rule the tier system does not have, and would make
 * the price of the item irrelevant to what it buys.</p>
 *
 * <h2>Ordering is total</h2>
 *
 * <p>Revealed families first, then domain, then concept name. The first clause keeps the shortcut
 * towards what comes next. The rest exists because the client selects a candidate by its
 * <em>index</em> in this list: two builds of the same pool in a different order is a stone that
 * grants whatever the server's index happened to point at, which is not what the player clicked.</p>
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
     * What one item buys into, and how much of it one level costs.
     *
     * @param perLevel how many of the item a single level costs. The rule is one per level; the
     *                 field exists because a recipe selling something eight levels deep should be
     *                 able to say so rather than quietly price itself at the cheapest rate.
     */
    public record Recipe(Item item, List<Selector> selectors, int perLevel) {

        public int costFor(int levels) {
            return Math.max(0, levels) * Math.max(1, perLevel);
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

        // Damage types are leveled to eight, so they cost more per level than a one-time does.
        register(Items.NETHER_WART, 1, "Type_WITHER");
        register(Items.BLAZE_POWDER, 2, "Type_FIRE");
        register(Items.SNOW_BLOCK, 2, "Type_FREEZE");
        // The catch-all. Everything, all sixteen, at four per level.
        register(Items.NETHER_STAR, 4, "Type_*");
    }

    /**
     * Registers one offering item. A later registration for the same item replaces an earlier one,
     * so a pack or another mod can retune a recipe without removing the built-in first.
     */
    public static void register(Item item, int perLevel, String... selectors) {
        List<Selector> parsed = new ArrayList<>(selectors.length);
        for (String text : selectors) {
            parsed.add(Selector.of(text));
        }
        BY_ITEM.put(item, new Recipe(item, List.copyOf(parsed), Math.max(1, perLevel)));
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

    /** The recipe an item stack buys into, or {@code null} if the stone does not take it. */
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
     * What the stone would offer for this recipe: every adaptation the wheel has not finished that
     * a selector reaches.
     *
     * <p>Exact selectors are resolved directly, family selectors by walking the registry. See the
     * class comment for why the two cannot be treated alike.</p>
     *
     * <p>Never offers {@code ADBERSITY}. It is a survival challenge rather than an adaptation, and
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

    private static boolean isFinished(PlayerAdaption data, String concept) {
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
     * How many levels can be bought at once.
     *
     * <p>A one-time adaptation is worth exactly one, and asking for more must not quietly consume
     * eight items for the same single grant — hence the collapse rather than a refusal.</p>
     */
    public static int maxLevelsFor(String concept) {
        return Concepts.isLevelBased(concept) ? PlayerAdaption.MAX_LEVEL : 1;
    }

    /** Clamps a requested level count into what the chosen concept can actually take. */
    public static int clampLevels(String concept, int requested) {
        return Math.max(1, Math.min(maxLevelsFor(concept), requested));
    }

    /** The domain of an adaptation, or {@link AdaptationDomain#SPECIAL} for an unregistered one. */
    public static AdaptationDomain domainOf(String concept) {
        AdaptationDefinition definition = AdaptationRegistry.get(concept);
        return definition == null ? AdaptationDomain.SPECIAL : definition.domain();
    }
}