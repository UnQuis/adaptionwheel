package ru.adaptionwheel.data;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import ru.adaptionwheel.AdaptionWheel;

import java.util.function.Supplier;

public class AttachmentTypes {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AdaptionWheel.MODID);

    public static final Supplier<AttachmentType<PlayerAdaption>> ADAPTION =
            ATTACHMENTS.register("adaption", () -> AttachmentType.builder(PlayerAdaption::new)
                    .serialize(PlayerAdaption.CODEC)
                    .copyOnDeath()
                    .build());
}
