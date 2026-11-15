package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindRuntime.Entry;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.State;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The account of a job: what its record says, with the age of each observation and the doubt about the robot (D17).
 * The report, the screen and the status command read a job through it. Each number comes from the record.
 * The inputs are explicit: the record, whether the robot runs now, and the game time. A test can give them without a robot.
 * The facts for the model are here too (D42, point 2), and the fixed sentences about the numbers that the facts do not give (D42, point 3).
 * The check of the numbers of the model is in Uno: it reads the whole context and the request, not one job (D42, point 4).
 */
record JobAccount(Job job, boolean running, long now) {
    /** Game ticks without a call from a running robot. After them Uno does not know whether the robot works. */
    static final int SILENT_TICKS = 300;
    /** Game ticks after which an observation is old: 5 minutes, the length of a long wait (D42, point 3). An initial value. */
    static final int OLD_TICKS = 6000;

    /** The account in the game: the robot runs when its computer has a live session now. */
    static JobAccount of(Job job, long now) {
        return new JobAccount(job, RobotApi.live(job.robot) != null, now);
    }

    /** The whole seconds since a game time. The one unit of the ages of an account. */
    long seconds(long at) {
        return (now - at) / 20;
    }

    /** An observed stock in words, with its age. */
    String seen(int count, long at) {
        return count == MindRuntime.UNKNOWN ? "never observed" : count + " items, " + seconds(at) + " seconds ago";
    }

    /** The report of Uno on the job. */
    String report() {
        String from = "'" + job.from + "'", to = "'" + job.to + "'";
        StringBuilder text = new StringBuilder("Job " + job.id + ", robot #" + job.robot + ": ");
        text.append(job.keepStock()
            ? "keep at least " + job.quantity + " items in " + to + ", reserve " + job.reserve + " in " + from + ". "
            : "carry " + job.quantity + " items from " + from + " to " + to + ", reserve " + job.reserve + ". ");
        text.append("I delivered ").append(job.delivered).append(" items. ");
        // The owner can lower the quantity below the deliveries. The record keeps the deliveries: the report must not hide the difference.
        if (!job.keepStock() && job.delivered > job.quantity) text.append("That is more than the quantity of the job: the quantity became lower after those deliveries. ");
        if (job.carried > 0) text.append("The record says that I have ").append(job.carried).append(" on board. ");
        text.append(switch (job.state) {
            case RUNNING -> running ? "I am working. " : "The job is open, but the robot is stopped. ";
            case SATISFIED -> "At the last observation the stock was at the requested level. "
                + (job.carried > 0 ? "I keep the load on board for the next need. " : "");
            case DONE -> "The job is complete. ";
            case PAUSED -> "I am paused at your request. ";
            case CANCELLED -> "You cancelled the job. ";
            case WAITING -> waiting(from, to);
            case BLOCKED -> blocked(from, to);
            case RECOVERY -> "I cannot say how it went. Recorded reason: " + job.reason + ". You decide whether to resume or cancel. ";
        });
        if (job.recoveries > 0) text.append("After a recovery the totals can be inexact. ");
        // The level of the last contact with an active job (D42, point 19). A turtle that needs no fuel has none.
        if (job.fuel != MindRuntime.UNKNOWN) {
            text.append("My fuel level is ").append(job.fuel).append(": ").append(Notices.trips(job.trips)).append(" between the stations. ");
        }
        text.append("Last observations: ").append(from).append(' ').append(seen(job.fromSeen, job.fromSeenAt))
            .append(", ").append(to).append(' ').append(seen(job.toSeen, job.toSeenAt)).append(". ");
        // The state of the link is not the state of the job. Old data must not look like current data.
        if (job.state.open() && job.state != State.PAUSED) {
            if (!running) {
                text.append("The robot is not running or not loaded. These are the last recorded data, not the current state.");
            } else if (silent()) {
                text.append("The robot has not contacted me for a while. I do not know if it is working.");
            }
        }
        return text.toString().trim();
    }

    /** Why the job waits. The sentence comes from the cause that the runtime recorded, not from a guess. */
    private String waiting(String from, String to) {
        String missing = job.keepStock() ? "To reach " + job.quantity + " in " + to + " I need more materials. "
            : "I must carry " + (job.quantity - job.delivered) + " more. I need more materials. ";
        return switch (job.cause) {
            case SOURCE_AT_RESERVE -> (job.reserve == 0 ? "Nothing remains in " + from + ". "
                : job.fromSeen + " remain in " + from + (job.fromSeen == job.reserve ? ": that is the reserve you asked me to keep. "
                    : ": that is less than the reserve of " + job.reserve + " you asked me to keep. ")) + missing;
            case SOURCE_KEEPS -> from + " has the items, but it does not let me take them. ";
            case ROBOT_FULL -> "My inventory is full: I cannot take the items from " + from + ". Make room in the robot. ";
            case DESTINATION_FULL -> "The destination is full. I am waiting for free space. ";
            case SOURCE_UNLOADED -> "The area of " + from + " is not loaded: I cannot see its inventory and I do not know its stock. ";
            case DESTINATION_UNLOADED -> "The area of " + to + " is not loaded: I cannot see its inventory and I do not know its stock. ";
            case PATH_OBSTRUCTED -> "My path is obstructed. I try to move again every few seconds, and I continue by myself when the path is free. ";
            // A record of an older version has no cause. The recorded text is all that Uno knows.
            default -> "I am waiting. Recorded reason: " + job.reason + ". ";
        };
    }

    /** Why the job is blocked. A block needs the hands of the owner. The sentence comes from the cause that the runtime recorded. */
    private String blocked(String from, String to) {
        return switch (job.cause) {
            case OUT_OF_FUEL -> "I am stopped: I have no fuel. Give me fuel, then resume the job. ";
            case SOURCE_NO_INVENTORY -> "I am stopped: I find no inventory at " + from + ". Put it back, then resume the job. ";
            case DESTINATION_NO_INVENTORY -> "I am stopped: I find no inventory at " + to + ". Put it back, then resume the job. ";
            case LOAD_ABOVE_QUANTITY -> "I am stopped: the quantity of the job is reached, and " + job.carried
                + " stay on board. Raise the quantity and resume, or cancel the job. ";
            // A record of an older version, or an impediment of a kind that the runtime does not know. The recorded text is all that Uno knows.
            default -> "I am stopped and I need your help. Recorded reason: " + job.reason + ". ";
        };
    }

    /** No call from the robot for more than SILENT_TICKS, or none since the start of the server. */
    private boolean silent() {
        return job.lastContact == MindRuntime.UNKNOWN || now - job.lastContact > SILENT_TICKS;
    }

    /**
     * The facts of the job: the part of the account that goes to the model (D42, point 2). Each number comes from the record.
     * The derived numbers come from the server: the quantity still to carry, and the stock of the source above the reserve (D42, point 4).
     * No coordinate, no player name and no free text of a reason: a recorded reason can hold a position.
     * <p>
     * The model does not get an uncertain number (D32). In its place a fixed sentence says which value is not given, and why (D42, point 3):
     * <ul>
     * <li>RECOVERY: the delivered total, the load on board and the stocks. The sentence gives the cause.</li>
     * <li>After a recovery: the delivered total.</li>
     * <li>A robot that is not running, or that is silent for more than SILENT_TICKS: the load on board, the stocks and the fuel.
     * They are the last recorded data, not the current state (T21). This holds in each state of the job.</li>
     * <li>An observation older than OLD_TICKS: that stock.</li>
     * </ul>
     * A derived number is not given when its number is not given. The fuel level and its trips are not uncertain in RECOVERY:
     * the runtime reads the level from the turtle (D42, point 19). A turtle that needs no fuel has no level.
     */
    Uno.Context.Facts facts() {
        List<String> uncertain = new ArrayList<>();
        boolean recovery = job.state == State.RECOVERY, delivered = !recovery && job.recoveries == 0, linked = running && !silent(),
            current = !recovery && linked, fuel = linked && job.fuel != MindRuntime.UNKNOWN;
        if (recovery) {
            uncertain.add("The result of the job is uncertain" + switch (job.cause) {
                case LOAD_DIFFERS -> ": the load in the robot differs from the record";
                case WORLD_OLDER -> ": the world is older than the record";
                // A record of an older version has no cause for its RECOVERY.
                default -> "";
            } + ". Not given: the delivered total, the load on board and the stocks.");
        } else if (!delivered) {
            uncertain.add("After a recovery the delivered total can be inexact. Not given: the delivered total.");
        }
        if (!running) {
            uncertain.add("The robot is not running or not loaded. Not given: the load on board, the stocks and the fuel.");
        } else if (silent()) {
            uncertain.add("The robot has not contacted the server for a while. Not given: the load on board, the stocks and the fuel.");
        }
        Uno.Context.Stock source = current ? stock(job.from, job.fromSeen, job.fromSeenAt, uncertain) : null;
        Uno.Context.Stock destination = current ? stock(job.to, job.toSeen, job.toSeenAt, uncertain) : null;
        // A transport carries its quantity one time. A stock job carries what the destination misses to its level.
        Integer toCarry = job.keepStock() ? (destination == null ? null : Math.max(0, job.quantity - destination.count()))
            : (delivered ? Math.max(0, job.quantity - job.delivered) : null);
        Integer aboveReserve = source == null ? null : Math.max(0, source.count() - job.reserve);
        return new Uno.Context.Facts(job.id, job.robot, job.behaviour.id, MindRuntime.itemName(job.item), job.from, job.to, job.quantity,
            job.reserve, job.state, job.cause, delivered ? job.delivered : null, current ? job.carried : null, source, destination,
            toCarry, aboveReserve, fuel ? job.fuel : null, fuel ? job.trips : null, List.copyOf(uncertain));
    }

    /**
     * The fixed sentences about the uncertain numbers of the job (D42, point 3): the numbers that facts() does not give, with their caution.
     * They come before the voice (D32). The cases are the cases of facts():
     * <ul>
     * <li>RECOVERY: the sentence of A, with the delivered total, the load on board and the stocks of the record. One sentence gives them all.</li>
     * <li>After a recovery: the delivered total of the record, which can be inexact.</li>
     * <li>A robot that is not running, or that is silent for more than SILENT_TICKS: the load on board and the stocks of the record.</li>
     * <li>An observation older than OLD_TICKS, from a robot that runs and calls: that stock, with its age.</li>
     * </ul>
     * A stock that no robot observed has no number: it gets no sentence. Empty when the facts give each number.
     */
    List<String> cautions() {
        String name = Notices.job(job.id, job.robot);
        if (job.state == State.RECOVERY) {
            return List.of(Notices.capital(name) + ": I cannot say how it went. Recorded reason: " + job.reason
                + ". The recorded data can be wrong: " + recorded(job.delivered + " delivered") + ". You decide whether to resume or cancel.");
        }
        List<String> said = new ArrayList<>();
        if (job.recoveries > 0) said.add("After a recovery of " + name + " the totals can be inexact: the record says " + job.delivered + " delivered.");
        if (!running || silent()) {
            said.add("Robot #" + job.robot + " of job " + job.id
                + (running ? " has not contacted me for a while: I do not know if it is working." : " is not running or not loaded.")
                + " The last recorded data, not the current state: " + recorded(null) + ".");
            return said;
        }
        old(job.from, job.fromSeen, job.fromSeenAt, said);
        old(job.to, job.toSeen, job.toSeenAt, said);
        return said;
    }

    /** The fixed sentence of an old observation of a station, with its stock and its age. Nothing for a recent or a missing observation. */
    private void old(String station, int count, long at, List<String> said) {
        if (count != MindRuntime.UNKNOWN && now - at > OLD_TICKS) {
            said.add("I last observed '" + station + "' " + Notices.age(now - at) + ": it had " + count + " then.");
        }
    }

    /** The last recorded data in words, after the first part if it is not null: the load on board and each observed stock with its age. */
    private String recorded(String first) {
        List<String> parts = new ArrayList<>();
        if (first != null) parts.add(first);
        parts.add(job.carried + " on board");
        if (job.fromSeen != MindRuntime.UNKNOWN) parts.add(job.fromSeen + " in '" + job.from + "' (seen " + Notices.age(now - job.fromSeenAt) + ")");
        if (job.toSeen != MindRuntime.UNKNOWN) parts.add(job.toSeen + " in '" + job.to + "' (seen " + Notices.age(now - job.toSeenAt) + ")");
        int last = parts.size() - 1;
        return last == 0 ? parts.get(0) : String.join(", ", parts.subList(0, last)) + " and " + parts.get(last);
    }

    /**
     * An observed stock for the facts, with its age in words. Null with a sentence for a stock that no robot observed:
     * unknown is not zero (T02). Null with a sentence for an old observation.
     */
    private Uno.Context.Stock stock(String station, int count, long at, List<String> uncertain) {
        if (count == MindRuntime.UNKNOWN) {
            uncertain.add("No robot observed '" + station + "' yet. Its stock is unknown.");
            return null;
        }
        if (now - at > OLD_TICKS) {
            uncertain.add("The last observation of '" + station + "' is old. Not given: its stock.");
            return null;
        }
        return new Uno.Context.Stock(count, Notices.age(now - at));
    }

    /** The lines of the diary in the context of a model: the newest ones (D42, point 2). An initial value. */
    static final int DIARY_LINES = 20;

    /**
     * The newest lines of a diary, as data for the model, the oldest first (D42, point 2). "Diary" is the diary that the actor reads.
     * "Jobs" are the facts of the jobs of the actor. A line has the quantities of its job at the moment of the entry, with three exceptions.
     * A line gives no number that the facts of its job do not give. A line in RECOVERY has no delivered total and no load on board,
     * as the facts of a job in RECOVERY. The lines of a job with a RECOVERY in the diary have no delivered total: after a recovery
     * the total can be inexact. This also holds for a job that a new job replaced on its robot: it has no facts any more.
     */
    static List<Uno.Context.Line> lines(List<Entry> diary, List<Uno.Context.Facts> jobs, long now) {
        Set<Integer> noDelivered = new HashSet<>(), noLoad = new HashSet<>();
        for (Uno.Context.Facts facts : jobs) {
            if (facts.delivered() == null) noDelivered.add(facts.job());
            if (facts.onBoard() == null) noLoad.add(facts.job());
        }
        for (Entry entry : diary) {
            if (entry.state() == State.RECOVERY) noDelivered.add(entry.job());
        }
        List<Uno.Context.Line> lines = new ArrayList<>();
        for (Entry entry : diary.subList(Math.max(0, diary.size() - DIARY_LINES), diary.size())) {
            boolean recovery = entry.state() == State.RECOVERY;
            lines.add(new Uno.Context.Line(Notices.age(now - entry.time()), entry.job(), entry.robot(), entry.kind(), entry.state(), entry.cause(),
                entry.quantity(), entry.reserve(), noDelivered.contains(entry.job()) ? null : entry.delivered(),
                recovery || noLoad.contains(entry.job()) ? null : entry.carried()));
        }
        return List.copyOf(lines);
    }

    /** The whole record, for the status command. */
    String describe() {
        Job j = job;
        return "Job " + j.id + " on robot #" + j.robot + ": " + j.state + (j.reason.isEmpty() ? "" : " (" + j.reason + ")")
            + "\n  " + (j.keepStock() ? "keep at least " : "") + j.quantity + " x " + MindRuntime.itemName(j.item)
            + " from '" + j.from + "' to '" + j.to + "', reserve " + j.reserve + ", revision " + j.revision
            + "\n  taken " + j.taken + ", delivered " + j.delivered + ", on board " + j.carried + " (recorded)"
            + (j.recoveries > 0 ? "\n  " + j.recoveries + " recovery. The totals can be inexact." : "")
            + "\n  source stock: " + stock(j.fromSeen, j.fromSeenAt) + ", destination stock: " + stock(j.toSeen, j.toSeenAt)
            + "\n  robot: " + (running ? "running" : "not running or not loaded")
            + ", last contact: " + (j.lastContact == MindRuntime.UNKNOWN ? "none since server start" : seconds(j.lastContact) + " s ago")
            + ", executor: " + (j.executor.isEmpty() ? "none" : j.executor)
            + "\n  fuel: " + (j.fuel == MindRuntime.UNKNOWN ? "unknown" : j.fuel + ", " + Notices.trips(j.trips) + " between the stations")
            + "\n  last operation: " + (j.lastOp.isEmpty() ? "none" : j.lastOp);
    }

    private String stock(int count, long at) {
        return count == MindRuntime.UNKNOWN ? "unknown" : count + " (seen " + seconds(at) + " s ago)";
    }
}
