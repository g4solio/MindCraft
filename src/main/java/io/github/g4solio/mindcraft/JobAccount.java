package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.State;

/**
 * The account of a job: what its record says, with the age of each observation and the doubt about the robot (D17).
 * The report, the screen and the status command read a job through it. Each number comes from the record.
 * The inputs are explicit: the record, whether the robot runs now, and the game time. A test can give them without a robot.
 * Increment B adds here the facts for the model and the check of its numbers (D32).
 */
record JobAccount(Job job, boolean running, long now) {
    /** Game ticks without a call from a running robot. After them Uno does not know whether the robot works. */
    static final int SILENT_TICKS = 300;

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
        text.append("Last observations: ").append(from).append(' ').append(seen(job.fromSeen, job.fromSeenAt))
            .append(", ").append(to).append(' ').append(seen(job.toSeen, job.toSeenAt)).append(". ");
        // The state of the link is not the state of the job. Old data must not look like current data.
        if (job.state.open() && job.state != State.PAUSED) {
            if (!running) {
                text.append("The robot is not running or not loaded. These are the last recorded data, not the current state.");
            } else if (job.lastContact == MindRuntime.UNKNOWN || now - job.lastContact > SILENT_TICKS) {
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
            + "\n  last operation: " + (j.lastOp.isEmpty() ? "none" : j.lastOp);
    }

    private String stock(int count, long at) {
        return count == MindRuntime.UNKNOWN ? "unknown" : count + " (seen " + seconds(at) + " s ago)";
    }
}
