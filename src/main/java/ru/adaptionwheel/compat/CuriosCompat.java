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
 * <p>API surface used, verified with {@code curios-neoforge-17.0.0-beta.2+26.3.jar} by
 * {@code javap} (Curios 26.x branch, {@code top.theillusivec4.curios.api}):</p>
 * <ul>
 *   <li>{@code CuriosApi.registerCurio(Item, ICurioItem)}</li>
 *   <li>{@code CuriosApi.getCuriosInventory(LivingEntity)} → {@code Optional<ICuriosItemHandler>}</li>
 *   <li>{@code ICuriosItemHandler.findFirstCurio(Item)} → {@code Optional<SlotResult>}</li>
 *   <li>{@code SlotResult.stack()} / {@code SlotContext.identifier()}</li>
 *   <li>{@code ICurioItem.canEquip / canEquipFromUse(SlotContext, ItemStack)} — each with a
 *       one-argument twin on the {@code ICurio} base interface that Curios also calls</li>
 *   <li>{@code ICurio.canUnequip(SlotContext)}, default {@code true}, reached from
 *       {@code CuriosStacksResourceHandler.extract} — i.e. this is the gate that lets a curio be
 *       taken off again, and answering {@code false} welds the item into the slot</li>
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

    /**
     * Whether the wheel may go into the curio slot a {@code SlotContext} names.
     *
     * <p>The context is asked for its id reflectively for the same reason as everything else here.
     * If that call ever fails the answer is {@code false} — <em>no</em> slot — rather than
     * {@code true}: an item that Curios cannot place is inert, while an item it can place anywhere
     * quietly stops being a curio at all.
     */
    private static boolean acceptsSlot(Object slotContext) {
        try {
            return isWheelSlot((String) Impl.slotContextId().invoke(slotContext));
        } catch (Throwable t) {
            Impl.warnOnce("slot-id", "Curios' SlotContext.identifier() could not be called; the wheel "
                    + "will fit into no curio slot at all rather than into any of them.", t);
            return false;
        }
    }

    /** Reflective binding to the Curios API; resolved on first use. */
    private static final class Impl {

        private static final String API = "top.theillusivec4.curios.api.";

        private static boolean resolved;
        private static boolean ok;

        private static Class<?> curioItemInterface;
        private static Class<?> slotContextClass;
        private static MethodHandle registerCurio;      // (Item, ICurioItem) -> void
        private static MethodHandle getCuriosInventory; // (LivingEntity) -> Optional<ICuriosItemHandler>
        private static MethodHandle findFirstCurio;     // (ICuriosItemHandler, Item) -> Optional<SlotResult>
        private static MethodHandle slotResultStack;    // (SlotResult) -> ItemStack
        private static MethodHandle slotContextId;      // (SlotContext) -> String

        /** Handles whose binding failed, and questions already logged, so one message per cause. */
        private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

        static MethodHandle slotContextId() {
            return slotContextId;
        }

        static void warnOnce(String key, String message, Throwable cause) {
            if (WARNED.add(key)) {
                LOGGER.warn(message, cause);
            }
        }

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
                slotContextClass = slotContext;

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
            Object curio;
            try {
                curio = Proxy.newProxyInstance(CuriosCompat.class.getClassLoader(),
                        new Class<?>[]{curioItemInterface}, new WheelCurioHandler());
                registerCurio.invoke(item, curio);
            } catch (Throwable t) {
                LOGGER.warn("Failed to register the wheel as a curio", t);
                return;
            }
            selfCheck(curio);
        }

        /**
         * Asks the freshly built proxy the three questions that decide whether the wheel works at
         * all, and logs what it answered.
         *
         * <p>A reflective proxy that answers wrongly is invisible from the outside — that is how the
         * wheel could sit in its slot looking perfectly equipped and refuse to come off. Curios
         * never logs the answers it gets from an {@code ICurioItem}, so the only place the truth
         * can be seen is here, at the moment the answers are written rather than guessed.
         */
        private static void selfCheck(Object curio) {
            try {
                Method canEquip = curioItemInterface.getMethod("canEquip", slotContextClass, ItemStack.class);
                Method canUnequip = curioItemInterface.getMethod("canUnequip", slotContextClass, ItemStack.class);
                LOGGER.info("Curios: wheel bound — accepts the wheel slot: {}, accepts the head slot: {}, "
                                + "can be taken off again: {}",
                        canEquip.invoke(curio, slotContext(MahoragaWheelItem.WHEEL_SLOT), ItemStack.EMPTY),
                        canEquip.invoke(curio, slotContext("head"), ItemStack.EMPTY),
                        canUnequip.invoke(curio, slotContext(MahoragaWheelItem.WHEEL_SLOT), ItemStack.EMPTY));
            } catch (Throwable t) {
                warnOnce("self-check", "Curios is bound but the wheel's curio item could not be questioned "
                        + "about its own behaviour; its answers to Curios are unknown.", t);
            }
        }

        /** A {@code SlotContext} for a bare slot id — nothing in this class reads anything else off it. */
        private static Object slotContext(String slotId) throws ReflectiveOperationException {
            return slotContextClass.getConstructor(String.class, LivingEntity.class, int.class,
                            boolean.class, boolean.class)
                    .newInstance(slotId, null, 0, false, true);
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
                warnOnce("find", "Curios lookup of an equipped item failed, the wheel will fall back to the "
                        + "off-hand/inventory check. Not logged again.", t);
                return Optional.empty();
            }
        }

        /**
         * The curio behaviour of the wheel: only the dedicated {@code wheel} slot accepts it, and it
         * can always be taken back off.
         *
         * <p>Everything else is answered by the interface's <em>own</em> default, and that is the
         * load-bearing part of this class. The override list used to carry {@code canUnequip} and
         * answer it with {@link #defaultAnswer}, whose synthesized value for a {@code boolean} is
         * {@code false} — the natural reading of "no opinion". Curios reads that as
         * <em>this item may never be removed</em>: the removal path is
         * {@code CuriosStacksResourceHandler.extract → ICurio.canUnequip(SlotContext)}, so the wheel
         * was welded into its slot and could not be taken off by any means. It failed silently,
         * because a proxy that answers {@code false} is indistinguishable from a proxy that answers
         * correctly about a slot the item does not belong in.
         *
         * <p>Two rules come out of that, and they are the only rules: <b>a permission is answered
         * with the permission's real default, never with a guess</b>, and <b>a method this class
         * has no opinion about is asked of the interface rather than invented</b>. The names are
         * matched without their arity on purpose — {@code ICurio.canEquip(SlotContext)} and
         * {@code ICurioItem.canEquip(SlotContext, ItemStack)} are the same question asked twice, and
         * the slot is the first argument of both.
         */
        private static final class WheelCurioHandler implements InvocationHandler {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Object[] a = args == null ? new Object[0] : args;
                switch (method.getName()) {
                    case "canEquip", "canEquipFromUse" -> {
                        return a.length > 0 && acceptsSlot(a[0]);
                    }
                    //Curios' own default, and the one answer that must never be guessed at: `false`
                    //means "this can never come off". See the class comment.
                    case "canUnequip" -> {
                        return Boolean.TRUE;
                    }
                    case "equals" -> {
                        return proxy == a[0];
                    }
                    case "hashCode" -> {
                        return System.identityHashCode(proxy);
                    }
                    case "toString" -> {
                        return "AdaptionWheel WheelCurio";
                    }
                    default -> {
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        //A method with no default in this Curios version is one this class cannot
                        //have an opinion about, and a guess is exactly how canUnequip broke. Name it
                        //in the log once instead of answering in silence.
                        warnOnce("method:" + method.getName(), "Curios asked the wheel's curio item for "
                                + method.getName() + "(), which has no default in this Curios version and no "
                                + "answer here; answering with the neutral value. Not logged again.", null);
                        return defaultAnswer(method);
                    }
                }
            }
        }
    }
}
