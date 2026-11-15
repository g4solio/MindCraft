package io.github.g4solio.mindcraft;

import dan200.computercraft.api.ComputerCraftAPI;
import dan200.computercraft.api.component.ComputerComponents;
import dan200.computercraft.api.lua.IComputerSystem;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.turtle.ITurtleAccess;
import io.github.g4solio.mindcraft.MindRuntime.Cause;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.State;
import io.github.g4solio.mindcraft.MindRuntime.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "mindcraft" Lua API. CC: Tweaked creates one instance for each turtle computer.
 * <p>
 * The server binds each instance to its computer. A Lua program cannot claim to be a different robot.
 * All functions run on the server thread. Thus an observation, a transfer and its record occur in the same tick.
 */
public class RobotApi implements ILuaAPI {
    /** Computer ID to the newest running instance. An older instance with the same ID loses its authority. */
    private static final Map<Integer, RobotApi> LIVE = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> BOOTS = new ConcurrentHashMap<>();

    final IComputerSystem computer;
    final ITurtleAccess turtle;

    private RobotApi(IComputerSystem computer, ITurtleAccess turtle) {
        this.computer = computer;
        this.turtle = turtle;
    }

    static void register() {
        ComputerCraftAPI.registerAPIFactory(computer -> {
            ITurtleAccess turtle = computer.getComponent(ComputerComponents.TURTLE);
            return turtle == null ? null : new RobotApi(computer, turtle);
        });
    }

    /**
     * Each server start calls this. See MindCraft.aboutToStart.
     * CC: Tweaked stops each computer at a server stop, but with a time limit. A session that it did not stop in time stays in the map.
     * Such a session must not count as a running robot of the next server.
     */
    static void newSession() {
        LIVE.clear();
        BOOTS.clear();
    }

    public static @Nullable RobotApi live(int computerId) {
        return LIVE.get(computerId);
    }

    public static @Nullable RobotApi at(Level level, BlockPos pos) {
        for (RobotApi robot : LIVE.values()) {
            if (robot.turtle.getLevel() == level && robot.turtle.getPosition().equals(pos)) return robot;
        }
        return null;
    }

    static int boots(int computerId) {
        return BOOTS.getOrDefault(computerId, 0);
    }

    public int id() {
        return computer.getID();
    }

    void wake() {
        computer.queueEvent("mindcraft");
    }

    int held(Item item) {
        return count(new InvWrapper(turtle.getInventory()), item);
    }

    @Override
    public String[] getNames() {
        return new String[]{"mindcraft"};
    }

    @Override
    public void startup() {
        LIVE.put(id(), this);
        BOOTS.merge(id(), 1, Integer::sum);
    }

    @Override
    public void shutdown() {
        LIVE.remove(id(), this);
    }

    // --- Lua functions -------------------------------------------------------------------------------------------

    @LuaFunction(mainThread = true)
    public final boolean enrolled() {
        return runtime().robots.containsKey(id());
    }

    /** The true position and direction. The robot does not count its moves. */
    @LuaFunction(mainThread = true)
    public final Object[] pose() {
        BlockPos pos = turtle.getPosition();
        return new Object[]{pos.getX(), pos.getY(), pos.getZ(), turtle.getDirection().getName()};
    }

    /**
     * The module tells the runtime which version runs the job. Returns the ID of that job.
     * A module runs one job only. With this ID it sees when the robot gets a different job.
     */
    @LuaFunction(mainThread = true)
    public final int attach(String executor) throws LuaException {
        Job job = activeJob();
        job.executor = executor;
        runtime().setDirty();
        return job.id;
    }

    /** The current job, or nil. This call also compares the real load with the record. */
    @LuaFunction(mainThread = true)
    public final @Nullable Map<String, Object> status() throws LuaException {
        Job job = contact();
        if (job == null) return null;
        int held = held(job.item);
        if (job.state.active() && held != job.carried) {
            runtime().set(job, State.RECOVERY, "the robot holds " + held + ", the record says " + job.carried);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("id", job.id);
        out.put("module", job.behaviour.id);
        out.put("state", job.state.name());
        // The module gets no "need" while the destination stock is unknown. Then it must observe first.
        if (job.need() != MindRuntime.UNKNOWN) out.put("need", job.need());
        out.put("reason", job.reason);
        out.put("item", MindRuntime.itemName(job.item));
        out.put("carried", job.carried);
        out.put("from", point(runtime().stations.get(job.from).dock()));
        out.put("to", point(runtime().stations.get(job.to).dock()));
        return out;
    }

    /** Moves the job item from the source inventory into the robot. Returns the moved quantity. */
    @LuaFunction(mainThread = true)
    public final Object[] take() throws LuaException {
        Job job = activeJob();
        IItemHandler inventory = docked(job, job.from, "source");
        if (inventory == null) return new Object[]{0, job.reason};
        int need = job.need();
        if (need == MindRuntime.UNKNOWN) throw new LuaException("The destination stock is unknown. Observe it first.");
        int stock = count(inventory, job.item);
        // The check and the effect are in the same tick. A different actor cannot change the stock between them.
        int want = Math.min(need, stock - job.reserve);
        IItemHandler hold = new InvWrapper(turtle.getInventory());
        int moved = want > 0 ? move(inventory, hold, job.item, want) : 0;
        job.took(moved, stock, now());
        if (moved > 0 || job.carried > 0) {
            runtime().set(job, State.RUNNING, "");
        } else if (need == 0) {
            runtime().settle(job);
        } else if (want <= 0) {
            runtime().waiting(job, Cause.SOURCE_AT_RESERVE, "the source has " + stock + ", the reserve is " + job.reserve);
        } else if (ItemHandlerHelper.insertItemStacked(hold, new ItemStack(job.item), true).isEmpty()) {
            // The robot has room for an item, but nothing moved. The record must not say that the robot is full.
            runtime().waiting(job, Cause.SOURCE_KEEPS, "the source has " + stock + " but released nothing");
        } else {
            runtime().waiting(job, Cause.ROBOT_FULL, "the robot inventory is full");
        }
        record(job, "take", moved);
        return new Object[]{moved, job.reason};
    }

    /** Observes the destination stock from its dock. A "keep_stock" job uses this to know when to bring more. */
    @LuaFunction(mainThread = true)
    public final Object[] observe() throws LuaException {
        Job job = activeJob();
        IItemHandler inventory = docked(job, job.to, "destination");
        if (inventory == null) return new Object[]{null, job.reason};
        job.observed(count(inventory, job.item), now());
        runtime().settle(job);
        return new Object[]{job.toSeen, job.reason};
    }

    /**
     * Moves the load from the robot into the destination inventory. Returns the moved quantity.
     * The delivery never goes above the quantity of the job, also when the owner lowered it after the pickup.
     * The behaviour of the job gives the limit, with the stock that this call counts in the same tick.
     */
    @LuaFunction(mainThread = true)
    public final Object[] give() throws LuaException {
        Job job = activeJob();
        IItemHandler inventory = docked(job, job.to, "destination");
        if (inventory == null) return new Object[]{0, job.reason};
        int allowed = Math.max(0, Math.min(job.carried, job.behaviour.room(job, count(inventory, job.item))));
        int moved = allowed > 0 ? move(new InvWrapper(turtle.getInventory()), inventory, job.item, allowed) : 0;
        job.gave(moved, count(inventory, job.item), now());
        if (moved < allowed) {
            runtime().waiting(job, Cause.DESTINATION_FULL, "the destination is full, " + job.carried + " still on board");
        } else {
            runtime().settle(job);
        }
        record(job, "give", moved);
        return new Object[]{moved, job.reason};
    }

    /**
     * The module reports an impediment that it cannot remove: a kind and the detail text, with the position.
     * The runtime decides the state from the kind. Returns the new state: "WAITING" when the module must try again later.
     */
    @LuaFunction(mainThread = true)
    public final String impediment(String kind, String detail) throws LuaException {
        Job job = activeJob();
        runtime().impediment(job, kind, detail);
        return job.state.name();
    }

    /** The module tells the runtime that the robot moved. A job that waited for a free path works again. */
    @LuaFunction(mainThread = true)
    public final void moved() throws LuaException {
        runtime().moved(activeJob());
    }

    // --- Internals -------------------------------------------------------------------------------------------------

    private MindRuntime runtime() {
        return MindRuntime.get(computer.getLevel().getServer());
    }

    long now() {
        return computer.getLevel().getGameTime();
    }

    /** Checks the authority of this instance. Returns the job of this robot, or null. */
    private @Nullable Job contact() throws LuaException {
        if (LIVE.get(id()) != this) throw new LuaException("A newer session of robot #" + id() + " replaced this session.");
        if (!runtime().robots.containsKey(id())) return null;
        Job job = runtime().jobs.get(id());
        if (job != null) job.lastContact = now();
        return job;
    }

    private Job activeJob() throws LuaException {
        Job job = contact();
        if (job == null || !job.state.active()) throw new LuaException("This robot has no active job.");
        return job;
    }

    /** The inventory of a station, only when the robot is at its dock. Sets the job state when there is no inventory. */
    private @Nullable IItemHandler docked(Job job, String stationName, String role) throws LuaException {
        Station station = runtime().stations.get(stationName);
        Level level = turtle.getLevel();
        if (level.dimension() != station.dim() || !turtle.getPosition().equals(station.dock())) {
            throw new LuaException("The robot is not at the " + role + " dock.");
        }
        if (!level.isLoaded(station.inventory())) {
            runtime().waiting(job, role.equals("source") ? Cause.SOURCE_UNLOADED : Cause.DESTINATION_UNLOADED,
                "the " + role + " inventory is not loaded, its stock is unknown");
            return null;
        }
        IItemHandler inventory = inventoryAt(level, station.inventory(), station.dock());
        if (inventory == null) {
            runtime().blocked(job, role.equals("source") ? Cause.SOURCE_NO_INVENTORY : Cause.DESTINATION_NO_INVENTORY,
                "the " + role + " station has no inventory at " + station.inventory().toShortString());
        }
        return inventory;
    }

    /** The inventory at a position, through the side that faces the dock. */
    static @Nullable IItemHandler inventoryAt(Level level, BlockPos inventory, BlockPos dock) {
        if (!level.isLoaded(inventory)) return null;
        BlockEntity blockEntity = level.getBlockEntity(inventory);
        if (blockEntity == null) return null;
        Direction side = null;
        for (Direction direction : Direction.values()) {
            if (inventory.relative(direction).equals(dock)) side = direction;
        }
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }

    // ponytail: items match by type only. NBT is ignored. Add a stack filter when a job needs it.
    static int count(IItemHandler inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** Moves at most {@code max} items of one type. Returns the quantity that arrived. */
    static int move(IItemHandler from, IItemHandler to, Item item, int max) {
        int moved = 0;
        for (int slot = 0; slot < from.getSlots() && moved < max; slot++) {
            if (!from.getStackInSlot(slot).is(item)) continue;
            ItemStack offer = from.extractItem(slot, max - moved, true);
            int accepted = offer.getCount() - ItemHandlerHelper.insertItemStacked(to, offer, true).getCount();
            if (accepted <= 0) continue;
            ItemStack taken = from.extractItem(slot, accepted, false);
            ItemStack rest = ItemHandlerHelper.insertItemStacked(to, taken, false);
            moved += taken.getCount() - rest.getCount();
            if (!rest.isEmpty()) from.insertItem(slot, rest, false);
        }
        return moved;
    }

    private void record(Job job, String kind, int moved) {
        job.operated(kind, moved, now());
        MindCraft.LOGGER.info("Robot #{} job {} op {}", id(), job.id, job.lastOp);
        if (moved > 0) {
            runtime().setDirty();
            runtime().flush(computer.getLevel().getServer());
        }
    }

    private static Map<String, Object> point(BlockPos pos) {
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
    }
}
