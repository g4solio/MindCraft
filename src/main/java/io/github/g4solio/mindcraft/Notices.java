package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindRuntime.Entry;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * The notices of Uno (D30). Uno speaks first only to warn: it proposes no remedy and starts no action.
 * The attention rule selects entries of the diary. A fixed template gives the sentence of an entry. One exit sends it.
 * Nothing here calls a provider: a notice also works without a key, and when the model service is down.
 */
public final class Notices {
    /** The one exit of the notices: the owner and the text. A test replaces it and reads the notices. */
    public interface Exit {
        void send(MinecraftServer server, UUID owner, String text);
    }

    /**
     * In the game: a system message to the owner, if the owner is connected.
     * If not, there is no chat line. The diary keeps the event for the summary of a later opening.
     */
    static final Exit CHAT = (server, owner, text) -> {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) player.sendSystemMessage(Component.literal("Uno › " + text));
    };

    static volatile Exit exit = CHAT;

    private Notices() {
    }

    /**
     * The attention rule (D30): a change to BLOCKED, RECOVERY or DONE that the runtime observed, and a long wait.
     * A change that a request made is not selected: a pause, a resume or a cancellation does not come back to the owner as a notice.
     * The other changes to WAITING are not selected: the ordinary cycle of a stock job is silent.
     */
    static boolean selected(Entry entry) {
        return !entry.requested()
            && (entry.longWait() || entry.state() == State.BLOCKED || entry.state() == State.RECOVERY || entry.state() == State.DONE);
    }

    /** The sentence of a selected entry, from a fixed template for its state and its cause. It names the job, the robot and the cause. */
    static String sentence(Entry entry) {
        String job = "Job " + entry.job() + " on robot #" + entry.robot() + " (" + MindRuntime.itemName(entry.item())
            + " from '" + entry.from() + "' to '" + entry.to() + "')";
        if (entry.longWait()) return job + longWait(entry);
        return switch (entry.state()) {
            case BLOCKED -> job + switch (entry.cause()) {
                case OUT_OF_FUEL -> " is blocked: the robot has no fuel.";
                case SOURCE_NO_INVENTORY -> " is blocked: there is no inventory at '" + entry.from() + "'.";
                case DESTINATION_NO_INVENTORY -> " is blocked: there is no inventory at '" + entry.to() + "'.";
                case LOAD_ABOVE_QUANTITY -> " is blocked: the quantity of the job is reached, and " + entry.carried() + " stay on board.";
                // A module error, an impediment of an unknown kind, or a record of an older version. The recorded text is all that Uno knows.
                default -> " is blocked. Recorded reason: " + entry.reason() + ".";
            };
            case RECOVERY -> job + " needs a check: I cannot say how it went. Recorded reason: " + entry.reason() + ".";
            case DONE -> job + " is done: " + entry.delivered() + " delivered.";
            default -> throw new IllegalArgumentException("No notice for the state " + entry.state());
        };
    }

    /** The end of the sentence of a long wait: its length and the cause at that moment. No remedy. */
    private static String longWait(Entry entry) {
        String waited = " has waited for " + MindRuntime.LONG_WAIT / 1200 + " minutes";
        return switch (entry.cause()) {
            case SOURCE_AT_RESERVE -> waited + ": " + (entry.reserve() == 0 ? "nothing remains in '" + entry.from() + "'."
                : "'" + entry.from() + "' has no item above the reserve of " + entry.reserve() + ".");
            case SOURCE_KEEPS -> waited + ": '" + entry.from() + "' has the items, but it releases none.";
            case ROBOT_FULL -> waited + ": the robot is full.";
            case DESTINATION_FULL -> waited + ": '" + entry.to() + "' is full, and " + entry.carried() + " stay on board.";
            case SOURCE_UNLOADED -> waited + ": the area of '" + entry.from() + "' is not loaded.";
            case DESTINATION_UNLOADED -> waited + ": the area of '" + entry.to() + "' is not loaded.";
            case PATH_OBSTRUCTED -> waited + ": the path of the robot is obstructed.";
            // A record of an older version has no cause. The recorded text is all that Uno knows.
            default -> waited + ". Recorded reason: " + entry.reason() + ".";
        };
    }

    /** The runtime calls this for each new entry of the diary. A job of the console has no player to warn. */
    static void entry(MinecraftServer server, Entry entry) {
        if (!selected(entry) || entry.owner().equals(Util.NIL_UUID)) return;
        exit.send(server, entry.owner(), sentence(entry));
    }
}
