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

    public static void registerWheel(Item item) {
        if (isLoaded() && Impl.available()) {
            Impl.register(item);
        }
    }

    public static boolean isUsable() {
        return isLoaded() && Impl.available();
    }

    public static Optional<ItemStack> findFirst(LivingEntity entity, Item item) {
        return isUsable() ? Impl.findFirst(entity, item) : Optional.empty();
    }

    public static boolean isWheelSlot(String slotId) {
        return MahoragaWheelItem.WHEEL_SLOT.equals(slotId);
    }

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

    private static boolean acceptsSlot(Object slotContext) {
        try {
            return isWheelSlot((String) Impl.slotContextId().invoke(slotContext));
        } catch (Throwable t) {
            Impl.warnOnce("slot-id", "Curios' SlotContext.identifier() could not be called; the wheel "
                    + "will fit into no curio slot at all rather than into any of them.", t);
            return false;
        }
    }

    private static final class Impl {

        private static final String API = "top.theillusivec4.curios.api.";

        private static boolean resolved;
        private static boolean ok;

        private static Class<?> curioItemInterface;
        private static Class<?> slotContextClass;
        private static MethodHandle registerCurio;
        private static MethodHandle getCuriosInventory;
        private static MethodHandle findFirstCurio;
        private static MethodHandle slotResultStack;
        private static MethodHandle slotContextId;

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

                warnOnce("find", "Curios lookup of an equipped item failed, the wheel will fall back to the "
                        + "off-hand/inventory check. Not logged again.", t);
                return Optional.empty();
            }
        }

        private static final class WheelCurioHandler implements InvocationHandler {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Object[] a = args == null ? new Object[0] : args;
                switch (method.getName()) {
                    case "canEquip", "canEquipFromUse" -> {
                        return a.length > 0 && acceptsSlot(a[0]);
                    }

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
