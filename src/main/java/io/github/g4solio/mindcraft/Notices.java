package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindRuntime.Entry;
import io.github.g4solio.mindcraft.MindRuntime.Forgotten;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
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

    /** The sentences in one summary at most (spec #1). An initial value, to review after the play session. */
    static final int SUMMARY_SENTENCES = 10;

    private Notices() {
    }

    /**
     * The attention rule (D30): a change to BLOCKED, RECOVERY or DONE that the runtime observed.
     * A change that a request made is not selected: a pause, a resume or a cancellation does not come back to the owner as a notice.
     */
    static boolean selected(Entry entry) {
        return !entry.requested() && (entry.state() == State.BLOCKED || entry.state() == State.RECOVERY || entry.state() == State.DONE);
    }

    /** The sentence of a selected entry, from a fixed template for its state and its cause. It names the job, the robot and the cause. */
    static String sentence(Entry entry) {
        String job = "Job " + entry.job() + " on robot #" + entry.robot() + " (" + MindRuntime.itemName(entry.item())
            + " from '" + entry.from() + "' to '" + entry.to() + "')";
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

    /** The runtime calls this for each new entry of the diary. A job of the console has no player to warn. */
    static void entry(MinecraftServer server, Entry entry) {
        if (!selected(entry) || entry.owner().equals(Util.NIL_UUID)) return;
        exit.send(server, entry.owner(), sentence(entry));
    }

    /**
     * The summary at an opening (D30): the sentences of the selected entries after the last opening, the oldest first, each with its age.
     * It shows the newest SUMMARY_SENTENCES, and says how many older ones it does not show.
     * If the runtime removed entries after the last opening, it says where the memory of Uno starts: those entries can be events too.
     * "Last" is UNKNOWN at the first opening: then the summary covers the whole diary. Null if there is nothing to say.
     * The entries are the diary that the actor reads. Thus the summary has the same check of the actor as the list of jobs.
     */
    static String summary(List<Entry> diary, Forgotten forgotten, long last, long now) {
        List<Entry> events = diary.stream().filter(entry -> entry.time() > last && selected(entry)).toList();
        boolean gap = forgotten.entries() > 0 && forgotten.until() > last;
        if (events.isEmpty() && !gap) return null;
        List<String> lines = new ArrayList<>();
        lines.add(last == MindRuntime.UNKNOWN ? "From the start of my memory:" : "Since you last opened this screen:");
        if (gap) lines.add("My memory starts " + age(now - forgotten.until()) + ". I do not remember older events.");
        int hidden = Math.max(0, events.size() - SUMMARY_SENTENCES);
        if (hidden > 0) lines.add("I do not show " + hidden + (hidden == 1 ? " older event." : " older events."));
        for (Entry entry : events.subList(hidden, events.size())) {
            String age = age(now - entry.time());
            lines.add(Character.toUpperCase(age.charAt(0)) + age.substring(1) + ": " + sentence(entry));
        }
        return String.join("\n", lines);
    }

    /**
     * A difference of game time in words. The game time does not advance while the server is stopped.
     * A negative difference: the entry is newer than the world, after a crash (D08.1). Then the age is unknown.
     */
    static String age(long ticks) {
        if (ticks < 0) return "at an unknown time";
        long seconds = ticks / 20;
        if (seconds < 60) return seconds + " s ago";
        if (seconds < 3600) return seconds / 60 + " min ago";
        if (seconds < 86400) return seconds / 3600 + " h ago";
        long days = seconds / 86400;
        return days == 1 ? "1 day ago" : days + " days ago";
    }
}
