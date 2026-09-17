# Mod API — integrating with the Adaption Wheel

All public integration surface lives in `ru.adaptionwheel.api`. The mod id is `adaptionwheel`
(curse/workshop lineage: ADAPTIONWHEEL). There are no hard API guarantees beyond what is documented here;
the classes are small and stable, but pin the version in your build.

## Querying a player (server side)

```java
import ru.adaptionwheel.api.AdaptionWheelAPI;

// during your event handler (server thread):
boolean wearing = AdaptionWheelAPI.isWearingWheel(player);      // wheel equipped in the Curios slot
int fireLevel   = AdaptionWheelAPI.getLevel(player, "Type_FIRE");     // 0..8, 0 if absent
boolean lava    = AdaptionWheelAPI.isAdapted(player, "Env_Lava");     // one-time flags
int count       = AdaptionWheelAPI.getAdaptCount(player);             // drives cumulative bonuses
List<String> running = AdaptionWheelAPI.getActiveTasks(player);       // concepts under analysis
```

Client-side calls return "not wearing"/0 — state is authoritative on the server and synced only to the owning
client's private mirror.

## Reacting to completions

```java
@Mod.EventBusSubscriber(modid = "yourmod")   // NeoForge GAME bus
public static class WheelListener {
    @SubscribeEvent
    public static void onAdapted(AdaptationCompleteEvent event) {
        // event.getConcept(): "Type_FIRE", "Move_SoulSand", "Mutation_Impact", ...
        // event.getLevel():  new level for leveled concepts, -1 for one-time grants
        if (event.getConcept().equals("Env_Lava")) {
            // e.g. award an advancement
        }
    }
}
```

The event fires from every completion path: leveled analysis tasks, one-time environment/debuff/movement grants,
combo mutations and existence adaptation. It cannot be canceled.

## Registering custom concept metadata

Concept keys are opaque strings; storage/sync work with any key. Registering metadata makes your key show up
nicely in the GUI, HUD colors and `/adaptionwheel registry`:

```java
AdaptionWheelAPI.registerDefinition(
    AdaptationDefinition.oneTime("Move_Quicksand", AdaptationDomain.MOVEMENT));
// or leveled:
AdaptionWheelAPI.registerDefinition(
    AdaptationDefinition.leveled("Type_Acid", AdaptationDomain.DOMAIN_DAMAGE)); // see enum
```

Display name resolution: `adaptionwheel.concept.<family>.<key>` lang entries via `Concepts.displayName`;
descriptions: `adaptionwheel.desc.<key>` (optional — GUI hides missing ones).

## What already works automatically for modded content

| Mechanism | Coverage |
|---|---|
| Custom **DamageType**s | Damage-category adaptation matches vanilla tags first (`is_fire`, `is_explosion`, `is_projectile`, …) then falls back to id heuristics — tagged modded damage types adapt without any code. |
| Harmful **MobEffect**s | The debuff system iterates *any* non-beneficial effect instance (`Debuff_<path>`), so modded potions/poisons adapt through normal exposure. |
| Boss-tagged entities | Existence adaptation recognizes anything in NeoForge's `EntityTypes.BOSSES` tag plus multi-part bodies (`PartEntity` subclasses) via `BossHelper`. |
| Attribute-driven movement | Swim-speed style bonuses compose through standard attribute modifiers. |

## Extension points for deeper integrations

- Movement/physics restrictions of modded blocks: add a `Move_*` concept + trigger + map the block inside
  `SurfaceAdaptations.neutralizedSpeedFactor` (or ship your own mixin calling the same helper).
- New damage pipelines that bypass events entirely (like DE's charged laser): follow the
  `GuardianDirectDamage` → `AdaptionEvents.handleDirectHealthReduction` pattern.
- Per-mob contact/offense/drop/existence concepts are generated on the fly from entity ids — nothing to register.

## Debug tooling (ops)

- `/adaptionwheel status [player]` — counters, adversity state, running task count.
- `/adaptionwheel list [domain] [player]` — everything known, filterable by domain.
- `/adaptionwheel info <concept>` — level/state/domain of one concept.
- `/adaptionwheel grant <concept> [level] [player]` — surgical grant (no heal/voice/events).
- `/adaptionwheel analyze <concept>` — start a real analysis task (full completion flow).
- `/adaptionwheel reset [player]` — wipe attachment + item data.
- `/adaptionwheel registry` — all registered definitions.

Reads are usable by players on themselves; mutations require permission level 2.
