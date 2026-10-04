package ru.adaptionwheel.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class AdaptionConfig {

    private static final double[] DEFENSE_REDUCTION = {5, 10, 15, 20, 25, 30, 45, 60};
    private static final double[] DEFENSE_HEAL_RATIO = {0, 0, 0, 0, 10, 15, 30, 50};
    private static final double[] INJURE_HEAL_SPEED = {1, 2, 3, 4, 6, 8, 10, 15};
    private static final double[] OFFENSE_DAMAGE_BONUS = {2, 3, 5, 7, 9, 10, 13, 15};
    private static final double[] OFFENSE_CRIT = {5, 10, 15, 20, 25, 30, 35, 50};
    private static final double[] OFFENSE_ARMOR_PEN = {12, 25, 37, 50, 62, 75, 87, 100};
    private static final double[] DROP_RATE_INCREASE = {100, 300, 500, 1000, 1500, 3000, 5000, 10000};
    private static final double[] DROP_RATE_KILLS = {1, 5, 10, 30, 100, 300, 500, 1000};

    private static final double[] DEFAULT_FIST_TIER_COST = {1.0, 1.5, 2.5, 4.0, 5.0};

    private static final double[] DEFAULT_FIST_DAMAGE_TIER_KILL_COST = {1.0, 2.0, 3.5, 6.0, 10.0};

    private static final double[] DEFAULT_FIST_DAMAGE_TIER_MULTIPLIER = {1.0, 2.0, 4.0, 8.0, 16.0};

    public static final ModConfigSpec.ConfigValue<Boolean> WHEEL_TIERS_ENABLED;

    public static final ModConfigSpec.ConfigValue<Boolean> SHEDDING_ENABLED;
    public static final ModConfigSpec.ConfigValue<Integer> SHEDDING_RELEASE_BASE_TICKS;
    public static final ModConfigSpec.ConfigValue<Integer> SHEDDING_RELEASE_TICKS_PER_LEVEL;
    public static final ModConfigSpec.ConfigValue<Double> SHEDDING_REATTACH_TIMER_FACTOR;
    public static final ModConfigSpec.ConfigValue<Boolean> SHEDDING_PROTECT_TIER;

    public static final ModConfigSpec.ConfigValue<Boolean> BRAZIER_HEAL_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> BRAZIER_HEAL_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<Boolean> TOTEM_ENABLED;
    public static final ModConfigSpec.ConfigValue<Integer> TOTEM_ACCELERATION_TICKS;
    public static final ModConfigSpec.ConfigValue<Boolean> ALTAR_ENABLED;
    public static final ModConfigSpec.ConfigValue<Boolean> ALTAR_TRADE_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> TRADE_COST_GROWTH;
    public static final ModConfigSpec.ConfigValue<Integer> TRADE_MAX_ITEMS;
    public static final ModConfigSpec.ConfigValue<Integer> TRADE_MAX_XP;

    public static final ModConfigSpec.ConfigValue<Boolean> TRANSFER_ENABLED;

    public static final ModConfigSpec.ConfigValue<Boolean> RESONANCE_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> RESONANCE_BLOCKS;
    public static final ModConfigSpec.ConfigValue<Boolean> RESONANCE_PARTICLES;

    public static final ModConfigSpec.ConfigValue<Integer> MAX_SIMULTANEOUS_ADAPTATIONS;
    public static final ModConfigSpec.ConfigValue<Integer> ADAPTATION_HEAL_AMOUNT;
    public static final ModConfigSpec.ConfigValue<Boolean> RESET_ADAPTATIONS_ON_DEATH;
    public static final ModConfigSpec.ConfigValue<Boolean> KEEP_DATA_ON_UNEQUIP;

    public static final ModConfigSpec.ConfigValue<Double> ENV_ANALYSIS_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> DEBUFF_ANALYSIS_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> DEFENSE_ANALYSIS_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> DEFENSE_ACCELERATION_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> OFFENSE_ANALYSIS_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> OFFENSE_ACCELERATION_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> ADVERSITY_COOLDOWN_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> EXISTENCE_REQUIRED_SECONDS;
    public static final ModConfigSpec.ConfigValue<Double> EXISTENCE_PROXIMITY_BLOCKS;
    public static final ModConfigSpec.ConfigValue<Double> FALL_ANALYSIS_SECONDS;

    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_DEFENSE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_OFFENSE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_ENVIRONMENT;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_DEBUFF;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_ADVERSITY;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_EXISTENCE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_LOOT;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_CHAOS_GUARDIAN;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_MOVEMENT;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_MINING;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_COMBAT;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_PERCEPTION;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_MUTATION_AQUATIC;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_MUTATION_IMPACT;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_MUTATION_SEA_EYE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_MUTATION_FLIGHT;
    public static final ModConfigSpec.DoubleValue FLIGHT_ALTITUDE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_INVENTORY_ADAPTATION;
    public static final ModConfigSpec.DoubleValue INVENTORY_ADAPTATION_SECONDS;
    public static final ModConfigSpec.ConfigValue<Boolean> FIST_DAMAGE_ENABLED;
    public static final ModConfigSpec.DoubleValue FIST_DAMAGE_FIRST_KILLS;
    public static final ModConfigSpec.DoubleValue FIST_DAMAGE_KILL_GROWTH;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> FIST_DAMAGE_TIER_KILL_COST;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> FIST_DAMAGE_TIER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue FIST_DAMAGE_BASE;
    public static final ModConfigSpec.DoubleValue FIST_DAMAGE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue FIST_DAMAGE_PER_ADAPTATION;

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> MINING_SPEED_LEVELS;

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> COOLDOWN_RECOVERY_LEVELS;

    public static final ModConfigSpec.ConfigValue<Boolean> FIST_ENABLED;
    public static final ModConfigSpec.ConfigValue<Boolean> FIST_HARVEST_WITHOUT_TOOL;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> FIST_TIER_COST_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<Double> FIST_SPEED_SCALE;
    public static final ModConfigSpec.ConfigValue<Integer> FIST_FIRST_LEVEL_BLOCKS;
    public static final ModConfigSpec.ConfigValue<Double> FIST_LEVEL_COST_GROWTH;
    public static final ModConfigSpec.ConfigValue<Boolean> FIST_INSTABREAK_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> FIST_INSTABREAK_SPEED;
    public static final ModConfigSpec.ConfigValue<Boolean> FIST_INSTABREAK_DEFAULT_ON;
    public static final ModConfigSpec.ConfigValue<Boolean> FIST_LUCK_ENABLED;

    public static final ModConfigSpec.ConfigValue<Boolean> DARKNESS_LIGHTMAP_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> DARKNESS_LIGHTMAP_FLOOR;

    public static final ModConfigSpec.ConfigValue<Double> IMPACT_STOMP_MIN_FALL;
    public static final ModConfigSpec.ConfigValue<Double> IMPACT_STOMP_DAMAGE_PER_BLOCK;
    public static final ModConfigSpec.ConfigValue<Double> IMPACT_STOMP_RADIUS;

    public static final ModConfigSpec.ConfigValue<Double> AQUATIC_SWIM_SPEED_BONUS;

    public static final ModConfigSpec.ConfigValue<Boolean> SKILL_ISSUE_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> SKILL_ISSUE_RADIUS;
    public static final ModConfigSpec.ConfigValue<Double> SKILL_ISSUE_STRENGTH;
    public static final ModConfigSpec.ConfigValue<Double> SKILL_ISSUE_MAX_DISTANCE;

    public static final ModConfigSpec.ConfigValue<Boolean> DIMENSION_DESTROY_ENABLED;
    public static final ModConfigSpec.ConfigValue<Integer> DIMENSION_DESTROY_REQUIRED;

    public static final ModConfigSpec.ConfigValue<Integer> CONTACT_IMMUNITY_LEVEL;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> CONTACT_PROTECTION_LEVELS;

    public static final ModConfigSpec.ConfigValue<Boolean> INSTANT_STARVE;
    public static final ModConfigSpec.ConfigValue<Boolean> RAPID_FALL_ANALYSIS;
    public static final ModConfigSpec.ConfigValue<Integer> VOICE_VOLUME;

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> DEFENSE_REDUCTION_LEVELS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> DEFENSE_HEAL_RATIO_LEVELS;

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> REGEN_SPEED_LEVELS;
    public static final ModConfigSpec.ConfigValue<Double> REGEN_HP_THRESHOLD;
    public static final ModConfigSpec.ConfigValue<Boolean> THERMAL_REGEN_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> THERMAL_REGEN_HP_PER_SECOND;

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> OFFENSE_DAMAGE_LEVELS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> OFFENSE_CRIT_LEVELS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> OFFENSE_ARMOR_PEN_LEVELS;
    public static final ModConfigSpec.ConfigValue<Double> DIMENSION_SLASH_HP_PERCENT;
    public static final ModConfigSpec.ConfigValue<Double> DIMENSION_SLASH_CHANCE;

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> LOOT_BONUS_LEVELS;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> LOOT_KILL_THRESHOLDS;

    public static final ModConfigSpec.ConfigValue<Double> BONUS_DAMAGE_PCT;
    public static final ModConfigSpec.ConfigValue<Double> BONUS_CRIT_PCT;
    public static final ModConfigSpec.ConfigValue<Double> BONUS_HP_PCT;
    public static final ModConfigSpec.ConfigValue<Double> BONUS_ARMOR_FLAT;

    public static final ModConfigSpec.ConfigValue<Double> EXISTENCE_REFLECT_MULTIPLIER;

    public static final ModConfigSpec.ConfigValue<Boolean> HUD_ENABLED;
    public static final ModConfigSpec.ConfigValue<Double> HUD_SCALE;
    public static final ModConfigSpec.ConfigValue<Integer> HUD_OFFSET_X;
    public static final ModConfigSpec.ConfigValue<Integer> HUD_OFFSET_Y;
    public static final ModConfigSpec.ConfigValue<Integer> HUD_OPACITY;
    public static final ModConfigSpec.ConfigValue<Boolean> HUD_SHOW_HISTORY;
    public static final ModConfigSpec.ConfigValue<Boolean> WHEEL_ABOVE_HEAD;
    public static final ModConfigSpec.ConfigValue<Double> WHEEL_SIZE;
    public static final ModConfigSpec.ConfigValue<Boolean> ENABLE_WHEEL_PARTICLES;

    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        ModConfigSpec.Builder s = new ModConfigSpec.Builder();

        s.comment("--- Wheel of Adaptation: Core ---").push("core");

        MAX_SIMULTANEOUS_ADAPTATIONS = s
                .comment("How many analysis processes can run at the same time.")
                .defineInRange("maxConcurrentAnalysis", 5, 1, 20);
        ADAPTATION_HEAL_AMOUNT = s
                .comment("HP restored each time an adaptation finishes.")
                .defineInRange("healOnComplete", 50, 0, 500);
        RESET_ADAPTATIONS_ON_DEATH = s
                .comment("Wipe all stored adaptations when the wearer dies.")
                .define("clearOnDeath", false);
        KEEP_DATA_ON_UNEQUIP = s
                .comment("Preserve progress on the item itself so it carries over when re-equipped.")
                .define("persistOnItem", true);

        s.pop();

        s.comment("--- Analysis Speed ---").push("timing");

        ENV_ANALYSIS_SECONDS = s
                .comment("Seconds of exposure needed to decode an environmental hazard.")
                .defineInRange("envAnalysisTime", 3.0, 0.1, 300.0);
        DEBUFF_ANALYSIS_SECONDS = s
                .comment("Seconds of exposure needed to decode a status effect.")
                .defineInRange("debuffAnalysisTime", 3.0, 0.1, 300.0);
        DEFENSE_ANALYSIS_SECONDS = s
                .comment("Base seconds to decode a new damage type after first contact.")
                .defineInRange("defenseAnalysisTime", 8.0, 0.1, 300.0);
        DEFENSE_ACCELERATION_SECONDS = s
                .comment("Seconds shaved off a running defense analysis each time the player is hit again.")
                .defineInRange("defenseHitAcceleration", 1.0, 0.0, 60.0);
        OFFENSE_ANALYSIS_SECONDS = s
                .comment("Base seconds to decode a mob's offensive pattern.")
                .defineInRange("offenseAnalysisTime", 8.0, 0.1, 300.0);
        OFFENSE_ACCELERATION_SECONDS = s
                .comment("Seconds shaved off offense analysis per hit landed on the target.")
                .defineInRange("offenseHitAcceleration", 1.0, 0.0, 60.0);
        ADVERSITY_COOLDOWN_SECONDS = s
                .comment("Minimum seconds between two Adversity triggers.")
                .defineInRange("adversityCooldown", 30.0, 1.0, 600.0);
        EXISTENCE_REQUIRED_SECONDS = s
                .comment("Seconds of sustained boss combat after the first hit (deal or receive) before existence adaptation completes.",
                        "Intentionally much slower than a full Lv8 offense analysis (~3 min at defaults).")
                .defineInRange("existenceRequiredSeconds", 240.0, 5.0, 36000.0);
        EXISTENCE_PROXIMITY_BLOCKS = s
                .comment("How close an adapted-progress boss has to stay for its existence timer to keep running.",
                        "Large arenas (e.g. Draconic Evolution's Chaos Guardian) may need a bigger radius.")
                .defineInRange("existenceProximityBlocks", 96.0, 16.0, 512.0);
        FALL_ANALYSIS_SECONDS = s
                .comment("Seconds of fall exposure before the fall adaptation finishes.")
                .defineInRange("fallAnalysisTime", 3.0, 0.1, 300.0);

        s.pop();

        s.comment("--- Module Toggles ---").push("modules");

        ENABLE_DEFENSE = s.comment("Decode incoming damage patterns for reduction.").define("defense", true);
        ENABLE_OFFENSE = s.comment("Decode mob weaknesses for bonus damage and armor penetration.").define("offense", true);
        ENABLE_ENVIRONMENT = s.comment("Decode hazards like lava, drowning, void, darkness, ice, etc.").define("environment", true);
        ENABLE_DEBUFF = s.comment("Decode harmful status effects for immunity.").define("debuff", true);
        ENABLE_ADVERSITY = s.comment("Allow the Adversity mechanism (survive lethal blow → mass analysis).").define("adversity", true);
        ENABLE_EXISTENCE = s.comment("Allow existence adaptation against bosses (full immunity to boss + attacks).").define("existence", true);
        ENABLE_LOOT = s.comment("Decode drop tables of mobs for extra loot rolls.").define("loot", true);
        ENABLE_CHAOS_GUARDIAN = s.comment("Draconic Evolution compat: dedicated adaptation to the Chaos Guardian and all its attacks.",
                        "Once Existence_ChaosGuardian is decoded: full immunity to the guardian, its parts,",
                        "fireballs, laser (including the charged twin beam that normally bypasses damage events),",
                        "summoned guardian withers and crystals. Also enables reflection and minion inheritance.")
                .define("chaosGuardian", true);
        ENABLE_MOVEMENT = s.comment("Decode movement restrictions imposed by blocks:",
                        "soul sand, honey block, powder snow, sweet berry bushes and bubble columns.",
                        "Exposure starts the usual analysis task; completion removes the restriction.")
                .define("movement", true);
        ENABLE_MINING = s.comment("Decode mining labor: breaking blocks trains a permanent mining speed bonus (Lv1-8).")
                .define("mining", true);
        ENABLE_COMBAT = s.comment("Decode combat discomforts: the 1.9 attack cooldown penalty",
                        "(leveled reduction, MAX ignores it) and axe shield-disabling (one-time immunity).")
                .define("combat", true);
        ENABLE_PERCEPTION = s.comment("Decode perception limitations: steady gaze removes the hurt-camera shake.")
                .define("perception", true);

        s.pop();

        s.comment("--- Wheel Awakening ---").push("wheelTiers");
        WHEEL_TIERS_ENABLED = s.comment("The wheel has tiers of its own, reached by holding more",
                        "adaptations. Each tier REVEALS another family of adaptations -- Contact,",
                        "Offense, Plunder, Existence -- so what the wheel can adapt to grows.",
                        "A tier never takes anything away and never costs anything: the wheel is",
                        "meant to be omnipotent, so a later tier is only ever a larger one.")
                .define("enabled", true);
        s.pop();

        s.comment("--- Shedding ---").push("shedding");
        SHEDDING_ENABLED = s.comment("A player may deliberately shed (give up) one of their adaptations",
                        "for a Wild Release burst. Nothing is lost for good: the concept becomes",
                        "re-analysable immediately, and at a fraction of the original time, because",
                        "the wheel remembers what it already worked out. Shedding is a swap, not a price.",
                        "Deliberately there is no downside anywhere in this feature: the point of the",
                        "wheel is that it can do anything, so acting on it must never be a trap.")
                .define("enabled", true);
        SHEDDING_RELEASE_BASE_TICKS = s.comment("Wild Release duration at level 1, in ticks")
                .defineInRange("releaseBaseTicks", 200, 20, 12000);
        SHEDDING_RELEASE_TICKS_PER_LEVEL = s.comment("Extra Wild Release ticks per level of a shed",
                        "leveled adaptation")
                .defineInRange("releaseTicksPerLevel", 40, 0, 1200);
        SHEDDING_REATTACH_TIMER_FACTOR = s.comment("How much of the original analysis time a re-shed",
                        "concept costs the second time, as a fraction. 0.35 means it re-adapts in",
                        "about a third of the time; 1.0 would make shedding pointless.")
                .defineInRange("reattachTimerFactor", 0.35D, 0.05D, 1.0D);
        SHEDDING_PROTECT_TIER = s.comment("Refuse a shed that would drop the player's wheel tier.",
                        "Without this, shedding the last adaptation of a family re-locks that whole",
                        "family -- taking something away, which this mod does not do. The refusal is",
                        "reported as a message, not silently.")
                .define("protectTier", true);
        s.pop();

        s.comment("--- Ritual Blocks ---").push("ritual");
        BRAZIER_HEAL_ENABLED = s.comment("The Adaptation Brazier heals a wheel-wearer standing",
                        "within 5 blocks. A flat amount per second per brazier, so a field of them",
                        "is worth more than one but never more than is missing.")
                .define("brazierHealEnabled", true);
        BRAZIER_HEAL_PER_SECOND = s.defineInRange("brazierHealPerSecond", 0.5D, 0.0D, 20.0D);
        TOTEM_ENABLED = s.comment("The Wheel Totem advances the analyses already running for a",
                        "wheel-wearer within 6 blocks. It grants nothing, so it is worth nothing to",
                        "a player with nothing to analyse.")
                .define("totemEnabled", true);
        TOTEM_ACCELERATION_TICKS = s.defineInRange("totemAccelerationTicks", 20, 0, 400);
        ALTAR_ENABLED = s.comment("The Resonance Altar adds one rung to a wheel-wearer's Resonance",
                        "within 8 blocks. It raises the rung rather than widening the search, so",
                        "stacking altars cannot stand in for other players.")
                .define("altarEnabled", true);
        ALTAR_TRADE_ENABLED = s.comment("Right-clicking the altar opens a trade: put an offering",
                        "item in the upper slot and the wheel in the other, and it offers what that",
                        "item opens up. Two kinds of offering, one list: an item on the stone's",
                        "price list buys the adaptation it names (ru.adaptionwheel.server.",
                        "DomainExchange), and a mob's own drop buys that mob's adaptations -- the",
                        "ability to hurt it, and the ability to take more from it (data/adaptionwheel/",
                        "domain_altar/<mob_path>.json). The item is the price, plus experience",
                        "levels, and an adaptation the wheel has already finished is not offered at",
                        "all. This is the altar's trade, so it is switched off on its own; the",
                        "resonance aura above is a separate switch.")
                .define("altarTradeEnabled", true);
        TRADE_COST_GROWTH = s.comment("One purchase is ONE level of an adaptation, not the whole",
                        "thing, so an adaptation that goes to level 8 is bought eight times. This is",
                        "what each of those purchases costs more than the last: the price of a level",
                        "is multiplied by this, once per level already held, so 2.0 doubles it. The",
                        "floor is the first level's own price -- one item for a mob's drop, or the",
                        "recipe's price -- and the ladder by kind is 1/2/3/4 experience levels.",
                        "Growth stops at the two caps below, which is what keeps the last levels",
                        "reachable instead of astronomically priced.")
                .defineInRange("tradeCostGrowth", 2.0D, 1.0D, 8.0D);
        TRADE_MAX_ITEMS = s.comment("Ceiling on the item side of one purchase's price.")
                .defineInRange("tradeMaxItems", 32, 1, 64);
        TRADE_MAX_XP = s.comment("Ceiling on the experience side of one purchase's price, in",
                        "whole levels -- the same integer vanilla stores, so part of a level has",
                        "nowhere to live and is never charged.")
                .defineInRange("tradeMaxXp", 30, 1, 128);
        s.pop();

        s.comment("--- Adaptation Transfer ---").push("transfer");
        TRANSFER_ENABLED = s.comment("Right-clicking another player with the wheel in hand hands",
                        "them your most developed adaptation that they do not have. Repeated",
                        "clicks walk down your own list. Nothing is invented: what they gain is",
                        "exactly what you gave up, at the same level.")
                .define("enabled", true);
        s.pop();

        s.comment("--- Resonance ---").push("resonance");
        RESONANCE_ENABLED = s.comment("Adapted players within range strengthen each other. The buff",
                        "rung counts OTHER wheel-wearers nearby and saturates at its maximum, so a",
                        "large group is not proportionally stronger than a small one.")
                .define("enabled", true);
        RESONANCE_BLOCKS = s.comment("Radius in blocks")
                .defineInRange("blocks", 12.0D, 2.0D, 64.0D);
        RESONANCE_PARTICLES = s.comment("Draw a small burst at a wearer's feet when their rung rises")
                .define("particles", true);
        s.pop();

        s.comment("--- Discomfort Scaling ---").push("discomfortScaling");

        MINING_SPEED_LEVELS = s.comment("Mining speed bonus % at each Mine_Labor level (1-8).",
                        "Early levels are deliberately strong enough to feel (a bare +5 % read as 'not working').")
                .defineList("miningSpeedPct", doubleList(new double[]{15, 30, 50, 75, 105, 140, 185, 240}), AdaptionConfig::isDouble);
        COOLDOWN_RECOVERY_LEVELS = s.comment("Share of the attack-cooldown damage penalty removed per Combat_Cooldown level.",
                        "100 = fully ignore the cooldown; values are % of the missing charge restored.",
                        "Only visible while attacking BELOW full charge (full swings have no penalty to remove).")
                .defineList("cooldownRecoveryPct", doubleList(new double[]{25, 42, 56, 70, 82, 91, 97, 100}), AdaptionConfig::isDouble);

        s.pop();

        s.comment("--- Combo Mutations ---").push("mutations");

        ENABLE_MUTATION_AQUATIC = s.comment("Aquatic Mastery: unlocked by Env_Liquid + Env_Drowning.",
                        "Grants a large permanent swim speed boost and faster underwater mining.")
                .define("aquaticMastery", true);
        ENABLE_MUTATION_IMPACT = s.comment("Impact Mastery: unlocked by Env_FallDamage + Env_Knockback.",
                        "Landing from great heights unleashes a damaging shockwave.")
                .define("impactMastery", true);
        IMPACT_STOMP_MIN_FALL = s.comment("Minimum fall distance (blocks) to trigger the Impact shockwave.")
                .defineInRange("impactMinFallDistance", 6.0, 1.0, 200.0);
        IMPACT_STOMP_DAMAGE_PER_BLOCK = s.comment("Shockwave damage per block fallen above the threshold.")
                .defineInRange("impactDamagePerBlock", 2.0, 0.0, 100.0);
        IMPACT_STOMP_RADIUS = s.comment("Shockwave radius in blocks.")
                .defineInRange("impactRadius", 3.5, 1.0, 32.0);
        AQUATIC_SWIM_SPEED_BONUS = s.comment("Flat swim speed bonus added by Aquatic Mastery.",
                        "(Base water swim acceleration is ~0.02; NeoForge swim-speed attribute scales it.)")
                .defineInRange("aquaticSwimSpeedBonus", 2.5, 0.0, 20.0);
        ENABLE_MUTATION_SEA_EYE = s.comment("Sea Eye: unlocked by Env_Liquid + Env_Drowning + Env_Lava.",
                        "Removes the liquid fog the camera reports: clear sight underwater, in lava",
                        "and in any modded liquid whose fog comes from the same camera query.")
                .define("seaEye", true);
        ENABLE_MUTATION_FLIGHT = s.comment("Flight: unlocked at altitude 310+ together with Contact_phantom Lv.8",
                        "and an adaptation to levitation. Grants creative-style flight.")
                .define("flight", true);
        FLIGHT_ALTITUDE = s.comment("Minimum Y (blocks) the wheel must have reached for Flight.")
                .defineInRange("flightAltitude", 310.0, 1.0, 4096.0);
        ENABLE_INVENTORY_ADAPTATION = s.comment("Inventory adaptation: fill every one of the 36 slots plus",
                        "the offhand to earn a 50 slot personal cache (key: the mod's cache key).",
                        "The extra slots are their OWN storage, never the vanilla inventory array,",
                        "so nothing you are carrying can be overwritten by them.")
                .define("inventoryAdaptation", true);
        INVENTORY_ADAPTATION_SECONDS = s.comment("Seconds of a full inventory for the analysis to finish.")
                .defineInRange("inventoryAnalysisSeconds", 60.0, 1.0, 3600.0);

        s.pop();

        s.comment("--- Hard Fist (the punching fist, five material stages) ---").push("fistDamage");
        FIST_DAMAGE_ENABLED = s.comment("The punching fist: unlocked by killing a hostile mob with a bare",
                        "hand -- a KILL, with the last blow landed by the hand and nothing in it that adds",
                        "attack damage. Every level is trained the same way, and the stages run",
                        "wood > stone > iron > diamond > netherite, 8 levels each, a stage opening",
                        "only when the previous one is maxed. The bonus is ADDED to a weapon's damage",
                        "rather than replacing it, so a Sword of Extermination stacks on top.")
                .define("enabled", true);
        FIST_DAMAGE_FIRST_KILLS = s.comment("Bare-handed hostile kills for level 1 of the first stage.")
                .defineInRange("firstLevelKills", 3.0, 1.0, 10000.0);
        FIST_DAMAGE_KILL_GROWTH = s.comment("Each level costs this multiple more kills than the last.")
                .defineInRange("levelCostGrowth", 1.6, 1.0, 10.0);
        FIST_DAMAGE_TIER_KILL_COST = s.comment("Per-stage kill cost multiplier, Wood > Stone > Iron >",
                        "Diamond > Netherite. Must have exactly one entry per stage; a wrong-length",
                        "list is ignored and the defaults are used instead.")
                .defineList("tierKillCost", doubleList(DEFAULT_FIST_DAMAGE_TIER_KILL_COST),
                        AdaptionConfig::isDouble);
        FIST_DAMAGE_TIER_MULTIPLIER = s.comment("Per-stage damage multiplier, same order. This is what makes",
                        "the later stages hit for dramatically more than the early ones.")
                .defineList("tierDamageMultiplier",
                        doubleList(DEFAULT_FIST_DAMAGE_TIER_MULTIPLIER),
                        AdaptionConfig::isDouble);
        FIST_DAMAGE_BASE = s.comment("Flat bonus damage of a level-1 punch, before the stage multiplier.")
                .defineInRange("baseDamage", 1.0, 0.0, 1000.0);
        FIST_DAMAGE_PER_LEVEL = s.comment("Bonus damage added per level above the first.")
                .defineInRange("damagePerLevel", 0.75, 0.0, 1000.0);
        FIST_DAMAGE_PER_ADAPTATION = s.comment("The fist also scales with how much the wheel knows: each",
                        "adaptation adds this fraction of the level bonus (0.04 = +4% per adaptation).")
                .defineInRange("damagePerAdaptation", 0.04, 0.0, 10.0);
        s.pop();

        s.comment("--- Fist Mastery (Mutation_Fist) ---").push("fistMastery");
        FIST_ENABLED = s.comment("Fist Mastery: max Mine_Labor and break a stone block bare-handed to",
                        "unlock a tool-less fist. The fist then advances through five materials",
                        "(Wood > Stone > Iron > Diamond > Netherite), 8 levels each.")
                .define("enabled", true);
        FIST_HARVEST_WITHOUT_TOOL = s.comment("Let the fist actually collect the blocks it breaks.",
                        "Off = the fist only mines faster but tool-gated blocks still drop nothing.")
                .define("harvestWithoutTool", true);
        FIST_FIRST_LEVEL_BLOCKS = s.comment("Blocks of the current tier's own material needed for its first level.")
                .defineInRange("firstLevelBlocks", 10, 1, 100000);
        FIST_LEVEL_COST_GROWTH = s.comment("Cost growth per level inside a tier (cost *= this each level).")
                .defineInRange("levelCostGrowth", 1.35, 1.0, 10.0);
        FIST_TIER_COST_MULTIPLIER = s.comment("Per-tier cost multiplier, Wood > Stone > Iron >",
                        "Diamond > Netherite. Must have exactly one entry per material; a",
                        "wrong-length list is ignored and repaired.")
                .defineList("tierCostMultiplier", doubleList(DEFAULT_FIST_TIER_COST), AdaptionConfig::isDouble);
        FIST_SPEED_SCALE = s.comment("Multiplier on the vanilla tool speed the fist copies.",
                        "1.0 = exact parity with the matching tool; raise it to make the fist",
                        "outrun its tool equivalent.")
                .defineInRange("fistSpeedScale", 1.0, 0.1, 20.0);
        FIST_INSTABREAK_ENABLED = s.comment("Netherite level 8 grants Instabreak: every breakable block is",
                        "removed in a single tick. Toggled in game with the Instabreak keybind.")
                .define("instabreakEnabled", true);
        FIST_INSTABREAK_SPEED = s.comment("Mining speed used while Instabreak is active. Must exceed the",
                        "hardest block's destroy speed by a wide margin (obsidian is 50).")
                .defineInRange("instabreakSpeed", 20000.0, 100.0, 1000000.0);
        FIST_INSTABREAK_DEFAULT_ON = s.comment("Whether Instabreak starts switched on the moment it is unlocked.")
                .define("instabreakDefaultOn", false);
        FIST_LUCK_ENABLED = s.comment("The fist's luck: every tier that much more of whatever an ore",
                        "drops, and the same multiple of its experience. Scales 1/2/3/5/10x by",
                        "tier and applies only to the blocks in the adaptionwheel:fist_luck",
                        "block tag.")
                .define("luckEnabled", true);
        s.pop();

        s.comment("--- Skill Issue (Combat_SkillIssue) ---").push("skillIssue");
        SKILL_ISSUE_ENABLED = s.comment("Skill Issue: projectiles from adapted bows/crossbows/tridents gently home in,",
                        "but ONLY onto entities whose bodies lie close to the arrow's flight path.",
                        "Shooting into empty space stays completely untouched - no target near the path, no correction.")
                .define("enabled", true);
        SKILL_ISSUE_RADIUS = s.comment("How far (blocks) an entity may sit from the flight path to be catchable.",
                        "'Close' counts after subtracting the entity's own half-width.")
                .defineInRange("radius", 2.0, 0.5, 8.0);
        SKILL_ISSUE_STRENGTH = s.comment("Steering blend per tick: 0 = vanilla flight, 1 = instant lock-on.",
                        "Values around 0.4-0.5 convert a near-miss within a couple of blocks of travel.")
                .defineInRange("strength", 0.45, 0.05, 1.0);
        SKILL_ISSUE_MAX_DISTANCE = s.comment("Maximum distance (blocks) ahead along the path where targets are considered.")
                .defineInRange("maxDistance", 16.0, 2.0, 64.0);
        s.pop();

        s.comment("--- Dimension Destroy (transcendence ultimate) ---").push("dimensionDestroy");
        DIMENSION_DESTROY_ENABLED = s.comment("Port of the original mod's Dimension Destroy: past the adaptation",
                        "threshold, Sword of Extermination swings tear three spatial rifts that sever",
                        "up to five lives each outright. Players are never targeted.")
                .define("enabled", true);
        DIMENSION_DESTROY_REQUIRED = s.comment("Adaptations on the wheel required to unlock Dimension Destroy.",
                        "The original gates it behind Transcendence; this mod uses the same idea.")
                .defineInRange("adaptationsRequired", 450, 1, 1000);
        s.pop();

        s.comment("--- Contact Defense ---").push("contactDefense");

        CONTACT_IMMUNITY_LEVEL = s
                .comment("At which contact level the player gains TOTAL immunity to that mob (no damage, no red flash).",
                        "Set to 0 to disable full immunity entirely; the protection table below still applies.",
                        "At level 8 the protection table reaches 60 % — set this to 9 or higher if you want",
                        "the table to be the ONLY defense mechanism without any hard immunity.")
                .defineInRange("immunityLevel", 0, 0, 64);
        CONTACT_PROTECTION_LEVELS = s
                .comment("Damage reduction % applied per contact level (1-8).",
                        "These are checked BEFORE the immunity level — so even without immunity,",
                        "high-level contact adaptations still provide meaningful protection.")
                .defineList("protectionPerLevel", doubleList(DEFENSE_REDUCTION), AdaptionConfig::isDouble);

        s.pop();

        s.comment("--- Shortcuts ---").push("shortcuts");

        INSTANT_STARVE = s
                .comment("Decode starvation immediately on first tick and refill hunger to full.")
                .define("instantStarve", true);
        RAPID_FALL_ANALYSIS = s
                .comment("Use a short dedicated timer for fall analysis instead of the generic env timer.")
                .define("rapidFall", true);
        VOICE_VOLUME = s
                .comment("Adaptation sound volume (0 = silent, 100 = max).")
                .defineInRange("voiceVolume", 100, 0, 100);
        ENABLE_WHEEL_PARTICLES = s
                .comment("Sparkle particles around the wheel above the head (denser while analyzing).")
                .define("wheelParticles", true);

        s.pop();

        s.comment("--- Defense Scaling (Lv1-8) ---").push("defenseScaling");

        DEFENSE_REDUCTION_LEVELS = s
                .comment("Flat damage reduction % at each defense level against that damage type.")
                .defineList("reductionPct", doubleList(DEFENSE_REDUCTION), AdaptionConfig::isDouble);
        DEFENSE_HEAL_RATIO_LEVELS = s
                .comment("At Lv5+ a fraction of incoming damage is converted to healing.",
                        "Values are % of damage absorbed.")
                .defineList("lifestealPct", doubleList(DEFENSE_HEAL_RATIO), AdaptionConfig::isDouble);

        s.pop();

        s.comment("--- Regeneration ---").push("regeneration");

        REGEN_HP_THRESHOLD = s
                .comment("Below this % of max HP the wheel begins injury analysis.")
                .defineInRange("hpThresholdPct", 50.0, 1.0, 100.0);
        THERMAL_REGEN_ENABLED = s
                .comment("Thermal Mastery mutation: heat-scaled regeneration (requires maxed Type_FIRE + Env_Lava).")
                .define("thermalRegenEnabled", true);
        THERMAL_REGEN_HP_PER_SECOND = s
                .comment("HP per second while standing in lava (scaled down by the heat factor in weaker heat).")
                .defineInRange("thermalRegenHpPerSecond", 2.0, 0.0, 100.0);
        REGEN_SPEED_LEVELS = s
                .comment("HP regenerated per second at each injury level.")
                .defineList("hpPerSecond", doubleList(INJURE_HEAL_SPEED), AdaptionConfig::isDouble);

        s.pop();

        s.comment("--- Offense Scaling (Lv1-8) ---").push("offenseScaling");

        OFFENSE_DAMAGE_LEVELS = s
                .comment("Flat bonus damage added per offense level.")
                .defineList("flatDamageBonus", doubleList(OFFENSE_DAMAGE_BONUS), AdaptionConfig::isDouble);
        OFFENSE_CRIT_LEVELS = s
                .comment("Crit chance % added per offense level.")
                .defineList("critChancePct", doubleList(OFFENSE_CRIT), AdaptionConfig::isDouble);
        OFFENSE_ARMOR_PEN_LEVELS = s
                .comment("Armor penetration % per offense level.")
                .defineList("armorPenPct", doubleList(OFFENSE_ARMOR_PEN), AdaptionConfig::isDouble);
        DIMENSION_SLASH_HP_PERCENT = s
                .comment("Bonus damage as % of target max HP when Dimension Slash triggers (Lv8 offense).")
                .defineInRange("dimensionSlashHpPct", 7.0, 0.0, 100.0);
        DIMENSION_SLASH_CHANCE = s
                .comment("Probability % for Dimension Slash to activate per hit at Lv8 offense.")
                .defineInRange("dimensionSlashChancePct", 6.0, 0.0, 100.0);

        s.pop();

        s.comment("--- Loot Scaling (Lv1-8) ---").push("lootScaling");

        LOOT_BONUS_LEVELS = s
                .comment("Extra loot roll multiplier % at each drop level.",
                        "100 = one extra roll, 1000 = ten extra rolls, etc.")
                .defineList("extraRollPct", doubleList(DROP_RATE_INCREASE), AdaptionConfig::isDouble);
        LOOT_KILL_THRESHOLDS = s
                .comment("Total kills of a mob required to reach each drop level.")
                .defineList("killThresholds", doubleList(DROP_RATE_KILLS), AdaptionConfig::isDouble);

        s.pop();

        s.comment("--- Cumulative Bonuses ---").push("cumulativeBonuses");

        BONUS_DAMAGE_PCT = s
                .comment("% damage bonus multiplied by the total number of completed adaptations.")
                .defineInRange("damagePctPerAdaptation", 0.5, 0.0, 100.0);
        BONUS_CRIT_PCT = s
                .comment("% crit bonus multiplied by the total number of completed adaptations.")
                .defineInRange("critPctPerAdaptation", 0.5, 0.0, 100.0);
        BONUS_HP_PCT = s
                .comment("% max HP bonus multiplied by the total number of completed adaptations.")
                .defineInRange("hpPctPerAdaptation", 0.5, 0.0, 100.0);
        BONUS_ARMOR_FLAT = s
                .comment("Flat armor points added per completed adaptation.")
                .defineInRange("armorPerAdaptation", 0.5, 0.0, 100.0);

        s.pop();

        s.comment("--- Existence Reflection ---").push("existenceReflection");

        EXISTENCE_REFLECT_MULTIPLIER = s
                .comment("Reflected damage multiplier applied to an adapted boss that touches the wearer",
                        "(the original mod reflects contact hits at 3x).")
                .defineInRange("reflectMultiplier", 3.0, 0.0, 100.0);

        s.pop();

        SERVER_SPEC = s.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();

        c.comment("--- Visual Settings ---").push("visual");

        HUD_ENABLED = c.define("showAnalysisHud", true);
        HUD_SCALE = c.defineInRange("hudScale", 0.8, 0.25, 3.0);
        HUD_OFFSET_X = c.defineInRange("hudOffsetX", -8, -500, 500);
        HUD_OFFSET_Y = c.defineInRange("hudOffsetY", 8, -500, 500);
        HUD_OPACITY = c.defineInRange("hudOpacity", 100, 5, 100);
        HUD_SHOW_HISTORY = c.define("showAdaptationLog", true);
        WHEEL_ABOVE_HEAD = c.define("renderWheelAboveHead", true);
        WHEEL_SIZE = c.defineInRange("wheelModelScale", 0.45, 0.1, 2.0);

        c.pop();

        c.comment("--- Adaptation to Darkness (Env_Darkness) ---").push("darkness");
        DARKNESS_LIGHTMAP_ENABLED = c.comment("Light the wearer's surroundings by lifting the lightmap.",
                        "Written on the frame the game already rebuilds it, so it is steady: no",
                        "duration to run out and nothing to flicker. Replaces the night vision",
                        "effect, which blinked out and back once per refresh window.")
                .define("darknessLightmapEnabled", true);
        DARKNESS_LIGHTMAP_FLOOR = c.comment("How bright the darkest areas become. 1.0 is flat white and",
                        "washes out all shading; 0.7 reads as night vision. Anything above this",
                        "floor keeps its own brightness, so torches still read as brighter.")
                .defineInRange("darknessLightmapFloor", 0.72, 0.0, 1.0);
        c.pop();

        CLIENT_SPEC = c.build();
    }

    public static double fistTierCost(int tier) {
        List<? extends Double> configured = FIST_TIER_COST_MULTIPLIER.get();
        if (configured.size() != ru.adaptionwheel.category.FistTiers.TIER_COUNT) {
            repairTierCostTable();
            configured = FIST_TIER_COST_MULTIPLIER.get();
        }
        int index = Math.max(0, Math.min(configured.size() - 1, tier));
        return configured.get(index);
    }

    private static void repairTierCostTable() {
        org.slf4j.LoggerFactory.getLogger("AdaptionWheel/Config").warn(
                "mutations.fistMastery.tierCostMultiplier had {} entries but there are {} fist materials; "
                        + "restoring the default",
                FIST_TIER_COST_MULTIPLIER.get().size(), ru.adaptionwheel.category.FistTiers.TIER_COUNT);
        FIST_TIER_COST_MULTIPLIER.set(doubleList(DEFAULT_FIST_TIER_COST));
    }

    public static int fistKillsForNextLevel(int tier, int currentLevel) {
        double base = Math.max(1.0, FIST_DAMAGE_FIRST_KILLS.get());
        double growth = Math.max(1.0, FIST_DAMAGE_KILL_GROWTH.get());
        double cost = base * Math.pow(growth, Math.max(0, currentLevel - 1))
                * listValue(FIST_DAMAGE_TIER_KILL_COST, tier, DEFAULT_FIST_DAMAGE_TIER_KILL_COST);
        return Math.max(1, (int) Math.round(cost));
    }

    public static float fistDamageTierMultiplier(int tier) {
        return (float) listValue(FIST_DAMAGE_TIER_MULTIPLIER, tier, DEFAULT_FIST_DAMAGE_TIER_MULTIPLIER);
    }

    public static int fistBlocksForNextLevel(int tier, int currentLevel) {
        double base = Math.max(1, FIST_FIRST_LEVEL_BLOCKS.get());
        double growth = Math.max(1.0, FIST_LEVEL_COST_GROWTH.get());
        double cost = base * Math.pow(growth, Math.max(0, currentLevel - 1)) * fistTierCost(tier);
        return Math.max(1, (int) Math.round(cost));
    }

    private static List<? extends Double> doubleList(double[] values) {
        return java.util.stream.DoubleStream.of(values).boxed().toList();
    }

    private static boolean isDouble(Object o) {
        return o instanceof Double;
    }

    public static double defenseReduction(int level) {
        return table(DEFENSE_REDUCTION_LEVELS, level, DEFENSE_REDUCTION);
    }

    public static double contactProtection(int level) {
        return table(CONTACT_PROTECTION_LEVELS, level, DEFENSE_REDUCTION);
    }

    public static double miningSpeedBonus(int level) {
        return table(MINING_SPEED_LEVELS, level, new double[]{15, 30, 50, 75, 105, 140, 185, 240});
    }

    public static double cooldownRecovery(int level) {
        return table(COOLDOWN_RECOVERY_LEVELS, level, new double[]{25, 42, 56, 70, 82, 91, 97, 100});
    }

    public static double defenseHealRatio(int level) {
        return table(DEFENSE_HEAL_RATIO_LEVELS, level, DEFENSE_HEAL_RATIO);
    }

    public static double regenSpeed(int level) {
        return table(REGEN_SPEED_LEVELS, level, INJURE_HEAL_SPEED);
    }

    public static double offenseDamageBonus(int level) {
        return table(OFFENSE_DAMAGE_LEVELS, level, OFFENSE_DAMAGE_BONUS);
    }

    public static double offenseCrit(int level) {
        return table(OFFENSE_CRIT_LEVELS, level, OFFENSE_CRIT);
    }

    public static double offenseArmorPen(int level) {
        return table(OFFENSE_ARMOR_PEN_LEVELS, level, OFFENSE_ARMOR_PEN);
    }

    public static double lootBonus(int level) {
        return table(LOOT_BONUS_LEVELS, level, DROP_RATE_INCREASE);
    }

    public static double lootKills(int level) {
        return table(LOOT_KILL_THRESHOLDS, level, DROP_RATE_KILLS);
    }

    public static int dropLevelFromKills(int kills) {
        int level = 0;
        for (int lv = 1; lv <= 8; lv++) {
            if (kills >= lootKills(lv)) {
                level = lv;
            }
        }
        return level;
    }

    private static double table(ModConfigSpec.ConfigValue<List<? extends Double>> config, int level, double[] fallback) {
        if (level < 1) {
            return 0;
        }
        List<? extends Double> values = config.get();
        int idx = Math.min(level - 1, values.size() - 1);
        if (idx < 0) {
            return 0;
        }
        if (values.size() < 8) {
            return fallback[Math.min(level - 1, fallback.length - 1)];
        }
        return values.get(idx);
    }

    /**
     * Reads one entry out of a per-tier list, falling back to the shipped defaults.
     *
     * <p>A wrong-length list is ignored rather than clamped into place: clamping would silently give
     * netherite the diamond entry, and a config typo should cost the player their own numbers, not
     * quietly hand them a different ladder.
     */
    private static double listValue(ModConfigSpec.ConfigValue<List<? extends Double>> config, int index,
            double[] fallback) {
        if (index < 0) {
            return 0;
        }
        List<? extends Double> values = config.get();
        if (values == null || values.size() != fallback.length) {
            return fallback[Math.min(index, fallback.length - 1)];
        }
        return values.get(Math.min(index, values.size() - 1));
    }

    private AdaptionConfig() {
    }
}
