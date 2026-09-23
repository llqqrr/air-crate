package com.llqqrr.aircrate.network;

import com.llqqrr.aircrate.AirCrate;
import com.llqqrr.aircrate.content.block.AviationCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative requests for the small world-space mode control. */
public final class AirCrateNetwork {
    private static final String PROTOCOL = "1";
    private static final CustomPacketPayload.Type<SetBreedingPayload> SET_BREEDING =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AirCrate.MOD_ID,
                    "set_breeding"));
    private static final StreamCodec<RegistryFriendlyByteBuf, SetBreedingPayload> SET_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, SetBreedingPayload::pos,
                    net.minecraft.network.codec.ByteBufCodecs.BOOL, SetBreedingPayload::enabled,
                    SetBreedingPayload::new);
    private static final CustomPacketPayload.Type<FrogportActionPayload> FROGPORT_ACTION =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AirCrate.MOD_ID, "frogport_action"));
    private static final StreamCodec<RegistryFriendlyByteBuf, FrogportActionPayload> FROGPORT_ACTION_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, FrogportActionPayload::pos,
                    net.minecraft.network.codec.ByteBufCodecs.BYTE, FrogportActionPayload::action,
                    FrogportActionPayload::new);
    private static final CustomPacketPayload.Type<FilterActionPayload> FILTER_ACTION =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AirCrate.MOD_ID, "filter_action"));
    private static final StreamCodec<RegistryFriendlyByteBuf, FilterActionPayload> FILTER_ACTION_CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.BYTE, FilterActionPayload::action,
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, FilterActionPayload::entityType,
                    FilterActionPayload::new);
    private static final CustomPacketPayload.Type<GunShotPayload> GUN_SHOT =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AirCrate.MOD_ID, "gun_shot"));
    private static final StreamCodec<RegistryFriendlyByteBuf, GunShotPayload> GUN_SHOT_CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.BOOL, GunShotPayload::release,
                    net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC, GunShotPayload::parcel,
                    GunShotPayload::new);

    public enum FrogportAction {
        TOGGLE_MODE, RESELECT, REMOVE_FILTER
    }
    public enum FilterAction { ADD, REMOVE, TOGGLE_MODE }

    private AirCrateNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AirCrateNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToServer(SET_BREEDING, SET_CODEC, AirCrateNetwork::handleSet);
        event.registrar(PROTOCOL).playToServer(FROGPORT_ACTION, FROGPORT_ACTION_CODEC,
                AirCrateNetwork::handleFrogportAction);
        event.registrar(PROTOCOL).playToServer(FILTER_ACTION, FILTER_ACTION_CODEC,
                AirCrateNetwork::handleFilterAction);
        event.registrar(PROTOCOL).playToClient(GUN_SHOT, GUN_SHOT_CODEC,
                AirCrateNetwork::handleGunShot);
    }

    /** Tells the firing player's client to play the packing gun tongue animation. */
    public static void gunShot(ServerPlayer player, boolean release, net.minecraft.world.item.ItemStack parcel) {
        PacketDistributor.sendToPlayer(player, new GunShotPayload(release, parcel));
    }

    private static void handleGunShot(GunShotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                com.llqqrr.aircrate.client.CreatureIntakeFanRenderer.triggerShot(
                        payload.release(), payload.parcel());
            }
        });
    }

    public static void setBreeding(BlockPos pos, boolean enabled) {
        PacketDistributor.sendToServer(new SetBreedingPayload(pos, enabled));
    }

    public static void frogportAction(BlockPos pos, FrogportAction action) {
        PacketDistributor.sendToServer(new FrogportActionPayload(pos, (byte) action.ordinal()));
    }

    public static void filterAction(FilterAction action, String entityType) {
        PacketDistributor.sendToServer(new FilterActionPayload((byte) action.ordinal(), entityType));
    }

    private static void handleFilterAction(FilterActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            net.minecraft.world.item.ItemStack filter = player.getMainHandItem();
            if (!filter.is(com.llqqrr.aircrate.registry.ACItems.CREATURE_FILTER.get())) filter = player.getOffhandItem();
            if (!filter.is(com.llqqrr.aircrate.registry.ACItems.CREATURE_FILTER.get())) return;
            FilterAction[] actions = FilterAction.values();
            if (payload.action() < 0 || payload.action() >= actions.length) return;
            com.llqqrr.aircrate.content.creature.CreatureFilterData data =
                    com.llqqrr.aircrate.content.creature.CreatureFilterItem.data(filter);
            switch (actions[payload.action()]) {
                case TOGGLE_MODE -> filter.set(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_FILTER.get(), data.toggleMode());
                case ADD, REMOVE -> {
                    ResourceLocation id = ResourceLocation.tryParse(payload.entityType());
                    if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return;
                    net.minecraft.world.entity.EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(id);
                    if (!com.llqqrr.aircrate.content.creature.CreatureCaptureService.isSupportedType(type, player.level())) return;
                    java.util.LinkedHashSet<String> entries = new java.util.LinkedHashSet<>(data.entityTypes());
                    if (actions[payload.action()] == FilterAction.ADD) entries.add(id.toString());
                    else entries.remove(id.toString());
                    filter.set(com.llqqrr.aircrate.registry.ACDataComponents.CREATURE_FILTER.get(),
                            new com.llqqrr.aircrate.content.creature.CreatureFilterData(data.whitelist(), java.util.List.copyOf(entries)));
                }
            }
            player.getInventory().setChanged();
        });
    }

    private static void handleFrogportAction(FrogportActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || player.distanceToSqr(payload.pos().getCenter()) > 64.0D
                    || !player.mayBuild()
                    || !(player.level().getBlockEntity(payload.pos())
                    instanceof com.llqqrr.aircrate.content.frogport.CreatureFrogportBlockEntity frogport)) return;
            FrogportAction[] actions = FrogportAction.values();
            if (payload.action() < 0 || payload.action() >= actions.length) return;
            switch (actions[payload.action()]) {
                case TOGGLE_MODE -> frogport.toggleMode();
                case RESELECT -> {
                    frogport.clearSelection();
                    com.llqqrr.aircrate.content.frogport.CreatureFrogportSelectionHandler.begin(player, payload.pos());
                }
                case REMOVE_FILTER -> {
                    net.minecraft.world.item.ItemStack filter = frogport.filter();
                    if (filter.isEmpty()) return;
                    frogport.setFilter(net.minecraft.world.item.ItemStack.EMPTY);
                    if (!player.getInventory().add(filter)) player.drop(filter, false);
                }
            }
        });
    }

    private static void handleSet(SetBreedingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || player.distanceToSqr(payload.pos().getCenter()) > 64.0D
                    || !player.mayBuild()
                    || !(player.level().getBlockEntity(payload.pos()) instanceof AviationCrateBlockEntity member)) {
                return;
            }
            AviationCrateBlockEntity controller = member.controller();
            controller.setBreedingEnabled(payload.enabled());
        });
    }

    private record SetBreedingPayload(BlockPos pos, boolean enabled) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return SET_BREEDING;
        }
    }

    private record FrogportActionPayload(BlockPos pos, byte action) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return FROGPORT_ACTION; }
    }
    private record FilterActionPayload(byte action, String entityType) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return FILTER_ACTION; }
    }
    private record GunShotPayload(boolean release, net.minecraft.world.item.ItemStack parcel) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return GUN_SHOT; }
    }
}
