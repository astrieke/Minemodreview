package dev.strata.core.mc;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import dev.strata.core.CodexProgress;
import dev.strata.core.StrataCore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Versionsanbindung für Minecraft 1.21.1 (NeoForge 21.1). */
public final class Mc {

    public static final String MINECRAFT_LINE = "1.21.1";

    /** IDs werden im gemeinsamen Code als Text geführt, z. B. {@code "strata:crystal_age"}. */
    public static final Codec<String> ID_CODEC = ResourceLocation.CODEC.xmap(ResourceLocation::toString, ResourceLocation::parse);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StrataCore.MOD_ID);

    private static final Supplier<AttachmentType<CodexProgress>> CODEX = ATTACHMENTS.register("codex",
            () -> AttachmentType.builder(() -> CodexProgress.EMPTY)
                    .serialize(CodexProgress.CODEC)
                    .copyOnDeath()
                    .build());

    private Mc() {}

    public static void registerAttachments(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static <T> ResourceKey<Registry<T>> registryKey(String path) {
        return ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(StrataCore.MOD_ID, path));
    }

    public static <T> Map<String, T> entries(MinecraftServer server, ResourceKey<Registry<T>> key) {
        Map<String, T> result = new LinkedHashMap<>();
        server.registryAccess().registry(key).ifPresent(reg ->
                reg.entrySet().forEach(e -> result.put(e.getKey().location().toString(), e.getValue())));
        return result;
    }

    public static CodexProgress getProgress(ServerPlayer player) {
        return player.getData(CODEX.get());
    }

    public static void setProgress(ServerPlayer player, CodexProgress progress) {
        player.setData(CODEX.get(), progress);
    }

    public static long seed(ServerPlayer player) {
        return player.serverLevel().getSeed();
    }

    public static Predicate<CommandSourceStack> opOnly() {
        return source -> source.hasPermission(2);
    }

    public static ArgumentType<?> idArgument() {
        return ResourceLocationArgument.id();
    }

    public static String getId(CommandContext<CommandSourceStack> ctx, String name) {
        return ResourceLocationArgument.getId(ctx, name).toString();
    }
}
