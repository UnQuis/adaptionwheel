package ru.adaptionwheel.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.adaptionwheel.item.MahoragaWheelItem;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * Soft Curios integration.
 *
 * <p>Curios has not shipped a Minecraft 26.3 build yet, so the mod must work without it and
 * cannot even compile against its API. This class is the only place that touches Curios, and it
 * does so purely reflectively (resolved once, lazily, only when the {@code curios} mod is
 * loaded). Every public method is a no-op / empty result when Curios is absent or the API
 * shape has changed. Once a 26.3 Curios API artifact exists, {@link Impl} can be replaced by
 * direct calls without touching anything else.</p>
 *
 * <p>API surface used (Curios 26.x branch, {@code top.theillusivec4.curios.api}):</p>
 * <ul>
 *   <li>{@code CuriosApi.registerCurio(Item, ICurioItem)}</li>
 *   <li>{@code CuriosApi.getCuriosInventory(LivingEntity)} → {@code Optional<ICuriosItemHandler>}</li>
 *   <li>{@code ICuriosItemHandler.findFirstCurio(Item)} → {@code Optional<SlotResult>}</li>
 *   <li>{@code SlotResult.stack()} / {@code SlotContext.identifier()}</li>
 *   <li>{@code ICurioItem.canEquip / canEquipFromUse(SlotContext, ItemStack)}</li>
 * </ul>
 */
public final class CuriosCompat {

    public static final String MOD_ID = "curios";

    private static final Logger LOGGER = LoggerFactory.getLogger("AdaptionWheel/Curios");

    private static Boolean loaded;

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return loaded;
    }

    /** Registers the wheel as a curio item (call from common setup, on the main thread). */
    public static void registerWheel(Item item) {
        if (isLoaded() && Impl.available()) {
            Impl.register(item);
        }
    }

    /**
     * Whether Curios is loaded <i>and</i> its API could actually be bound. Callers must use this (not
     * {@link #isLoaded()}) to decide whether the Curios code path is authoritative, so a drifted Curios
     * API degrades into the off-hand/inventory fallback instead of making the wheel silently dead.
     */
    public static boolean isUsable() {
        return isLoaded() && Impl.available();
    }

    /** Whether the given item is equipped in any Curios slot of the entity. */
    public static boolean isEquipped(LivingEntity entity, Item item) {
        return isUsable() && findFirst(entity, item).isPresent();
    }

    /** First stack of the given item found in the entity's Curios slots. */
    public static Optional<ItemStack> findFirst(LivingEntity entity, Item item) {
        return isUsable() ? Impl.findFirst(entity, item) : Optional.empty();
    }

    /** Whether the wheel may be equipped in the given curio slot id. */
    public static boolean isWheelSlot(String slotId) {
        return MahoragaWheelItem.WHEEL_SLOT.equals(slotId);
    }

    /** The value a {@code ICurioItem} method should answer with when we have no opinion about it. */
    private static Object defaultAnswer(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == void.class) {
            return null;
        }
        return type.isInstance(Optional.empty()) ? Optional.empty() : null;
    }

    /** Reflective binding to the Curios API; resolved on first use. */
    private static final class Impl {

        private static final String API = "top.theillusivec4.curios.api.";

        private static final java.util.concurrent.atomic.AtomicBoolean WARNED_FIND_FAILURE =
                new java.util.concurrent.atomic.AtomicBoolean();

        private static boolean resolved;
        private static boolean ok;

        private static Class<?> curioItemInterface;
        private static MethodHandle registerCurio;      // (Item, ICurioItem) -> void
        private static MethodHandle getCuriosInventory; // (LivingEntity) -> Optional<ICuriosItemHandler>
        private static MethodHandle findFirstCurio;     // (ICuriosItemHandler, Item) -> Optional<SlotResult>
        private static MethodHandle slotResultStack;    // (SlotResult) -> ItemStack
        private static MethodHandle slotContextId;      // (SlotContext) -> String

        static synchronized boolean available() {
            if (resolved) {
                return ok;
            }
            resolved = true;
            try {
                ClassLoader cl = CuriosCompat.class.getClassLoader();
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> curiosApi = Class.forName(API + "CuriosApi", true, cl);
                curioItemInterface = Class.forName(API + "type.capability.ICurioItem", true, cl);
                Class<?> handler = Class.forName(API + "type.capability.ICuriosItemHandler", true, cl);
                Class<?> slotResult = Class.forName(API + "SlotResult", true, cl);
                Class<?> slotContext = Class.forName(API + "SlotContext", true, cl);

                registerCurio = lookup.findStatic(curiosApi, "registerCurio",
                        MethodType.methodType(void.class, Item.class, curioItemInterface));
                getCuriosInventory = lookup.findStatic(curiosApi, "getCuriosInventory",
                        MethodType.methodType(Optional.class, LivingEntity.class));
                findFirstCurio = lookup.findVirtual(handler, "findFirstCurio",
                        MethodType.methodType(Optional.class, Item.class));
                slotResultStack = lookup.findVirtual(slotResult, "stack",
                        MethodType.methodType(ItemStack.class));
                slotContextId = lookup.findVirtual(slotContext, "identifier",
                        MethodType.methodType(String.class));
                ok = true;
            } catch (Throwable t) {
                ok = false;
                LOGGER.warn("Curios is present but its API could not be bound; the wheel will use "
                        + "the inventory/off-hand fallback instead of a curio slot.", t);
            }
            return ok;
        }

        static void register(Item item) {
            try {
                Object curio = Proxy.newProxyInstance(CuriosCompat.class.getClassLoader(),
                        new Class<?>[]{curioItemInterface}, new WheelCurioHandler());
                registerCurio.invoke(item, curio);
            } catch (Throwable t) {
                LOGGER.warn("Failed to register the wheel as a curio", t);
            }
        }

        static Optional<ItemStack> findFirst(LivingEntity entity, Item item) {
            try {
                Optional<?> handler = (Optional<?>) getCuriosInventory.invoke(entity);
                if (handler.isEmpty()) {
                    return Optional.empty();
                }
                Optional<?> result = (Optional<?>) findFirstCurio.invoke(handler.get(), item);
                if (result.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.ofNullable((ItemStack) slotResultStack.invoke(result.get()));
            } catch (Throwable t) {
                //Never swallow this silently: a drifted Curios API looks exactly like "the wheel is not equipped"
                if (WARNED_FIND_FAILURE.compareAndSet(false, true)) {
                    LOGGER.warn("Curios lookup of an equipped item failed, the wheel will fall back to the "
                            + "off-hand/inventory check. Further occurrences are not logged.", t);
                }
                return Optional.empty();
            }
        }

        /**
         * The curio behaviour of the wheel: only the dedicated {@code wheel} slot accepts it.
         * Every other {@code ICurioItem} method falls through to the interface default.
         */
        private static final class WheelCurioHandler implements InvocationHandler {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ((name.equals("canEquip") || name.equals("canEquipFromUse"))
                        && args != null && args.length == 2) {
                    String slotId = (String) slotContextId.invoke(args[0]);
                    return isWheelSlot(slotId);
                }
                if (name.equals("canUnequip") || name.equals("getSlotContext") || name.equals("canShow")) {
                    return defaultAnswer(method);
                }
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                return switch (name) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "AdaptionWheel WheelCurio";
                    //Anything else (curioTick, render helpers, tooltips of a newer Curios) must not throw inside
                    //Curios' own call sites: the default answer is "nothing special"
                    default -> defaultAnswer(method);
                };
            }
        }
    }
}
