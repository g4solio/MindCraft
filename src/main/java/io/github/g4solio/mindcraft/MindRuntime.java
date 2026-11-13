package io.github.g4solio.mindcraft;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The authoritative MindCraft state: stations, enrolled robots and jobs.
 * Minecraft saves it with the world. Use it on the server thread only.
 */
public class MindRuntime extends SavedData {
    public static final int UNKNOWN = -1;

    public static final String TRANSPORT = "transport", KEEP_STOCK = "keep_stock";

    public enum State {
        RUNNING, WAITING, SATISFIED, BLOCKED, PAUSED, RECOVERY, DONE, CANCELLED;

        /** The robot may act. */
        public boolean active() {
            return this == RUNNING || this == WAITING || this == SATISFIED;
        }

        /** The job still holds the robot. */
        public boolean open() {
            return this != DONE && this != CANCELLED;
        }
    }

    /**
     * Why a job waits or is blocked. The runtime records the cause together with the state, from what it observed in that tick.
     * Uno reads this value. It does not guess the cause from the load or from the text of the reason.
     * D34: a condition that can end by itself is a wait. A condition that needs the hands of the owner is a block.
     */
    public enum Cause {
        /**
         * No recorded cause: the job does not wait and is not blocked, an older version of the mod wrote the record,
         * or a module reported an impediment of an unknown kind. Then the recorded reason is all that Uno knows.
         */
        NONE,

        // Causes of WAITING.
        /** The source has no item above the reserve. */
        SOURCE_AT_RESERVE,
        /** The source has items above the reserve and the robot has room, but the source released nothing. */
        SOURCE_KEEPS,
        /** The robot cannot accept one more item. */
        ROBOT_FULL,
        /** The destination did not accept the whole load. */
        DESTINATION_FULL,
        /** The area of the inventory is not loaded. Its stock is unknown. */
        SOURCE_UNLOADED, DESTINATION_UNLOADED,
        /** The robot cannot move. It tries again, and the job works again at the first move that succeeds. */
        PATH_OBSTRUCTED,

        // Causes of BLOCKED.
        /** The robot cannot move and has no fuel item. The owner gives fuel and resumes. */
        OUT_OF_FUEL,
        /** The robot is at the dock, but the station has no inventory at its position. */
        SOURCE_NO_INVENTORY, DESTINATION_NO_INVENTORY,
        /** A transport reached its quantity with a load on board: the owner lowered the quantity after the pickup. */
        LOAD_ABOVE_QUANTITY
    }

    /** The kinds of impediment that a Lua module reports. The runtime decides the state from the kind. See impediment(). */
    static final String OBSTACLE = "obstacle", NO_FUEL = "fuel";

    /** The player or console that makes a request. The server decides this value, not the client. */
    public record Actor(UUID id, boolean admin) {
        boolean owns(UUID owner) {
            return admin || id.equals(owner);
        }
    }

    /** An inventory and the position where a robot stands to use it. */
    public record Station(UUID owner, ResourceKey<Level> dim, BlockPos inventory, BlockPos dock) {
    }

    /** A registered place of the base: the position of a projector. The owner is the player who put it there. */
    public record Place(UUID owner, ResourceKey<Level> dim, BlockPos pos) {
    }

    /** What a projector shows to each player who sees it. It contains no data of a job. */
    public enum Mood {
        IDLE, WORKING, ATTENTION
    }

    /** A request that the runtime does not accept. The message is for the player. */
    public static class Refusal extends RuntimeException {
        Refusal(String message) {
            super(message);
        }
    }

    /**
     * What a job must do. A command or Uno writes it. The runtime validates it in start().
     * For "transport" the quantity is the total to deliver. For "keep_stock" it is the level to keep in the destination.
     */
    public record Order(String module, int robot, String from, String to, Item item, int quantity, int reserve) {
    }

    public static class Job {
        public int id;
        public UUID owner;
        /** The robot of the job: its key in the record. Not saved: the load takes it from the key. */
        public int robot = UNKNOWN;
        public String module = TRANSPORT;
        public String from, to;
        public Item item;
        public int quantity, reserve;
        /** Increases at each change of the quantity or the reserve. It detects a change by a different actor. */
        public int revision;
        public State state = State.RUNNING;
        public String reason = "";
        /** The cause of a WAITING state. NONE in each other state. */
        public Cause cause = Cause.NONE;
        /** Totals of the transfers that the runtime did itself. */
        public int taken, delivered;
        /** The quantity that the record expects in the robot. */
        public int carried;
        /** The last stock that a docked robot observed. UNKNOWN until the first observation. */
        public int fromSeen = UNKNOWN, toSeen = UNKNOWN;
        public long fromSeenAt, toSeenAt;
        public String executor = "";
        public int ops;
        public String lastOp = "";
        /** Game time of the last transfer. A value in the future shows that the world is older than this record. */
        public long lastOpAt;
        public int recoveries;
        /** Game time of the last call from the robot. Not saved. */
        public long lastContact = UNKNOWN;

        public boolean keepStock() {
            return module.equals(KEEP_STOCK);
        }

        /**
         * The quantity that the robot must still bring to the destination.
         * The load in the robot counts as in transit. It is not stock.
         * UNKNOWN for "keep_stock" until a robot observes the destination.
         */
        public int need() {
            if (!keepStock()) return Math.max(0, quantity - delivered - carried);
            return toSeen == UNKNOWN ? UNKNOWN : Math.max(0, quantity - toSeen - carried);
        }
    }

    /**
     * One line of the diary: a change of the state of a job, or of the cause of a wait or of a block (D31).
     * The entry carries what its sentence needs, because the record of the job can be gone: a new job replaces it on the robot.
     * The quantities are the values of the job at that moment.
     * "Requested" says that a request of an actor made the change: a start, a pause, a resume or a cancellation.
     * The other changes come from what the runtime observed.
     */
    public record Entry(long time, UUID owner, int job, int robot, String from, String to, Item item, State state, Cause cause,
                        String reason, boolean requested, int quantity, int reserve, int delivered, int carried) {
    }

    /** What the runtime removed from a diary: the number of entries, and the game time of the newest of them. UNKNOWN if none. */
    public record Forgotten(int entries, long until) {
    }

    public final List<Place> places = new ArrayList<>();
    public final Map<String, Station> stations = new TreeMap<>();
    /** Computer ID to owner. */
    public final Map<Integer, UUID> robots = new TreeMap<>();
    /** Computer ID to the current or last job. A robot has one job at most. */
    public final Map<Integer, Job> jobs = new TreeMap<>();
    /** The diary of all owners, the oldest entry first (D31). It keeps the entries of a job after a new job replaced it. */
    private final List<Entry> diary = new ArrayList<>();
    /** The entries for each owner at most (D31). An initial value, to review after the play session. */
    static final int DIARY_LIMIT = 200;
    /** Owner to what the runtime removed from the diary of that owner. */
    private final Map<UUID, Forgotten> forgotten = new HashMap<>();
    /** Player to the game time of the last opening of a contact point by that player (D30). Minecraft saves it with the world. */
    private final Map<UUID, Long> openings = new HashMap<>();
    private int nextJobId = 1;
    /** True after the comparison of the record with the age of the world. Not saved. */
    private boolean ageChecked;
    /** The server of this record, for the game time of the diary. Not saved. Null in a copy that load() made outside the server. */
    private MinecraftServer server;

    public static MindRuntime get(MinecraftServer server) {
        MindRuntime runtime = server.overworld().getDataStorage().computeIfAbsent(MindRuntime::load, MindRuntime::new, MindCraft.MOD_ID);
        runtime.server = server;
        if (!runtime.ageChecked) {
            runtime.ageChecked = true;
            long now = server.overworld().getGameTime();
            for (Job job : runtime.jobs.values()) {
                // A crash or a restored backup. The world possibly does not contain the last transfers of the record.
                // The runtime must not report them as facts. The owner decides.
                if (job.lastOpAt > now && job.state != State.CANCELLED) {
                    job.fromSeen = UNKNOWN;
                    job.toSeen = UNKNOWN;
                    runtime.set(job, State.RECOVERY, "the world is older than the record of this job");
                }
            }
        }
        return runtime;
    }

    // --- Requests. Each request checks the actor. -------------------------------------------------------------

    public void addStation(Actor actor, String name, Level level, BlockPos inventory, BlockPos dock) {
        Station old = stations.get(name);
        if (old != null && !actor.owns(old.owner())) throw new Refusal("Station '" + name + "' belongs to another player.");
        if (jobs.values().stream().anyMatch(j -> j.state.open() && (j.from.equals(name) || j.to.equals(name)))) {
            throw new Refusal("An open job uses station '" + name + "'.");
        }
        if (inventory.distManhattan(dock) != 1) throw new Refusal("The dock must be next to the inventory.");
        if (RobotApi.inventoryAt(level, inventory, dock) == null) throw new Refusal("No inventory at " + inventory.toShortString() + ".");
        stations.put(name, new Station(actor.id(), level.dimension(), inventory.immutable(), dock.immutable()));
        setDirty();
    }

    public void addPlace(UUID owner, Level level, BlockPos pos) {
        places.removeIf(place -> place.dim() == level.dimension() && place.pos().equals(pos));
        places.add(new Place(owner, level.dimension(), pos.immutable()));
        setDirty();
    }

    public void removePlace(Level level, BlockPos pos) {
        if (places.removeIf(place -> place.dim() == level.dimension() && place.pos().equals(pos))) setDirty();
    }

    /** True if a projector is at this distance or less. A projector is a contact point for each player near it. */
    public boolean nearPlace(Level level, BlockPos pos, int distance) {
        return places.stream().anyMatch(place -> place.dim() == level.dimension() && place.pos().closerThan(pos, distance)
            && level.isLoaded(place.pos()) && level.getBlockState(place.pos()).is(MindCraft.PROJECTOR.get()));
    }

    /** The general state of the jobs of the owner of a place. A projector without an owner is idle. */
    public Mood mood(Level level, BlockPos pos) {
        Mood mood = Mood.IDLE;
        for (Place place : places) {
            if (place.dim() != level.dimension() || !place.pos().equals(pos)) continue;
            for (Job job : jobs.values()) {
                if (!job.owner.equals(place.owner())) continue;
                if (job.state == State.BLOCKED || job.state == State.RECOVERY || job.state == State.WAITING) return Mood.ATTENTION;
                if (job.state == State.RUNNING) mood = Mood.WORKING;
            }
        }
        return mood;
    }

    public void enroll(Actor actor, RobotApi robot) {
        GameProfile placer = robot.turtle.getOwningPlayer();
        if (!actor.admin() && (placer == null || !actor.id().equals(placer.getId()))) throw new Refusal("This turtle is not yours.");
        UUID owner = robots.get(robot.id());
        if (owner != null && !actor.owns(owner)) throw new Refusal("Robot #" + robot.id() + " belongs to another player.");
        robots.put(robot.id(), actor.id());
        setDirty();
    }

    public void release(Actor actor, int robotId) {
        requireRobot(actor, robotId);
        Job job = jobs.get(robotId);
        if (job != null && job.state.open()) throw new Refusal("Robot #" + robotId + " has an open job. Cancel it first.");
        robots.remove(robotId);
        setDirty();
    }

    /** The ordinary validation of an order. Each source of orders goes through it: a command, a test or Uno. */
    public RobotApi check(Actor actor, Order order) {
        int robotId = order.robot();
        if (!order.module().equals(TRANSPORT) && !order.module().equals(KEEP_STOCK)) {
            throw new Refusal("Unknown module '" + order.module() + "'.");
        }
        requireRobot(actor, robotId);
        Job current = jobs.get(robotId);
        if (current != null && current.state.open()) {
            throw new Refusal("Robot #" + robotId + " already has job " + current.id + " (" + current.state + ").");
        }
        RobotApi robot = RobotApi.live(robotId);
        if (robot == null) throw new Refusal("Robot #" + robotId + " is not running. Load its area and turn it on.");
        if (order.from() == null || order.to() == null || order.from().equals(order.to())) {
            throw new Refusal("The source and the destination must be different.");
        }
        for (String name : List.of(order.from(), order.to())) {
            Station station = stations.get(name);
            if (station == null) throw new Refusal("Unknown station '" + name + "'.");
            if (!actor.owns(station.owner())) throw new Refusal("Station '" + name + "' is not yours.");
            if (station.dim() != robot.turtle.getLevel().dimension()) throw new Refusal("Station '" + name + "' is in another dimension.");
        }
        if (order.item() == null || order.item() == Items.AIR) throw new Refusal("Unknown item.");
        requireQuantities(order.quantity(), order.reserve());
        int held = robot.held(order.item());
        if (held > 0) throw new Refusal("Robot #" + robotId + " already holds " + held + " of this item. Empty it first.");
        return robot;
    }

    public Job start(Actor actor, Order order) {
        RobotApi robot = check(actor, order);
        Job job = new Job();
        job.id = nextJobId++;
        job.owner = actor.id();
        job.robot = order.robot();
        job.module = order.module();
        job.from = order.from();
        job.to = order.to();
        job.item = order.item();
        job.quantity = order.quantity();
        job.reserve = order.reserve();
        jobs.put(order.robot(), job);
        // The first entry of the job: it is RUNNING from now on.
        write(job, true);
        setDirty();
        robot.wake();
        return job;
    }

    /**
     * Changes the quantity and the reserve of an open job. The robot uses the new values at its next step.
     * The request names the job and its revision. The job ID refuses a request that is about an earlier job of the robot.
     * The revision refuses a request that is about an earlier state of the same job.
     * <p>
     * A lower quantity changes nothing that the robot already did: no item goes back.
     * From the change on, the robot delivers only up to the new quantity. A load above it stays on board. See RobotApi.give.
     */
    public void update(Actor actor, int robotId, int jobId, int expectedRevision, int quantity, int reserve) {
        Job job = checkUpdate(actor, robotId, jobId, expectedRevision, quantity, reserve);
        job.quantity = quantity;
        job.reserve = reserve;
        job.revision++;
        setDirty();
        RobotApi robot = RobotApi.live(robotId);
        if (robot != null) robot.wake();
    }

    /** The validation of update(), without the change. Uno checks a change with it before the proposal. update() checks again. */
    Job checkUpdate(Actor actor, int robotId, int jobId, int expectedRevision, int quantity, int reserve) {
        Job job = openJob(actor, robotId, jobId);
        if (job.revision != expectedRevision) throw new Refusal("Job " + job.id + " changed. Read it again.");
        requireQuantities(quantity, reserve);
        return job;
    }

    /** For a command that names only the robot: the request is about the job that the robot has now. */
    public void update(Actor actor, int robotId, int expectedRevision, int quantity, int reserve) {
        update(actor, robotId, currentJob(robotId), expectedRevision, quantity, reserve);
    }

    private int currentJob(int robotId) {
        Job job = jobs.get(robotId);
        return job == null ? UNKNOWN : job.id;
    }

    private Job openJob(Actor actor, int robotId, int jobId) {
        Job job = jobs.get(robotId);
        if (job == null || !job.state.open()) throw new Refusal("Robot #" + robotId + " has no open job.");
        if (!actor.owns(job.owner)) throw new Refusal("Job " + job.id + " is not yours.");
        if (job.id != jobId) throw new Refusal("Robot #" + robotId + " has job " + job.id + " now, not job " + jobId + ". Read it again.");
        return job;
    }

    private static void requireQuantities(int quantity, int reserve) {
        if (quantity <= 0 || reserve < 0) throw new Refusal("The quantity must be positive. The reserve must not be negative.");
    }

    /** For a command that names only the robot: the request is about the job that the robot has now. */
    public void control(Actor actor, int robotId, String action) {
        control(actor, robotId, currentJob(robotId), action);
    }

    /** The validation of control(), without the change. Uno checks a resume or a cancellation with it before the proposal. control() checks again. */
    Job checkControl(Actor actor, int robotId, int jobId, String action) {
        Job job = openJob(actor, robotId, jobId);
        switch (action) {
            case "pause" -> {
                if (!job.state.active()) throw new Refusal("Job " + job.id + " is not running (" + job.state + ").");
            }
            case "resume" -> {
                if (job.state.active()) throw new Refusal("Job " + job.id + " is already running.");
            }
            case "cancel" -> {
            }
            default -> throw new Refusal("Unknown action '" + action + "'.");
        }
        return job;
    }

    /** Pause, resume or cancel of the named job. The robot finishes its current single move or transfer first. */
    public void control(Actor actor, int robotId, int jobId, String action) {
        Job job = checkControl(actor, robotId, jobId, action);
        RobotApi robot = RobotApi.live(robotId);
        switch (action) {
            case "pause" -> set(job, State.PAUSED, Cause.NONE, "paused by the owner", true);
            // The load stays in the robot. The status shows it.
            case "cancel" -> set(job, State.CANCELLED, Cause.NONE, "cancelled by the owner", true);
            case "resume" -> {
                if (job.state == State.RECOVERY) {
                    // The owner accepts the observed load as the truth. The totals can be inexact after this.
                    if (robot == null) throw new Refusal("Robot #" + robotId + " is not running. The runtime cannot observe its load.");
                    int held = robot.held(job.item);
                    job.lastOp = "recovery: owner accepted load " + held + ", the record was " + job.carried;
                    if (job.lastOpAt > robot.now() && held > job.carried) {
                        // The world is older than the record, and the robot holds more than the record says.
                        // Then the record counts deliveries of that load, but the world does not contain them.
                        // The delivery limit uses the delivered total: with the wrong total the load would stay on board.
                        job.delivered = Math.max(0, job.delivered - (held - job.carried));
                    }
                    job.carried = held;
                    job.lastOpAt = Math.min(job.lastOpAt, robot.now());
                    job.recoveries++;
                }
                // A resume can complete the job: the owner accepted the observed load, and nothing is on board.
                set(job, !job.keepStock() && job.carried == 0 && job.delivered >= job.quantity ? State.DONE : State.RUNNING, Cause.NONE, "", true);
            }
        }
        if (robot != null) robot.wake();
    }

    /** The diary entries that the actor owns, the oldest first. Only the owner and an operator read an entry, as for a job. */
    public List<Entry> diary(Actor actor) {
        return diary.stream().filter(entry -> actor.owns(entry.owner())).toList();
    }

    /** What the runtime removed from the diaries that the actor owns. */
    public Forgotten forgotten(Actor actor) {
        int entries = 0;
        long until = UNKNOWN;
        for (Map.Entry<UUID, Forgotten> owner : forgotten.entrySet()) {
            if (!actor.owns(owner.getKey())) continue;
            entries += owner.getValue().entries();
            until = Math.max(until, owner.getValue().until());
        }
        return new Forgotten(entries, until);
    }

    /**
     * The actor opens a contact point at this game time. The runtime records it.
     * Returns the game time of the opening before it, or UNKNOWN at the first opening of the actor in this world.
     */
    public long open(Actor actor, long now) {
        Long last = openings.put(actor.id(), now);
        setDirty();
        return last == null ? UNKNOWN : last;
    }

    /** The status of a robot and its job, for a request of a player. Only the owner and an operator read it. */
    public String status(Actor actor, int robotId, long now) {
        UUID robotOwner = robots.get(robotId);
        Job job = jobs.get(robotId);
        if ((robotOwner != null && !actor.owns(robotOwner)) || (job != null && !actor.owns(job.owner))) {
            throw new Refusal("Robot #" + robotId + " is not yours.");
        }
        return describe(robotId, now);
    }

    /** The whole record of a robot, without a check of the actor. A request of a player goes through status(). */
    String describe(int robotId, long now) {
        Job j = jobs.get(robotId);
        if (j == null) return "Robot #" + robotId + ": no job.";
        return "Job " + j.id + " on robot #" + robotId + ": " + j.state + (j.reason.isEmpty() ? "" : " (" + j.reason + ")")
            + "\n  " + (j.keepStock() ? "keep at least " : "") + j.quantity + " x " + itemName(j.item)
            + " from '" + j.from + "' to '" + j.to + "', reserve " + j.reserve + ", revision " + j.revision
            + "\n  taken " + j.taken + ", delivered " + j.delivered + ", on board " + j.carried + " (recorded)"
            + (j.recoveries > 0 ? "\n  " + j.recoveries + " recovery. The totals can be inexact." : "")
            + "\n  source stock: " + seen(j.fromSeen, j.fromSeenAt, now) + ", destination stock: " + seen(j.toSeen, j.toSeenAt, now)
            + "\n  robot: " + (RobotApi.live(robotId) == null ? "not running or not loaded" : "running")
            + ", last contact: " + (j.lastContact == UNKNOWN ? "none since server start" : (now - j.lastContact) + " ticks ago")
            + ", executor: " + (j.executor.isEmpty() ? "none" : j.executor)
            + "\n  last operation: " + (j.lastOp.isEmpty() ? "none" : j.lastOp);
    }

    /**
     * Writes the state to the disk immediately. The runtime does this after each transfer.
     * After a crash, the world and this record on the disk can have different ages.
     * If the world contains a transfer that the record does not contain, the runtime cannot detect it.
     * With this write, the record is never older than the world. The runtime can detect the opposite condition.
     */
    // ponytail: one small file write on the server thread for each transfer. Use an async write if this is slow.
    void flush(MinecraftServer server) {
        save(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(MindCraft.MOD_ID + ".dat").toFile());
    }

    void set(Job job, State state, String reason) {
        set(job, state, Cause.NONE, reason);
    }

    /**
     * The one place where the state of a job changes. The cause belongs to the state: a new state without a cause has NONE.
     * A change of the state or of the cause writes an entry in the diary. The same state with the same cause writes none:
     * a robot that tries again reports the same state again, and only the reason can be new.
     */
    void set(Job job, State state, Cause cause, String reason) {
        set(job, state, cause, reason, false);
    }

    /** As above. "Requested": a request of an actor makes the change, not an observation of the runtime. Such a change gives no notice. */
    private void set(Job job, State state, Cause cause, String reason, boolean requested) {
        boolean changed = job.state != state || job.cause != cause;
        job.state = state;
        job.reason = reason;
        job.cause = cause;
        if (changed) write(job, requested);
        setDirty();
    }

    /**
     * Writes the job as it is now in the diary. Above the limit, the oldest entries of the same owner go.
     * Then the attention rule reads the entry: it can give a notice (D30). A copy that load() made outside the server gives none.
     */
    private void write(Job job, boolean requested) {
        Entry entry = new Entry(server == null ? 0 : server.overworld().getGameTime(), job.owner, job.id, job.robot, job.from, job.to,
            job.item, job.state, job.cause, job.reason, requested, job.quantity, job.reserve, job.delivered, job.carried);
        diary.add(entry);
        forget(job.owner);
        if (server != null) Notices.entry(server, entry);
    }

    /** Removes the oldest entries of the owner above the limit, and records what it removed. The entries of other owners stay. */
    private void forget(UUID owner) {
        int count = (int) diary.stream().filter(entry -> entry.owner().equals(owner)).count();
        for (Iterator<Entry> entries = diary.iterator(); count > DIARY_LIMIT && entries.hasNext(); ) {
            Entry old = entries.next();
            if (!old.owner().equals(owner)) continue;
            entries.remove();
            count--;
            Forgotten before = forgotten.getOrDefault(owner, new Forgotten(0, UNKNOWN));
            forgotten.put(owner, new Forgotten(before.entries() + 1, Math.max(before.until(), old.time())));
        }
    }

    /** The job waits for a condition that can change without the owner. The cause is what the runtime observed. */
    void waiting(Job job, Cause cause, String reason) {
        set(job, State.WAITING, cause, reason);
    }

    /** The job needs the hands of the owner. It continues only after a resume. */
    void blocked(Job job, Cause cause, String reason) {
        set(job, State.BLOCKED, cause, reason);
    }

    /**
     * A Lua module reports an impediment that it cannot remove. The runtime decides the state from the kind (D34).
     * A condition that can end by itself is a wait. A condition that needs the hands of the owner is a block.
     * An unknown kind is a block without a cause. The reason keeps the kind: the cause cannot name it.
     */
    void impediment(Job job, String kind, String detail) {
        switch (kind) {
            case OBSTACLE -> waiting(job, Cause.PATH_OBSTRUCTED, detail);
            case NO_FUEL -> blocked(job, Cause.OUT_OF_FUEL, detail);
            default -> blocked(job, Cause.NONE, kind + ": " + detail);
        }
    }

    /** The robot moved. A job that waited for a free path works again. The owner gives no resume. */
    void moved(Job job) {
        if (job.state == State.WAITING && job.cause == Cause.PATH_OBSTRUCTED) set(job, State.RUNNING, "");
    }

    private void requireRobot(Actor actor, int robotId) {
        UUID owner = robots.get(robotId);
        if (owner == null) throw new Refusal("Robot #" + robotId + " is not enrolled.");
        if (!actor.owns(owner)) throw new Refusal("Robot #" + robotId + " is not yours.");
    }

    private static String seen(int count, long at, long now) {
        return count == UNKNOWN ? "unknown" : count + " (seen " + (now - at) + " ticks ago)";
    }

    static String itemName(Item item) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(item));
    }

    // --- Persistence -------------------------------------------------------------------------------------------

    /**
     * Writes the record to the disk. The ordinary save of the world and flush() both call this method.
     * The record goes to a temporary file in the same folder. One move then puts that file in the place of the record.
     * Thus a stop of the process during the write leaves the earlier record or the new one on the disk, never a part of one.
     * A failure leaves the earlier record, writes an error in the server log, and keeps the record dirty: the next save tries again.
     * The content is the same as in SavedData.save(File) of Minecraft 1.20.1.
     * Limit: the runtime does not force the data to the disk before the move. A stop of the operating system can still lose the write.
     */
    @Override
    public void save(File file) {
        if (!isDirty()) return;
        CompoundTag root = new CompoundTag();
        root.put("data", save(new CompoundTag()));
        NbtUtils.addCurrentDataVersion(root);
        Path target = file.toPath();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(root, temporary.toFile());
            // On Windows the JDK uses MoveFileEx with MOVEFILE_REPLACE_EXISTING. On Linux it uses rename. Both replace the record.
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            setDirty(false);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
            MindCraft.LOGGER.error("MindCraft could not write its record to {}. The earlier record stays on the disk.", target, e);
        }
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        root.putInt("nextJobId", nextJobId);
        CompoundTag stationTags = new CompoundTag();
        stations.forEach((name, s) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", s.owner());
            t.putString("dim", s.dim().location().toString());
            t.put("inventory", NbtUtils.writeBlockPos(s.inventory()));
            t.put("dock", NbtUtils.writeBlockPos(s.dock()));
            stationTags.put(name, t);
        });
        root.put("stations", stationTags);
        ListTag placeTags = new ListTag();
        for (Place place : places) {
            CompoundTag t = new CompoundTag();
            t.putUUID("owner", place.owner());
            t.putString("dim", place.dim().location().toString());
            t.put("pos", NbtUtils.writeBlockPos(place.pos()));
            placeTags.add(t);
        }
        root.put("places", placeTags);
        CompoundTag robotTags = new CompoundTag();
        robots.forEach((id, owner) -> robotTags.putUUID(id.toString(), owner));
        root.put("robots", robotTags);
        CompoundTag jobTags = new CompoundTag();
        jobs.forEach((robotId, j) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("id", j.id);
            t.putUUID("owner", j.owner);
            t.putString("module", j.module);
            t.putInt("revision", j.revision);
            t.putString("from", j.from);
            t.putString("to", j.to);
            t.putString("item", itemName(j.item));
            t.putInt("quantity", j.quantity);
            t.putInt("reserve", j.reserve);
            t.putString("state", j.state.name());
            t.putString("reason", j.reason);
            t.putString("cause", j.cause.name());
            t.putInt("taken", j.taken);
            t.putInt("delivered", j.delivered);
            t.putInt("carried", j.carried);
            t.putInt("fromSeen", j.fromSeen);
            t.putInt("toSeen", j.toSeen);
            t.putLong("fromSeenAt", j.fromSeenAt);
            t.putLong("toSeenAt", j.toSeenAt);
            t.putString("executor", j.executor);
            t.putInt("ops", j.ops);
            t.putString("lastOp", j.lastOp);
            t.putLong("lastOpAt", j.lastOpAt);
            t.putInt("recoveries", j.recoveries);
            jobTags.put(robotId.toString(), t);
        });
        root.put("jobs", jobTags);
        ListTag diaryTags = new ListTag();
        for (Entry e : diary) {
            CompoundTag t = new CompoundTag();
            t.putLong("time", e.time());
            t.putUUID("owner", e.owner());
            t.putInt("job", e.job());
            t.putInt("robot", e.robot());
            t.putString("from", e.from());
            t.putString("to", e.to());
            t.putString("item", itemName(e.item()));
            t.putString("state", e.state().name());
            t.putString("cause", e.cause().name());
            t.putString("reason", e.reason());
            t.putBoolean("requested", e.requested());
            t.putInt("quantity", e.quantity());
            t.putInt("reserve", e.reserve());
            t.putInt("delivered", e.delivered());
            t.putInt("carried", e.carried());
            diaryTags.add(t);
        }
        root.put("diary", diaryTags);
        CompoundTag forgottenTags = new CompoundTag();
        forgotten.forEach((owner, f) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("entries", f.entries());
            t.putLong("until", f.until());
            forgottenTags.put(owner.toString(), t);
        });
        root.put("forgotten", forgottenTags);
        CompoundTag openingTags = new CompoundTag();
        openings.forEach((player, time) -> openingTags.putLong(player.toString(), time));
        root.put("openings", openingTags);
        return root;
    }

    public static MindRuntime load(CompoundTag root) {
        MindRuntime rt = new MindRuntime();
        rt.nextJobId = root.getInt("nextJobId");
        CompoundTag stationTags = root.getCompound("stations");
        for (String name : stationTags.getAllKeys()) {
            CompoundTag t = stationTags.getCompound(name);
            rt.stations.put(name, new Station(t.getUUID("owner"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(t.getString("dim"))),
                NbtUtils.readBlockPos(t.getCompound("inventory")), NbtUtils.readBlockPos(t.getCompound("dock"))));
        }
        for (Tag tag : root.getList("places", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) tag;
            rt.places.add(new Place(t.getUUID("owner"), ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(t.getString("dim"))),
                NbtUtils.readBlockPos(t.getCompound("pos"))));
        }
        CompoundTag robotTags = root.getCompound("robots");
        for (String id : robotTags.getAllKeys()) rt.robots.put(Integer.valueOf(id), robotTags.getUUID(id));
        CompoundTag jobTags = root.getCompound("jobs");
        for (String robotId : jobTags.getAllKeys()) {
            CompoundTag t = jobTags.getCompound(robotId);
            Job j = new Job();
            j.id = t.getInt("id");
            j.owner = t.getUUID("owner");
            j.module = t.getString("module");
            j.revision = t.getInt("revision");
            j.from = t.getString("from");
            j.to = t.getString("to");
            j.item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(t.getString("item")));
            j.quantity = t.getInt("quantity");
            j.reserve = t.getInt("reserve");
            j.state = State.valueOf(t.getString("state"));
            j.reason = t.getString("reason");
            // A record of an older version has no cause.
            if (t.contains("cause")) j.cause = Cause.valueOf(t.getString("cause"));
            j.taken = t.getInt("taken");
            j.delivered = t.getInt("delivered");
            j.carried = t.getInt("carried");
            j.fromSeen = t.getInt("fromSeen");
            j.toSeen = t.getInt("toSeen");
            j.fromSeenAt = t.getLong("fromSeenAt");
            j.toSeenAt = t.getLong("toSeenAt");
            j.executor = t.getString("executor");
            j.ops = t.getInt("ops");
            j.lastOp = t.getString("lastOp");
            j.lastOpAt = t.getLong("lastOpAt");
            j.recoveries = t.getInt("recoveries");
            j.robot = Integer.parseInt(robotId);
            rt.jobs.put(j.robot, j);
        }
        // A record of an older version has no diary: it loads with an empty one.
        for (Tag tag : root.getList("diary", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) tag;
            rt.diary.add(new Entry(t.getLong("time"), t.getUUID("owner"), t.getInt("job"), t.getInt("robot"), t.getString("from"),
                t.getString("to"), ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(t.getString("item"))),
                State.valueOf(t.getString("state")), Cause.valueOf(t.getString("cause")), t.getString("reason"), t.getBoolean("requested"),
                t.getInt("quantity"), t.getInt("reserve"), t.getInt("delivered"), t.getInt("carried")));
        }
        CompoundTag forgottenTags = root.getCompound("forgotten");
        for (String owner : forgottenTags.getAllKeys()) {
            CompoundTag t = forgottenTags.getCompound(owner);
            rt.forgotten.put(UUID.fromString(owner), new Forgotten(t.getInt("entries"), t.getLong("until")));
        }
        // A record of an older version has no openings: the next opening of each player is a first opening.
        CompoundTag openingTags = root.getCompound("openings");
        for (String player : openingTags.getAllKeys()) rt.openings.put(UUID.fromString(player), openingTags.getLong(player));
        return rt;
    }
}
