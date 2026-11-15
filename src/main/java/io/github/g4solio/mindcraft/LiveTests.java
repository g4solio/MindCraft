package io.github.g4solio.mindcraft;

import io.github.g4solio.mindcraft.MindCraftTests.Rig;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.github.g4solio.mindcraft.MindCraftTests.check;

/**
 * The Claude provider with the real service of Anthropic. Each test needs an API key and costs money. Each run needs the consent of the
 * project owner (CLAUDE.md).
 * <pre>
 *   gradlew runGameTestServer -Plive                                t18_claude_live: four requests
 *   gradlew runGameTestServer -Pmeasure -Pmodel=claude-sonnet-5-5   b_measurement: the measurement on one model, 19 requests
 * </pre>
 * Each run needs ANTHROPIC_API_KEY in the environment.
 * The model is a parameter of the run: -Pmodel (D42, choices of the implementation). Without it, the model is the setting "model" of
 * run-gametest/config/mindcraft-common.toml, as on a server (D42, point 6). The log names it.
 * The answers of a language model can change between two runs. Thus these tests are not part of the normal run.
 * Forge selects game tests by the namespace of their template. Thus each test has a namespace and a template of its own:
 * mindcraft_live and mindcraft_measure. A run of one test does not run the other one.
 */
@GameTestHolder("mindcraft_live")
@PrefixGameTestTemplate(false)
public class LiveTests {
    /** The model of the run: -Pmodel of build.gradle, or empty for the setting. */
    private static final String MODEL = System.getProperty("mindcraft.model", "");

    /**
     * The Claude provider of a run, with the key of the environment and the model of the run. The provider reads the setting when it is
     * built. With -Pmodel the setting has the model of the run only while the provider is built: then the value of before is back.
     */
    private static Uno.Provider claude() {
        return MODEL.isBlank() ? ClaudeProvider.fromEnv() : MindCraftTests.withModel(MODEL, ClaudeProvider::fromEnv);
    }

    /**
     * The fixed rules cannot read this request: the numbers are words and the station names are not exact.
     * The requests are in Italian on purpose: a player can write in any language. Uno answers in English.
     */
    private static final String REQUEST = "Fai in modo che alla fornace non manchi mai il carbone: tienine sempre almeno sessantaquattro pezzi. "
        + "Prendilo dal Deposito, ma lì lasciane trentadue di scorta.";
    /** Requests that must not become a proposal: a value is missing, it is not an order, a station is not known. */
    private static final List<String> NO_ORDER = List.of("Porta del carbone alle fornaci", "Che tempo farà domani?",
        "Porta 10 pezzi di carbone dal deposito alla segheria");

    @GameTest(template = MindCraftTests.EMPTY, timeoutTicks = MindCraftTests.TIMEOUT)
    public static void t18_claude_live(GameTestHelper helper) {
        String key = System.getenv(ClaudeProvider.KEY_VARIABLE);
        check(key != null && !key.isBlank(), "this check needs " + ClaudeProvider.KEY_VARIABLE + " in the environment");
        // Only an operator uses Claude (D33). This test runs alone: the operator sees only the stations of this test.
        Actor player = MindCraftTests.operator("live");
        Rig rig = new Rig(helper, "live", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 64));
        rig.chest(rig.source).setItem(1, new ItemStack(Items.COAL, 16));
        rig.chest(rig.dest).setItem(0, new ItemStack(Items.COAL, 10));
        // The names that a player would give. "miniera" is a third station that the request does not name.
        rig.runtime.addStation(player, "deposito", rig.level, helper.absolutePos(rig.source), helper.absolutePos(rig.sourceDock));
        rig.runtime.addStation(player, "fornaci", rig.level, helper.absolutePos(rig.dest), helper.absolutePos(rig.destDock));
        rig.runtime.addStation(player, "miniera", rig.level, helper.absolutePos(rig.source), helper.absolutePos(rig.sourceDock));
        Uno.Provider claude = claude();
        MindCraft.LOGGER.info("t18_claude_live uses {}", claude.name());
        List<String> answers = new CopyOnWriteArrayList<>();

        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> {
                Uno.Action rules = new Uno.RuleProvider().answer(REQUEST, Uno.context(rig.runtime, player, rig.level.getGameTime())).action();
                check(rules instanceof Uno.Action.None, "the fixed rules read the request: it is too easy for this check: " + rules);
                Uno.ask(server, player, REQUEST, claude, Uno.TIMEOUT_MS, answers::add);
            })
            .thenWaitUntil(() -> check(answers.size() == 1, "Claude did not answer yet"))
            .thenExecute(() -> {
                String answer = answers.get(0);
                MindCraft.LOGGER.info("t18_claude_live proposal: {}", answer);
                check(answer.startsWith("Proposal: keep at least 64 x minecraft:coal in 'fornaci', taken from 'deposito'"), answer);
                check(answer.contains("Reserve to leave in 'deposito': 32") && answer.contains("Robot #" + rig.id + "."), answer);
                check(rig.job() == null && rig.coal(rig.source) == 80 && rig.coal(rig.dest) == 10, "the proposal changed something");
                String started = Uno.confirm(server, player);
                check(started.startsWith("Job") && rig.job() != null, started);
                Job job = rig.job();
                check(job.keepStock() && job.quantity == 64 && job.reserve == 32 && job.owner.equals(player.id()), rig.describe());
            })
            // A failed check above does not stop the sequence. Without a job, this check fails instead of a crash of the server.
            .thenWaitUntil(() -> check(rig.job() != null && rig.job().state == State.WAITING && rig.job().delivered == 48, rig.describe()))
            .thenExecute(() -> check(rig.coal(rig.dest) == 58 && rig.coal(rig.source) == 32, rig.describe()));
        for (int i = 0; i < NO_ORDER.size(); i++) {
            int index = i + 1;
            String request = NO_ORDER.get(i);
            sequence = sequence
                .thenExecute(() -> Uno.ask(server, player, request, claude, Uno.TIMEOUT_MS, answers::add))
                .thenWaitUntil(() -> check(answers.size() > index, "Claude did not answer yet"))
                .thenExecute(() -> {
                    MindCraft.LOGGER.info("t18_claude_live request '{}': {}", request, answers.get(index));
                    check(!Uno.hasProposal(player), "this became a proposal: " + answers.get(index));
                    // The sentence must come from the model, and pass the checks of Uno: the voice of "none" (D42, point 15).
                    // A failure of the service, the fixed rules and a failed check have different answers.
                    check(!answers.get(index).startsWith("The model gave no valid answer") && !answers.get(index).startsWith("I cannot propose a job: ")
                        && !answers.get(index).equals("I cannot answer that. I did nothing."), answers.get(index));
                });
        }
        sequence
            .thenExecute(() -> {
                // The counter of the session: four requests to Claude, with the tokens that the service reported.
                Uno.Usage usage = Uno.usage(player);
                MindCraft.LOGGER.info("t18_claude_live counter: {}", usage);
                check(usage.requests() == 4 && usage.inputTokens() > 0 && usage.outputTokens() > 0, "the counter: " + usage);
            })
            .thenSucceed();
    }

    /**
     * The measurement of the models (D33, and D42 points 7 and 20), and the sample of the T21 case of D32 with the real service.
     * The harness of Measurement builds the world and sends its fixed set of 19 requests, one after the other. It writes the record:
     * the file run-gametest/measurement-(model).md and the server log. Each sentence of the model is next to its facts.
     * The test does not judge the model: it fails only when the harness cannot build the world or write the record.
     * The project owner and the record decide (D42, point 20). One run is about 20 requests on one model: D42, point 8 has the budget.
     * The harness is the harness of MindCraftTests.b_measurement_with_the_stand_in, which runs in the normal run without the real service.
     */
    @GameTest(template = MindCraftTests.EMPTY, templateNamespace = "mindcraft_measure", timeoutTicks = 12_000)
    public static void b_measurement(GameTestHelper helper) {
        String key = System.getenv(ClaudeProvider.KEY_VARIABLE);
        check(key != null && !key.isBlank(), "this check needs " + ClaudeProvider.KEY_VARIABLE + " in the environment");
        Uno.Provider claude = claude();
        MindCraft.LOGGER.info("b_measurement uses {}", claude.name());
        Measurement.run(helper, world -> claude, "The measurement on " + claude.model(), "Type: a test with the real Claude service, "
            + "in the game and automatic.", "measurement-" + claude.model() + ".md", report -> {
        }).thenSucceed();
    }
}
