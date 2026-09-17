package ru.adaptionwheel.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.adaptionwheel.AdaptionWheel;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AdaptionWheel.MODID);

    public static final DeferredItem<Item> MAHORAGA_WHEEL_WOOD =
            ITEMS.registerItem("mahoraga_wheel_wood", Item::new, new Item.Properties());

    public static final DeferredItem<MahoragaWheelItem> MAHORAGA_WHEEL =
            ITEMS.registerItem("mahoraga_wheel", p -> new MahoragaWheelItem(p.stacksTo(1)), new Item.Properties());

    public static final DeferredItem<AllAdaptionItem> ALL_ADAPTION =
            ITEMS.registerItem("all_adaption", p -> new AllAdaptionItem(p.stacksTo(1)), new Item.Properties());

    public static final DeferredItem<SwordOfExterminationItem> SWORD_OF_EXTERMINATION =
            ITEMS.registerItem("sword_of_extermination", p ->
                    new SwordOfExterminationItem(Tiers.NETHERITE, p), new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 12, -2.4f))
                    .component(DataComponents.UNBREAKABLE, new Unbreakable(true)));

    private ModItems() {
    }
}
