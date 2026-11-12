package io.github.g4solio.mindcraft;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The two network messages of the screen of Uno.
 * The server sends a view. The client sends an action with the text of the fields.
 */
final class Net {
    private static final String VERSION = "1";
    /** The distance in blocks at which a projector serves a player. */
    static final int REACH = 8;
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ResourceLocation.tryParse(MindCraft.MOD_ID + ":main"), () -> VERSION, VERSION::equals, VERSION::equals);

    record View(String json) {
    }

    record Action(String action, String values) {
    }

    private Net() {
    }

    static void register() {
        CHANNEL.registerMessage(0, View.class, (view, buffer) -> buffer.writeUtf(view.json()), buffer -> new View(buffer.readUtf()),
            Net::onView, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Action.class, (action, buffer) -> {
            buffer.writeUtf(action.action(), 64);
            buffer.writeUtf(action.values(), 4096);
        }, buffer -> new Action(buffer.readUtf(64), buffer.readUtf(4096)), Net::onAction, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    static void sendView(ServerPlayer player) {
        String view = UnoView.build(MindRuntime.get(player.server), MindCraft.actor(player), player.serverLevel().getGameTime());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new View(view));
    }

    /** A contact point is a tablet in the inventory or a projector near the player. Without one the client gets no answer. */
    private static boolean hasContactPoint(ServerPlayer player) {
        return player.getInventory().contains(new ItemStack(MindCraft.TABLET.get()))
            || MindRuntime.get(player.server).nearPlace(player.level(), player.blockPosition(), REACH);
    }

    private static void onView(View view, Supplier<NetworkEvent.Context> context) {
        // UnoScreen is a client class. A dedicated server must not load it.
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UnoScreen.show(view.json())));
        context.get().setPacketHandled(true);
    }

    private static void onAction(Action action, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null || !hasContactPoint(player)) return;
            UnoView.act(player.server, MindCraft.actor(player), action.action(), action.values());
            sendView(player);
        });
        context.get().setPacketHandled(true);
    }
}
