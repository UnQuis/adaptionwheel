package ru.adaptionwheel.test;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import ru.adaptionwheel.AdaptionWheel;
import ru.adaptionwheel.adapt.AdaptationDefinition;
import ru.adaptionwheel.adapt.AdaptationRegistry;
import ru.adaptionwheel.category.CombatFistTiers;
import ru.adaptionwheel.category.Concepts;
import ru.adaptionwheel.data.LegacyConcepts;
import ru.adaptionwheel.data.PlayerAdaption;
import ru.adaptionwheel.server.AdaptionEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins the guarantees behind "eat the All Adaptation item and you are adapted to everything".
 *
 * <p>Every test here exists because the opposite was true and nothing noticed. The item used to carry
 * a hand-written copy of the core concept list, so a concept added anywhere else simply did not
 * appear in it; and the debuff half had two live spellings, so levitation denial was granted under a
 * key the flight gate never asked about. Both failures are silent — the item reports success — so they
 * are pinned here rather than left to a manual re-read of the list.
 */
@GameTestHolder(AdaptionWheel.MODID)
@PrefixGameTestTemplate(false)
public class AllAdaptationTests {

    private static final List<String> REGISTERED = new ArrayList<>();

    private static List<String> registeredConcepts() {
        if (REGISTERED.isEmpty()) {
            for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
                REGISTERED.add(def.concept());
            }
        }
        return REGISTERED;
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void everyRegisteredConceptIsSomethingTheAllAdaptationItemGrants(GameTestHelper helper) {

        helper.assertTrue(!registeredConcepts().isEmpty(),
                "the registry is empty, so this test would pass while granting nothing");

        // The item walks the registry, so this is the shape of the loop in grantAllAdaptations.
        PlayerAdaption data = new PlayerAdaption();
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (def.leveled()) {
                data.levels.put(def.concept(), def.maxLevel());
            } else {
                data.adapted.add(def.concept());
            }
            data.addHistory(def.concept());
        }

        List<String> missed = new ArrayList<>();
        for (String concept : registeredConcepts()) {
            boolean granted = Concepts.isLevelBased(concept)
                    ? data.levelOrZero(concept) >= PlayerAdaption.MAX_LEVEL
                    : data.active(concept);
            if (!granted) {
                missed.add(concept);
            }
        }
        helper.assertTrue(missed.isEmpty(),
                "these registered concepts are not adapted after a full adaptation: " + missed);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theConceptsThatWereMissingAreNowCovered(GameTestHelper helper) {

        // Named one by one because the failure this guards against is silent: the item succeeded and
        // the player was still not adapted. Each of these was absent from the hand-written list.
        String[] mustBeGranted = {
                Concepts.ENV_INVENTORY,
                Concepts.MUTATION_SEA_EYE,
                Concepts.MUTATION_FLIGHT,
                CombatFistTiers.concept(0),
                CombatFistTiers.concept(1),
                CombatFistTiers.concept(2),
                CombatFistTiers.concept(3),
                CombatFistTiers.concept(4)
        };

        PlayerAdaption data = new PlayerAdaption();
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (def.leveled()) {
                data.levels.put(def.concept(), def.maxLevel());
            } else {
                data.adapted.add(def.concept());
            }
        }

        for (String concept : mustBeGranted) {
            helper.assertTrue(AdaptationRegistry.isRegistered(concept),
                    concept + " is not even registered, so the item could never grant it");
            helper.assertTrue(Concepts.isLevelBased(concept)
                            ? data.levelOrZero(concept) >= PlayerAdaption.MAX_LEVEL
                            : data.active(concept),
                    concept + " is still missing after a full adaptation");
        }
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aDebuffKeyIsTheSameWhicheverFormTheCallerHolds(GameTestHelper helper) {

        String fromFullId = Concepts.debuff("minecraft:levitation");
        String fromBarePath = Concepts.debuff("levitation");

        helper.assertTrue(fromFullId.equals(fromBarePath),
                "the runtime and a caller holding a bare path must mint one key, got "
                        + fromFullId + " and " + fromBarePath);
        helper.assertTrue(fromFullId.contains(":"),
                "a debuff key must carry a namespace, or two mods shipping the same effect path share"
                        + " one adaptation and the display name cannot resolve: " + fromFullId);
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void levitationDeniedByTheItemIsTheLevitationTheFlightGateAsksFor(GameTestHelper helper) {

        // The concrete failure: the item granted Debuff_levitation, flight asked for
        // Debuff_minecraft:levitation, and neither one nor the other was wrong on its own.
        String levitation = Concepts.debuff(MobEffects.LEVITATION);
        helper.assertTrue(levitation.equals("Debuff_minecraft:levitation"),
                "the levitation concept the game mints is " + levitation);

        PlayerAdaption data = new PlayerAdaption();
        data.adapted.add(levitation);

        helper.assertTrue(data.isAdapted(Concepts.debuff("minecraft:levitation")),
                "granting the item's levitation key must satisfy the flight gate's identical key");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void anOldBareDebuffKeyIsRenamedRatherThanLeftAsASecondSpelling(GameTestHelper helper) {

        PlayerAdaption data = new PlayerAdaption();
        data.adapted.add("Debuff_levitation");
        data.levels.put("Debuff_poison", PlayerAdaption.MAX_LEVEL);
        data.history.add("Debuff_levitation");

        LegacyConcepts.migrate(data);

        helper.assertTrue(!data.adapted.contains("Debuff_levitation"),
                "the bare key is still there next to the new one, so the panel lists both");
        helper.assertTrue(data.adapted.contains("Debuff_minecraft:levitation"),
                "the bare levitation key was dropped instead of renamed, losing the adaptation: "
                        + data.adapted);
        helper.assertTrue(data.levels.containsKey("Debuff_minecraft:poison"),
                "the bare poison level was not renamed: " + data.levels.keySet());
        helper.assertTrue(data.history.contains("Debuff_minecraft:levitation"),
                "history still holds the old spelling: " + data.history);
        helper.assertTrue(!data.history.contains("Debuff_levitation"),
                "history kept the old spelling alongside the new one");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void isLivingTypeAgreesWithActuallyCreatingTheEntity(GameTestHelper helper) {

        // The entity pass used to ask EntityType#getBaseClass(), which in 1.21.1 is EntityTypeTest's
        // default and answers Entity.class for every registered type -- so the check was false for
        // every mob, vanilla and modded alike, and the pass granted nothing. Nothing logged it.
        // This compares the predicate against the question it is standing in for, entity by entity.
        List<String> mismatches = new ArrayList<>();
        int living = 0;

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null) {
                continue;
            }
            boolean expected;
            if (type == EntityType.PLAYER) {
                // EntityType.PLAYER cannot be created, so the probe below would answer null; it is
                // nonetheless a living type and the contact family covers it.
                expected = true;
            } else {
                try {
                    expected = type.create(helper.getLevel()) instanceof LivingEntity;
                } catch (RuntimeException e) {
                    // A modded type whose constructor throws cannot be classified either way; the
                    // predicate must not throw for it, but there is nothing to compare against.
                    continue;
                }
            }
            if (expected) {
                living++;
            }
            if (AdaptionEvents.isLivingType(type, helper.getLevel()) != expected) {
                mismatches.add(key + " should be " + expected);
            }
        }

        helper.assertTrue(mismatches.isEmpty(),
                "these types are classified wrongly, so the item grants the wrong concept families: "
                        + mismatches);
        helper.assertTrue(living > 70,
                "only " + living + " living types were found; vanilla 1.21.1 registers about 80, so"
                        + " the registry walk itself is suspect");
        helper.assertTrue(AdaptionEvents.isLivingType(EntityType.ARMOR_STAND, helper.getLevel()),
                "the armour stand is living but registered as MISC, which is exactly the case the"
                        + " category shortcut cannot answer");
        helper.assertTrue(!AdaptionEvents.isLivingType(EntityType.ITEM, helper.getLevel()),
                "an item entity is MISC and not living, so the probe must reject it");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theEntityPassCoversModdedMobsAsWellAsVanillaOnes(GameTestHelper helper) {

        // Mirrors the entity pass in grantAllAdaptations. The mod's own Disciple is the modded type
        // in reach here, and it has to come out with all three families: a grant that only knew
        // vanilla ids is precisely the reported "модовых нет".
        PlayerAdaption data = new PlayerAdaption();
        int living = 0;

        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null || !AdaptionEvents.isLivingType(type, helper.getLevel())) {
                continue;
            }
            living++;
            String path = key.toString();
            data.levels.put(Concepts.contact(path), PlayerAdaption.MAX_LEVEL);
            data.levels.put(Concepts.offense(path), PlayerAdaption.MAX_LEVEL);
            if (type != EntityType.PLAYER) {
                data.levels.put(Concepts.drop(path), PlayerAdaption.MAX_LEVEL);
            }
        }

        String disciple = "adaptionwheel:disciple";
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(
                        ResourceLocation.fromNamespaceAndPath("adaptionwheel", "disciple")),
                "the mod's own mob is not registered, so this test would not prove anything about"
                        + " modded types");

        for (String path : new String[]{"minecraft:zombie", disciple}) {
            helper.assertTrue(data.level(Concepts.contact(path)) == PlayerAdaption.MAX_LEVEL,
                    path + " has no contact adaptation: " + data.levels.keySet().stream()
                            .filter(k -> k.contains(path)).toList());
            helper.assertTrue(data.level(Concepts.offense(path)) == PlayerAdaption.MAX_LEVEL,
                    path + " has no offence adaptation");
            helper.assertTrue(data.level(Concepts.drop(path)) == PlayerAdaption.MAX_LEVEL,
                    path + " has no drop adaptation");
        }

        helper.assertTrue(data.level(Concepts.contact("minecraft:player")) == PlayerAdaption.MAX_LEVEL,
                "the player is a living type and the contact family covers it");
        helper.assertTrue(!data.levels.containsKey(Concepts.drop("minecraft:player")),
                "the player drops nothing to adapt to, so Drop_NPC_minecraft:player must not exist");
        helper.assertTrue(!data.levels.containsKey(Concepts.contact("minecraft:item")),
                "an item entity is not a mob and must not get a contact adaptation");

        helper.assertTrue(living > 70, "only " + living + " living types were classified");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void theWholeGrantIsHundredsOfConceptsNotSeventy(GameTestHelper helper) {

        // The reported symptom was a wheel with the ~70 registered concepts and none of the
        // per-mob families. Counting the three passes together is the only end-to-end statement
        // that the item now grants "everything": registry + harmful effects + (living types x 3 - 1).
        PlayerAdaption data = new PlayerAdaption();
        for (AdaptationDefinition def : AdaptationRegistry.allDefinitions()) {
            if (def.leveled()) {
                data.levels.put(def.concept(), def.maxLevel());
            } else {
                data.adapted.add(def.concept());
            }
        }
        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            if (effect.isBeneficial()) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (key != null) {
                data.adapted.add(Concepts.debuff(key.toString()));
            }
        }
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (key == null || !AdaptionEvents.isLivingType(type, helper.getLevel())) {
                continue;
            }
            String path = key.toString();
            data.levels.put(Concepts.contact(path), PlayerAdaption.MAX_LEVEL);
            data.levels.put(Concepts.offense(path), PlayerAdaption.MAX_LEVEL);
            if (type != EntityType.PLAYER) {
                data.levels.put(Concepts.drop(path), PlayerAdaption.MAX_LEVEL);
            }
        }

        int count = data.getAdaptCount();
        helper.assertTrue(count > 300,
                "a full grant produced only " + count + " adaptations; the per-mob families are"
                        + " missing again");
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void aConceptStoredInBothSetsCountsOnce(GameTestHelper helper) {

        // The All Adaptation item puts debuffs in both `adapted` and `levels`. The old count summed
        // the two collections, so the HUD reported more adaptations than the menu listed for the
        // same wheel. The union is the number a player actually has.
        PlayerAdaption data = new PlayerAdaption();
        String levitation = Concepts.debuff(MobEffects.LEVITATION);
        data.adapted.add(levitation);
        data.levels.put(levitation, PlayerAdaption.MAX_LEVEL);
        data.adapted.add("Env_Void");
        data.levels.put("Type_Fire", 1);

        helper.assertTrue(data.getAdaptCount() == 3,
                "one debuff stored in both collections plus two distinct concepts must count as 3,"
                        + " got " + data.getAdaptCount());
        helper.succeed();
    }

    @GameTest(template = "aw_empty5x5x5", templateNamespace = "adaptionwheel")
    public static void migratingNeverCostsAKnownedLevel(GameTestHelper helper) {

        // The merging rules have to compose: copper folded into Iron and the flat punching fist folded
        // into the wooden stage must not silently discard what the other already earned.
        PlayerAdaption data = new PlayerAdaption();
        data.levels.put("Fist_Copper", 3);
        data.levels.put("Fist_Iron", 2);
        data.levels.put("Combat_FistDamage", 4);

        LegacyConcepts.migrate(data);

        helper.assertTrue(!data.levels.containsKey("Fist_Copper"),
                "the removed copper tier is still stored: " + data.levels.keySet());
        helper.assertTrue(data.level("Fist_Iron") == 5,
                "copper 3 plus iron 2 should give iron 5, got " + data.level("Fist_Iron"));
        helper.assertTrue(!data.levels.containsKey("Combat_FistDamage"),
                "the removed flat fist concept is still stored: " + data.levels.keySet());
        helper.assertTrue(data.level(CombatFistTiers.concept(0)) == 4,
                "the flat fist's 4 levels should land on the wooden stage, got "
                        + data.level(CombatFistTiers.concept(0)));
        helper.succeed();
    }
}
