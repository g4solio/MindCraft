package io.github.g4solio.mindcraft;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.sun.net.httpserver.HttpServer;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.Cause;
import io.github.g4solio.mindcraft.MindRuntime.Entry;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.Order;
import io.github.g4solio.mindcraft.MindRuntime.Refusal;
import io.github.g4solio.mindcraft.MindRuntime.State;
import io.github.g4solio.mindcraft.MindRuntime.Station;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * In-game checks. "gradlew runGameTestServer" runs them on a real headless server with real turtles.
 * The names refer to the adversarial matrix in docs/MINDCRAFT_CLAUDE_BRIEF.md, section 8.
 */
// ponytail: the tests are in the main source set and go into the mod jar. Move them before a public release.
@GameTestHolder(MindCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public class MindCraftTests {
    static final Actor ADMIN = new Actor(Util.NIL_UUID, true);
    static final String EMPTY = "empty";
    static final int TIMEOUT = 2400;

    private static long nextTick;

    static {
        // The game test server runs ticks without a pause, but computers run in real time.
        // This listener holds the server at 20 ticks for each second, as in a real game.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase != TickEvent.Phase.END) return;
            long now = System.nanoTime();
            if (nextTick > now) LockSupport.parkNanos(nextTick - now);
            nextTick = Math.max(nextTick, now) + 50_000_000L;
        });
    }

    /**
     * The test setup. Each object is at y = 1, z = 1. The path between the docks is free.
     * <pre>
     *   x:  0         1      2        3    4      5
     *       [source]  dock   turtle   .    dock   [destination]
     * </pre>
     * Simplification: the turtle gets its fuel as a number. It does not burn the job item.
     */
    static final class Rig {
        final BlockPos source, sourceDock, start, destDock, dest;
        final GameTestHelper helper;
        final ServerLevel level;
        final MindRuntime runtime;
        final Actor actor;
        final String from, to;
        int id = -1;
        private int bootsAtEnroll;

        /** Only the positions and names. This constructor does not change the world. */
        Rig(GameTestHelper helper, String name, Actor actor, BlockPos origin) {
            this(helper, name, actor, origin, origin.east(1), origin.east(2), origin.east(4), origin.east(5));
        }

        /** A rig with other positions. This constructor does not change the world. */
        Rig(GameTestHelper helper, String name, Actor actor, BlockPos source, BlockPos sourceDock, BlockPos start,
            BlockPos destDock, BlockPos dest) {
            this.source = source;
            this.sourceDock = sourceDock;
            this.start = start;
            this.destDock = destDock;
            this.dest = dest;
            this.helper = helper;
            this.level = helper.getLevel();
            this.runtime = MindRuntime.get(level.getServer());
            this.actor = actor;
            this.from = name + "_source";
            this.to = name + "_destination";
        }

        Rig(GameTestHelper helper, String name, int sourceCoal, int fuel) {
            this(helper, name, sourceCoal, fuel, ADMIN);
        }

        Rig(GameTestHelper helper, String name, int sourceCoal, int fuel, Actor actor) {
            this(helper, name, actor, new BlockPos(0, 1, 1));
            build(sourceCoal, fuel);
        }

        void build(int sourceCoal, int fuel) {
            helper.setBlock(source, Blocks.CHEST);
            helper.setBlock(dest, Blocks.CHEST);
            if (sourceCoal > 0) chest(source).setItem(0, new ItemStack(Items.COAL, sourceCoal));
            placeTurtle(helper, start, -1, fuel, actor.admin() ? null : actor.id());
            runtime.addStation(actor, from, level, helper.absolutePos(source), helper.absolutePos(sourceDock));
            runtime.addStation(actor, to, level, helper.absolutePos(dest), helper.absolutePos(destDock));
        }

        /** Waits for the turtle, then enrolls it. The enrollment restarts the turtle. */
        void enroll() {
            RobotApi robot = RobotApi.at(level, helper.absolutePos(start));
            check(robot != null, "the turtle is not running yet");
            runtime.enroll(actor, robot);
            id = robot.id();
            bootsAtEnroll = RobotApi.boots(id);
            MindCraft.reboot(level.getServer(), id);
        }

        void restarted() {
            check(RobotApi.boots(id) > bootsAtEnroll && RobotApi.live(id) != null, "the turtle did not restart yet");
        }

        /** A sequence that continues when the enrolled robot runs. */
        GameTestSequence begin() {
            return helper.startSequence().thenWaitUntil(this::enroll).thenWaitUntil(this::restarted);
        }

        Job startJob(int count, int reserve) {
            return runtime.start(actor, order(MindRuntime.TRANSPORT, count, reserve));
        }

        Order order(String module, int quantity, int reserve) {
            return new Order(module, id, from, to, Items.COAL, quantity, reserve);
        }

        Job job() {
            return runtime.jobs.get(id);
        }

        RobotApi robot() {
            return RobotApi.live(id);
        }

        String describe() {
            return runtime.describe(id, level.getGameTime());
        }

        void state(State expected) {
            check(job() != null && job().state == expected, "expected " + expected + ", but: " + describe());
        }

        void carried(int expected) {
            check(job().carried == expected && job().state.active(), "expected " + expected + " on board, but: " + describe());
        }

        /** The robot is at a position of the rig, and the job says that the load on board is the expected one. */
        void at(BlockPos relative, int carried) {
            RobotApi robot = robot();
            check(robot != null, "the robot is not running");
            BlockPos position = robot.turtle.getPosition();
            check(position.equals(helper.absolutePos(relative)) && job().carried == carried,
                "expected the robot at " + helper.absolutePos(relative).toShortString() + " with " + carried
                    + " on board, but it is at " + position.toShortString() + ". " + describe());
        }

        Container chest(BlockPos relative) {
            return (Container) level.getBlockEntity(helper.absolutePos(relative));
        }

        int coal(BlockPos relative) {
            return coal(chest(relative));
        }

        static int coal(Container container) {
            int total = 0;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).is(Items.COAL)) total += container.getItem(slot).getCount();
            }
            return total;
        }

        int held() {
            return coal(robot().turtle.getInventory());
        }

        /** The final checks of a complete transfer. */
        void assertTransferred(int sourceLeft, int delivered) {
            state(State.DONE);
            check(coal(source) == sourceLeft && coal(dest) == delivered && held() == 0,
                "source " + coal(source) + ", destination " + coal(dest) + ", robot " + held() + ". " + describe());
            check(job().carried == 0, describe());
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static void refused(Runnable request, String expectedMessage) {
        try {
            request.run();
        } catch (Refusal refusal) {
            check(refusal.getMessage().contains(expectedMessage), "wrong refusal: " + refusal.getMessage());
            return;
        }
        throw new GameTestAssertException("the runtime accepted a request that it must refuse: " + expectedMessage);
    }

    /** Places a turtle that is on. A computer ID of -1 lets CC: Tweaked assign the ID. */
    static void placeTurtle(GameTestHelper helper, BlockPos relative, int computerId, int fuel, UUID owner) {
        helper.setBlock(relative, ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse("computercraft:turtle_normal")));
        BlockEntity turtle = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        CompoundTag tag = turtle.saveWithoutMetadata();
        tag.putBoolean("On", true);
        tag.putInt("Fuel", fuel);
        if (computerId >= 0) tag.putInt("ComputerId", computerId);
        if (owner != null) {
            CompoundTag ownerTag = new CompoundTag();
            ownerTag.putLong("UpperId", owner.getMostSignificantBits());
            ownerTag.putLong("LowerId", owner.getLeastSignificantBits());
            ownerTag.putString("Name", "test");
            tag.put("Owner", ownerTag);
        }
        turtle.load(tag);
    }

    // --- S1: transport --------------------------------------------------------------------------------------------

    /** The base case. Also T02: an inventory that no robot observed is "unknown", not zero. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s1_transfer(GameTestHelper helper) {
        Rig rig = new Rig(helper, "s1", 64, 1000);
        rig.begin()
            .thenExecute(() -> {
                rig.startJob(24, 0);
                check(rig.job().fromSeen == MindRuntime.UNKNOWN, "the stock must be unknown before the first observation");
                check(rig.describe().contains("source stock: unknown"), rig.describe());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                rig.assertTransferred(40, 24);
                Job job = rig.job();
                check(job.taken == 24 && job.delivered == 24 && job.fromSeen == 40 && job.toSeen == 24, rig.describe());
                check(job.executor.equals("transport@3"), rig.describe());
                MindCraft.LOGGER.info("s1_transfer final status:\n{}", rig.describe());
            })
            .thenSucceed();
    }

    /** The same flow through the /mindcraft commands, as the server console. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s1_commands(GameTestHelper helper) {
        Rig rig = new Rig(helper, "cmd", 64, 1000);
        helper.startSequence()
            .thenWaitUntil(() -> {
                RobotApi robot = RobotApi.at(rig.level, helper.absolutePos(rig.start));
                check(robot != null, "the turtle is not running yet");
                rig.id = robot.id();
                command(helper, "mindcraft station add cmd_a " + xyz(helper, rig.source) + " " + xyz(helper, rig.sourceDock));
                command(helper, "mindcraft station add cmd_b " + xyz(helper, rig.dest) + " " + xyz(helper, rig.destDock));
                command(helper, "mindcraft robot enroll " + xyz(helper, rig.start));
                check(rig.runtime.stations.containsKey("cmd_a") && rig.runtime.stations.containsKey("cmd_b"), "station add failed");
                check(rig.runtime.robots.containsKey(rig.id), "robot enroll failed");
            })
            .thenWaitUntil(() -> {
                // The runtime refuses the job while the turtle restarts.
                command(helper, "mindcraft job transport " + rig.id + " cmd_a cmd_b minecraft:coal 24");
                check(rig.job() != null, "the job did not start yet");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                rig.assertTransferred(40, 24);
                command(helper, "mindcraft job pause " + rig.id);
                rig.state(State.DONE);
            })
            .thenSucceed();
    }

    private static void command(GameTestHelper helper, String command) {
        MindCraft.command(helper.getLevel().getServer(), command);
    }

    private static String xyz(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** T01: no coal. The job waits with a readable reason. The retries have a limited rate. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t01_source_empty(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t01", 0, 1000);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenIdle(260)
            .thenExecute(() -> {
                rig.state(State.WAITING);
                check(rig.job().reason.contains("the source has 0"), rig.describe());
                check(rig.job().ops >= 2 && rig.job().ops <= 4, "expected 2 to 4 attempts in 13 seconds, got " + rig.job().ops);
                rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 24));
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(0, 24))
            .thenSucceed();
    }

    /** T03: the destination accepts only a part. The runtime shows the real quantity and the remainder. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t03_destination_partial(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t03", 64, 1000);
        Container dest = rig.chest(rig.dest);
        dest.setItem(0, new ItemStack(Items.COAL, 60));
        for (int slot = 1; slot < dest.getContainerSize(); slot++) dest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> {
                rig.state(State.WAITING);
                check(rig.job().delivered == 4, rig.describe());
            })
            .thenExecute(() -> {
                Job job = rig.job();
                check(job.taken == 24 && job.carried == 20 && rig.held() == 20 && rig.coal(rig.dest) == 64, rig.describe());
                check(job.reason.contains("20 still on board"), rig.describe());
                dest.setItem(1, ItemStack.EMPTY);
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 84))
            .thenSucceed();
    }

    /**
     * T04: the destination disappears while the robot carries the load. The robot keeps the load.
     * The job is blocked with a cause of its own, and Uno takes its sentence from the cause.
     * A record of an older version has no cause: then Uno gives the recorded reason, as before.
     * The notice to the owner names the cause too (D30).
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t04_destination_removed(GameTestHelper helper) {
        listen();
        Actor player = player("t04");
        Rig rig = new Rig(helper, "t04", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> helper.setBlock(rig.dest, Blocks.AIR))
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            .thenExecute(() -> {
                check(rig.job().cause == Cause.DESTINATION_NO_INVENTORY && rig.job().reason.contains("no inventory"), rig.describe());
                check(rig.held() == 24 && rig.job().delivered == 0, rig.describe());
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t04_destination_removed report: {}", report);
                check(report.contains("I find no inventory at '" + rig.to + "'") && !report.contains("Recorded reason"), report);
                check(notices(player).equals(List.of(subject(rig, rig.job()) + " is blocked: there is no inventory at '" + rig.to + "'.")),
                    notices(player).toString());

                long now = rig.level.getGameTime();
                CompoundTag saved = rig.runtime.save(new CompoundTag());
                check(MindRuntime.load(saved).jobs.get(rig.id).cause == Cause.DESTINATION_NO_INVENTORY, "the saved cause is different");
                saved.getCompound("jobs").getCompound(String.valueOf(rig.id)).remove("cause");
                String old = Uno.report(MindRuntime.load(saved), player, now);
                check(old.contains("I am stopped and I need your help. Recorded reason: " + rig.job().reason + "."), old);

                helper.setBlock(rig.dest, Blocks.CHEST);
                rig.runtime.control(rig.actor, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /** T04 for the source: the source has no inventory when the robot arrives. The job is blocked with a cause of its own. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t04_source_removed(GameTestHelper helper) {
        listen();
        Actor player = player("t04src");
        // An empty source: a chest with items drops them when it disappears.
        Rig rig = new Rig(helper, "t04src", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> {
                helper.setBlock(rig.source, Blocks.AIR);
                rig.startJob(24, 0);
            })
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            .thenExecute(() -> {
                check(rig.job().cause == Cause.SOURCE_NO_INVENTORY && rig.job().carried == 0, rig.describe());
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t04_source_removed report: {}", report);
                check(report.contains("I find no inventory at '" + rig.from + "'") && !report.contains("Recorded reason"), report);
                check(notices(player).equals(List.of(subject(rig, rig.job()) + " is blocked: there is no inventory at '" + rig.from + "'.")),
                    notices(player).toString());
                helper.setBlock(rig.source, Blocks.CHEST);
                rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 24));
                rig.runtime.control(player, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(0, 24))
            .thenSucceed();
    }

    /**
     * T05: an obstacle on the path. The robot does not remove it. An obstacle can end by itself, thus it is a wait (D34).
     * After 10 attempts at 1 second the job waits with the cause "path obstructed". The robot keeps its load.
     * During the wait the robot tries one move every 5 seconds. When the obstacle goes away, the job continues by itself:
     * the owner gives no resume. Also D30: a check 5 minutes after the start of the wait gives one notice of a long wait.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t05_obstacle(GameTestHelper helper) {
        listen();
        Actor player = player("t05");
        Rig rig = new Rig(helper, "t05", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        BlockPos obstacle = new BlockPos(3, 1, 1);
        helper.setBlock(obstacle, Blocks.STONE);
        // The pickup, the start of the observed window, and the longest time without a call of the robot in that window.
        long[] time = new long[3];
        BlockPos[] stop = new BlockPos[1];
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> time[0] = rig.level.getGameTime())
            .thenWaitUntil(() -> check(rig.job().state != State.RUNNING, rig.describe()))
            .thenExecute(() -> {
                long now = rig.level.getGameTime();
                rig.state(State.WAITING);
                check(rig.job().cause == Cause.PATH_OBSTRUCTED, rig.describe());
                check(now - time[0] >= 160, "the job waits " + (now - time[0]) + " ticks after the pickup: less than 10 attempts at 1 second");
                helper.assertBlockPresent(Blocks.STONE, obstacle);
                check(rig.held() == 24 && rig.job().carried == 24 && rig.coal(rig.dest) == 0, rig.describe());
                // The reason with the position stays in the record, on the screen and in the status command.
                String reason = rig.job().reason;
                check(reason.contains("cannot move from") && reason.contains("in front: minecraft:stone") && reason.contains("Movement obstructed"), rig.describe());
                String view = UnoView.build(rig.runtime, player, now);
                check(view.contains("Recorded reason: " + reason), view);
                check(rig.runtime.status(player, rig.id, now).contains(reason), rig.runtime.status(player, rig.id, now));
                // The report takes its sentence from the cause, not from the recorded text.
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t05_obstacle report: {}", report);
                check(report.contains("My path is obstructed") && !report.contains("Recorded reason"), report);
                stop[0] = rig.robot().turtle.getPosition();
                time[1] = now;
            })
            // A window of 15 seconds. Each attempt is a call of the robot. One attempt every 5 seconds: about 100 ticks without a call.
            .thenWaitUntil(() -> {
                long now = rig.level.getGameTime();
                time[2] = Math.max(time[2], now - rig.job().lastContact);
                check(now - time[1] >= 300, "the window is not complete");
            })
            .thenExecute(() -> {
                MindCraft.LOGGER.info("t05_obstacle: the job waited {} ticks after the pickup. Longest time without a call of the robot during the wait: {} ticks",
                    time[1] - time[0], time[2]);
                rig.state(State.WAITING);
                check(rig.job().cause == Cause.PATH_OBSTRUCTED && rig.robot().turtle.getPosition().equals(stop[0]), rig.describe());
                check(time[2] >= 80 && time[2] <= 150, "during the wait the longest time without a call of the robot was "
                    + time[2] + " ticks: expected one attempt every 5 seconds");
                // D30: each attempt reported the same wait again, and the time of the wait did not start again.
                // 5 minutes after the start of the wait, one notice. The test gives the moment of the check as a parameter.
                List<String> states = states(rig.runtime, player, rig.job().id);
                check(states.equals(List.of("RUNNING", "WAITING PATH_OBSTRUCTED")), states.toString());
                rig.runtime.checkWait(rig.job(), entries(rig.runtime, player, rig.job().id).get(1).time() + MindRuntime.LONG_WAIT);
                check(notices(player).equals(List.of(longWait(rig, rig.job()) + "the path of the robot is obstructed.")), notices(player).toString());
                helper.setBlock(obstacle, Blocks.AIR);
            })
            .thenWaitUntil(() -> check(rig.job().state != State.WAITING, rig.describe()))
            .thenExecute(() -> rig.state(State.RUNNING))
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /**
     * T05 with T14: a pause and a cancellation during the wait for a free path. Each applies at the next attempt.
     * The robot does not move while the job is paused or cancelled, also when the obstacle is gone.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t05_pause_and_cancel_during_the_wait(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t05stop", 64, 1000);
        BlockPos obstacle = new BlockPos(3, 1, 1);
        helper.setBlock(obstacle, Blocks.STONE);
        BlockPos[] stop = new BlockPos[1];
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> check(rig.job().state != State.RUNNING, rig.describe()))
            .thenExecute(() -> {
                rig.state(State.WAITING);
                check(rig.job().cause == Cause.PATH_OBSTRUCTED, rig.describe());
                stop[0] = rig.robot().turtle.getPosition();
                rig.runtime.control(rig.actor, rig.id, "pause");
            })
            // More than one wait interval: the robot makes its next attempt, sees the pause and stops.
            .thenIdle(140)
            .thenExecute(() -> {
                rig.state(State.PAUSED);
                helper.setBlock(obstacle, Blocks.AIR);
            })
            .thenIdle(140)
            .thenExecute(() -> {
                rig.state(State.PAUSED);
                check(rig.robot().turtle.getPosition().equals(stop[0]) && rig.held() == 24, "the robot moved during the pause: " + rig.describe());
                helper.setBlock(obstacle, Blocks.STONE);
                rig.runtime.control(rig.actor, rig.id, "resume");
            })
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().cause == Cause.PATH_OBSTRUCTED, rig.describe()))
            .thenExecute(() -> rig.runtime.control(rig.actor, rig.id, "cancel"))
            .thenIdle(140)
            .thenExecute(() -> {
                rig.state(State.CANCELLED);
                helper.setBlock(obstacle, Blocks.AIR);
            })
            .thenIdle(140)
            .thenExecute(() -> {
                rig.state(State.CANCELLED);
                check(rig.robot().turtle.getPosition().equals(stop[0]) && rig.held() == 24 && rig.job().carried == 24,
                    "the robot moved after the cancel: " + rig.describe());
            })
            .thenSucceed();
    }

    /** T05 for a stock job. The stock module uses the move functions of the transport module, thus it waits in the same way. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t05_obstacle_keep_stock(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t05keep", 64, 1000);
        BlockPos obstacle = new BlockPos(3, 1, 1);
        rig.begin()
            .thenExecute(() -> rig.runtime.start(rig.actor, rig.order(MindRuntime.KEEP_STOCK, 24, 0)))
            // The robot observes the destination first. The obstacle comes after the pickup, between the robot and the destination.
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> helper.setBlock(obstacle, Blocks.STONE))
            .thenWaitUntil(() -> check(rig.job().state != State.RUNNING, rig.describe()))
            .thenExecute(() -> {
                rig.state(State.WAITING);
                check(rig.job().cause == Cause.PATH_OBSTRUCTED && rig.held() == 24 && rig.coal(rig.dest) == 0, rig.describe());
                helper.setBlock(obstacle, Blocks.AIR);
            })
            .thenWaitUntil(() -> rig.state(State.SATISFIED))
            .thenExecute(() -> check(rig.coal(rig.dest) == 24 && rig.coal(rig.source) == 40 && rig.held() == 0, rig.describe()))
            .thenSucceed();
    }

    /**
     * T06: no fuel. The fuel does not come back by itself, thus this is a block (D34) with the cause "out of fuel".
     * The job continues only after the owner gives fuel and a resume.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t06_no_fuel(GameTestHelper helper) {
        Actor player = player("t06");
        Rig rig = new Rig(helper, "t06", 64, 0, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            .thenExecute(() -> {
                check(rig.job().cause == Cause.OUT_OF_FUEL && rig.job().reason.contains("Out of fuel"), rig.describe());
                check(rig.coal(rig.source) == 64 && rig.job().carried == 0, rig.describe());
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t06_no_fuel report: {}", report);
                check(report.contains("I have no fuel") && !report.contains("Recorded reason"), report);
                rig.robot().turtle.setFuelLevel(1000);
            })
            // The fuel alone does not end the block.
            .thenIdle(140)
            .thenExecute(() -> {
                rig.state(State.BLOCKED);
                check(rig.coal(rig.source) == 64 && rig.job().carried == 0, rig.describe());
                rig.runtime.control(rig.actor, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /** A module reports an impediment of a kind that the runtime does not know. A block without a cause: Uno gives the recorded reason. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t06_impediment_of_an_unknown_kind(GameTestHelper helper) {
        listen();
        Actor player = player("t06kind");
        Rig rig = new Rig(helper, "t06kind", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> {
                rig.startJob(24, 0);
                String state;
                try {
                    state = rig.robot().impediment("landslide", "the floor fell at 1 2 3");
                } catch (dan200.computercraft.api.lua.LuaException e) {
                    throw new GameTestAssertException("the impediment failed: " + e.getMessage());
                }
                check(state.equals("BLOCKED"), "the module got " + state);
                rig.state(State.BLOCKED);
                check(rig.job().cause == Cause.NONE && rig.job().reason.equals("landslide: the floor fell at 1 2 3"), rig.describe());
                String report = Uno.report(server, player);
                check(report.contains("I am stopped and I need your help. Recorded reason: landslide: the floor fell at 1 2 3."), report);
                check(notices(player).equals(List.of(subject(rig, rig.job()) + " is blocked. Recorded reason: landslide: the floor fell at 1 2 3.")),
                    notices(player).toString());
            })
            .thenIdle(60)
            .thenExecute(() -> {
                rig.state(State.BLOCKED);
                check(rig.coal(rig.source) == 64 && rig.job().carried == 0, rig.describe());
            })
            .thenSucceed();
    }

    /** T07: two jobs for one robot. T08: a repeated transfer command. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t07_t08_one_job_no_blind_repeat(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t07", 64, 1000);
        rig.begin()
            .thenExecute(() -> {
                Job first = rig.startJob(24, 0);
                refused(() -> rig.startJob(8, 0), "already has job");
                check(rig.job() == first, "the first job must stay");
            })
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> {
                // The same command again, in the same tick as the first transfer.
                Object[] result;
                try {
                    result = rig.robot().take();
                } catch (Exception e) {
                    throw new GameTestAssertException("the repeated take failed: " + e.getMessage());
                }
                check((int) result[0] == 0, "the repeated take moved " + result[0]);
                check(rig.job().taken == 24 && rig.held() == 24 && rig.coal(rig.source) == 40, rig.describe());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /**
     * T09: the world contains a pickup that the record does not contain.
     * The test edits the record. It does not kill the server process.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t09_record_older_than_world(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t09", 64, 1000);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> {
                rig.job().carried = 0;
                rig.job().taken = 0;
            })
            .thenWaitUntil(() -> rig.state(State.RECOVERY))
            .thenIdle(60)
            .thenExecute(() -> {
                rig.state(State.RECOVERY);
                check(rig.job().reason.contains("holds 24, the record says 0"), rig.describe());
                check(rig.coal(rig.dest) == 0 && rig.held() == 24, "the robot must stop: " + rig.describe());
                rig.runtime.control(rig.actor, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 24 && rig.job().recoveries == 1, rig.describe());
                check(rig.describe().contains("inexact"), rig.describe());
            })
            .thenSucceed();
    }

    /** T10: a different actor empties the robot. The runtime cannot know where the load is. It stops. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t10_load_lost(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t10", 64, 1000);
        int[] opsAtLoss = new int[1];
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> {
                rig.robot().turtle.getInventory().clearContent();
                opsAtLoss[0] = rig.job().ops;
            })
            .thenWaitUntil(() -> rig.state(State.RECOVERY))
            .thenIdle(60)
            .thenExecute(() -> {
                rig.state(State.RECOVERY);
                check(rig.job().reason.contains("holds 0, the record says 24"), rig.describe());
                check(rig.job().ops == opsAtLoss[0] && rig.job().delivered == 0 && rig.coal(rig.dest) == 0, rig.describe());
            })
            .thenSucceed();
    }

    /**
     * T11, executor part: the turtle computer restarts during the transport and loses its Lua state.
     * Also a save and load of the runtime data. Not covered here: chunk unload and a server restart.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t11_executor_restart(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t11", 64, 1000);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> MindCraft.reboot(rig.level.getServer(), rig.id))
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                rig.assertTransferred(40, 24);
                check(rig.job().taken == 24 && rig.job().delivered == 24, rig.describe());
                MindRuntime copy = MindRuntime.load(rig.runtime.save(new CompoundTag()));
                check(sameJob(copy.jobs.get(rig.id), rig.job()), "the saved job is different");
                check(copy.stations.equals(rig.runtime.stations) && copy.robots.equals(rig.runtime.robots), "the saved stations or robots are different");
            })
            .thenSucceed();
    }

    private static boolean sameJob(Job a, Job b) {
        return a.id == b.id && a.owner.equals(b.owner) && a.from.equals(b.from) && a.to.equals(b.to) && a.item == b.item
            && a.module.equals(b.module) && a.revision == b.revision
            && a.quantity == b.quantity && a.reserve == b.reserve && a.state == b.state && a.reason.equals(b.reason) && a.cause == b.cause
            && a.taken == b.taken && a.delivered == b.delivered && a.carried == b.carried && a.fromSeen == b.fromSeen
            && a.toSeen == b.toSeen && a.executor.equals(b.executor) && a.ops == b.ops && a.lastOp.equals(b.lastOp)
            && a.recoveries == b.recoveries && a.lastOpAt == b.lastOpAt;
    }

    /** T12: something moves the turtle. The robot uses its true position and continues. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t12_turtle_moved(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t12a", 64, 1000);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> check(rig.robot().turtle.teleportTo(rig.level, helper.absolutePos(new BlockPos(2, 2, 2))), "teleport failed"))
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /**
     * T12: a second turtle with the same computer ID starts. Only the newest session has authority.
     * The result depends on the moment. The second turtle can finish the job, find a load mismatch,
     * or find the first turtle on its path. Then the second turtle waits for a free path, without an end (D34).
     * In each case the first turtle must stop and no coal must be lost.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t12_second_session(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t12b", 64, 1000);
        RobotApi[] first = new RobotApi[1];
        rig.begin()
            .thenExecute(() -> {
                rig.startJob(24, 0);
                first[0] = rig.robot();
                placeTurtle(helper, new BlockPos(2, 1, 0), rig.id, 1000, null);
            })
            .thenWaitUntil(() -> check(rig.robot() != null && rig.robot() != first[0], "the second turtle is not running yet"))
            .thenWaitUntil(() -> check(!rig.job().state.active() || rig.job().cause == Cause.PATH_OBSTRUCTED, rig.describe()))
            .thenIdle(100)
            .thenExecute(() -> {
                int total = rig.coal(rig.source) + rig.coal(rig.dest) + first[0].held(Items.COAL) + rig.held();
                check(total == 64, "coal was lost or duplicated: " + total);
                check(rig.job().taken <= 24 && rig.coal(rig.dest) <= 24, rig.describe());
                try {
                    first[0].status();
                    throw new GameTestAssertException("the first session still has authority");
                } catch (dan200.computercraft.api.lua.LuaException expected) {
                    check(expected.getMessage().contains("newer session"), expected.getMessage());
                }
                MindCraft.LOGGER.info("t12_second_session final status:\n{}", rig.describe());
            })
            .thenSucceed();
    }

    /**
     * D28: a path with one corner. Stone walls leave one corridor between the docks.
     * The robot starts between the corner and the destination dock. It goes over the corner to the source,
     * then over the corner again to the destination.
     * Toward the source, the first axis of the move step (x) is walled. Thus the step must try the next axis (z).
     * <pre>
     *   z \ x:  0         1      2   3   4   5        6
     *   0       [source]  dock   .   .   .   corner
     *   1                 #      #   #   #   turtle
     *   2                 #      #   #   #   dock     [destination]
     * </pre>
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_path_with_one_corner(GameTestHelper helper) {
        Rig rig = new Rig(helper, "corner", ADMIN, new BlockPos(0, 1, 0), new BlockPos(1, 1, 0), new BlockPos(5, 1, 1),
            new BlockPos(5, 1, 2), new BlockPos(6, 1, 2));
        BlockPos corner = new BlockPos(5, 1, 0);
        List<BlockPos> walls = new ArrayList<>();
        for (int x = 1; x <= 4; x++) {
            walls.add(new BlockPos(x, 1, 1));
            walls.add(new BlockPos(x, 1, 2));
        }
        walls.forEach(wall -> helper.setBlock(wall, Blocks.STONE));
        rig.build(64, 1000);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            // Toward the source: nothing on board yet.
            .thenWaitUntil(() -> rig.at(corner, 0))
            // Toward the destination: the load of the source on board.
            .thenWaitUntil(() -> rig.at(corner, 24))
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                rig.assertTransferred(40, 24);
                rig.at(rig.destDock, 0);
                walls.forEach(wall -> helper.assertBlockPresent(Blocks.STONE, wall));
            })
            .thenSucceed();
    }

    /** T13: a different actor takes items after the job was planned. The reserve stays in the source. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t13_external_withdrawal(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t13", 64, 1000);
        rig.begin()
            .thenExecute(() -> {
                rig.startJob(40, 32);
                rig.chest(rig.source).removeItem(0, 20);
            })
            .thenWaitUntil(() -> {
                rig.state(State.WAITING);
                check(rig.job().delivered == 12, rig.describe());
            })
            .thenExecute(() -> {
                check(rig.coal(rig.source) == 32 && rig.coal(rig.dest) == 12, rig.describe());
                check(rig.job().reason.contains("the reserve is 32"), rig.describe());
            })
            .thenSucceed();
    }

    /** T14: pause during the transport. The robot stops and keeps the load. Then it continues. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t14_pause(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t14a", 64, 1000);
        BlockPos[] stop = new BlockPos[1];
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> rig.runtime.control(rig.actor, rig.id, "pause"))
            .thenIdle(30)
            .thenExecute(() -> stop[0] = rig.robot().turtle.getPosition())
            .thenIdle(40)
            .thenExecute(() -> {
                rig.state(State.PAUSED);
                check(rig.robot().turtle.getPosition().equals(stop[0]), "the robot moved during the pause");
                check(rig.held() == 24 && rig.coal(rig.dest) == 0, rig.describe());
                rig.runtime.control(rig.actor, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /** T14: cancel during the transport. The load stays in the robot. The status shows it. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t14_cancel(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t14b", 64, 1000);
        BlockPos[] stop = new BlockPos[1];
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> rig.runtime.control(rig.actor, rig.id, "cancel"))
            .thenIdle(30)
            .thenExecute(() -> stop[0] = rig.robot().turtle.getPosition())
            .thenIdle(40)
            .thenExecute(() -> {
                rig.state(State.CANCELLED);
                check(rig.robot().turtle.getPosition().equals(stop[0]), "the robot moved after the cancel");
                check(rig.held() == 24 && rig.coal(rig.dest) == 0 && rig.job().carried == 24, rig.describe());
                check(rig.describe().contains("on board 24"), rig.describe());
                refused(() -> rig.startJob(8, 0), "already holds 24");
            })
            .thenSucceed();
    }

    /** T19, minimum: the server checks the owner of the turtle, the robot, the stations and the job. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t19_ownership(GameTestHelper helper) {
        Actor alice = new Actor(UUID.fromString("00000000-0000-0000-0000-00000000000a"), false);
        Actor bob = new Actor(UUID.fromString("00000000-0000-0000-0000-00000000000b"), false);
        Rig rig = new Rig(helper, "t19", 64, 1000, alice);
        helper.startSequence()
            .thenWaitUntil(() -> {
                RobotApi robot = RobotApi.at(rig.level, helper.absolutePos(rig.start));
                check(robot != null, "the turtle is not running yet");
                refused(() -> rig.runtime.enroll(bob, robot), "not yours");
            })
            .thenWaitUntil(rig::enroll)
            .thenWaitUntil(rig::restarted)
            .thenExecute(() -> {
                rig.runtime.addStation(bob, "t19_bob", rig.level, helper.absolutePos(rig.dest), helper.absolutePos(rig.destDock));
                refused(() -> rig.runtime.addStation(bob, rig.from, rig.level, helper.absolutePos(rig.source), helper.absolutePos(rig.sourceDock)), "another player");
                refused(() -> rig.runtime.start(bob, rig.order(MindRuntime.TRANSPORT, 8, 0)), "not yours");
                refused(() -> rig.runtime.start(alice, new Order(MindRuntime.TRANSPORT, rig.id, rig.from, "t19_bob", Items.COAL, 8, 0)), "not yours");
                rig.startJob(8, 0);
                refused(() -> rig.runtime.control(bob, rig.id, "cancel"), "not yours");
                refused(() -> rig.runtime.release(bob, rig.id), "not yours");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(56, 8))
            .thenSucceed();
    }

    // --- S2: keep stock -----------------------------------------------------------------------------------------------

    /**
     * The reference request: keep at least 64 in the destination, leave at least 32 in the source.
     * The job brings what is missing, continues after a consumption, obeys the reserve and uses a new reserve.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s2_keep_stock(GameTestHelper helper) {
        Rig rig = new Rig(helper, "s2keep", 0, 1000);
        Container source = rig.chest(rig.source), dest = rig.chest(rig.dest);
        source.setItem(0, new ItemStack(Items.COAL, 64));
        source.setItem(1, new ItemStack(Items.COAL, 64));
        dest.setItem(0, new ItemStack(Items.COAL, 10));
        rig.begin()
            .thenExecute(() -> {
                rig.runtime.start(rig.actor, rig.order(MindRuntime.KEEP_STOCK, 64, 32));
                check(rig.job().need() == MindRuntime.UNKNOWN, "the need must be unknown before the first observation");
            })
            // 1. The robot observes 10 in the destination, then brings 54.
            .thenWaitUntil(() -> rig.state(State.SATISFIED))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 64 && rig.coal(rig.source) == 74 && rig.held() == 0, rig.describe());
                check(rig.job().delivered == 54 && rig.job().executor.equals("keep_stock@3"), rig.describe());
                // 2. Something uses 20 pieces.
                dest.removeItem(0, 20);
            })
            .thenWaitUntil(() -> check(rig.job().delivered == 74 && rig.job().state == State.SATISFIED, rig.describe()))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 64 && rig.coal(rig.source) == 54, rig.describe());
                // 3. Something uses 40 pieces. The source can give only 22 pieces above the reserve.
                dest.removeItem(0, 40);
            })
            .thenWaitUntil(() -> check(rig.job().delivered == 96 && rig.job().state == State.WAITING, rig.describe()))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 46 && rig.coal(rig.source) == 32 && rig.held() == 0, rig.describe());
                check(rig.job().reason.contains("the reserve is 32"), rig.describe());
                MindCraft.LOGGER.info("s2_keep_stock status at the reserve limit:\n{}", rig.describe());
                // 4. The owner lowers the reserve. A change that uses an old revision is refused.
                int revision = rig.job().revision;
                rig.runtime.update(rig.actor, rig.id, revision, 64, 0);
                refused(() -> rig.runtime.update(rig.actor, rig.id, revision, 64, 5), "changed");
            })
            .thenWaitUntil(() -> rig.state(State.SATISFIED))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 64 && rig.coal(rig.source) == 14 && rig.held() == 0, rig.describe());
                check(rig.job().delivered == 114 && rig.job().taken == 114, rig.describe());
                // 5. The turtle computer restarts. The job has no end, thus the robot must continue to keep the stock.
                MindCraft.reboot(rig.level.getServer(), rig.id);
                dest.removeItem(0, 10);
            })
            .thenWaitUntil(() -> check(rig.job().delivered == 124 && rig.job().state == State.SATISFIED, rig.describe()))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 64 && rig.coal(rig.source) == 4, rig.describe());
                MindCraft.LOGGER.info("s2_keep_stock final status:\n{}", rig.describe());
            })
            .thenSucceed();
    }

    // --- S2: Uno ------------------------------------------------------------------------------------------------------

    static Actor player(String name) {
        return new Actor(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), false);
    }

    /** The action "start" of a provider, with its order. */
    private static String startJson(String module, int robot, String from, String to, int quantity) {
        return "{\"action\":\"start\",\"module\":\"" + module + "\",\"robot\":" + robot + ",\"from\":\"" + from + "\",\"to\":\"" + to
            + "\",\"item\":\"minecraft:coal\",\"quantity\":" + quantity + ",\"reserve\":0}";
    }

    /** The answer of a provider of the test. It calls no model, thus it uses no tokens. */
    private static Uno.Answer answer(String json) {
        return new Uno.Answer(json, 0, 0);
    }

    /**
     * The reference request as text. Uno proposes, the player confirms, the robot works, Uno reports.
     * Also T22: a proposal does not change the world and does not start a job.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s2_uno_request(GameTestHelper helper) {
        Actor player = player("s2uno");
        Rig rig = new Rig(helper, "s2uno", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 64));
        rig.chest(rig.source).setItem(1, new ItemStack(Items.COAL, 16));
        rig.chest(rig.dest).setItem(0, new ItemStack(Items.COAL, 10));
        AtomicReference<String> answer = new AtomicReference<>();
        rig.begin()
            .thenExecute(() -> Uno.ask(server, player, "Keep at least 64 pieces of coal in " + rig.to
                + ", taking them from " + rig.from + ". Leave at least 32 pieces in " + rig.from + ".", answer::set))
            .thenWaitUntil(() -> check(answer.get() != null, "Uno did not answer yet"))
            .thenExecute(() -> {
                MindCraft.LOGGER.info("s2_uno_request proposal: {}", answer.get());
                check(answer.get().startsWith("Proposal: keep at least 64 x minecraft:coal in '" + rig.to + "'"), answer.get());
                check(answer.get().contains("Reserve to leave in '" + rig.from + "': 32"), answer.get());
                check(rig.job() == null && rig.coal(rig.source) == 80 && rig.coal(rig.dest) == 10, "the proposal changed something");
                String started = Uno.confirm(server, player);
                check(started.startsWith("Job") && rig.job() != null, started);
                Job job = rig.job();
                check(job.keepStock() && job.quantity == 64 && job.reserve == 32 && job.owner.equals(player.id()), rig.describe());
                check(Uno.confirm(server, player).startsWith("I have no pending proposal"), "a proposal must not start two times");
            })
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 48, rig.describe()))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("s2_uno_request report: {}", report);
                check(rig.coal(rig.dest) == 58 && rig.coal(rig.source) == 32, rig.describe());
                check(report.contains("I delivered 48 items.") && report.contains("To reach 64"), report);
                check(report.contains("32 remain in '" + rig.from + "': that is the reserve you asked me to keep"), report);
            })
            .thenSucceed();
    }

    /**
     * The reference request of the brief, word for word, with stations named "depot" and "furnace".
     * Then the same request in Italian, the language of the first version of the brief: the fixed rules also read Italian.
     * The answer of the fixed rules is the action "start" with the order, and zero tokens: the fixed rules call no model.
     * A request without a complete order is the action "none", with the reason for the player.
     */
    @GameTest(template = EMPTY)
    public static void s2_uno_reference_sentence(GameTestHelper helper) {
        String robots = "\"robots\":[{\"id\":7,\"free\":true},{\"id\":8,\"free\":false}]}";
        String context = "{\"stations\":[\"depot\",\"furnace\",\"mine\"]," + robots;
        Uno.Answer order = new Uno.RuleProvider().answer("Keep at least 64 pieces of coal in the furnace chest, "
            + "taking them from the depot. Leave at least 32 pieces in the depot.", context);
        check(order.json().equals("{\"action\":\"start\",\"module\":\"keep_stock\",\"robot\":7,\"from\":\"depot\",\"to\":\"furnace\","
            + "\"item\":\"minecraft:coal\",\"quantity\":64,\"reserve\":32}"), order.json());
        Uno.Answer transport = new Uno.RuleProvider().answer("Move 24 coal from depot to furnace with robot 8", context);
        check(transport.json().equals("{\"action\":\"start\",\"module\":\"transport\",\"robot\":8,\"from\":\"depot\",\"to\":\"furnace\","
            + "\"item\":\"minecraft:coal\",\"quantity\":24,\"reserve\":0}"), transport.json());
        Uno.Answer italian = new Uno.RuleProvider().answer("Mantieni almeno 64 pezzi di carbone nel baule delle fornaci, "
            + "prendendoli dal deposito. Lascia almeno 32 pezzi nel deposito.", "{\"stations\":[\"deposito\",\"fornaci\",\"miniera\"]," + robots);
        check(italian.json().equals("{\"action\":\"start\",\"module\":\"keep_stock\",\"robot\":7,\"from\":\"deposito\",\"to\":\"fornaci\","
            + "\"item\":\"minecraft:coal\",\"quantity\":64,\"reserve\":32}"), italian.json());
        Uno.Answer none = new Uno.RuleProvider().answer("Move coal from depot to furnace", context);
        check(none.json().equals("{\"action\":\"none\",\"reason\":\"the quantity is missing\"}"), none.json());
        for (Uno.Answer answer : List.of(order, transport, italian, none)) {
            check(answer.inputTokens() == 0 && answer.outputTokens() == 0, "the fixed rules reported tokens: " + answer);
        }
        helper.succeed();
    }

    /**
     * T18: the provider is offline, slow or gives a bad answer. Uno says so and creates no proposal.
     * A job in progress continues. A valid answer with an invalid order does not pass the ordinary validation.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t18_provider_failures(GameTestHelper helper) {
        Actor player = player("t18");
        Rig rig = new Rig(helper, "t18", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        List<String> invalid = new CopyOnWriteArrayList<>(), failed = new CopyOnWriteArrayList<>();
        GameTestSequence sequence = rig.begin();
        sequence = ask(sequence, server, player, "x", (r, c) -> answer(startJson(MindRuntime.TRANSPORT, rig.id, rig.from, "t18_unknown", 8)), 5000, invalid);
        sequence = ask(sequence, server, player, "x", (r, c) -> answer(startJson(MindRuntime.TRANSPORT, rig.id, rig.from, rig.to, -5)), 5000, invalid);
        sequence = ask(sequence, server, player, "x", (r, c) -> answer(startJson("dig_a_mine", rig.id, rig.from, rig.to, 8)), 5000, invalid);
        sequence = sequence.thenExecute(() -> {
            MindCraft.LOGGER.info("t18_provider_failures invalid orders: {}", invalid);
            check(invalid.stream().allMatch(a -> a.startsWith("I cannot propose this job")), invalid.toString());
            check(invalid.stream().anyMatch(a -> a.contains("Unknown station")) && invalid.stream().anyMatch(a -> a.contains("must be positive"))
                && invalid.stream().anyMatch(a -> a.contains("Unknown module")), invalid.toString());
            check(Uno.confirm(server, player).startsWith("I have no pending proposal"), "an invalid order became a proposal");
            rig.startJob(24, 0);
        });
        sequence = ask(sequence, server, player, "x", (r, c) -> {
            throw new IOException("provider offline");
        }, 5000, failed);
        sequence = ask(sequence, server, player, "x", (r, c) -> {
            Thread.sleep(3000);
            return answer("{}");
        }, 300, failed);
        sequence = ask(sequence, server, player, "x", (r, c) -> answer("this is not JSON"), 5000, failed);
        // An action outside the closed list is not a valid answer.
        sequence = ask(sequence, server, player, "x", (r, c) -> answer("{\"action\":\"dig\",\"robot\":null,\"station\":null}"), 5000, failed);
        // An exception is always a failure, also an IllegalArgumentException. Only the action "none" says that a value is missing.
        sequence = ask(sequence, server, player, "x", (r, c) -> {
            throw new IllegalArgumentException("a defect of the provider");
        }, 5000, failed);
        // Not a failure: the request has no quantity. The answer must not look like a failure of the provider.
        sequence = ask(sequence, server, player, "carry coal", new Uno.RuleProvider(), 5000, failed);
        sequence
            .thenExecute(() -> {
                MindCraft.LOGGER.info("t18_provider_failures failures: {}", failed);
                check(failed.stream().filter(a -> a.startsWith("I cannot interpret the request now")).count() == 3, failed.toString());
                check(failed.stream().anyMatch(a -> a.contains("provider offline")) && failed.stream().anyMatch(a -> a.contains("TimeoutException"))
                    && failed.contains("I cannot interpret the request now (a defect of the provider). The jobs in progress continue."), failed.toString());
                check(failed.stream().filter(a -> a.startsWith("I did not get a valid configuration")).count() == 2, failed.toString());
                check(failed.contains("I cannot propose a job: the quantity is missing."), failed.toString());
                check(Uno.confirm(server, player).startsWith("I have no pending proposal"), "a failure became a proposal");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /** Sends one request and waits for its answer. Uno accepts one request at a time from each player. */
    private static GameTestSequence ask(GameTestSequence sequence, MinecraftServer server, Actor player, String request,
                                        Uno.Provider provider, long timeoutMs, List<String> answers) {
        int[] before = new int[1];
        return sequence
            .thenExecute(() -> {
                before[0] = answers.size();
                Uno.ask(server, player, request, provider, timeoutMs, answers::add);
            })
            .thenWaitUntil(() -> check(answers.size() > before[0], "Uno did not answer yet"));
    }

    /** An answer of the Messages API with one text block, or with no content if the text is null. */
    private static String claudeMessage(String stopReason, String text) {
        return claudeMessage(stopReason, text, 1, 1);
    }

    private static String claudeMessage(String stopReason, String text, long inputTokens, long outputTokens) {
        JsonObject message = JsonParser.parseString("{\"id\":\"msg_test\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\""
            + ClaudeProvider.MODEL + "\",\"content\":[],\"stop_sequence\":null}").getAsJsonObject();
        message.addProperty("stop_reason", stopReason);
        JsonObject usage = new JsonObject();
        usage.addProperty("input_tokens", inputTokens);
        usage.addProperty("output_tokens", outputTokens);
        message.add("usage", usage);
        if (text != null) {
            JsonObject block = new JsonObject();
            block.addProperty("type", "text");
            block.addProperty("text", text);
            message.getAsJsonArray("content").add(block);
        }
        return message.toString();
    }

    /**
     * An action in the shape of the output schema of the Claude provider: each field is present, and null if the action does not use it.
     * The arguments after the reason are pairs of a field name and a value.
     */
    private static String modelAction(String action, String reason, Object... fields) {
        JsonObject json = new JsonObject();
        json.addProperty("action", action);
        json.addProperty("reason", reason);
        for (String name : List.of("robot", "station", "module", "from", "to", "item", "quantity", "reserve")) json.add(name, JsonNull.INSTANCE);
        for (int i = 0; i < fields.length; i += 2) {
            if (fields[i + 1] instanceof Number number) json.addProperty((String) fields[i], number);
            else json.addProperty((String) fields[i], (String) fields[i + 1]);
        }
        return json.toString();
    }

    /** One answer of the false API and the text that Uno must say then. Status -1: the API is off. */
    private record ClaudeCase(int status, Supplier<String> body, String expected) {
    }

    /**
     * T18 for the Claude provider. A local HTTP server is in the place of the API of Anthropic.
     * This checks the code of the provider and the SDK inside the game: the request that goes out, and each kind of answer.
     * The provider reads one action from an answer, and the input and output tokens of the answer.
     * It does not check the language model. LiveTests does that with the real service.
     * Also: Uno accepts one request at a time from each player.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t18_claude_provider(GameTestHelper helper) throws IOException {
        Actor player = player("t18claude");
        Rig rig = new Rig(helper, "t18claude", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        AtomicInteger status = new AtomicInteger();
        AtomicReference<String> body = new AtomicReference<>(), sent = new AtomicReference<>(), beta = new AtomicReference<>();
        HttpServer api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/", exchange -> {
            sent.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            beta.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        api.start();
        Uno.Provider claude = new ClaudeProvider(AnthropicOkHttpClient.builder().apiKey("not-a-key")
            .baseUrl("http://127.0.0.1:" + api.getAddress().getPort()).maxRetries(0).build());
        // Only an operator uses a provider with a model (D33). The player of this test is not an operator: an operator owns
        // each station of the server, and the check of the context below needs the stations of one player only.
        // Thus the requests through Uno use the code of the provider without its model flag. The limits have their own tests.
        Uno.Provider claudeCode = claude::answer;

        String request = "Carry eight pieces of coal to the destination";
        String none = modelAction("none", "You did not give the source.");
        Supplier<String> pause = () -> modelAction("pause", "", "robot", rig.id);
        Supplier<String> start = () -> modelAction("start", "", "robot", rig.id, "module", MindRuntime.TRANSPORT, "from", rig.from, "to", rig.to,
            "item", "minecraft:coal", "quantity", 8, "reserve", 0);
        String error = "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"test\"}}";
        List<ClaudeCase> cases = List.of(
            // A missing value is not a failure of the service: the answer has a different sentence.
            new ClaudeCase(200, () -> claudeMessage("end_turn", none), "I cannot propose a job: You did not give the source."),
            new ClaudeCase(200, () -> claudeMessage("refusal", null), "(the model declined the request)"),
            new ClaudeCase(200, () -> claudeMessage("max_tokens", "{\"action\":"), "(the answer of the model is incomplete)"),
            new ClaudeCase(401, () -> error, "(the API key is not valid or has no permission)"),
            new ClaudeCase(500, () -> error, "(the model service answered with error 500)"),
            // A pause of the robot, which has no job: Uno asks for the robot number of a job and creates no proposal.
            new ClaudeCase(200, () -> claudeMessage("end_turn", pause.get()), "You have no job on robot #"),
            new ClaudeCase(200, () -> claudeMessage("end_turn", start.get()), "Proposal: carry 8 x minecraft:coal from '" + rig.from + "' to '" + rig.to + "'"),
            new ClaudeCase(-1, () -> "", "(the model service is not reachable)"));
        int proposalCase = 6;

        List<String> answers = new CopyOnWriteArrayList<>(), busy = new CopyOnWriteArrayList<>();
        AtomicReference<Object> direct = new AtomicReference<>();
        CountDownLatch open = new CountDownLatch(1);
        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> {
                // The provider of the first request waits for the test. A fast provider can answer before the second
                // request arrives: then the first request is not open any more, and the test does not check the rule.
                Uno.ask(server, player, "x", (r, c) -> {
                    open.await(10, TimeUnit.SECONDS);
                    return answer("{\"action\":\"none\",\"reason\":\"the quantity is missing\"}");
                }, 5000, busy::add);
                Uno.ask(server, player, "x", busy::add);
                check(busy.equals(List.of("I am still working on your previous request.")), "a second request did not wait: " + busy);
                open.countDown();
            })
            .thenWaitUntil(() -> check(busy.size() == 2, "answers: " + busy))
            .thenExecute(() -> {
                // The answer of the provider itself: one action, and the tokens of the answer.
                // The provider runs off the server thread, as in Uno.ask. The first request also loads the classes of the SDK.
                status.set(200);
                body.set(claudeMessage("end_turn", start.get(), 1234, 56));
                String context = Uno.context(rig.runtime, player);
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return claude.answer(request, context);
                    } catch (Exception e) {
                        throw new CompletionException(e);
                    }
                }).whenComplete((answer, failure) -> direct.set(failure == null ? answer : failure));
            })
            .thenWaitUntil(() -> check(direct.get() != null, "the provider did not answer yet"))
            .thenExecute(() -> check(direct.get() instanceof Uno.Answer answer && answer.json().equals(start.get())
                && answer.inputTokens() == 1234 && answer.outputTokens() == 56, "the answer of the provider: " + direct.get()));
        for (int i = 0; i < cases.size(); i++) {
            int index = i;
            ClaudeCase test = cases.get(i);
            sequence = sequence
                .thenExecute(() -> {
                    if (test.status() < 0) api.stop(0);
                    status.set(test.status());
                    body.set(test.body().get());
                    Uno.ask(server, player, request, claudeCode, 15_000, answers::add);
                })
                .thenWaitUntil(() -> check(answers.size() > index, "Uno did not answer yet"))
                .thenExecute(() -> {
                    MindCraft.LOGGER.info("t18_claude_provider answer {}: {}", index, answers.get(index));
                    check(answers.get(index).contains(test.expected()), answers.get(index));
                    // Only the complete order becomes a proposal. The request after it fails: then the proposal must be gone.
                    check(Uno.hasProposal(player) == (index == proposalCase), "wrong proposal state after answer " + index);
                });
        }
        sequence
            .thenExecute(() -> {
                // The request that went to the API for the complete order.
                JsonObject message = JsonParser.parseString(sent.get()).getAsJsonObject();
                JsonObject output = message.getAsJsonObject("output_config");
                check(message.get("model").getAsString().equals(ClaudeProvider.MODEL) && message.get("fallbacks").getAsString().equals("default")
                    && beta.get().contains("server-side-fallback-2026-07-01"), sent.get());
                check(output.get("effort").getAsString().equals("low") && output.getAsJsonObject("format").get("type").getAsString().equals("json_schema"), sent.get());
                check(!message.has("thinking") && !message.has("temperature"), sent.get());
                // The closed list of the actions. Each field is required: the model writes null in a field that its action does not use.
                JsonObject schema = output.getAsJsonObject("format").getAsJsonObject("schema");
                check(schema.getAsJsonObject("properties").getAsJsonObject("action").get("enum").toString()
                    .equals("[\"start\",\"change\",\"pause\",\"resume\",\"cancel\",\"report\",\"none\"]"), schema.toString());
                Set<String> required = new HashSet<>();
                schema.getAsJsonArray("required").forEach(field -> required.add(field.getAsString()));
                check(required.equals(Set.of("action", "reason", "robot", "station", "module", "from", "to", "item", "quantity", "reserve"))
                    && schema.getAsJsonObject("properties").keySet().equals(required), schema.toString());
                String system = message.get("system").getAsString();
                check(Stream.of("start", "change", "pause", "resume", "cancel", "report", "none").allMatch(action -> system.contains("\"" + action + "\"")), system);
                // The context is the same as before the actions: the station names of the player, the robot numbers and the "free" flag (D21).
                String content = message.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();
                check(content.equals("Context: {\"stations\":[\"" + rig.to + "\",\"" + rig.from + "\"],\"robots\":[{\"id\":" + rig.id
                    + ",\"free\":true}]}\nRequest: " + request), content);
            })
            .thenSucceed();
    }

    /**
     * Increment A: a provider answers with one action from the closed list of D29. Uno does not trust the answer.
     * A change without a new value, with a value of a wrong type or with a negative quantity gives no proposal.
     * A "none" action gives its reason with the length limit. In each case the job does not change.
     * During the test the job waits for materials: its state does not change by itself.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_provider_answers_with_an_action(GameTestHelper helper) {
        Actor player = player("a_actions");
        Rig rig = new Rig(helper, "a_actions", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        // An answer of the provider and the sentence of Uno. %d is the robot of the job.
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("{\"action\":\"change\",\"robot\":%d,\"station\":null,\"quantity\":null,\"reserve\":null}",
            "I cannot propose a change: the request gives no new quantity and no new reserve. I created no proposal.");
        cases.put("{\"action\":\"change\",\"robot\":%d,\"station\":null,\"quantity\":\"many\",\"reserve\":null}",
            "I did not get a valid configuration. I created no proposal.");
        cases.put("{\"action\":\"change\",\"robot\":%d,\"station\":null,\"quantity\":-5,\"reserve\":null}",
            "I cannot propose this change: The quantity must be positive. The reserve must not be negative.");
        // The reason of "none" has a limit of 200 characters.
        cases.put("{\"action\":\"none\",\"reason\":\"" + "a".repeat(150) + "b".repeat(150) + "\"}",
            "I cannot propose a job: " + "a".repeat(150) + "b".repeat(50) + ".");
        // A model does not guarantee the case of a value of the schema.
        cases.put("{\"action\":\"None\",\"reason\":\"The request names no station.\"}", "I cannot propose a job: The request names no station.");
        // A "none" action without a reason is not a valid answer.
        cases.put("{\"action\":\"none\",\"reason\":\" \"}", "I did not get a valid configuration. I created no proposal.");

        List<String> answers = new CopyOnWriteArrayList<>();
        int[] job = new int[2];
        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> {
                job[0] = rig.job().id;
                job[1] = rig.job().revision;
            });
        for (Map.Entry<String, String> test : cases.entrySet()) {
            sequence = ask(sequence, server, player, "x", (r, c) -> answer(test.getKey().formatted(rig.id)), 5000, answers)
                .thenExecute(() -> {
                    String said = answers.get(answers.size() - 1);
                    MindCraft.LOGGER.info("a_provider_answers_with_an_action: {} -> {}", test.getKey(), said);
                    check(said.equals(test.getValue()), said);
                    check(!Uno.hasProposal(player), "the action became a proposal: " + test.getKey());
                    Job now = rig.job();
                    check(now.id == job[0] && now.state == State.WAITING && now.quantity == 24 && now.reserve == 0 && now.revision == job[1],
                        "the action changed the job: " + rig.describe());
                });
        }
        sequence.thenSucceed();
    }

    /**
     * Increment A: the fixed rules read a simple form of a report and of a pause, in English and in Italian.
     * The action names the job with a robot number ("robot N"), with one station of the player, or with nothing.
     * A request that names two stations stays a start. The fixed rules call no model, thus they report zero tokens.
     */
    @GameTest(template = EMPTY)
    public static void a_fixed_rules_report_and_pause(GameTestHelper helper) {
        String robots = "\"robots\":[{\"id\":7,\"free\":false},{\"id\":8,\"free\":true}]}";
        String english = "{\"stations\":[\"depot\",\"furnace\",\"mine\"]," + robots;
        String italian = "{\"stations\":[\"deposito\",\"fornaci\",\"miniera\"]," + robots;
        String report = "{\"action\":\"report\",\"robot\":%s,\"station\":%s}", pause = "{\"action\":\"pause\",\"robot\":%s,\"station\":%s}";
        // A request, its context and the answer of the fixed rules.
        List<String[]> cases = List.of(
            new String[]{"How is the furnace?", english, report.formatted("null", "\"furnace\"")},
            new String[]{"Report", english, report.formatted("null", "null")},
            new String[]{"What is the status of robot 8?", english, report.formatted("8", "null")},
            new String[]{"Pause the furnace job", english, pause.formatted("null", "\"furnace\"")},
            new String[]{"pause", english, pause.formatted("null", "null")},
            new String[]{"Pause robot 7.", english, pause.formatted("7", "null")},
            new String[]{"Come vanno le fornaci?", italian, report.formatted("null", "\"fornaci\"")},
            new String[]{"Rapporto", italian, report.formatted("null", "null")},
            new String[]{"Come sta il robot 8?", italian, report.formatted("8", "null")},
            new String[]{"Metti in pausa il lavoro del deposito", italian, pause.formatted("null", "\"deposito\"")},
            new String[]{"Pausa", italian, pause.formatted("null", "null")},
            new String[]{"Sospendi il robot 7", italian, pause.formatted("7", "null")},
            // Two stations: a start, as before. The action word does not decide.
            new String[]{"Pause, then carry 24 coal from depot to furnace with robot 8", english,
                "{\"action\":\"start\",\"module\":\"transport\",\"robot\":8,\"from\":\"depot\",\"to\":\"furnace\","
                    + "\"item\":\"minecraft:coal\",\"quantity\":24,\"reserve\":0}"});
        List<String> wrong = new ArrayList<>();
        for (String[] test : cases) {
            Uno.Answer answer = new Uno.RuleProvider().answer(test[0], test[1]);
            if (!answer.json().equals(test[2]) || answer.inputTokens() != 0 || answer.outputTokens() != 0) {
                wrong.add("'" + test[0] + "' -> " + answer);
            }
        }
        check(wrong.isEmpty(), wrong.toString());
        helper.succeed();
    }

    /**
     * Increment A: the fixed rules read a simple form of a change, a resume and a cancellation, in English and in Italian.
     * A change gives the new values that the request names. A value that the request does not name is null: it does not change.
     * "robot N" names the job. It is not a new value. A request that names two stations stays a start.
     */
    @GameTest(template = EMPTY)
    public static void a_fixed_rules_change_resume_cancel(GameTestHelper helper) {
        String robots = "\"robots\":[{\"id\":7,\"free\":false},{\"id\":8,\"free\":true}]}";
        String english = "{\"stations\":[\"depot\",\"furnace\",\"mine\"]," + robots;
        String italian = "{\"stations\":[\"deposito\",\"fornaci\",\"miniera\"]," + robots;
        String change = "{\"action\":\"change\",\"robot\":%s,\"station\":%s,\"quantity\":%s,\"reserve\":%s}";
        String resume = "{\"action\":\"resume\",\"robot\":%s,\"station\":%s}", cancel = "{\"action\":\"cancel\",\"robot\":%s,\"station\":%s}";
        // A request, its context and the answer of the fixed rules.
        List<String[]> cases = List.of(
            new String[]{"Leave only 16 in the depot", english, change.formatted("null", "\"depot\"", "null", "16")},
            new String[]{"Set the quantity of robot 7 to 30", english, change.formatted("7", "null", "30", "null")},
            new String[]{"Keep the level of the furnace at 48 and leave 8", english, change.formatted("null", "\"furnace\"", "48", "8")},
            new String[]{"Resume the furnace job", english, resume.formatted("null", "\"furnace\"")},
            new String[]{"Continue robot 7", english, resume.formatted("7", "null")},
            new String[]{"Cancel the job of the depot", english, cancel.formatted("null", "\"depot\"")},
            new String[]{"cancel", english, cancel.formatted("null", "null")},
            new String[]{"Lascia solo 16 nel deposito", italian, change.formatted("null", "\"deposito\"", "null", "16")},
            new String[]{"Porta la quantità del robot 7 a 30", italian, change.formatted("7", "null", "30", "null")},
            new String[]{"Riprendi il lavoro delle fornaci", italian, resume.formatted("null", "\"fornaci\"")},
            new String[]{"Continua", italian, resume.formatted("null", "null")},
            new String[]{"Annulla il robot 7", italian, cancel.formatted("7", "null")},
            new String[]{"Cancella il lavoro del deposito", italian, cancel.formatted("null", "\"deposito\"")},
            // A resume of a paused job is a resume, not a pause.
            new String[]{"Riprendi il lavoro in pausa", italian, resume.formatted("null", "null")},
            // The number of the robot is not a reserve. Without a new value the request is not a change.
            new String[]{"How is the reserve of robot 7?", english, "{\"action\":\"report\",\"robot\":7,\"station\":null}"},
            // Two stations: a start, as before. The words of a change do not decide.
            new String[]{"Leave 8 and carry 24 coal from depot to furnace with robot 8", english,
                "{\"action\":\"start\",\"module\":\"transport\",\"robot\":8,\"from\":\"depot\",\"to\":\"furnace\","
                    + "\"item\":\"minecraft:coal\",\"quantity\":24,\"reserve\":8}"});
        List<String> wrong = new ArrayList<>();
        for (String[] test : cases) {
            Uno.Answer answer = new Uno.RuleProvider().answer(test[0], test[1]);
            if (!answer.json().equals(test[2]) || answer.inputTokens() != 0 || answer.outputTokens() != 0) {
                wrong.add("'" + test[0] + "' -> " + answer);
            }
        }
        check(wrong.isEmpty(), wrong.toString());
        helper.succeed();
    }

    /** Two rigs of one player, side by side. The second is 3 blocks to the south of the first. */
    private static Rig[] twoRigs(GameTestHelper helper, String name, Actor actor) {
        Rig first = new Rig(helper, name, actor, new BlockPos(0, 1, 1));
        Rig second = new Rig(helper, name + "2", actor, new BlockPos(0, 1, 4));
        first.build(0, 1000);
        second.build(0, 1000);
        return new Rig[]{first, second};
    }

    /** A provider of the test that always gives this answer. */
    private static Uno.Provider gives(Supplier<String> json) {
        return (request, context) -> answer(json.get());
    }

    /**
     * Increment A: report and pause by words, through the conversation with a provider of the test.
     * Two jobs of one player wait for materials. Both use the destination of the first rig: two robots bring coal to one furnace.
     * Uno finds the job from the robot number, from a station of exactly one open job, or from the only open job.
     * In each other case Uno says how many jobs fit, asks for the robot number and changes nothing.
     * A pause runs immediately through the ordinary request of the runtime. Uno creates no proposal.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_report_and_pause_by_words(GameTestHelper helper) {
        Actor player = player("a_words");
        Rig[] rigs = twoRigs(helper, "a_words", player);
        Rig a = rigs[0], b = rigs[1];
        MinecraftServer server = a.level.getServer();
        List<String> answers = new CopyOnWriteArrayList<>();
        Supplier<String> last = () -> answers.get(answers.size() - 1);
        String ask = " Tell me the robot number of the job. I did nothing.";
        // The jobs before each request that must change nothing: robot, job ID, state and revision.
        Runnable unchanged = () -> {
            check(!Uno.hasProposal(player), "Uno created a proposal: " + last.get());
            check(a.job().state == State.WAITING && a.job().revision == 0 && b.job().state == State.WAITING && b.job().revision == 0,
                "a request changed a job: " + a.describe() + "\n" + b.describe());
        };
        GameTestSequence sequence = helper.startSequence()
            .thenWaitUntil(a::enroll).thenWaitUntil(a::restarted)
            .thenWaitUntil(b::enroll).thenWaitUntil(b::restarted)
            .thenExecute(() -> {
                a.startJob(24, 0);
                a.runtime.start(player, new Order(MindRuntime.TRANSPORT, b.id, b.from, a.to, Items.COAL, 16, 0));
            })
            .thenWaitUntil(() -> {
                a.state(State.WAITING);
                b.state(State.WAITING);
            });

        // A report by a station of one job: the report of that job only.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("report", "", "station", a.from)), 5000, answers)
            .thenExecute(() -> {
                MindCraft.LOGGER.info("a_report_and_pause_by_words report by station: {}", last.get());
                check(last.get().startsWith("Job " + a.job().id + ", robot #" + a.id + ": carry 24 items from '" + a.from + "' to '" + a.to + "'"), last.get());
                check(!last.get().contains("\n") && !last.get().contains("robot #" + b.id + ":"), "the report is not about one job: " + last.get());
                unchanged.run();
            });
        // A report by a robot number: the report of the job of that robot only.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("report", "", "robot", b.id)), 5000, answers)
            .thenExecute(() -> {
                check(last.get().startsWith("Job " + b.job().id + ", robot #" + b.id + ": carry 16 items from '" + b.from + "' to '" + a.to + "'"), last.get());
                check(!last.get().contains("\n") && !last.get().contains("robot #" + a.id + ":"), "the report is not about one job: " + last.get());
                unchanged.run();
            });
        // A report without a target: the report on all jobs, as the "Report" button gives it.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("report", "")), 5000, answers)
            .thenExecute(() -> {
                String[] lines = last.get().split("\n");
                check(lines.length == 2 && Stream.of(lines).anyMatch(line -> line.startsWith("Job " + a.job().id + ", robot #" + a.id + ":"))
                    && Stream.of(lines).anyMatch(line -> line.startsWith("Job " + b.job().id + ", robot #" + b.id + ":")), last.get());
                unchanged.run();
            });
        // A station of two open jobs, no target with two open jobs, a station and a robot of no job: Uno asks and changes nothing.
        Map<Supplier<String>, Supplier<String>> doubts = new LinkedHashMap<>();
        doubts.put(() -> modelAction("report", "", "station", a.to), () -> "2 of your open jobs use the station '" + a.to + "'." + ask);
        doubts.put(() -> modelAction("pause", "", "station", a.to), () -> "2 of your open jobs use the station '" + a.to + "'." + ask);
        doubts.put(() -> modelAction("pause", ""), () -> "You have 2 open jobs." + ask);
        doubts.put(() -> modelAction("pause", "", "station", "a_words_nothing"), () -> "None of your open jobs uses the station 'a_words_nothing'." + ask);
        doubts.put(() -> modelAction("pause", "", "robot", 999_999), () -> "You have no job on robot #999999." + ask);
        doubts.put(() -> modelAction("report", "", "robot", 999_999), () -> "You have no job on robot #999999." + ask);
        for (Map.Entry<Supplier<String>, Supplier<String>> doubt : doubts.entrySet()) {
            sequence = ask(sequence, server, player, "x", gives(doubt.getKey()), 5000, answers)
                .thenExecute(() -> {
                    check(last.get().equals(doubt.getValue().get()), last.get());
                    unchanged.run();
                });
        }
        // A pause by a station of one job: immediately, without a proposal. The other job continues to wait.
        int[] jobB = new int[1];
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("pause", "", "station", b.from)), 5000, answers)
            .thenExecute(() -> {
                jobB[0] = b.job().id;
                check(last.get().equals("Job " + jobB[0] + " on robot #" + b.id + " is paused. The cargo stays on board."), last.get());
                check(!Uno.hasProposal(player), "a pause became a proposal");
                check(b.job().state == State.PAUSED && b.job().reason.equals("paused by the owner") && b.job().id == jobB[0], b.describe());
                a.state(State.WAITING);
            });
        // A pause of a job that is not running: the refusal of the runtime.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("pause", "", "robot", b.id)), 5000, answers)
            .thenExecute(() -> {
                check(last.get().equals("I cannot pause the job: Job " + jobB[0] + " is not running (PAUSED)."), last.get());
                b.state(State.PAUSED);
                // The second job ends. The player has one open job now.
                a.runtime.control(player, b.id, "cancel");
            });
        // A pause without a target, with one open job: Uno pauses that job.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("pause", "")), 5000, answers)
            .thenExecute(() -> {
                check(last.get().equals("Job " + a.job().id + " on robot #" + a.id + " is paused. The cargo stays on board."), last.get());
                check(!Uno.hasProposal(player), "a pause became a proposal");
                a.state(State.PAUSED);
                b.state(State.CANCELLED);
            });
        sequence.thenSucceed();
    }

    /**
     * Increment A and T19: a different player names the job of the owner, by its robot number, by its stations or with nothing.
     * The other player cannot pause the job and gets no data of it: each sentence is the one for a job that does not exist.
     * The job of the owner does not change.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_t19_words_of_another_player(GameTestHelper helper) {
        Actor owner = player("a_t19_owner"), other = player("a_t19_other");
        Rig rig = new Rig(helper, "a_t19", 0, 1000, owner);
        MinecraftServer server = rig.level.getServer();
        String ask = " Tell me the robot number of the job. I did nothing.";
        List<String> answers = new CopyOnWriteArrayList<>();
        // An answer of the provider and the sentence for the other player.
        Map<Supplier<String>, Supplier<String>> cases = new LinkedHashMap<>();
        for (String action : List.of("pause", "report")) {
            cases.put(() -> modelAction(action, "", "robot", rig.id), () -> "You have no job on robot #" + rig.id + "." + ask);
            cases.put(() -> modelAction(action, "", "station", rig.from), () -> "None of your open jobs uses the station '" + rig.from + "'." + ask);
            cases.put(() -> modelAction(action, "", "station", rig.to), () -> "None of your open jobs uses the station '" + rig.to + "'." + ask);
        }
        cases.put(() -> modelAction("pause", ""), () -> "You have no open job." + ask);
        cases.put(() -> modelAction("report", ""), () -> "I have no record of a job of yours.");
        int[] job = new int[1];
        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> job[0] = rig.startJob(24, 0).id)
            .thenWaitUntil(() -> rig.state(State.WAITING));
        for (Map.Entry<Supplier<String>, Supplier<String>> test : cases.entrySet()) {
            sequence = ask(sequence, server, other, "x", gives(test.getKey()), 5000, answers)
                .thenExecute(() -> check(answers.get(answers.size() - 1).equals(test.getValue().get()), answers.get(answers.size() - 1)));
        }
        // The same request through the fixed rules: the context of the other player has no station and no robot.
        // The text is built when the step runs: the robot has its number only after the enrollment.
        sequence
            .thenExecute(() -> Uno.ask(server, other, "Pause robot " + rig.id, new Uno.RuleProvider(), 5000, answers::add))
            .thenWaitUntil(() -> check(answers.size() == cases.size() + 1, "Uno did not answer yet"))
            .thenExecute(() -> {
                MindCraft.LOGGER.info("a_t19_words_of_another_player: {}", answers);
                check(answers.get(answers.size() - 1).equals("You have no job on robot #" + rig.id + "." + ask), answers.get(answers.size() - 1));
                check(answers.stream().noneMatch(a -> a.contains("Job " + job[0]) || a.contains("delivered") || a.contains("paused")), answers.toString());
                check(rig.job().id == job[0] && rig.job().state == State.WAITING, rig.describe());
                check(!Uno.hasProposal(other), "the other player has a proposal");
            })
            .thenSucceed();
    }

    /**
     * Increment A: the request of the ticket, "leave only N in the depot", through the fixed rules.
     * The source has 30 and the reserve is 20: the robot delivers 10, then waits at the reserve.
     * The change is a proposal. Its text comes from the server: the old and the new reserve, and the quantity that stays.
     * Nothing changes before the confirmation. After it the job has the new reserve and a new revision,
     * and the robot takes the items above the new reserve at its next step.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_change_by_words(GameTestHelper helper) {
        Actor player = player("a_change");
        Rig rig = new Rig(helper, "a_change", 30, 1000, player);
        MinecraftServer server = rig.level.getServer();
        List<String> answers = new CopyOnWriteArrayList<>();
        int[] job = new int[1];
        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> job[0] = rig.startJob(24, 20).id)
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 10, rig.describe()));
        sequence = ask(sequence, server, player, "Leave only 6 in the " + rig.from, new Uno.RuleProvider(), 5000, answers)
            .thenExecute(() -> {
                MindCraft.LOGGER.info("a_change_by_words proposal: {}", answers.get(0));
                check(answers.get(0).equals("Proposal: change job " + job[0] + " on robot #" + rig.id + " (carry minecraft:coal from '" + rig.from
                    + "' to '" + rig.to + "'). Quantity to carry: 24, unchanged. Reserve to leave in '" + rig.from + "': from 20 to 6."
                    + " I have done nothing yet. Confirm with /uno confirm."), answers.get(0));
                check(Uno.hasProposal(player), "no proposal");
            })
            // The robot looks at the source each 5 seconds. Before the confirmation it must not take anything.
            .thenIdle(120)
            .thenExecute(() -> {
                Job now = rig.job();
                check(now.id == job[0] && now.reserve == 20 && now.quantity == 24 && now.revision == 0 && now.state == State.WAITING,
                    "the proposal changed the job: " + rig.describe());
                check(rig.coal(rig.source) == 20 && rig.coal(rig.dest) == 10, "the robot used the new reserve before the confirmation: " + rig.describe());
                String confirmed = Uno.confirm(server, player);
                MindCraft.LOGGER.info("a_change_by_words confirmation: {}", confirmed);
                check(confirmed.equals("Job " + job[0] + " on robot #" + rig.id + " changed. Quantity to carry: 24. Reserve to leave in '"
                    + rig.from + "': 6. The robot uses the new values at its next step."), confirmed);
                check(now.reserve == 6 && now.quantity == 24 && now.revision == 1 && !Uno.hasProposal(player), rig.describe());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(6, 24));
        sequence.thenSucceed();
    }

    /**
     * Increment A: a confirmation never reaches a different job or older values.
     * The proposal of a change holds the robot, the job ID and the revision. The proposal of a cancellation holds the robot and the job ID.
     * 1. A change of the quantity: the text says that the reserve stays. Then the "Apply" button changes the job. The confirmation is refused.
     * 2. A change, then the robot gets a different job with the same revision. The confirmation is refused.
     * 3. A cancellation, then the robot gets a different job. The confirmation is refused.
     * Also: a start for the busy robot is refused with the number of its job, and a new request replaces the proposal.
     * The source is empty: each job waits for materials, and its state does not change by itself.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_confirmation_of_older_values(GameTestHelper helper) {
        Actor player = player("a_older");
        Rig rig = new Rig(helper, "a_older", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        List<String> answers = new CopyOnWriteArrayList<>();
        Supplier<String> last = () -> answers.get(answers.size() - 1);
        Job[] job = new Job[1];
        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> job[0] = rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING));
        // A new job for the busy robot stays refused, with the number of its job.
        sequence = ask(sequence, server, player, "x", gives(() -> startJson(MindRuntime.TRANSPORT, rig.id, rig.from, rig.to, 8)), 5000, answers)
            .thenExecute(() -> {
                check(last.get().equals("I cannot propose this job: Robot #" + rig.id + " already has job " + job[0].id + " (WAITING)."), last.get());
                check(!Uno.hasProposal(player) && rig.job() == job[0], "a start for a busy robot became a proposal");
            });
        // 1. The quantity changes, the reserve stays. A button changes the job before the confirmation.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("change", "", "robot", rig.id, "quantity", 30)), 5000, answers)
            .thenExecute(() -> {
                check(last.get().equals("Proposal: change job " + job[0].id + " on robot #" + rig.id + " (carry minecraft:coal from '" + rig.from
                    + "' to '" + rig.to + "'). Quantity to carry: from 24 to 30. Reserve to leave in '" + rig.from + "': 0, unchanged."
                    + " I have done nothing yet. Confirm with /uno confirm."), last.get());
                check(job[0].quantity == 24 && job[0].revision == 0, rig.describe());
                UnoView.act(server, player, "apply:" + rig.id + ":" + job[0].id + ":0", "{\"quantity:" + rig.id + "\":\"24\",\"reserve:" + rig.id + "\":\"5\"}");
                check(job[0].reserve == 5 && job[0].revision == 1, rig.describe());
                String confirmed = Uno.confirm(server, player);
                check(confirmed.equals("I cannot change the job: Job " + job[0].id + " changed. Read it again."), confirmed);
                check(job[0].quantity == 24 && job[0].reserve == 5 && job[0].revision == 1, "the old proposal changed the job: " + rig.describe());
            });
        // A new request replaces the proposal before it.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("change", "", "robot", rig.id, "reserve", 2)), 5000, answers)
            .thenExecute(() -> check(Uno.hasProposal(player) && last.get().contains("from 5 to 2"), last.get()));
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("report", "")), 5000, answers)
            .thenExecute(() -> {
                check(Uno.confirm(server, player).startsWith("I have no pending proposal") && job[0].reserve == 5, rig.describe());
            });
        // 2. A change, then a different job on the robot, with the same revision as the job of the proposal.
        Job[] second = new Job[1];
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("change", "", "station", rig.to, "quantity", 30, "reserve", 3)), 5000, answers)
            .thenExecute(() -> {
                check(last.get().contains("Quantity to carry: from 24 to 30. Reserve to leave in '" + rig.from + "': from 5 to 3."), last.get());
                rig.runtime.control(player, rig.id, "cancel");
                second[0] = rig.startJob(16, 0);
                rig.runtime.update(player, rig.id, 0, 16, 0);
                check(second[0].revision == 1 && second[0].id != job[0].id, rig.describe());
                String confirmed = Uno.confirm(server, player);
                check(confirmed.equals("I cannot change the job: Robot #" + rig.id + " has job " + second[0].id + " now, not job " + job[0].id
                    + ". Read it again."), confirmed);
                check(second[0].quantity == 16 && second[0].reserve == 0 && second[0].revision == 1, "the proposal changed a different job: " + rig.describe());
            });
        // 3. A cancellation, then a different job on the robot.
        Job[] third = new Job[1];
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("cancel", "", "robot", rig.id)), 5000, answers)
            .thenExecute(() -> {
                check(last.get().startsWith("Proposal: cancel job " + second[0].id + " on robot #" + rig.id), last.get());
                rig.runtime.control(player, rig.id, "cancel");
                third[0] = rig.startJob(8, 0);
                String confirmed = Uno.confirm(server, player);
                check(confirmed.equals("I cannot cancel the job: Robot #" + rig.id + " has job " + third[0].id + " now, not job " + second[0].id
                    + ". Read it again."), confirmed);
                check(rig.job() == third[0] && third[0].state.open(), "the proposal cancelled a different job: " + rig.describe());
            });
        sequence.thenSucceed();
    }

    /**
     * Increment A: resume and cancel by words, through the fixed rules. Each is a proposal, with a text from the server.
     * A different actor empties the robot during the transport: the job goes to RECOVERY (T10).
     * The proposal to resume it says that the runtime accepts the observed load and that the totals can be inexact.
     * Then the robot takes a new load and the owner pauses the job. The proposal to cancel it says that the cargo stays on board.
     * Uno checks a request before the proposal: a resume of a running job and a cancellation of a closed job give no proposal.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_resume_and_cancel_by_words(GameTestHelper helper) {
        Actor player = player("a_resume");
        Rig rig = new Rig(helper, "a_resume", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        List<String> answers = new CopyOnWriteArrayList<>();
        Supplier<String> last = () -> answers.get(answers.size() - 1);
        Supplier<String> name = () -> "job " + rig.job().id + " on robot #" + rig.id + " (carry minecraft:coal from '" + rig.from + "' to '" + rig.to + "')";
        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> rig.robot().turtle.getInventory().clearContent())
            .thenWaitUntil(() -> rig.state(State.RECOVERY));
        sequence = ask(sequence, server, player, "Resume the job of " + rig.to, new Uno.RuleProvider(), 5000, answers)
            .thenExecute(() -> {
                MindCraft.LOGGER.info("a_resume_and_cancel_by_words resume: {}", last.get());
                check(last.get().equals("Proposal: resume " + name.get() + ". The job needs a check: the runtime accepts the load that it observes"
                    + " in the robot, and the totals can be inexact. I have done nothing yet. Confirm with /uno confirm."), last.get());
                check(rig.job().state == State.RECOVERY && rig.job().recoveries == 0, "the proposal resumed the job: " + rig.describe());
                String confirmed = Uno.confirm(server, player);
                check(confirmed.equals("I resume job " + rig.job().id + " on robot #" + rig.id + "."), confirmed);
                check(rig.job().state.active() && rig.job().recoveries == 1 && rig.job().carried == 0, rig.describe());
            });
        // The job runs: a resume gives no proposal.
        sequence = ask(sequence, server, player, "Continua", new Uno.RuleProvider(), 5000, answers)
            .thenExecute(() -> {
                check(last.get().equals("I cannot propose to resume the job: Job " + rig.job().id + " is already running."), last.get());
                check(!Uno.hasProposal(player), "a refused resume became a proposal");
            })
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> rig.runtime.control(player, rig.id, "pause"));
        sequence = ask(sequence, server, player, "Annulla il lavoro di " + rig.from, new Uno.RuleProvider(), 5000, answers)
            .thenExecute(() -> {
                MindCraft.LOGGER.info("a_resume_and_cancel_by_words cancel: {}", last.get());
                check(last.get().equals("Proposal: cancel " + name.get() + ". The cargo stays on board the robot."
                    + " I have done nothing yet. Confirm with /uno confirm."), last.get());
                rig.state(State.PAUSED);
                String confirmed = Uno.confirm(server, player);
                check(confirmed.equals("Job " + rig.job().id + " on robot #" + rig.id + " is cancelled. The cargo stays on board the robot."), confirmed);
                rig.state(State.CANCELLED);
                check(rig.held() == 24 && rig.job().carried == 24, "the cargo did not stay on board: " + rig.describe());
            });
        // The job is closed: a cancellation gives no proposal.
        sequence = ask(sequence, server, player, "x", gives(() -> modelAction("cancel", "", "robot", rig.id)), 5000, answers)
            .thenExecute(() -> {
                check(last.get().equals("I cannot propose to cancel the job: Robot #" + rig.id + " has no open job."), last.get());
                check(!Uno.hasProposal(player), "a refused cancellation became a proposal");
            });
        sequence.thenSucceed();
    }

    /**
     * Increment A and T19: a change by words through the screen. The "Confirm" button names the number of the proposal,
     * and the screen shows the text of the proposal next to it. The number fields keep the values of the record until the confirmation.
     * A different player sees no proposal. With the button of the owner and with the command, the other player confirms nothing.
     * A change request of the other player about the job of the owner gets the sentence for a job that does not exist.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_change_through_the_view(GameTestHelper helper) {
        Actor player = player("a_view_change"), other = player("a_view_change_other");
        Rig rig = new Rig(helper, "a_viewchg", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Supplier<String> view = () -> UnoView.build(rig.runtime, player, rig.level.getGameTime());
        Supplier<String> otherView = () -> UnoView.build(rig.runtime, other, rig.level.getGameTime());
        Supplier<String> reserveField = () -> "\"id\":\"reserve:" + rig.id + "\",\"label\":\"Reserve in the source\",\"value\":";
        List<String> answers = new CopyOnWriteArrayList<>();
        String[] button = new String[1];
        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> UnoView.act(server, player, "ask", "{\"request\":\"Lascia solo 5 nel " + rig.from + "\"}"))
            .thenWaitUntil(() -> check(action(view.get(), "confirm") != null, "no proposal in the view yet: " + view.get()))
            .thenExecute(() -> {
                MindCraft.LOGGER.info("a_change_through_the_view: {}", view.get());
                button[0] = action(view.get(), "confirm");
                check(button[0].equals("confirm:" + Uno.proposal(player).number()), button[0]);
                check(view.get().contains("Waiting for your confirmation: change job " + rig.job().id + " on robot #" + rig.id
                    + " (carry minecraft:coal from '" + rig.from + "' to '" + rig.to + "'). Quantity to carry: 24, unchanged. Reserve to leave in '"
                    + rig.from + "': from 0 to 5."), view.get());
                check(view.get().contains(reserveField.get() + "0"), "the number field shows the value of the proposal: " + view.get());
                // T19: the other player.
                check(action(otherView.get(), "confirm") == null && !otherView.get().contains(rig.from), otherView.get());
                UnoView.act(server, other, button[0], "{}");
                String command = Uno.confirm(server, other);
                check(command.startsWith("I have no pending proposal"), command);
                check(rig.job().reserve == 0 && rig.job().revision == 0 && Uno.hasProposal(player), "the other player confirmed: " + rig.describe());
            });
        sequence = ask(sequence, server, other, "x", gives(() -> modelAction("change", "", "robot", rig.id, "reserve", 2)), 5000, answers)
            .thenExecute(() -> {
                check(answers.get(0).equals("You have no job on robot #" + rig.id + ". Tell me the robot number of the job. I did nothing."), answers.get(0));
                check(!Uno.hasProposal(other) && Uno.hasProposal(player) && rig.job().reserve == 0, rig.describe());
                // The owner presses the button.
                UnoView.act(server, player, button[0], "{}");
                check(rig.job().reserve == 5 && rig.job().quantity == 24 && rig.job().revision == 1, rig.describe());
                check(action(view.get(), "confirm") == null && view.get().contains("Uno › Job " + rig.job().id + " on robot #" + rig.id + " changed.")
                    && view.get().contains(reserveField.get() + "5"), view.get());
            });
        sequence.thenSucceed();
    }

    /**
     * Increment A: the same behaviour through the "ask" action of the screen, with the fixed rules of the server.
     * A report by a station, a pause in Italian without a target, a pause of a job that is not running, a report of all jobs.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_report_and_pause_through_the_view(GameTestHelper helper) {
        Actor player = player("a_view");
        Rig rig = new Rig(helper, "a_view", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Supplier<String> view = () -> UnoView.build(rig.runtime, player, rig.level.getGameTime());
        String[] job = new String[1];
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> {
                job[0] = "Job " + rig.job().id;
                UnoView.act(server, player, "ask", "{\"request\":\"How is " + rig.to + "?\"}");
            })
            .thenWaitUntil(() -> check(view.get().contains("Uno › " + job[0] + ", robot #" + rig.id + ": carry 24 items"), view.get()))
            .thenExecute(() -> {
                MindCraft.LOGGER.info("a_report_and_pause_through_the_view report: {}", view.get());
                rig.state(State.WAITING);
                UnoView.act(server, player, "ask", "{\"request\":\"Metti in pausa\"}");
            })
            .thenWaitUntil(() -> check(view.get().contains("Uno › " + job[0] + " on robot #" + rig.id + " is paused. The cargo stays on board."), view.get()))
            .thenExecute(() -> {
                rig.state(State.PAUSED);
                check(action(view.get(), "confirm") == null, "a pause became a proposal: " + view.get());
                check(action(view.get(), "resume") != null && view.get().contains(" · paused"), view.get());
                UnoView.act(server, player, "ask", "{\"request\":\"Pause robot " + rig.id + "\"}");
            })
            .thenWaitUntil(() -> check(view.get().contains("Uno › I cannot pause the job: " + job[0] + " is not running (PAUSED)."), view.get()))
            .thenExecute(() -> UnoView.act(server, player, "ask", "{\"request\":\"Rapporto\"}"))
            .thenWaitUntil(() -> check(view.get().contains("Uno › " + job[0] + ", robot #" + rig.id + ":")
                && view.get().contains("I am paused at your request."), view.get()))
            .thenExecute(() -> {
                rig.state(State.PAUSED);
                check(action(view.get(), "confirm") == null, view.get());
            })
            .thenSucceed();
    }

    /** T21: the robot is off and the last observation is old. Uno gives the last known data and says that it is old. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t21_report_with_old_data(GameTestHelper helper) {
        Actor player = player("t21");
        Rig rig = new Rig(helper, "t21", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> rig.runtime.start(player, rig.order(MindRuntime.KEEP_STOCK, 24, 0)))
            .thenWaitUntil(() -> rig.state(State.SATISFIED))
            .thenExecute(() -> {
                check(!Uno.report(server, player).contains("is not running"), Uno.report(server, player));
                MindCraft.command(server, "computercraft shutdown #" + rig.id);
            })
            .thenWaitUntil(() -> check(RobotApi.live(rig.id) == null, "the computer is not off yet"))
            .thenExecute(() -> rig.chest(rig.dest).removeItem(0, 10))
            .thenIdle(100)
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t21_report_with_old_data report: {}", report);
                check(rig.coal(rig.dest) == 14, "the destination must have 14 now");
                check(report.contains("'" + rig.to + "' 24 items,") && report.contains("seconds ago") && !report.contains("14 items"), report);
                check(report.contains("At the last observation") && report.contains("not the current state"), report);
            })
            .thenSucceed();
    }

    /**
     * T22 and T23: a proposal belongs to one player. A different player cannot see or confirm it.
     * The runtime validates again at the confirmation. A proposal is not saved, the jobs and their limits are.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t22_t23_proposals_and_contact_points(GameTestHelper helper) {
        Actor alice = player("t23_alice"), bob = player("t23_bob");
        Rig rig = new Rig(helper, "t23", 64, 1000, alice);
        MinecraftServer server = rig.level.getServer();
        AtomicReference<String> answer = new AtomicReference<>();
        rig.begin()
            .thenExecute(() -> Uno.ask(server, alice, "Carry 24 coal from " + rig.from + " to " + rig.to, answer::set))
            .thenWaitUntil(() -> check(answer.get() != null, "Uno did not answer yet"))
            .thenExecute(() -> {
                check(answer.get().startsWith("Proposal: carry 24 x minecraft:coal from '" + rig.from + "' to '" + rig.to + "'"), answer.get());
                check(Uno.confirm(server, bob).startsWith("I have no pending proposal"), "Bob confirmed the proposal of Alice");
                check(Uno.report(server, bob).equals("I have no record of a job of yours."), Uno.report(server, bob));
                String bobContext = Uno.context(rig.runtime, bob);
                check(!bobContext.contains(rig.from) && !bobContext.contains("\"id\":" + rig.id), "the context of Bob has data of Alice: " + bobContext);
                // The robot gets a different job before the confirmation.
                rig.startJob(8, 0);
                check(Uno.confirm(server, alice).startsWith("I cannot start the job"), "the confirmation did not validate again");
                check(rig.job().quantity == 8, rig.describe());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                rig.assertTransferred(56, 8);
                long now = rig.level.getGameTime();
                MindRuntime copy = MindRuntime.load(rig.runtime.save(new CompoundTag()));
                String report = Uno.report(rig.runtime, alice, now);
                check(report.contains("I delivered 8 items.") && report.contains("The job is complete."), report);
                check(Uno.report(copy, alice, now).equals(report), "the report after a save and a load is different");
                check(Uno.confirm(server, alice).startsWith("I have no pending proposal"), "Uno remembered a proposal that it did not save");
            })
            .thenSucceed();
    }

    /**
     * The whole S2 flow through the view of the tablet: request, confirmation, work, a new reserve, a reopened view.
     * The test calls the same functions that the network messages call. It does not open a client screen.
     * Also T16 (an action from an old view), T19 (a different player) and T20 (the server keeps no state for a screen).
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s2_tablet_view(GameTestHelper helper) {
        Actor player = player("s2tablet"), other = player("s2tablet_other");
        Rig rig = new Rig(helper, "s2tablet", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 64));
        rig.chest(rig.source).setItem(1, new ItemStack(Items.COAL, 16));
        rig.chest(rig.dest).setItem(0, new ItemStack(Items.COAL, 10));
        Supplier<String> view = () -> UnoView.build(rig.runtime, player, rig.level.getGameTime());
        rig.begin()
            .thenExecute(() -> {
                check(view.get().contains("I have no record of a job of yours.") && action(view.get(), "confirm") == null, view.get());
                UnoView.act(server, player, "ask", "{\"request\":\"Keep at least 64 pieces of coal in " + rig.to
                    + ", taking them from " + rig.from + ". Leave at least 32 pieces in " + rig.from + ".\"}");
            })
            .thenWaitUntil(() -> check(action(view.get(), "confirm") != null, "no proposal in the view yet: " + view.get()))
            .thenExecute(() -> {
                check(rig.job() == null && view.get().contains("Proposal: keep at least 64"), view.get());
                check(view.get().contains("Waiting for your confirmation: keep at least 64 x minecraft:coal in '" + rig.to + "'"), view.get());
                // A "Confirm" that does not name the proposal starts nothing.
                UnoView.act(server, player, "confirm", "{}");
                check(rig.job() == null && view.get().contains("I did not understand the action"), view.get());
                UnoView.act(server, player, action(view.get(), "confirm"), "{}");
                check(rig.job() != null && rig.job().keepStock() && action(view.get(), "confirm") == null, view.get());
            })
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 48, rig.describe()))
            .thenExecute(() -> {
                String atLimit = view.get();
                MindCraft.LOGGER.info("s2_tablet_view at the reserve limit: {}", atLimit);
                check(atLimit.contains("waiting") && atLimit.contains("\"value\":58,\"max\":64"), atLimit);
                check(atLimit.contains("\"id\":\"reserve:" + rig.id + "\",\"label\":\"Reserve in the source\",\"value\":32"), atLimit);
                String job = rig.id + ":" + rig.job().id;
                check(atLimit.contains("\"action\":\"apply:" + job + ":0\""), atLimit);
                check(view.get().equals(atLimit), "two views of the same state are different");

                String otherView = UnoView.build(rig.runtime, other, rig.level.getGameTime());
                check(!otherView.contains(rig.from) && otherView.contains("I have no record of a job of yours."), otherView);
                UnoView.act(server, other, "cancel:" + job, "{}");
                rig.state(State.WAITING);

                String fields = "{\"quantity:" + rig.id + "\":\"64\",\"reserve:" + rig.id + "\":\"%s\"}";
                UnoView.act(server, player, "apply:" + job + ":0", String.format(fields, "0"));
                check(rig.job().reserve == 0 && rig.job().revision == 1, rig.describe());
                UnoView.act(server, player, "apply:" + job + ":0", String.format(fields, "50"));
                check(rig.job().reserve == 0 && view.get().contains("I cannot"), view.get());
                UnoView.act(server, player, "apply:" + job + ":1", String.format(fields, "abc"));
                check(rig.job().reserve == 0 && view.get().contains("I did not understand the action"), view.get());
                UnoView.act(server, player, "dig_a_mine:" + rig.id, "{}");
                check(view.get().contains("I did not understand the action"), view.get());
            })
            .thenWaitUntil(() -> rig.state(State.SATISFIED))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 64 && rig.coal(rig.source) == 26 && rig.held() == 0, rig.describe());
                check(view.get().contains("stock at level") && view.get().contains("\"value\":64,\"max\":64"), view.get());
                UnoView.act(server, player, "report", "{}");
                check(view.get().contains("I delivered 54 items."), view.get());
            })
            .thenSucceed();
    }

    /**
     * The projector. The player who puts it down registers a place. The block state shows only a general state.
     * A projector near a player is a contact point. The test does not look at the drawing of the hologram.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s2_projector(GameTestHelper helper) {
        Player placer = helper.makeMockPlayer();
        Actor owner = new Actor(placer.getUUID(), false);
        Rig rig = new Rig(helper, "s2proj", 64, 1000, owner);
        BlockPos relative = new BlockPos(3, 1, 0), pos = helper.absolutePos(relative);
        helper.setBlock(relative, MindCraft.PROJECTOR.get());
        MindCraft.PROJECTOR.get().setPlacedBy(rig.level, pos, rig.level.getBlockState(pos), placer, ItemStack.EMPTY);
        Supplier<ProjectorBlock.Look> look = () -> rig.level.getBlockState(pos).getValue(ProjectorBlock.MOOD);

        check(rig.runtime.places.stream().anyMatch(place -> place.pos().equals(pos) && place.owner().equals(owner.id())), "the place is not registered");
        check(rig.runtime.nearPlace(rig.level, pos.offset(5, 0, 0), Net.REACH), "a player at 5 blocks must reach the projector");
        check(!rig.runtime.nearPlace(rig.level, pos.offset(30, 0, 0), Net.REACH), "a player at 30 blocks must not reach the projector");
        check(UnoView.build(rig.runtime, owner, 0).contains("Places of the base with a projector: 1"), "the view of the owner does not show the place");
        check(!UnoView.build(rig.runtime, player("s2proj_other"), 0).contains("Places of the base"), "the view of a different player shows the place");
        rig.begin()
            .thenExecute(() -> {
                check(look.get() == ProjectorBlock.Look.IDLE, "expected idle, got " + look.get());
                rig.startJob(24, 0);
            })
            .thenWaitUntil(() -> check(look.get() == ProjectorBlock.Look.WORKING, "expected working, got " + look.get()))
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenWaitUntil(() -> check(look.get() == ProjectorBlock.Look.IDLE, "expected idle after the job, got " + look.get()))
            // The source has 40 and the reserve is 60: the robot cannot bring anything.
            .thenExecute(() -> rig.runtime.start(owner, rig.order(MindRuntime.KEEP_STOCK, 64, 60)))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenWaitUntil(() -> check(look.get() == ProjectorBlock.Look.ATTENTION, "expected attention, got " + look.get()))
            .thenExecute(() -> {
                MindRuntime copy = MindRuntime.load(rig.runtime.save(new CompoundTag()));
                check(copy.places.equals(rig.runtime.places), "the saved places are different");
                helper.setBlock(relative, Blocks.AIR);
                check(rig.runtime.places.stream().noneMatch(place -> place.pos().equals(pos)), "the place stays after the removal of the projector");
                check(!rig.runtime.nearPlace(rig.level, pos, Net.REACH), "a removed projector is still a contact point");
            })
            .thenSucceed();
    }

    // --- Findings of the code review of 2026-11 ------------------------------------------------------------------------

    /** The actions of the buttons of a view, as a client sends them. */
    private static List<String> actions(String view) {
        List<String> actions = new ArrayList<>();
        for (JsonElement widget : JsonParser.parseString(view).getAsJsonObject().getAsJsonArray("widgets")) {
            if (!widget.getAsJsonObject().has("buttons")) continue;
            for (JsonElement button : widget.getAsJsonObject().getAsJsonArray("buttons")) {
                actions.add(button.getAsJsonObject().get("action").getAsString());
            }
        }
        return actions;
    }

    /** The action of one button of a view, by its first word. Null if the view does not have that button. */
    private static String action(String view, String verb) {
        return actions(view).stream().filter(a -> a.equals(verb) || a.startsWith(verb + ":")).findFirst().orElse(null);
    }

    /**
     * T22: "Confirm" starts only the proposal that the player sees.
     * 1. A proposal, then a request that gives no order: the old proposal must not start.
     * 2. A proposal, then a request without an answer yet: a confirmation must not start the old proposal.
     * 3. The answer arrives: the button of the old proposal must not start the new one. A proposal starts one time only.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t22_confirm_only_the_shown_proposal(GameTestHelper helper) {
        Actor player = player("t22shown");
        Rig rig = new Rig(helper, "t22shown", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Supplier<String> view = () -> UnoView.build(rig.runtime, player, rig.level.getGameTime());
        String carry8 = "Carry 8 coal from " + rig.from + " to " + rig.to;
        String[] button = new String[1];
        List<String> answers = new CopyOnWriteArrayList<>();
        CountDownLatch slow = new CountDownLatch(1);
        rig.begin()
            .thenExecute(() -> UnoView.act(server, player, "ask", "{\"request\":\"" + carry8 + "\"}"))
            .thenWaitUntil(() -> check(action(view.get(), "confirm") != null, "no proposal in the view yet: " + view.get()))
            .thenExecute(() -> {
                button[0] = action(view.get(), "confirm");
                // This request has no quantity. It gives no order.
                UnoView.act(server, player, "ask", "{\"request\":\"Carry coal from " + rig.from + " to " + rig.to + "\"}");
            })
            .thenWaitUntil(() -> check(view.get().contains("I cannot propose a job"), view.get()))
            .thenExecute(() -> {
                List<String> wrong = new ArrayList<>();
                if (action(view.get(), "confirm") != null) wrong.add("the view offers Confirm below the answer to a failed request");
                UnoView.act(server, player, button[0], "{}");
                if (rig.job() != null) wrong.add("the button started the old proposal: " + rig.describe());
                String command = Uno.confirm(server, player);
                if (wrong.size() < 2 && rig.job() != null) wrong.add("/uno confirm started the old proposal: " + command);
                check(wrong.isEmpty(), wrong.toString());
                Uno.ask(server, player, carry8, answers::add);
            })
            .thenWaitUntil(() -> check(answers.size() == 1, "Uno did not answer yet"))
            .thenExecute(() -> {
                check(answers.get(0).startsWith("Proposal: carry 8 x"), answers.get(0));
                button[0] = action(view.get(), "confirm");
                // A command made this request, not the screen. The screen must show in words what its button confirms.
                check(button[0] != null && view.get().contains("carry 8 x minecraft:coal from '" + rig.from + "'"), view.get());
                Uno.ask(server, player, "x", (request, context) -> {
                    slow.await(10, TimeUnit.SECONDS);
                    return answer(startJson(MindRuntime.TRANSPORT, rig.id, rig.from, rig.to, 16));
                }, 15_000, answers::add);
                UnoView.act(server, player, button[0], "{}");
                check(rig.job() == null, "Confirm started the old proposal while a newer request was open");
                String command = Uno.confirm(server, player);
                check(command.startsWith("I am still working") && rig.job() == null, "/uno confirm during an open request: " + command);
                check(action(view.get(), "confirm") == null, view.get());
                slow.countDown();
            })
            .thenWaitUntil(() -> check(answers.size() == 2, "Uno did not answer yet"))
            .thenExecute(() -> {
                check(answers.get(1).startsWith("Proposal: carry 16 x"), answers.get(1));
                UnoView.act(server, player, button[0], "{}");
                check(rig.job() == null, "the button of an older proposal started the newer proposal");
                check(view.get().contains("carry 16 x minecraft:coal from '" + rig.from + "'"), view.get());
                String current = action(view.get(), "confirm");
                UnoView.act(server, player, current, "{}");
                check(rig.job() != null && rig.job().quantity == 16, "the current button did not start the proposal: " + view.get());
                int started = rig.job().id;
                UnoView.act(server, player, current, "{}");
                check(Uno.confirm(server, player).startsWith("I have no pending proposal") && rig.job().id == started, "a proposal started two times");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(48, 16))
            .thenSucceed();
    }

    /**
     * T23: two server sessions in the same Java process. The player, the robot number and the station names are the same.
     * The second session must get no message, no proposal and no open request of the first.
     * An answer to a request of the first session must change nothing in the second.
     * The test calls the function that each server start calls. It does not start a second server.
     * It has its own batch: a new session also removes the proposals of the tests that run at the same time.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT, batch = "uno_session")
    public static void t23_state_of_an_earlier_session(GameTestHelper helper) {
        Actor player = player("t23session");
        Rig rig = new Rig(helper, "t23session", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Supplier<String> view = () -> UnoView.build(rig.runtime, player, rig.level.getGameTime());
        String carry8 = "Carry 8 coal from " + rig.from + " to " + rig.to;
        List<String> late = new CopyOnWriteArrayList<>(), second = new CopyOnWriteArrayList<>();
        CountDownLatch slow = new CountDownLatch(1);
        rig.begin()
            .thenExecute(() -> UnoView.act(server, player, "ask", "{\"request\":\"" + carry8 + "\"}"))
            .thenWaitUntil(() -> check(Uno.hasProposal(player), "no proposal yet"))
            .thenExecute(() -> {
                check(view.get().contains("Proposal: carry 8 x"), view.get());
                Uno.newSession();
                List<String> wrong = new ArrayList<>();
                if (Uno.hasProposal(player)) wrong.add("the new session has the proposal of the old session");
                if (view.get().contains("carry 8 x")) wrong.add("the new session shows the message of the old session: " + Uno.said(player));
                String confirmed = Uno.confirm(server, player);
                if (rig.job() != null) wrong.add("the new session started the proposal of the old session: " + confirmed);
                check(wrong.isEmpty(), wrong.toString());

                // A request is open when the session ends. Its answer would be a valid order of 16.
                Uno.ask(server, player, "x", (request, context) -> {
                    slow.await(10, TimeUnit.SECONDS);
                    return answer(startJson(MindRuntime.TRANSPORT, rig.id, rig.from, rig.to, 16));
                }, 15_000, late::add);
                Uno.newSession();
                // The open request of the old session must not keep the player waiting in the new session.
                Uno.ask(server, player, carry8, second::add);
            })
            .thenWaitUntil(() -> check(second.size() == 1, "no answer in the new session"))
            .thenExecute(() -> {
                check(second.get(0).startsWith("Proposal: carry 8 x"), second.get(0));
                slow.countDown();
            })
            .thenIdle(40)
            .thenExecute(() -> {
                check(late.isEmpty(), "the answer of the old session arrived in the new session: " + late);
                check(!view.get().contains("carry 16 x"), view.get());
                String started = Uno.confirm(server, player);
                check(rig.job() != null && rig.job().quantity == 8, "the proposal of the new session changed: " + started);
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(56, 8))
            .thenSucceed();
    }

    /**
     * T23 for the robots: the static state of the robots belongs to one server session, as the state of Uno does.
     * A robot session, a boot count and a pending computer restart of an earlier server must not count in the next server.
     * The test calls the listener of the server start with a new event. It does not post the event and does not start a second server.
     * It has its own batch: the listener also ends the robot sessions of the tests that run at the same time.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT, batch = "robot_session")
    public static void t23_robots_of_an_earlier_session(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t23robots", 64, 1000);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> {
                RobotApi old = rig.robot();
                MindCraft.reboot(server, rig.id);
                check(old != null && RobotApi.boots(rig.id) > 0 && MindCraft.REBOOTS.containsKey(rig.id), "the setup is not complete");
                MindCraft.aboutToStart(new ServerAboutToStartEvent(server));
                List<String> wrong = new ArrayList<>();
                if (RobotApi.live(rig.id) != null) wrong.add("the robot session of the old server is a running robot in the new one");
                if (RobotApi.boots(rig.id) != 0) wrong.add("the new server has the boot count of the old one: " + RobotApi.boots(rig.id));
                if (!MindCraft.REBOOTS.isEmpty()) wrong.add("the new server has a pending computer restart of the old one");
                try {
                    rig.startJob(8, 0);
                    wrong.add("the runtime started a job on a robot session of the old server");
                } catch (Refusal expected) {
                    if (!expected.getMessage().contains("is not running")) wrong.add("wrong refusal: " + expected.getMessage());
                }
                try {
                    old.status();
                    wrong.add("the robot session of the old server still has authority");
                } catch (dan200.computercraft.api.lua.LuaException expected) {
                    // The old session must not act.
                }
                check(wrong.isEmpty(), wrong.toString());
            })
            .thenSucceed();
    }

    // --- Increment A: limits on the model calls (D33) ----------------------------------------------------------------

    /** The answer of the fixed rules to the request "x": it has no quantity. */
    private static final String RULES_X = "I cannot propose a job: the quantity is missing.";
    /** The answer of Uno to the action "none" of a CountingModel made with {@link CountingModel#none}. */
    private static final String MODEL_X = "I cannot propose a job: the model read it.";

    static Actor operator(String name) {
        return new Actor(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), true);
    }

    /**
     * A provider of the tests that counts as a provider with a model. Thus the operator rule and the session limit apply to it.
     * It calls no model and costs nothing. It counts its calls, and gives the answer of the provider inside it.
     */
    static final class CountingModel implements Uno.Provider {
        final AtomicInteger calls = new AtomicInteger();
        private final Uno.Provider inner;

        CountingModel(Uno.Provider inner) {
            this.inner = inner;
        }

        /** A model that answers "none" with the reason "the model read it", and reports these tokens. */
        static CountingModel none(long inputTokens, long outputTokens) {
            return new CountingModel((request, context) ->
                new Uno.Answer("{\"action\":\"none\",\"reason\":\"the model read it\"}", inputTokens, outputTokens));
        }

        @Override
        public Uno.Answer answer(String request, String context) throws Exception {
            calls.incrementAndGet();
            return inner.answer(request, context);
        }

        @Override
        public boolean callsModel() {
            return true;
        }
    }

    /** Sends the request "x" the given number of times. Each request leaves after the answer to the one before it. */
    private static void askTimes(MinecraftServer server, Actor actor, Uno.Provider provider, int times, List<String> answers) {
        if (times <= 0) return;
        Uno.ask(server, actor, "x", provider, 5000, answer -> {
            answers.add(answer);
            askTimes(server, actor, provider, times - 1, answers);
        });
    }

    /** The counter of the actor in this server session: the requests to a model, the input tokens and the output tokens. */
    private static void usage(Actor actor, int requests, long inputTokens, long outputTokens) {
        Uno.Usage usage = Uno.usage(actor);
        check(usage.equals(new Uno.Usage(requests, inputTokens, outputTokens)), "expected " + requests + " requests, "
            + inputTokens + " input tokens and " + outputTokens + " output tokens, but: " + usage);
    }

    /**
     * Uno counts, for each actor in one server session, the requests that it sent to a model, and their tokens.
     * A request counts when Uno sends it: before its answer. A failure and the time limit count with zero tokens.
     * A request that finds an open request is not sent, thus it does not count. The fixed rules do not count.
     * The console is an actor too, with a counter of its own.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_model_requests_count(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Actor operator = operator("a_count"), other = operator("a_count_other");
        CountingModel model = CountingModel.none(120, 30);
        CountDownLatch held = new CountDownLatch(1);
        CountingModel slow = new CountingModel((request, context) -> {
            held.await(10, TimeUnit.SECONDS);
            return new Uno.Answer("{\"action\":\"none\",\"reason\":\"the model read it\"}", 5, 5);
        });
        CountingModel broken = new CountingModel((request, context) -> {
            throw new IOException("model offline");
        });
        AtomicReference<Boolean> lateReturned = new AtomicReference<>(false);
        CountingModel late = new CountingModel((request, context) -> {
            Thread.sleep(2000);
            lateReturned.set(true);
            return new Uno.Answer("{\"action\":\"none\",\"reason\":\"the model read it\"}", 999, 999);
        });
        List<String> answers = new CopyOnWriteArrayList<>(), busy = new CopyOnWriteArrayList<>();
        Uno.Usage[] console = new Uno.Usage[1];

        GameTestSequence sequence = ask(helper.startSequence(), server, operator, "x", model, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(0).equals(MODEL_X), answers.toString());
                usage(operator, 1, 120, 30);
                // The provider of this request waits for the test. Thus the request is open when the second one arrives.
                Uno.ask(server, operator, "x", slow, 15_000, answers::add);
                Uno.ask(server, operator, "x", model, 5000, busy::add);
                check(busy.equals(List.of("I am still working on your previous request.")), "a second request did not wait: " + busy);
                check(model.calls.get() == 1, "the refused request went to the model");
                // The open request counts already, with zero tokens until its answer.
                usage(operator, 2, 120, 30);
                held.countDown();
            })
            .thenWaitUntil(() -> check(answers.size() == 2, "Uno did not answer yet"))
            .thenExecute(() -> usage(operator, 2, 125, 35));
        sequence = ask(sequence, server, operator, "x", broken, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(2).startsWith("I cannot interpret the request now (model offline)"), answers.get(2));
                usage(operator, 3, 125, 35);
            });
        sequence = ask(sequence, server, operator, "x", late, 300, answers)
            .thenExecute(() -> {
                check(answers.get(3).contains("TimeoutException"), answers.get(3));
                usage(operator, 4, 125, 35);
            })
            // The provider answers after the time limit, with tokens. Uno does not use that answer.
            .thenWaitUntil(() -> check(lateReturned.get(), "the late provider did not return yet"))
            .thenIdle(5)
            .thenExecute(() -> usage(operator, 4, 125, 35));
        // The fixed rules call no model. Thus they do not count.
        sequence = ask(sequence, server, operator, "x", new Uno.RuleProvider(), 5000, answers)
            .thenExecute(() -> {
                check(answers.get(4).equals(RULES_X), answers.get(4));
                usage(operator, 4, 125, 35);
                usage(other, 0, 0, 0);
                check(slow.calls.get() == 1 && broken.calls.get() == 1 && late.calls.get() == 1, "a provider got a wrong number of calls");
                console[0] = Uno.usage(ADMIN);
            });
        sequence = ask(sequence, server, ADMIN, "x", model, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(5).equals(MODEL_X), answers.get(5));
                usage(ADMIN, console[0].requests() + 1, console[0].inputTokens() + 120, console[0].outputTokens() + 30);
                usage(operator, 4, 125, 35);
            });
        sequence.thenSucceed();
    }

    /**
     * The limit is 100 requests to a model for each actor in one server session. The request after the limit goes to the
     * fixed rules. Its answer says one time that the limit is reached. Each actor has a limit of its own.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_model_limit(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Actor operator = operator("a_limit"), other = operator("a_limit_other");
        CountingModel model = CountingModel.none(10, 2);
        List<String> answers = new CopyOnWriteArrayList<>();
        GameTestSequence sequence = helper.startSequence()
            .thenExecute(() -> askTimes(server, operator, model, 100, answers))
            .thenWaitUntil(() -> check(answers.size() == 100, "answers so far: " + answers.size()))
            .thenExecute(() -> {
                check(answers.stream().allMatch(MODEL_X::equals), "an answer below the limit is not the answer of the model: " + answers);
                check(model.calls.get() == 100, "calls: " + model.calls.get());
                usage(operator, 100, 1000, 200);
            });
        sequence = ask(sequence, server, operator, "x", model, 5000, answers)
            .thenExecute(() -> {
                String said = answers.get(100);
                MindCraft.LOGGER.info("a_model_limit, the request after the limit: {}", said);
                check(model.calls.get() == 100, "the request after the limit went to the model");
                check(said.startsWith("You reached the limit of 100 model requests in this server session.") && said.endsWith(" " + RULES_X), said);
                usage(operator, 100, 1000, 200);
            });
        sequence = ask(sequence, server, operator, "x", model, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(101).equals(RULES_X), "the second answer after the limit: " + answers.get(101));
                check(model.calls.get() == 100, "a request after the limit went to the model");
            });
        sequence = ask(sequence, server, other, "x", model, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(102).equals(MODEL_X) && model.calls.get() == 101, "the limit of one operator stopped a different operator");
                usage(other, 1, 10, 2);
            });
        sequence.thenSucceed();
    }

    /**
     * Only an operator uses a provider that calls a model. Each other player gets the fixed rules, and has no counter.
     * A provider of the tests without a model still serves each player: the rule is only for a provider with a model.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_model_only_for_operators(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Actor guest = player("a_guest");
        CountingModel model = CountingModel.none(10, 2);
        List<String> answers = new CopyOnWriteArrayList<>();
        GameTestSequence sequence = ask(helper.startSequence(), server, guest, "x", model, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(0).equals(RULES_X), answers.get(0));
                check(model.calls.get() == 0, "the request of a player who is not an operator went to the model");
                usage(guest, 0, 0, 0);
            });
        sequence = ask(sequence, server, guest, "x", (request, context) -> answer("{\"action\":\"none\",\"reason\":\"the test read it\"}"), 5000, answers)
            .thenExecute(() -> check(answers.get(1).equals("I cannot propose a job: the test read it."), answers.get(1)));
        sequence.thenSucceed();
    }

    /**
     * A new server session starts the counters again: an operator at the limit uses the model again.
     * The test calls the function that each server start calls. It does not start a second server.
     * It has its own batch: a new session also removes the proposals and the counters of the tests that run at the same time.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT, batch = "a_model_session")
    public static void a_new_session_restarts_the_counters(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Actor operator = operator("a_session");
        CountingModel model = CountingModel.none(10, 2);
        List<String> answers = new CopyOnWriteArrayList<>();
        GameTestSequence sequence = helper.startSequence()
            .thenExecute(() -> askTimes(server, operator, model, 100, answers))
            .thenWaitUntil(() -> check(answers.size() == 100, "answers so far: " + answers.size()));
        sequence = ask(sequence, server, operator, "x", model, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(100).startsWith("You reached the limit") && model.calls.get() == 100, "the limit is not reached: " + answers.get(100));
                Uno.newSession();
                usage(operator, 0, 0, 0);
            });
        sequence = ask(sequence, server, operator, "x", model, 5000, answers)
            .thenExecute(() -> {
                check(answers.get(101).equals(MODEL_X) && model.calls.get() == 101, "the new session did not use the model: " + answers.get(101));
                usage(operator, 1, 10, 2);
            });
        sequence.thenSucceed();
    }

    /**
     * The screen and the provider of the server. With Claude on the server, the screen of each player says where the written
     * requests of that player go. The counter is on the screen of an operator, also at the limit. It is not on the screen of a
     * player who is not an operator. With the fixed rules on the server, the screen has no counter and no line about a provider.
     * A local stand-in is in the place of the API: no request reaches the real service.
     * It has its own batch, because it changes the provider of the server. It changes it only inside a step, and puts the
     * provider of before back at the end of that step, also when a check fails. A screen reads the provider of the server
     * when it builds a view and when it sends a request.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT, batch = "a_server_provider")
    public static void a_screen_model_lines(GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        MindRuntime runtime = MindRuntime.get(server);
        Actor operator = operator("a_screen"), guest = player("a_screen_guest"), spent = operator("a_screen_spent");
        AtomicInteger calls = new AtomicInteger();
        HttpServer api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        api.createContext("/", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = claudeMessage("end_turn", modelAction("none", "You did not give the source."), 1234, 56).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        api.start();
        Uno.Provider claude = new ClaudeProvider(AnthropicOkHttpClient.builder().apiKey("not-a-key")
            .baseUrl("http://127.0.0.1:" + api.getAddress().getPort()).maxRetries(0).build());
        // A view and a request through the screen with Claude as the provider of the server.
        Function<Actor, String> view = actor -> withServerProvider(claude, () -> UnoView.build(runtime, actor, helper.getLevel().getGameTime()));
        BiConsumer<Actor, String> write = (actor, request) -> withServerProvider(claude, () -> {
            UnoView.act(server, actor, "ask", "{\"request\":\"" + request + "\"}");
            return null;
        });
        String claudeLine = "Written requests go to Claude, a model of Anthropic.", rulesLine = "Uno uses the fixed rules for you";
        List<String> answers = new CopyOnWriteArrayList<>();
        helper.startSequence()
            .thenExecute(() -> {
                for (Actor actor : List.of(operator, guest)) {
                    String rules = UnoView.build(runtime, actor, helper.getLevel().getGameTime());
                    check(!rules.contains("model requests") && !rules.contains("Claude") && !rules.contains("fixed rules"),
                        "the fixed rules are on the server, but the view has a counter or a line about a provider: " + rules);
                }
                String mine = view.apply(operator);
                check(mine.contains(claudeLine) && mine.contains("Your model requests in this session: 0 of 100. Tokens: 0 in, 0 out.")
                    && !mine.contains(rulesLine), "the view of an operator: " + mine);
                String theirs = view.apply(guest);
                check(!theirs.contains(claudeLine) && !theirs.contains("model requests")
                    && theirs.contains(rulesLine + ": only an operator uses Claude."), "the view of a player who is not an operator: " + theirs);
                write.accept(operator, "x");
            })
            .thenWaitUntil(() -> check(view.apply(operator).contains("You did not give the source."), "Claude did not answer yet"))
            .thenExecute(() -> {
                String mine = view.apply(operator);
                check(calls.get() == 1 && mine.contains("Your model requests in this session: 1 of 100. Tokens: 1234 in, 56 out."), mine);
                write.accept(guest, "x");
            })
            .thenWaitUntil(() -> check(view.apply(guest).contains(RULES_X), view.apply(guest)))
            .thenExecute(() -> {
                check(calls.get() == 1, "the request of a player who is not an operator went to Claude");
                // The counter belongs to the actor, not to the provider: a model of the test brings this operator to the limit.
                askTimes(server, spent, CountingModel.none(10, 2), 100, answers);
            })
            .thenWaitUntil(() -> check(answers.size() == 100, "answers so far: " + answers.size()))
            .thenExecute(() -> {
                String atLimit = view.apply(spent);
                check(atLimit.contains("Your model requests in this session: 100 of 100. Tokens: 1000 in, 200 out.") && !atLimit.contains(claudeLine)
                    && atLimit.contains(rulesLine + ": you reached the limit of model requests in this session."), "the view at the limit: " + atLimit);
                write.accept(spent, "x");
            })
            .thenWaitUntil(() -> check(view.apply(spent).contains(RULES_X), view.apply(spent)))
            .thenExecute(() -> {
                String atLimit = view.apply(spent);
                check(calls.get() == 1 && atLimit.contains("You reached the limit of 100 model requests in this server session."), atLimit);
                api.stop(0);
            })
            .thenSucceed();
    }

    /** Runs the action with this provider of the server, then puts the provider of before back, also after a failed check. */
    private static <T> T withServerProvider(Uno.Provider provider, Supplier<T> action) {
        Uno.Provider before = Uno.provider;
        Uno.provider = provider;
        try {
            return action.get();
        } finally {
            Uno.provider = before;
        }
    }

    /**
     * T16: the owner lowers the quantity of a transport while the load is on board.
     * The robot delivers the new quantity only. The rest stays on board and the job says so. The owner decides.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t16_quantity_lowered_with_load(GameTestHelper helper) {
        listen();
        Actor player = player("t16load");
        Rig rig = new Rig(helper, "t16load", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> rig.startJob(64, 0))
            .thenWaitUntil(() -> rig.carried(64))
            .thenExecute(() -> rig.runtime.update(player, rig.id, 0, 10, 0))
            .thenWaitUntil(() -> check(rig.job().delivered > 0, rig.describe()))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t16_quantity_lowered_with_load report: {}", report);
                check(rig.coal(rig.dest) == 10 && rig.held() == 54 && rig.coal(rig.source) == 0,
                    "destination " + rig.coal(rig.dest) + ", robot " + rig.held() + ". " + rig.describe());
                rig.state(State.BLOCKED);
                check(rig.job().cause == Cause.LOAD_ABOVE_QUANTITY, rig.describe());
                check(rig.job().delivered == 10 && rig.job().carried == 54 && rig.job().reason.contains("54 stay on board"), rig.describe());
                check(report.contains("I delivered 10 items.") && report.contains("54 stay on board") && !report.contains("The job is complete"), report);
                check(!report.contains("Recorded reason"), "the sentence must come from the cause: " + report);
                check(notices(player).equals(List.of(subject(rig, rig.job()) + " is blocked: the quantity of the job is reached, and 54 stay on board.")),
                    notices(player).toString());
            })
            .thenIdle(140)
            .thenExecute(() -> {
                // Seven seconds later. The robot delivers nothing more without a decision of the owner.
                rig.state(State.BLOCKED);
                check(rig.coal(rig.dest) == 10 && rig.held() == 54, rig.describe());
                rig.runtime.update(player, rig.id, 1, 64, 0);
                rig.runtime.control(player, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(0, 64))
            .thenSucceed();
    }

    /**
     * T11, the sudden stop, without a second server run. The record says that the load is delivered, but the robot still holds it.
     * The test writes the record of a later moment. It does not stop the server. See RestartTests for the real stop.
     * After "resume" the robot must deliver the load that it holds: the limit of the quantity must not keep it on board.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t11_resume_when_the_world_is_older(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t11older", 64, 1000);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> {
                Job job = rig.job();
                job.delivered = 24;
                job.carried = 0;
                job.lastOpAt = rig.level.getGameTime() + 10_000;
                rig.runtime.set(job, State.RECOVERY, "the world is older than the record of this job");
            })
            .thenIdle(60)
            .thenExecute(() -> {
                rig.state(State.RECOVERY);
                check(rig.held() == 24 && rig.coal(rig.dest) == 0, "the robot must stop: " + rig.describe());
                rig.runtime.control(rig.actor, rig.id, "resume");
            })
            .thenWaitUntil(() -> check(!rig.job().state.active(), rig.describe()))
            .thenExecute(() -> {
                rig.assertTransferred(40, 24);
                check(rig.job().recoveries == 1 && rig.job().delivered == 24 && rig.describe().contains("inexact"), rig.describe());
            })
            .thenSucceed();
    }

    /** The rig of T03: the destination has 60 items of coal and room for 4 more. */
    private static Container almostFull(Rig rig) {
        Container dest = rig.chest(rig.dest);
        dest.setItem(0, new ItemStack(Items.COAL, 60));
        for (int slot = 1; slot < dest.getContainerSize(); slot++) dest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        return dest;
    }

    /** T16 with T03: the destination accepts a part, then the owner lowers the quantity. The robot delivers up to the new quantity. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t16_quantity_lowered_after_partial_delivery(GameTestHelper helper) {
        Actor player = player("t16part");
        Rig rig = new Rig(helper, "t16part", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Container dest = almostFull(rig);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 4, rig.describe()))
            .thenExecute(() -> {
                rig.runtime.update(player, rig.id, 0, 10, 0);
                dest.setItem(1, ItemStack.EMPTY);
            })
            .thenWaitUntil(() -> check(rig.job().delivered > 4, rig.describe()))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t16_quantity_lowered_after_partial_delivery report: {}", report);
                check(rig.coal(rig.dest) == 70 && rig.held() == 14 && rig.coal(rig.source) == 40,
                    "destination " + rig.coal(rig.dest) + ", robot " + rig.held() + ". " + rig.describe());
                rig.state(State.BLOCKED);
                check(rig.job().cause == Cause.LOAD_ABOVE_QUANTITY, rig.describe());
                check(rig.job().delivered == 10 && rig.job().carried == 14, rig.describe());
                check(report.contains("I delivered 10 items.") && report.contains("14 stay on board"), report);
            })
            .thenSucceed();
    }

    /**
     * T16: the new quantity is lower than the delivered quantity, and a load is on board.
     * The job must not be complete while a load is on board. No more items go to the destination.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t16_quantity_below_delivered(GameTestHelper helper) {
        Actor player = player("t16below");
        Rig rig = new Rig(helper, "t16below", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Container dest = almostFull(rig);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 4, rig.describe()))
            .thenExecute(() -> rig.runtime.update(player, rig.id, 0, 2, 0))
            // The robot tries the delivery again after 5 seconds. The destination is still full.
            .thenWaitUntil(() -> check(rig.job().state != State.WAITING, rig.describe()))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t16_quantity_below_delivered report: {}", report);
                rig.state(State.BLOCKED);
                check(rig.job().cause == Cause.LOAD_ABOVE_QUANTITY, rig.describe());
                check(rig.coal(rig.dest) == 64 && rig.held() == 20 && rig.coal(rig.source) == 40, rig.describe());
                check(rig.job().delivered == 4 && rig.job().carried == 20, rig.describe());
                check(report.contains("carry 2 items") && report.contains("I delivered 4 items. That is more than the quantity"), report);
                check(report.contains("20 stay on board") && !report.contains("The job is complete"), report);
                // The destination has room now, and the owner resumes. The quantity stays 2.
                dest.setItem(1, ItemStack.EMPTY);
                rig.runtime.control(player, rig.id, "resume");
            })
            .thenIdle(140)
            .thenExecute(() -> {
                rig.state(State.BLOCKED);
                check(rig.coal(rig.dest) == 64 && rig.held() == 20, "the robot delivered above the quantity: " + rig.describe());
                rig.runtime.control(player, rig.id, "cancel");
                rig.state(State.CANCELLED);
                check(rig.describe().contains("on board 20"), rig.describe());
            })
            .thenSucceed();
    }

    /**
     * T16 for "keep_stock": the owner lowers the level while the load is on board.
     * The robot fills the destination up to the new level. The rest stays on board for the next need.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t16_level_lowered_with_load(GameTestHelper helper) {
        Actor player = player("t16level");
        Rig rig = new Rig(helper, "t16level", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Container source = rig.chest(rig.source), dest = rig.chest(rig.dest);
        source.setItem(0, new ItemStack(Items.COAL, 64));
        source.setItem(1, new ItemStack(Items.COAL, 64));
        dest.setItem(0, new ItemStack(Items.COAL, 10));
        rig.begin()
            .thenExecute(() -> rig.runtime.start(player, rig.order(MindRuntime.KEEP_STOCK, 64, 0)))
            .thenWaitUntil(() -> rig.carried(54))
            .thenExecute(() -> rig.runtime.update(player, rig.id, 0, 20, 0))
            .thenWaitUntil(() -> check(rig.job().delivered > 0, rig.describe()))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t16_level_lowered_with_load report: {}", report);
                check(rig.coal(rig.dest) == 20 && rig.held() == 44 && rig.coal(rig.source) == 74,
                    "destination " + rig.coal(rig.dest) + ", robot " + rig.held() + ". " + rig.describe());
                rig.state(State.SATISFIED);
                check(rig.job().delivered == 10 && rig.job().carried == 44 && rig.job().reason.contains("44 stay on board"), rig.describe());
                check(report.contains("I have 44 on board") && report.contains("I keep the load on board"), report);
                // Something uses 15 items. The robot fills the level again from the load on board.
                dest.removeItem(0, 15);
            })
            .thenWaitUntil(() -> check(rig.job().delivered == 25, rig.describe()))
            .thenExecute(() -> {
                rig.state(State.SATISFIED);
                check(rig.coal(rig.dest) == 20 && rig.held() == 29 && rig.coal(rig.source) == 74, rig.describe());
            })
            .thenSucceed();
    }

    /**
     * T16, a stale view: the screen shows job A. Job A ends and job B starts on the same robot.
     * The actions of the view of job A must not change job B: Apply, Pause, Resume and Cancel.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t16_actions_of_an_old_view(GameTestHelper helper) {
        Actor player = player("t16view");
        Rig rig = new Rig(helper, "t16view", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Supplier<String> view = () -> UnoView.build(rig.runtime, player, rig.level.getGameTime());
        String[] old = new String[3];
        rig.begin()
            .thenExecute(() -> {
                rig.startJob(8, 0);
                old[0] = action(view.get(), "apply");
                old[1] = action(view.get(), "pause");
                old[2] = action(view.get(), "cancel");
                check(old[0] != null && old[1] != null && old[2] != null, view.get());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                Job b = rig.startJob(8, 0);
                String fields = "{\"quantity:" + rig.id + "\":\"50\",\"reserve:" + rig.id + "\":\"7\"}";
                List<String> wrong = new ArrayList<>();
                UnoView.act(server, player, old[0], fields);
                if (b.quantity != 8 || b.reserve != 0 || b.revision != 0) wrong.add("Apply changed job B to " + b.quantity + ", reserve " + b.reserve);
                UnoView.act(server, player, old[1], "{}");
                if (b.state != State.RUNNING) wrong.add("Pause changed job B to " + b.state);
                if (b.state.active()) rig.runtime.control(player, rig.id, "pause");
                UnoView.act(server, player, old[1].replaceFirst("pause", "resume"), "{}");
                if (b.state != State.PAUSED) wrong.add("Resume changed job B to " + b.state);
                UnoView.act(server, player, old[2], "{}");
                if (b.state == State.CANCELLED) wrong.add("Cancel changed job B");
                check(wrong.isEmpty(), wrong.toString());
                check(view.get().contains("I cannot"), view.get());

                // The actions of the current view work on job B.
                UnoView.act(server, player, action(view.get(), "resume"), "{}");
                check(b.state == State.RUNNING, rig.describe());
                UnoView.act(server, player, action(view.get(), "apply"), "{\"quantity:" + rig.id + "\":\"16\",\"reserve:" + rig.id + "\":\"0\"}");
                check(b.quantity == 16 && b.revision == 1, rig.describe());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /** Keeps the answers of the commands that a source sends. */
    private static CommandSource sink(List<String> answers) {
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                answers.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
    }

    /**
     * T15, minimum: a transport ends, and a "keep_stock" job starts on the same robot in the same tick.
     * The Lua module of the first job must not run the second job. Each job runs with its own module.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t15_next_job_with_another_module(GameTestHelper helper) {
        Rig rig = new Rig(helper, "t15next", 64, 1000);
        rig.begin()
            .thenExecute(() -> rig.startJob(8, 0))
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.runtime.start(rig.actor, rig.order(MindRuntime.KEEP_STOCK, 16, 0)))
            .thenWaitUntil(() -> check(rig.job().state == State.SATISFIED || rig.job().state == State.BLOCKED, rig.describe()))
            .thenExecute(() -> {
                rig.state(State.SATISFIED);
                check(rig.job().executor.equals("keep_stock@3") && rig.coal(rig.dest) == 16 && rig.coal(rig.source) == 48, rig.describe());
            })
            .thenSucceed();
    }

    /** A command source of a player entity with a permission level. */
    private static CommandSourceStack source(Player player, int permission, List<String> answers) {
        return player.createCommandSourceStack().withPermission(permission).withSource(sink(answers));
    }

    /**
     * T19 for the status command. The test sends the real command as three player entities and as the console.
     * Only the owner, an operator and the console read the job. A different player gets a refusal without data of the job.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t19_status_command(GameTestHelper helper) {
        Player ownerPlayer = helper.makeMockPlayer(), otherPlayer = helper.makeMockPlayer();
        Rig rig = new Rig(helper, "t19cmd", 64, 1000, new Actor(ownerPlayer.getUUID(), false));
        MinecraftServer server = rig.level.getServer();
        List<String> owner = new ArrayList<>(), other = new ArrayList<>(), operator = new ArrayList<>(), console = new ArrayList<>();
        rig.begin()
            .thenExecute(() -> {
                rig.startJob(24, 0);
                String status = "mindcraft job status " + rig.id;
                check(server.getCommands().performPrefixedCommand(source(ownerPlayer, 0, owner), status) == 1
                    && owner.toString().contains("Job " + rig.job().id) && owner.toString().contains(rig.from), "the owner: " + owner);
                int result = server.getCommands().performPrefixedCommand(source(otherPlayer, 0, other), status);
                check(result == 0 && other.toString().contains("not yours"), "a different player got result " + result + ": " + other);
                check(!other.toString().contains(rig.from) && !other.toString().contains("minecraft:coal"), "a different player read the job: " + other);
                check(server.getCommands().performPrefixedCommand(source(otherPlayer, 2, operator), status) == 1
                    && operator.toString().contains(rig.from), "an operator: " + operator);
                check(server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(sink(console)), status) == 1
                    && console.toString().contains(rig.from), "the console: " + console);
                // The same path for a control: the command of a different player changes nothing.
                check(server.getCommands().performPrefixedCommand(source(otherPlayer, 0, other), "mindcraft job cancel " + rig.id) == 0, other.toString());
                check(rig.job().state.active() && !other.toString().contains(rig.from), other.toString());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /**
     * T21: the cause of a wait in the report. The source is empty and the reserve is 0: the report must not name a reserve.
     * Then the source has 44 and the reserve is 32: the report names the reserve, with the observed number.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t21_report_source_at_reserve(GameTestHelper helper) {
        Actor player = player("t21reserve");
        Rig rig = new Rig(helper, "t21reserve", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t21_report_source_at_reserve report, empty source: {}", report);
                check(report.contains("Nothing remains in '" + rig.from + "'") && !report.contains("reserve you asked"), report);
                rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 44));
                rig.runtime.update(player, rig.id, 0, 24, 32);
            })
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 12, rig.describe()))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t21_report_source_at_reserve report, at the reserve: {}", report);
                check(rig.coal(rig.source) == 32 && rig.coal(rig.dest) == 12, rig.describe());
                check(report.contains("32 remain in '" + rig.from + "': that is the reserve you asked me to keep"), report);
                check(report.contains("I delivered 12 items.") && report.contains("'" + rig.from + "' 32 items,"), report);

                // A save and a load keep the cause. A record of an older version has no cause: Uno gives the recorded text and adds nothing.
                long now = rig.level.getGameTime();
                CompoundTag saved = rig.runtime.save(new CompoundTag());
                MindRuntime copy = MindRuntime.load(saved);
                check(copy.jobs.get(rig.id).cause == rig.job().cause, "the saved cause is different");
                check(Uno.report(copy, player, now).contains("that is the reserve you asked me to keep"), Uno.report(copy, player, now));
                saved.getCompound("jobs").getCompound(String.valueOf(rig.id)).remove("cause");
                String old = Uno.report(MindRuntime.load(saved), player, now);
                check(old.contains("I am waiting. Recorded reason: the source has 32, the reserve is 32.") && !old.contains("reserve you asked"), old);
            })
            .thenSucceed();
    }

    /** T21: the robot waits because its inventory is full. The report must say that, not name the reserve. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t21_report_robot_full(GameTestHelper helper) {
        Actor player = player("t21full");
        Rig rig = new Rig(helper, "t21full", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> {
                Container inventory = rig.robot().turtle.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                rig.startJob(24, 0);
            })
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t21_report_robot_full report: {}", report);
                check(rig.job().reason.contains("the robot inventory is full") && rig.coal(rig.source) == 64, rig.describe());
                check(report.contains("My inventory is full") && !report.contains("reserve you asked"), report);
                rig.robot().turtle.getInventory().setItem(0, ItemStack.EMPTY);
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    /**
     * T21: the robot waits because the area of the source inventory is not loaded. The report must say that the stock is unknown.
     * Artificial condition: the test moves the inventory of the station record to an area that is not loaded. The dock stays.
     * The runtime does not accept such a station at the registration: the dock must be next to the inventory.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void t21_report_inventory_not_loaded(GameTestHelper helper) {
        Actor player = player("t21far");
        Rig rig = new Rig(helper, "t21far", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        Station real = rig.runtime.stations.get(rig.from);
        rig.begin()
            .thenExecute(() -> {
                rig.runtime.stations.put(rig.from, new Station(real.owner(), real.dim(), real.inventory().offset(1_000_000, 0, 0), real.dock()));
                rig.startJob(24, 0);
            })
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> {
                String report = Uno.report(server, player);
                MindCraft.LOGGER.info("t21_report_inventory_not_loaded report: {}", report);
                check(rig.job().reason.contains("the source inventory is not loaded"), rig.describe());
                check(report.contains("is not loaded") && report.contains("I do not know its stock"), report);
                check(!report.contains("reserve you asked") && !report.contains("-1") && report.contains("'" + rig.from + "' never observed"), report);
                rig.runtime.stations.put(rig.from, real);
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> rig.assertTransferred(40, 24))
            .thenSucceed();
    }

    // --- Increment A: the job diary and the notices ---------------------------------------------------------------

    /** The entries of one job that the actor reads, the oldest first. */
    private static List<Entry> entries(MindRuntime runtime, Actor actor, int jobId) {
        return runtime.diary(actor).stream().filter(entry -> entry.job() == jobId).toList();
    }

    /** The state and the cause of each entry of one job, the oldest first. A long wait is "LONG_WAIT" and its cause. */
    static List<String> states(MindRuntime runtime, Actor actor, int jobId) {
        return entries(runtime, actor, jobId).stream()
            .map(entry -> (entry.longWait() ? "LONG_WAIT" : entry.state().name()) + (entry.cause() == Cause.NONE ? "" : " " + entry.cause()))
            .toList();
    }

    /**
     * D31: the runtime writes an entry at each change of the state of a job, and at each change of the cause of a wait.
     * A repeated record of the same state and cause writes no entry. The robot first waits for materials, then waits because it is full.
     * The entries of a job stay after a new job replaced it on the robot. Only the owner reads them.
     * A save and a load give the same diary. A record of an older version loads with an empty diary.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_diary_of_a_job(GameTestHelper helper) {
        Actor player = player("a_diary");
        Rig rig = new Rig(helper, "a_diary", 0, 1000, player);
        Job[] first = new Job[1];
        long[] started = new long[1];
        rig.begin()
            .thenExecute(() -> {
                started[0] = rig.level.getGameTime();
                first[0] = rig.startJob(24, 0);
            })
            .thenWaitUntil(() -> rig.state(State.WAITING))
            // At least one more attempt of the robot. Each attempt records the same state and cause again.
            .thenIdle(140)
            .thenExecute(() -> {
                check(rig.job().ops >= 2, "expected two attempts or more: " + rig.describe());
                List<String> states = states(rig.runtime, player, first[0].id);
                check(states.equals(List.of("RUNNING", "WAITING SOURCE_AT_RESERVE")), states.toString());
                Container inventory = rig.robot().turtle.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 24));
            })
            .thenWaitUntil(() -> check(rig.job().cause == Cause.ROBOT_FULL, rig.describe()))
            .thenExecute(() -> rig.robot().turtle.getInventory().setItem(0, ItemStack.EMPTY))
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                long now = rig.level.getGameTime();
                List<String> states = states(rig.runtime, player, first[0].id);
                check(states.equals(List.of("RUNNING", "WAITING SOURCE_AT_RESERVE", "WAITING ROBOT_FULL", "RUNNING", "DONE")), states.toString());
                List<Entry> entries = entries(rig.runtime, player, first[0].id);
                for (int i = 0; i < entries.size(); i++) {
                    Entry entry = entries.get(i);
                    check(entry.time() >= started[0] && entry.time() <= now && (i == 0 || entry.time() >= entries.get(i - 1).time()),
                        "the time of entry " + i + " is " + entry.time() + ": the job started at " + started[0] + ", now is " + now);
                    check(entry.owner().equals(player.id()) && entry.job() == first[0].id && entry.robot() == rig.id
                        && entry.from().equals(rig.from) && entry.to().equals(rig.to) && entry.item() == Items.COAL
                        && entry.quantity() == 24 && entry.reserve() == 0, entry.toString());
                }
                // The quantities at each moment: nothing yet, nothing yet, nothing yet, 24 on board, 24 delivered.
                List<String> quantities = entries.stream().map(entry -> entry.delivered() + "/" + entry.carried()).toList();
                check(quantities.equals(List.of("0/0", "0/0", "0/0", "0/24", "24/0")), "delivered/on board: " + quantities);
                check(entries.get(2).reason().equals("the robot inventory is full"), entries.get(2).toString());

                // A new job replaces the first one on the robot. The entries of the first job stay.
                Job second = rig.startJob(8, 0);
                check(rig.job() == second && states(rig.runtime, player, first[0].id).equals(states), states(rig.runtime, player, first[0].id).toString());
                check(states(rig.runtime, player, second.id).equals(List.of("RUNNING")), states(rig.runtime, player, second.id).toString());
                // Only the owner and an operator read the diary (D37).
                check(rig.runtime.diary(player("a_diary_other")).stream().noneMatch(entry -> entry.owner().equals(player.id())),
                    "a different player reads the diary of the owner");
                check(entries(rig.runtime, ADMIN, first[0].id).equals(entries), "the console reads a different diary");

                CompoundTag saved = rig.runtime.save(new CompoundTag());
                check(MindRuntime.load(saved).diary(ADMIN).equals(rig.runtime.diary(ADMIN)), "the loaded diary is different");
                saved.remove("diary");
                check(MindRuntime.load(saved).diary(ADMIN).isEmpty(), "a record of an older version loads with entries");
            })
            .thenSucceed();
    }

    /**
     * D31: the limit is 200 entries for each owner. The runtime removes the oldest entries of that owner, and records that it removed them.
     * The entries of a different owner stay, also when they are older. The test makes the entries with pauses and resumes of the owner.
     * An operator starts the first job in its own name: thus the operator is a different owner.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_diary_limit(GameTestHelper helper) {
        Actor player = player("a_limit");
        Actor other = player("a_limit_operator");
        Actor operator = new Actor(other.id(), true);
        Rig rig = new Rig(helper, "a_limit", 0, 1000, player);
        rig.begin()
            .thenExecute(() -> {
                long now = rig.level.getGameTime();
                Job first = rig.runtime.start(operator, rig.order(MindRuntime.TRANSPORT, 8, 0));
                rig.runtime.control(operator, rig.id, first.id, "cancel");
                Job job = rig.startJob(8, 0);
                for (int i = 0; i < 110; i++) {
                    rig.runtime.control(player, rig.id, job.id, "pause");
                    rig.runtime.control(player, rig.id, job.id, "resume");
                }
                // The start and 220 changes: 221 entries. The oldest 21 go.
                List<Entry> kept = rig.runtime.diary(player);
                check(kept.size() == 200, "the owner has " + kept.size() + " entries");
                check(kept.get(0).state() == State.PAUSED && kept.get(199).state() == State.RUNNING, kept.get(0) + " ... " + kept.get(199));
                check(rig.runtime.forgotten(player).equals(new MindRuntime.Forgotten(21, now)), String.valueOf(rig.runtime.forgotten(player)));
                check(states(rig.runtime, other, first.id).equals(List.of("RUNNING", "CANCELLED")), states(rig.runtime, other, first.id).toString());
                check(rig.runtime.forgotten(other).entries() == 0, String.valueOf(rig.runtime.forgotten(other)));

                MindRuntime copy = MindRuntime.load(rig.runtime.save(new CompoundTag()));
                check(copy.diary(player).equals(kept) && copy.forgotten(player).equals(rig.runtime.forgotten(player)),
                    "the loaded diary is different: " + copy.forgotten(player));
                rig.runtime.control(player, rig.id, job.id, "cancel");
            })
            .thenSucceed();
    }

    /** A notice that the exit got: the owner and the text. */
    record Notice(UUID owner, String text) {
    }

    /** The notices since the first test that listens. Tests that run at the same time add to the same list: each test reads its own owner. */
    static final List<Notice> NOTICES = new CopyOnWriteArrayList<>();
    private static boolean listening;

    /**
     * Replaces the exit of the notices with one that records each notice, then sends it as before.
     * The exit is a value of the whole server. This one changes nothing for the tests that run at the same time.
     */
    static synchronized void listen() {
        if (listening) return;
        listening = true;
        Notices.Exit game = Notices.exit;
        Notices.exit = (server, owner, text) -> {
            NOTICES.add(new Notice(owner, text));
            game.send(server, owner, text);
        };
    }

    /** The texts of the notices to this owner, the oldest first. */
    static List<String> notices(Actor owner) {
        return NOTICES.stream().filter(notice -> notice.owner().equals(owner.id())).map(Notice::text).toList();
    }

    /** The notices about one job, to any owner. */
    static List<Notice> noticesAbout(int jobId, int robotId) {
        return NOTICES.stream().filter(notice -> notice.text().startsWith("Job " + jobId + " on robot #" + robotId + " ")).toList();
    }

    /** How a notice names a job of a rig. */
    static String subject(Rig rig, Job job) {
        return "Job " + job.id + " on robot #" + rig.id + " (minecraft:coal from '" + rig.from + "' to '" + rig.to + "')";
    }

    /**
     * D30: a change to BLOCKED, RECOVERY or DONE gives one notice to the owner. The sentence comes from a fixed template.
     * It names the job, the robot and the cause in words. A resume by an operator, and a pause, a resume and a cancellation
     * by the owner give no notice. No notice goes to the operator or to a different player.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_notices_for_blocked_recovery_and_done(GameTestHelper helper) {
        listen();
        Actor player = player("a_notice");
        Actor other = player("a_notice_operator");
        Actor operator = new Actor(other.id(), true);
        Rig rig = new Rig(helper, "a_notice", 64, 0, player);
        Job[] jobs = new Job[2];
        List<String> expected = new ArrayList<>();
        rig.begin()
            .thenExecute(() -> jobs[0] = rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            // The robot stays blocked. The notice does not repeat.
            .thenIdle(60)
            .thenExecute(() -> {
                expected.add(subject(rig, jobs[0]) + " is blocked: the robot has no fuel.");
                check(notices(player).equals(expected), notices(player).toString());
                rig.robot().turtle.setFuelLevel(1000);
                rig.runtime.control(operator, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.carried(24))
            .thenExecute(() -> rig.robot().turtle.getInventory().clearContent())
            .thenWaitUntil(() -> rig.state(State.RECOVERY))
            .thenExecute(() -> {
                expected.add(subject(rig, jobs[0]) + " needs a check: I cannot say how it went. Recorded reason: the robot holds 0, the record says 24.");
                check(notices(player).equals(expected), notices(player).toString());
                // The owner accepts the observed load. Then a pause and a resume.
                rig.runtime.control(player, rig.id, "resume");
                rig.runtime.control(player, rig.id, "pause");
                rig.runtime.control(player, rig.id, "resume");
                check(notices(player).equals(expected), notices(player).toString());
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 24 && rig.coal(rig.source) == 16, rig.describe());
                expected.add(subject(rig, jobs[0]) + " is done: 24 delivered.");
                // A new job, and its cancellation by the owner.
                jobs[1] = rig.startJob(8, 0);
                rig.runtime.control(player, rig.id, "cancel");
                check(notices(player).equals(expected), notices(player).toString());
                check(notices(other).isEmpty() && notices(operator).isEmpty(), "the operator got a notice: " + notices(other));
                List<Notice> about = new ArrayList<>(noticesAbout(jobs[0].id, rig.id));
                about.addAll(noticesAbout(jobs[1].id, rig.id));
                check(about.size() == 3 && about.stream().allMatch(notice -> notice.owner().equals(player.id())), about.toString());
            })
            .thenSucceed();
    }

    /**
     * D30: a resume by the owner gives no notice, also when the resume completes the job.
     * The destination accepts 4. The robot loses the rest of its load. The owner lowers the quantity to 4 and resumes:
     * the owner accepts the observed load, nothing is on board, and the job is DONE.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_no_notice_for_a_resume_that_completes_the_job(GameTestHelper helper) {
        listen();
        Actor player = player("a_notice_resume");
        Rig rig = new Rig(helper, "a_notice_resume", 64, 1000, player);
        almostFull(rig);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 4, rig.describe()))
            .thenExecute(() -> rig.robot().turtle.getInventory().clearContent())
            .thenWaitUntil(() -> rig.state(State.RECOVERY))
            .thenExecute(() -> {
                List<String> before = notices(player);
                check(before.equals(List.of(subject(rig, rig.job()) + " needs a check: I cannot say how it went. "
                    + "Recorded reason: the robot holds 0, the record says 20.")), before.toString());
                rig.runtime.update(player, rig.id, rig.job().id, rig.job().revision, 4, 0);
                rig.runtime.control(player, rig.id, "resume");
                rig.state(State.DONE);
                check(notices(player).equals(before), notices(player).toString());
                List<String> states = states(rig.runtime, player, rig.job().id);
                check(states.equals(List.of("RUNNING", "WAITING DESTINATION_FULL", "RECOVERY", "DONE")), states.toString());
            })
            .thenSucceed();
    }

    /**
     * D30: the ordinary cycle of a stock job is silent. RUNNING, WAITING and SATISFIED give no notice.
     * A pause, a resume and a cancellation by the owner give no notice.
     * The destination has 10 and needs 24. The source has 40 and a reserve of 30: the robot brings 10, then waits for materials.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_no_notice_for_the_ordinary_cycle(GameTestHelper helper) {
        listen();
        Actor player = player("a_notice_cycle");
        Rig rig = new Rig(helper, "a_notice_cycle", 40, 1000, player);
        rig.chest(rig.dest).setItem(0, new ItemStack(Items.COAL, 10));
        rig.begin()
            .thenExecute(() -> rig.runtime.start(player, rig.order(MindRuntime.KEEP_STOCK, 24, 30)))
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.coal(rig.dest) == 20, rig.describe()))
            .thenExecute(() -> rig.chest(rig.source).setItem(1, new ItemStack(Items.COAL, 8)))
            .thenWaitUntil(() -> rig.state(State.SATISFIED))
            .thenExecute(() -> {
                check(rig.coal(rig.dest) == 24, rig.describe());
                rig.runtime.control(player, rig.id, "pause");
                rig.runtime.control(player, rig.id, "resume");
                rig.runtime.control(player, rig.id, "cancel");
                List<String> states = states(rig.runtime, player, rig.job().id);
                check(states.equals(List.of("RUNNING", "WAITING SOURCE_AT_RESERVE", "RUNNING", "SATISFIED", "PAUSED", "RUNNING", "CANCELLED")),
                    states.toString());
                check(notices(player).isEmpty(), notices(player).toString());
            })
            .thenSucceed();
    }

    /** D30: a job of the console has no player to warn. Its diary has the events, but no notice goes to the exit. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_no_notice_for_a_console_job(GameTestHelper helper) {
        listen();
        Rig rig = new Rig(helper, "a_notice_console", 64, 0);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            .thenExecute(() -> {
                rig.robot().turtle.setFuelLevel(1000);
                rig.runtime.control(rig.actor, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                List<String> states = states(rig.runtime, ADMIN, rig.job().id);
                check(states.equals(List.of("RUNNING", "BLOCKED OUT_OF_FUEL", "RUNNING", "DONE")), states.toString());
                check(noticesAbout(rig.job().id, rig.id).isEmpty(), noticesAbout(rig.job().id, rig.id).toString());
                check(notices(ADMIN).isEmpty(), notices(ADMIN).toString());
            })
            .thenSucceed();
    }

    /**
     * D30, D33: a notice calls no provider. The provider of the server counts its calls while a job goes to BLOCKED and to DONE,
     * and while a second job waits for 5 minutes.
     * The test moves the start of that wait back by 6000 ticks: thus the check of the server tick finds a long wait without a wait of 5 minutes.
     * It is a provider with a model. It gives the answers of the provider of before, thus a failure of this test changes no answer.
     * It has its own batch: the provider of the server is a value of the whole server.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT, batch = "notice_provider")
    public static void a_notices_call_no_provider(GameTestHelper helper) {
        listen();
        Actor player = player("a_notice_provider");
        Rig rig = new Rig(helper, "a_notice_provider", 64, 0, player);
        Uno.Provider before = Uno.provider;
        CountingModel model = new CountingModel(before);
        rig.begin()
            .thenExecute(() -> {
                Uno.provider = model;
                rig.startJob(24, 0);
            })
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            .thenExecute(() -> {
                rig.robot().turtle.setFuelLevel(1000);
                rig.runtime.control(player, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                // A second job: the source is empty, thus the job waits.
                rig.chest(rig.source).clearContent();
                rig.startJob(8, 0);
            })
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> rig.job().waitingSince -= MindRuntime.LONG_WAIT)
            .thenWaitUntil(() -> check(notices(player).size() == 3, notices(player).toString()))
            .thenExecute(() -> {
                Uno.provider = before;
                rig.runtime.control(player, rig.id, "cancel");
                check(notices(player).get(2).equals(longWait(rig, rig.job()) + "nothing remains in '" + rig.from + "'."), notices(player).toString());
                check(model.calls.get() == 0, "the provider got " + model.calls.get() + " calls");
            })
            .thenSucceed();
    }

    // --- Increment A: a notice for a long wait ------------------------------------------------------------------------

    /** How the notice of a long wait of a job of a rig starts. */
    private static String longWait(Rig rig, Job job) {
        return subject(rig, job) + " has waited for 5 minutes: ";
    }

    /**
     * D30: a job that stays WAITING for 6000 ticks of game time without an interruption gets one "long wait" entry and one notice.
     * A change of the cause does not start the time again. The entry and the notice name the cause at that moment.
     * A later check gives no second notice, also after a save and a load. A new wait can give a new notice, after 5 new minutes.
     * The test gives the moment of the check as a parameter. It does not wait 5 minutes.
     * The source is empty: the job waits for materials. Then the robot is full: the cause changes.
     * Then the robot takes 12 of 24, delivers them, and waits again for materials.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_long_wait(GameTestHelper helper) {
        listen();
        Actor player = player("a_long_wait");
        Rig rig = new Rig(helper, "a_long_wait", 0, 1000, player);
        long[] since = new long[2];
        List<String> expected = new ArrayList<>();
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> {
                since[0] = entries(rig.runtime, player, rig.job().id).get(1).time();
                Container inventory = rig.robot().turtle.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 12));
            })
            .thenWaitUntil(() -> check(rig.job().cause == Cause.ROBOT_FULL, rig.describe()))
            .thenExecute(() -> {
                Job job = rig.job();
                List<String> waits = List.of("RUNNING", "WAITING SOURCE_AT_RESERVE", "WAITING ROBOT_FULL");
                check(states(rig.runtime, player, job.id).equals(waits), states(rig.runtime, player, job.id).toString());
                CompoundTag beforeNotice = rig.runtime.save(new CompoundTag());
                rig.runtime.checkWait(job, since[0] + 5999);
                check(notices(player).isEmpty(), notices(player).toString());
                // The cause changed after the start of the wait. The time of the wait did not start again.
                rig.runtime.checkWait(job, since[0] + 6000);
                expected.add(longWait(rig, job) + "the robot is full.");
                check(notices(player).equals(expected), notices(player).toString());
                List<String> withLongWait = new ArrayList<>(waits);
                withLongWait.add("LONG_WAIT ROBOT_FULL");
                check(states(rig.runtime, player, job.id).equals(withLongWait), states(rig.runtime, player, job.id).toString());
                Entry entry = entries(rig.runtime, player, job.id).get(3);
                check(entry.time() == since[0] + 6000 && entry.state() == State.WAITING && !entry.requested() && entry.owner().equals(player.id())
                    && entry.delivered() == 0 && entry.carried() == 0, entry.toString());
                // A later check gives no second entry and no second notice.
                rig.runtime.checkWait(job, since[0] + 7200);
                check(notices(player).equals(expected), notices(player).toString());
                check(states(rig.runtime, player, job.id).equals(withLongWait), states(rig.runtime, player, job.id).toString());

                // After a save and a load the long wait stays written: a later check writes no second entry.
                // A copy of the record sends no notice. Its diary shows whether it would send one: each notice comes from an entry.
                MindRuntime copy = MindRuntime.load(rig.runtime.save(new CompoundTag()));
                copy.checkWait(copy.jobs.get(rig.id), since[0] + 12000);
                check(states(copy, player, job.id).equals(withLongWait), "after a load: " + states(copy, player, job.id));
                // The record before the check: after a load the wait keeps its start, and the check writes the long wait.
                MindRuntime earlier = MindRuntime.load(beforeNotice);
                earlier.checkWait(earlier.jobs.get(rig.id), since[0] + 5999);
                check(states(earlier, player, job.id).equals(waits), "before 6000 ticks: " + states(earlier, player, job.id));
                earlier.checkWait(earlier.jobs.get(rig.id), since[0] + 6000);
                check(states(earlier, player, job.id).equals(withLongWait), "after 6000 ticks: " + states(earlier, player, job.id));
                // A record of an older version has no start of the wait. The time starts at the first check after the load.
                beforeNotice.getCompound("jobs").getCompound(String.valueOf(rig.id)).remove("waitingSince");
                MindRuntime older = MindRuntime.load(beforeNotice);
                older.checkWait(older.jobs.get(rig.id), since[0] + 6000);
                older.checkWait(older.jobs.get(rig.id), since[0] + 11999);
                check(states(older, player, job.id).equals(waits), "an older record: " + states(older, player, job.id));
                older.checkWait(older.jobs.get(rig.id), since[0] + 12000);
                check(states(older, player, job.id).equals(withLongWait), "an older record: " + states(older, player, job.id));

                rig.robot().turtle.getInventory().setItem(0, ItemStack.EMPTY);
            })
            // The robot takes 12, delivers them, and waits again: a new wait.
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 12, rig.describe()))
            .thenExecute(() -> {
                Job job = rig.job();
                List<String> states = states(rig.runtime, player, job.id);
                check(states.equals(List.of("RUNNING", "WAITING SOURCE_AT_RESERVE", "WAITING ROBOT_FULL", "LONG_WAIT ROBOT_FULL",
                    "RUNNING", "WAITING SOURCE_AT_RESERVE")), states.toString());
                since[1] = entries(rig.runtime, player, job.id).get(5).time();
                rig.runtime.checkWait(job, since[1] + 5999);
                check(notices(player).equals(expected), notices(player).toString());
                rig.runtime.checkWait(job, since[1] + 6000);
                expected.add(longWait(rig, job) + "nothing remains in '" + rig.from + "'.");
                check(notices(player).equals(expected), notices(player).toString());
                rig.runtime.control(player, rig.id, "cancel");
                check(notices(player).equals(expected), notices(player).toString());
                List<Notice> about = noticesAbout(job.id, rig.id);
                check(about.size() == 2 && about.stream().allMatch(notice -> notice.owner().equals(player.id())), about.toString());
            })
            .thenSucceed();
    }

    /**
     * D30: the ordinary cycle of a stock job is silent, also when its waits together last more than 5 minutes.
     * The time of a wait starts again at each new wait. The destination has 10 and needs 24. The source has 40 and a reserve of 30:
     * the robot brings 10, then waits. The test adds 8: the job is SATISFIED. Then the test empties the destination:
     * the robot brings 4 and waits again.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_no_long_wait_for_the_stock_cycle(GameTestHelper helper) {
        listen();
        Actor player = player("a_long_wait_stock");
        Rig rig = new Rig(helper, "a_long_wait_stock", 40, 1000, player);
        rig.chest(rig.dest).setItem(0, new ItemStack(Items.COAL, 10));
        long[] since = new long[1];
        rig.begin()
            .thenExecute(() -> rig.runtime.start(player, rig.order(MindRuntime.KEEP_STOCK, 24, 30)))
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.coal(rig.dest) == 20, rig.describe()))
            .thenExecute(() -> {
                since[0] = entries(rig.runtime, player, rig.job().id).get(1).time();
                rig.chest(rig.source).setItem(1, new ItemStack(Items.COAL, 8));
            })
            .thenWaitUntil(() -> rig.state(State.SATISFIED))
            .thenExecute(() -> {
                // The job does not wait now.
                rig.runtime.checkWait(rig.job(), since[0] + 6000);
                rig.chest(rig.dest).clearContent();
            })
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.coal(rig.dest) == 4, rig.describe()))
            .thenExecute(() -> {
                Job job = rig.job();
                List<String> cycle = List.of("RUNNING", "WAITING SOURCE_AT_RESERVE", "RUNNING", "SATISFIED", "RUNNING", "WAITING SOURCE_AT_RESERVE");
                check(states(rig.runtime, player, job.id).equals(cycle), states(rig.runtime, player, job.id).toString());
                long second = entries(rig.runtime, player, job.id).get(5).time();
                // 6000 ticks after the start of the first wait, and 5999 after the start of this one.
                rig.runtime.checkWait(job, since[0] + 6000);
                rig.runtime.checkWait(job, second + 5999);
                rig.runtime.control(player, rig.id, "cancel");
                List<String> states = states(rig.runtime, player, job.id);
                check(states.equals(List.of("RUNNING", "WAITING SOURCE_AT_RESERVE", "RUNNING", "SATISFIED", "RUNNING", "WAITING SOURCE_AT_RESERVE",
                    "CANCELLED")), states.toString());
                check(notices(player).isEmpty(), notices(player).toString());
            })
            .thenSucceed();
    }

    // --- Increment A: the summary at an opening ----------------------------------------------------------------------

    /** The first line of a summary at the first opening of a player in this world. */
    static final String FIRST = "From the start of my memory:";
    /** The first line of a summary at each later opening. */
    static final String SINCE = "Since you last opened this screen:";
    /** The age at the start of a line of a summary. */
    private static final Pattern AGE = Pattern.compile("^(\\d+ s ago|\\d+ min ago|\\d+ h ago|1 day ago|\\d+ days ago|At an unknown time): ", Pattern.MULTILINE);

    /** A sentence of Uno with each age of a summary replaced by "<age>". The age of a real event depends on the speed of the robot. */
    static String ageless(String said) {
        return AGE.matcher(said).replaceAll("<age>: ");
    }

    /** The text of the sentence of Uno in a view. The view is JSON: a text with a line break or a quote is escaped in it. */
    private static String voice(String view) {
        for (JsonElement widget : JsonParser.parseString(view).getAsJsonObject().getAsJsonArray("widgets")) {
            JsonObject text = widget.getAsJsonObject();
            if (text.has("style") && text.get("style").getAsString().equals("uno")) return text.get("text").getAsString();
        }
        throw new GameTestAssertException("the view has no sentence of Uno: " + view);
    }

    /** A module of the robot reports an impediment, through the function that the Lua modules call. */
    private static void impediment(Rig rig, String kind, String detail) {
        try {
            rig.robot().impediment(kind, detail);
        } catch (dan200.computercraft.api.lua.LuaException e) {
            throw new GameTestAssertException("the impediment failed: " + e.getMessage());
        }
    }

    /**
     * D30, spec #1 "The summary": the use of the tablet and the click on a projector are an opening.
     * At an opening Uno says the sentences of the selected entries since the last opening of that player, the oldest first, each with its age.
     * The first opening in a world covers the whole diary of the player. The next answer of Uno replaces the summary.
     * A second opening without a new event gives no summary: Uno keeps its sentence. A new view after an action of the screen is not an opening.
     * The test calls the methods of the tablet and of the projector with a fake player of Forge: a server player without a client.
     * The provider of the server counts its calls: the summary calls none (D33).
     * It has its own batch: the provider of the server is a value of the whole server.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT, batch = "summary_provider")
    public static void a_summary_at_an_opening(GameTestHelper helper) {
        UUID id = UUID.nameUUIDFromBytes("a_summary".getBytes(StandardCharsets.UTF_8));
        FakePlayer fake = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, "a_summary"));
        Actor player = MindCraft.actor(fake);
        Rig rig = new Rig(helper, "a_summary", 64, 0, player);
        MinecraftServer server = rig.level.getServer();
        BlockPos relative = new BlockPos(3, 1, 0), projector = helper.absolutePos(relative);
        helper.setBlock(relative, MindCraft.PROJECTOR.get());
        Runnable tablet = () -> MindCraft.TABLET.get().use(rig.level, fake, InteractionHand.MAIN_HAND);
        Runnable click = () -> rig.level.getBlockState(projector).use(rig.level, fake, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(projector), Direction.UP, projector, false));
        Uno.Provider before = Uno.provider;
        CountingModel model = new CountingModel(before);
        Job[] jobs = new Job[2];
        String[] report = new String[1];
        rig.begin()
            .thenExecute(() -> {
                check(!player.admin(), "the fake player is an operator");
                Uno.provider = model;
                jobs[0] = rig.startJob(24, 0);
            })
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            .thenExecute(() -> {
                rig.robot().turtle.setFuelLevel(1000);
                rig.runtime.control(player, rig.id, "resume");
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                check(Uno.said(player).equals("Tell me what you need."), Uno.said(player));
                tablet.run();
                // The start and the resume are requests of the owner: the attention rule does not select them.
                String first = FIRST + "\n<age>: " + subject(rig, jobs[0]) + " is blocked: the robot has no fuel."
                    + "\n<age>: " + subject(rig, jobs[0]) + " is done: 24 delivered.";
                check(ageless(Uno.said(player)).equals(first), "the first opening: " + Uno.said(player));
                MindCraft.LOGGER.info("a_summary_at_an_opening, the first opening: {}", Uno.said(player));
                String voice = voice(UnoView.build(rig.runtime, player, rig.level.getGameTime()));
                check(voice.equals("Uno › " + Uno.said(player)), "the view does not show the summary: " + voice);
                UnoView.act(server, player, "report", "{}");
                report[0] = Uno.said(player);
                check(report[0].startsWith("Job " + jobs[0].id + ", robot #" + rig.id + ":"), "the report did not replace the summary: " + report[0]);
                click.run();
                check(Uno.said(player).equals(report[0]), "the second opening changed the sentence of Uno: " + Uno.said(player));
                rig.robot().turtle.setFuelLevel(0);
                jobs[1] = rig.startJob(8, 0);
            })
            .thenWaitUntil(() -> rig.state(State.BLOCKED))
            .thenExecute(() -> {
                // The screen asks for a new view each second. That view is not an opening.
                UnoView.act(server, player, "refresh", "{}");
                UnoView.build(rig.runtime, player, rig.level.getGameTime());
                check(Uno.said(player).equals(report[0]), "a new view after an action gave a summary: " + Uno.said(player));
                click.run();
                String since = SINCE + "\n<age>: " + subject(rig, jobs[1]) + " is blocked: the robot has no fuel.";
                check(ageless(Uno.said(player)).equals(since), "the opening with the projector: " + Uno.said(player));
                Uno.provider = before;
                check(model.calls.get() == 0, "the provider got " + model.calls.get() + " calls");
                rig.runtime.control(player, rig.id, "cancel");
            })
            .thenSucceed();
    }

    /**
     * Spec #1: the summary shows the 10 newest sentences at most, the oldest first, and says how many older ones it does not show.
     * The age is the game time between the event and the opening. The moment of the opening is a parameter: 12 minutes later.
     * Built artificially: in one tick the test reports 12 impediments of unknown kinds through the function that the Lua modules call.
     * Each one is a block without a cause, with a reason of its own. The owner resumes after each one.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_summary_shows_the_10_newest(GameTestHelper helper) {
        Actor player = player("a_summary_ten");
        Rig rig = new Rig(helper, "a_summary_ten", 64, 1000, player);
        rig.begin()
            .thenExecute(() -> {
                long now = rig.level.getGameTime();
                Job job = rig.startJob(24, 0);
                for (int i = 1; i <= 12; i++) {
                    impediment(rig, "kind" + i, "event " + i);
                    rig.runtime.control(player, rig.id, "resume");
                }
                UnoView.open(rig.runtime, player, now + 12 * 60 * 20);
                List<String> expected = new ArrayList<>(List.of(FIRST, "I do not show 2 older events."));
                for (int i = 3; i <= 12; i++) expected.add("12 min ago: " + subject(rig, job) + " is blocked. Recorded reason: kind" + i + ": event " + i + ".");
                check(Uno.said(player).equals(String.join("\n", expected)), Uno.said(player));
                rig.runtime.control(player, rig.id, "cancel");
            })
            .thenSucceed();
    }

    /**
     * Spec #1: if the runtime removed entries of this owner that are newer than the last opening, the summary says where the memory of Uno starts.
     * The first opening is before the events. Then, in one later tick, the owner starts a job and gives 110 pauses and 110 resumes:
     * the runtime removes the oldest 21 entries. A copy of the record at that moment has no selected entry. Its summary has the line about the memory.
     * Then an impediment gives a selected entry. The moment of the opening is a parameter: 3 hours later.
     * A later opening, after the answer to a report, has no new event, and the removed entries are older than the last opening: Uno keeps its sentence.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_summary_says_where_the_memory_starts(GameTestHelper helper) {
        Actor player = player("a_summary_memory");
        Rig rig = new Rig(helper, "a_summary_memory", 64, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.begin()
            .thenExecute(() -> {
                UnoView.open(rig.runtime, player, rig.level.getGameTime());
                check(Uno.said(player).equals("Tell me what you need."), "a first opening without events: " + Uno.said(player));
            })
            .thenIdle(2)
            .thenExecute(() -> {
                long later = rig.level.getGameTime() + 3 * 60 * 60 * 20;
                Job job = rig.startJob(24, 0);
                for (int i = 0; i < 110; i++) {
                    rig.runtime.control(player, rig.id, job.id, "pause");
                    rig.runtime.control(player, rig.id, job.id, "resume");
                }
                String memory = "My memory starts 3 h ago. I do not remember older events.";
                UnoView.open(MindRuntime.load(rig.runtime.save(new CompoundTag())), player, later);
                check(Uno.said(player).equals(SINCE + "\n" + memory), "only removed entries: " + Uno.said(player));
                impediment(rig, "kind", "event");
                UnoView.open(rig.runtime, player, later);
                String summary = SINCE + "\n" + memory + "\n3 h ago: " + subject(rig, job) + " is blocked. Recorded reason: kind: event.";
                check(Uno.said(player).equals(summary), Uno.said(player));
                UnoView.act(server, player, "report", "{}");
                String report = Uno.said(player);
                UnoView.open(rig.runtime, player, later + 1200);
                check(Uno.said(player).equals(report), "an opening without new events changed the sentence: " + Uno.said(player));
                rig.runtime.control(player, rig.id, "cancel");
            })
            .thenSucceed();
    }

    /** T19 for the summary (D37): the summary of a player has no event of a different player. That opening changes nothing for the owner. */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_t19_summary_of_another_player(GameTestHelper helper) {
        Actor owner = player("a_summary_owner"), other = player("a_summary_other");
        Rig rig = new Rig(helper, "a_summary_owner", 64, 1000, owner);
        rig.begin()
            .thenExecute(() -> {
                long now = rig.level.getGameTime();
                Job job = rig.startJob(24, 0);
                impediment(rig, "kind", "event");
                String view = UnoView.open(rig.runtime, other, now);
                check(Uno.said(other).equals("Tell me what you need.") && !view.contains(rig.from) && !view.contains("kind: event"),
                    "the opening of a different player: " + view);
                UnoView.open(rig.runtime, owner, now);
                check(Uno.said(owner).equals(FIRST + "\n0 s ago: " + subject(rig, job) + " is blocked. Recorded reason: kind: event."), Uno.said(owner));
                rig.runtime.control(owner, rig.id, "cancel");
            })
            .thenSucceed();
    }

    /**
     * Spec #1: the time of the last opening of each player is saved with the world. After a save and a load the summary is the same.
     * One event before the first opening, one event after it. The summary of the loaded copy has only the event after the opening.
     * A record of an older version has no time of an opening: its summary covers the whole diary.
     * The moment of the opening is a parameter: two days later.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_summary_after_a_save_and_a_load(GameTestHelper helper) {
        Actor player = player("a_summary_saved");
        Rig rig = new Rig(helper, "a_summary_saved", 64, 1000, player);
        Job[] job = new Job[1];
        rig.begin()
            .thenExecute(() -> {
                job[0] = rig.startJob(24, 0);
                impediment(rig, "early", "event");
                rig.runtime.control(player, rig.id, "resume");
                UnoView.open(rig.runtime, player, rig.level.getGameTime());
            })
            .thenIdle(2)
            .thenExecute(() -> {
                impediment(rig, "late", "event");
                long later = rig.level.getGameTime() + 2 * 24 * 60 * 60 * 20;
                String early = "\n2 days ago: " + subject(rig, job[0]) + " is blocked. Recorded reason: early: event.";
                String late = "\n2 days ago: " + subject(rig, job[0]) + " is blocked. Recorded reason: late: event.";
                CompoundTag saved = rig.runtime.save(new CompoundTag());
                UnoView.open(MindRuntime.load(saved), player, later);
                String loaded = Uno.said(player);
                UnoView.open(rig.runtime, player, later);
                check(Uno.said(player).equals(SINCE + late) && loaded.equals(Uno.said(player)), "running: " + Uno.said(player) + " loaded: " + loaded);
                saved.remove("openings");
                UnoView.open(MindRuntime.load(saved), player, later);
                check(Uno.said(player).equals(FIRST + early + late), "a record of an older version: " + Uno.said(player));
                rig.runtime.control(player, rig.id, "cancel");
            })
            .thenSucceed();
    }

    /**
     * Tickets #9 and #10: the attention rule selects a long wait. Thus it is in the summary, with the sentence of its notice.
     * The source is empty: the job waits. The check of the long wait and the opening are later moments: parameters.
     */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void a_summary_has_the_long_wait(GameTestHelper helper) {
        Actor player = player("a_summary_wait");
        Rig rig = new Rig(helper, "a_summary_wait", 0, 1000, player);
        rig.begin()
            .thenExecute(() -> rig.startJob(24, 0))
            .thenWaitUntil(() -> rig.state(State.WAITING))
            .thenExecute(() -> {
                long check = rig.job().waitingSince + MindRuntime.LONG_WAIT;
                rig.runtime.checkWait(rig.job(), check);
                UnoView.open(rig.runtime, player, check + 2 * 60 * 20);
                check(Uno.said(player).equals(FIRST + "\n2 min ago: " + longWait(rig, rig.job()) + "nothing remains in '" + rig.from + "'."), Uno.said(player));
                rig.runtime.control(player, rig.id, "cancel");
            })
            .thenSucceed();
    }

    // --- S0: probes of the Lua execution model -----------------------------------------------------------------------
    // Each probe writes a startup file into the folder of a test computer. This is the folder that a player can edit.

    private static Path computerFolder(GameTestHelper helper, int computerId) {
        return helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("computercraft/computer/" + computerId);
    }

    private static void placeProbe(GameTestHelper helper, int computerId, String startup) {
        try {
            Path folder = computerFolder(helper, computerId);
            Files.createDirectories(folder);
            Files.deleteIfExists(folder.resolve("probe.txt"));
            Files.writeString(folder.resolve("startup.lua"), startup);
        } catch (IOException e) {
            throw new GameTestAssertException("cannot write the probe: " + e);
        }
        placeTurtle(helper, new BlockPos(2, 1, 1), computerId, 1000, null);
    }

    private static List<String> probeResult(GameTestHelper helper, int computerId) {
        Path file = computerFolder(helper, computerId).resolve("probe.txt");
        check(Files.exists(file), "the probe has no result yet");
        try {
            List<String> lines = Files.readAllLines(file);
            check(lines.contains("end"), "the probe result is not complete yet");
            return lines;
        } catch (IOException e) {
            throw new GameTestAssertException("cannot read the probe result: " + e);
        }
    }

    private static final String PROBE_HEADER = """
        local lines = {}
        local function probe(name, env, source)
            local fn, err = load(source, "probe", "t", env)
            local ok, result
            if fn then ok, result = pcall(fn) else ok, result = false, err end
            lines[#lines + 1] = name .. "=" .. tostring(ok and result)
        end
        local function finish()
            local file = fs.open("probe.txt", "w")
            for _, line in ipairs(lines) do file.writeLine(line) end
            file.writeLine("end")
            file.close()
        end
        """;

    /** Which environments let a Lua program reach the turtle API or change shared code? */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s0_probe_environment(GameTestHelper helper) {
        placeProbe(helper, 9001, PROBE_HEADER + """
            local reach = "return turtle ~= nil or _G ~= nil or getfenv ~= nil or load ~= nil or debug ~= nil or fs ~= nil"
            probe("empty_env_reaches_globals", {}, reach)
            probe("empty_env_string_methods", {}, "return ('ab'):upper()")
            probe("load_reaches_turtle", { load = load }, "return load('return turtle ~= nil')()")
            probe("getfenv_exists", { getfenv = getfenv }, "return getfenv ~= nil")
            probe("getfenv_reaches_turtle", { getfenv = getfenv }, "return getfenv(0).turtle ~= nil")
            probe("string_table_write", { string = string }, "string.mc_probe = function() return 'changed' end return true")
            lines[#lines + 1] = "string_table_shared=" .. tostring(("x").mc_probe ~= nil and ("x"):mc_probe())
            string.mc_probe = nil
            probe("getmetatable_string_write", { getmetatable = getmetatable }, "getmetatable('').__index.mc_probe = function() return 'changed' end return true")
            lines[#lines + 1] = "getmetatable_string_shared=" .. tostring(("x").mc_probe ~= nil and ("x"):mc_probe())
            string.mc_probe = nil
            probe("debug_reaches_turtle", { debug = debug, print = print, type = type }, [[
                local i = 1
                while true do
                    local name, value = debug.getupvalue(print, i)
                    if not name then return false end
                    if type(value) == "table" and value.turtle then return true end
                    i = i + 1
                end
            ]])
            finish()
            """);
        helper.startSequence()
            .thenWaitUntil(() -> probeResult(helper, 9001))
            .thenExecute(() -> {
                List<String> lines = probeResult(helper, 9001);
                MindCraft.LOGGER.info("s0_probe_environment result: {}", lines);
                check(lines.contains("empty_env_reaches_globals=false"), "an empty environment reached the globals: " + lines);
                check(lines.contains("load_reaches_turtle=true"), "expected the raw load function to reach the turtle API: " + lines);
                check(lines.contains("string_table_shared=changed"), "expected a shared string table: " + lines);
            })
            .thenSucceed();
    }

    /** What occurs when a Lua program does not yield? */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s0_probe_no_yield(GameTestHelper helper) {
        placeProbe(helper, 9002, PROBE_HEADER + """
            local start = os.clock()
            local ok, err = pcall(function() while true do end end)
            lines[#lines + 1] = "ok=" .. tostring(ok)
            lines[#lines + 1] = "error=" .. tostring(err)
            lines[#lines + 1] = "seconds=" .. (os.clock() - start)
            finish()
            """);
        helper.startSequence()
            .thenWaitUntil(() -> probeResult(helper, 9002))
            .thenExecute(() -> {
                List<String> lines = probeResult(helper, 9002);
                MindCraft.LOGGER.info("s0_probe_no_yield result: {}", lines);
                check(lines.contains("ok=false") && lines.stream().anyMatch(l -> l.contains("Too long without yielding")), lines.toString());
            })
            .thenSucceed();
    }

    /** Can the server stop a program that uses only the standard turtle API and does not cooperate? */
    @GameTest(template = EMPTY, timeoutTicks = TIMEOUT)
    public static void s0_probe_hard_stop(GameTestHelper helper) {
        placeProbe(helper, 9003, "while true do turtle.turnLeft() end");
        RobotApi[] robot = new RobotApi[1];
        Direction[] stopped = new Direction[1];
        helper.startSequence()
            .thenWaitUntil(() -> {
                robot[0] = RobotApi.live(9003);
                check(robot[0] != null && robot[0].turtle.getDirection() != Direction.NORTH, "the turtle does not turn yet");
            })
            .thenExecute(() -> MindCraft.command(helper.getLevel().getServer(), "computercraft shutdown #9003"))
            .thenWaitUntil(() -> check(RobotApi.live(9003) == null, "the computer is not off yet"))
            .thenIdle(20)
            .thenExecute(() -> stopped[0] = robot[0].turtle.getDirection())
            .thenIdle(60)
            .thenExecute(() -> check(robot[0].turtle.getDirection() == stopped[0], "the turtle still turns after the shutdown"))
            .thenSucceed();
    }
}
