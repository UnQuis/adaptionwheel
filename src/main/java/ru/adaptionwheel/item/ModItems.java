package ru.adaptionwheel.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AdaptionWheel.MODID);

    public static final DeferredItem<Item> MAHORAGA_WHEEL_WOOD =
            ITEMS.registerItem("mahoraga_wheel_wood", Item::new, () -> new Item.Properties());

    public static final DeferredItem<MahoragaWheelItem> MAHORAGA_WHEEL =
            ITEMS.registerItem("mahoraga_wheel", MahoragaWheelItem::new, () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<AllAdaptionItem> ALL_ADAPTION =
            ITEMS.registerItem("all_adaption", AllAdaptionItem::new, () -> new Item.Properties().stacksTo(1));

    public static final DeferredItem<SwordOfExterminationItem> SWORD_OF_EXTERMINATION =
            ITEMS.registerItem("sword_of_extermination", SwordOfExterminationItem::new, () -> new Item.Properties()
                    .sword(ToolMaterial.NETHERITE, 12f, -2.4f)
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .component(DataComponents.UNBREAKABLE, Unit.INSTANCE));

    private ModItems() {
    }
}
