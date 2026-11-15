package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindCraftTests.Rig;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static io.github.g4solio.mindcraft.MindCraftTests.check;

/**
 * T11: a real server restart during a transport. The check needs two server runs on the same world.
 * <pre>
 *   gradlew runGameTestServer -PrestartPhase=stop          start a job, then stop the server normally during the transport
 *   gradlew runGameTestServer -PrestartPhase=check         start the server on the saved world, expect a normal continuation
 *
 *   gradlew runGameTestServer -PrestartPhase=crash         start a job, save, deliver, open the screen, then halt the process without a save
 *   gradlew runGameTestServer -PrestartPhase=check-crash   start the server on the saved world, expect a recovery state
 * </pre>
 * Forge selects game tests by the namespace of their template. Thus this class has its own namespace and template.
 */
@GameTestHolder("mindcraft_restart")
@PrefixGameTestTemplate(false)
public class RestartTests {
    private static final String PHASE = System.getProperty("mindcraft.restartPhase", "");
    /** An operator who opens the screen of Uno. Thus the summary has the events of the job of the console. */
    private static final Actor OPERATOR = MindCraftTests.operator("t11_operator");

    @GameTest(template = MindCraftTests.EMPTY, timeoutTicks = MindCraftTests.TIMEOUT)
    public static void t11_server_restart(GameTestHelper helper) {
        check(!PHASE.isEmpty(), "this check needs -PrestartPhase=stop, check, crash or check-crash");
        // The setup is at a fixed place, high above the ground and outside the test structure.
        // The test structure of the next run must not erase it. The chunk stays loaded without a player.
        // GameTestHelper.relativePos is not used: in Minecraft 1.20.1 it mirrors the position.
        helper.getLevel().setChunkForced(0, 0, true);
        Rig rig = new Rig(helper, "t11", MindCraftTests.ADMIN, new BlockPos(0, 100, 1).subtract(helper.absolutePos(BlockPos.ZERO)));
        if (PHASE.startsWith("check")) {
            examine(helper, rig, PHASE.equals("check-crash"));
            return;
        }

        rig.build(64, 1000);
        MindCraft.LOGGER.info("t11_server_restart setup: source {}, turtle {}, destination {}",
            helper.absolutePos(rig.source), helper.absolutePos(rig.start), helper.absolutePos(rig.dest));
        var sequence = rig.begin()
            .thenExecute(() -> {
                rig.startJob(24, 0);
                // The first opening of the operator. Nothing needed attention yet. The time of this opening goes into the saved world.
                UnoView.open(rig.runtime, OPERATOR, rig.level.getGameTime());
                check(Uno.said(OPERATOR).equals("Tell me what you need."), "the opening gave a summary: " + Uno.said(OPERATOR));
            })
            .thenWaitUntil(() -> rig.carried(24));
        if (PHASE.equals("crash")) {
            // The saved world contains the pickup. The delivery occurs after the save.
            // The process halts 15 seconds later, without a normal stop. The runtime saved its record at the delivery.
            // The wait shows if Minecraft writes the chunks of the delivery to the disk on its own.
            sequence
                .thenExecute(() -> check(rig.level.getServer().saveEverything(true, true, true), "the save failed"))
                .thenWaitUntil(() -> rig.state(State.DONE))
                .thenExecute(() -> {
                    // Finding P1 of the code review: a second opening after the save of the world. Its summary has the delivery.
                    // The write puts it on the disk, as the next transfer of any robot does. Thus the record holds an opening later than the world.
                    UnoView.open(rig.runtime, OPERATOR, rig.level.getGameTime());
                    String done = "\n<age>: " + MindCraftTests.subject(rig, rig.job()) + " is done: 24 delivered.";
                    check(MindCraftTests.ageless(Uno.said(OPERATOR)).equals(MindCraftTests.SINCE + done), "the opening after the save: " + Uno.said(OPERATOR));
                    rig.runtime.flush(rig.level.getServer());
                })
                .thenIdle(300)
                .thenExecute(() -> {
                    MindCraft.LOGGER.info("t11_server_restart status at the halt, game time {}:\n{}", rig.level.getGameTime(), rig.describe());
                    Runtime.getRuntime().halt(0);
                });
        } else {
            // A normal stop in the tick of the pickup. The server saves the world. The robot is on its way.
            sequence
                .thenExecute(() -> {
                    MindCraft.LOGGER.info("t11_server_restart status at the stop, game time {}:\n{}", rig.level.getGameTime(), rig.describe());
                    rig.level.getServer().halt(false);
                })
                .thenSucceed();
        }
    }

    /**
     * Also D31: Minecraft saves the diary with the world. After the restart the diary has the entries of the first run.
     * After the normal stop: the start of the job. After the crash: the start and the delivery that the record wrote at the halt,
     * then the recovery that the load of the record found.
     */
    private static void examine(GameTestHelper helper, Rig rig, boolean afterCrash) {
        check(rig.runtime.robots.size() == 1, "expected one enrolled robot in the saved world, found " + rig.runtime.robots.size());
        rig.id = rig.runtime.robots.keySet().iterator().next();
        MindCraft.LOGGER.info("t11_server_restart record after the restart, game time {}:\n{}", rig.level.getGameTime(), rig.describe());
        int jobId = rig.job().id;
        List<String> saved = MindCraftTests.states(rig.runtime, rig.actor, jobId);
        MindCraft.LOGGER.info("t11_server_restart diary after the restart: {}", saved);
        check(saved.equals(afterCrash ? List.of("RUNNING", "DONE", "RECOVERY") : List.of("RUNNING")), "the saved diary is " + saved);
        if (afterCrash) rig.state(State.RECOVERY);
        helper.startSequence()
            .thenWaitUntil(() -> check(rig.robot() != null && (rig.job().state == State.DONE || rig.job().state == State.RECOVERY), rig.describe()))
            .thenExecute(() -> {
                MindCraft.LOGGER.info("t11_server_restart first stable state: source {}, destination {}, robot {}\n{}",
                    rig.coal(rig.source), rig.coal(rig.dest), rig.held(), rig.describe());
                if (afterCrash) {
                    // The runtime must not report a delivery that the world does not contain. The owner decides.
                    rig.state(State.RECOVERY);
                    check(rig.job().reason.contains("older than the record"), rig.describe());
                    rig.runtime.control(rig.actor, rig.id, "resume");
                }
            })
            .thenWaitUntil(() -> rig.state(State.DONE))
            .thenExecute(() -> {
                rig.assertTransferred(40, 24);
                check(rig.job().recoveries == (afterCrash ? 1 : 0), rig.describe());
                List<String> diary = MindCraftTests.states(rig.runtime, rig.actor, jobId);
                MindCraft.LOGGER.info("t11_server_restart final diary: {}", diary);
                check(diary.equals(afterCrash ? List.of("RUNNING", "DONE", "RECOVERY", "RUNNING", "DONE") : List.of("RUNNING", "DONE")),
                    "the diary is " + diary);
                MindCraft.LOGGER.info("t11_server_restart final status:\n{}", rig.describe());

                // Also D30: the summary works after a restart. The last opening of the operator is from the first run.
                // Thus the summary has the events after it, and its first line is not the line of a first opening.
                // After the crash that opening is later than the world, and its summary had the first delivery (finding P1 of the code review).
                // The summary has the recovery that the load wrote at an earlier game time, and the delivery after it.
                UnoView.open(rig.runtime, OPERATOR, rig.level.getGameTime());
                MindCraft.LOGGER.info("t11_server_restart summary after the restart: {}", Uno.said(OPERATOR));
                String done = "\n<age>: " + MindCraftTests.subject(rig, rig.job()) + " is done: 24 delivered.";
                String recovery = "\n<age>: " + MindCraftTests.subject(rig, rig.job())
                    + " needs a check: I cannot say how it went. Recorded reason: the world is older than the record of this job.";
                String summary = MindCraftTests.ageless(Uno.said(OPERATOR));
                check(summary.equals(MindCraftTests.SINCE + (afterCrash ? recovery + done : done)), "the summary after the restart: " + Uno.said(OPERATOR));
            })
            .thenSucceed();
    }
}
