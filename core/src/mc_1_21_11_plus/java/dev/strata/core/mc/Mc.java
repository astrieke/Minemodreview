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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Versionsanbindung für Minecraft 1.21.11 bis 26.3. */
public final class Mc {

    /** IDs werden im gemeinsamen Code als Text geführt, z. B. {@code "strata:crystal_age"}. */
    public static final Codec<String> ID_CODEC = Identifier.CODEC.xmap(Identifier::toString, Identifier::parse);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StrataCore.MOD_ID);

    private static final Supplier<AttachmentType<CodexProgress>> CODEX = ATTACHMENTS.register("codex",
            () -> AttachmentType.builder(() -> CodexProgress.EMPTY)
                    .serialize(CodexProgress.MAP_CODEC)
                    .copyOnDeath()
                    .build());

    private Mc() {}

    public static void registerAttachments(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static <T> ResourceKey<Registry<T>> registryKey(String path) {
        return ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(StrataCore.MOD_ID, path));
    }

    public static <T> Map<String, T> entries(MinecraftServer server, ResourceKey<Registry<T>> key) {
        Map<String, T> result = new LinkedHashMap<>();
        server.registryAccess().lookup(key).ifPresent(reg ->
                reg.entrySet().forEach(e -> result.put(e.getKey().identifier().toString(), e.getValue())));
        return result;
    }

    public static CodexProgress getProgress(ServerPlayer player) {
        return player.getData(CODEX.get());
    }

    public static void setProgress(ServerPlayer player, CodexProgress progress) {
        player.setData(CODEX.get(), progress);
    }

    public static long seed(ServerPlayer player) {
        return player.level().getSeed();
    }

    public static Predicate<CommandSourceStack> opOnly() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }

    public static ArgumentType<?> idArgument() {
        return IdentifierArgument.id();
    }

    public static String getId(CommandContext<CommandSourceStack> ctx, String name) {
        return IdentifierArgument.getId(ctx, name).toString();
    }

    // ---- Helfer für alle Strata-Mods ----

    public static String levelId(Level level) {
        return level.dimension().identifier().toString();
    }

    public static ServerLevel level(MinecraftServer server, String id) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(id)));
    }

    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        player.teleportTo(level, x, y, z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
    }

    public static int minY(Level level) {
        return level.getMinY();
    }

    public static int maxY(Level level) {
        return level.getMaxY();
    }

    public static void addCooldown(ServerPlayer player, ItemStack stack, int ticks) {
        player.getCooldowns().addCooldown(stack, ticks);
    }

    public static boolean isOnCooldown(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        return player.getCooldowns().isOnCooldown(stack);
    }

    public static void actionbar(ServerPlayer player, Component message) {
        player.sendSystemMessage(message, true);
    }

    public static Holder<MobEffect> slowness() {
        return MobEffects.SLOWNESS;
    }

    public static Holder<MobEffect> miningFatigue() {
        return MobEffects.MINING_FATIGUE;
    }

    /** Legt ein Attachment an, das mit dem Codec gespeichert wird. */
    public static <T> Supplier<AttachmentType<T>> attachment(DeferredRegister<AttachmentType<?>> register, String name,
                                                            Supplier<T> defaultValue, Codec<T> codec, boolean copyOnDeath) {
        return register.register(name, () -> {
            var builder = AttachmentType.builder(defaultValue).serialize(codec.fieldOf("value"));
            if (copyOnDeath) builder.copyOnDeath();
            return builder.build();
        });
    }

    public static DeferredRegister<AttachmentType<?>> attachmentRegister(String modId) {
        return DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, modId);
    }

    public static long seed(ServerLevel level) {
        return level.getSeed();
    }

    /** Registry-Einträge auch auf dem Client lesen (Datapack-Registries werden synchronisiert). */
    public static <T> Map<String, T> entries(net.minecraft.core.RegistryAccess access, ResourceKey<Registry<T>> key) {
        Map<String, T> result = new LinkedHashMap<>();
        access.lookup(key).ifPresent(reg ->
                reg.entrySet().forEach(e -> result.put(e.getKey().identifier().toString(), e.getValue())));
        return result;
    }

    public static boolean hasAdvancement(MinecraftServer server, String id) {
        return server.getAdvancements().get(Identifier.parse(id)) != null;
    }

    /** Vergibt alle offenen Kriterien eines Advancements. Gibt false zurück, wenn es das Advancement nicht gibt. */
    public static boolean awardAdvancement(ServerPlayer player, String id) {
        var holder = player.level().getServer().getAdvancements().get(Identifier.parse(id));
        if (holder == null) return false;
        var progress = player.getAdvancements().getOrStartProgress(holder);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
        return true;
    }
}
