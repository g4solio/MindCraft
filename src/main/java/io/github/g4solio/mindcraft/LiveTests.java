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
 * The Claude provider with the real service of Anthropic. This check needs an API key and costs a little money: four requests.
 * <pre>
 *   gradlew runGameTestServer -Plive       with ANTHROPIC_API_KEY in the environment
 * </pre>
 * The answers of a language model can change between two runs. Thus this check is not part of the normal run.
 * Forge selects game tests by the namespace of their template. Thus this class has its own namespace and template.
 */
@GameTestHolder("mindcraft_live")
@PrefixGameTestTemplate(false)
public class LiveTests {
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
        Actor player = MindCraftTests.player("live");
        Rig rig = new Rig(helper, "live", 0, 1000, player);
        MinecraftServer server = rig.level.getServer();
        rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, 64));
        rig.chest(rig.source).setItem(1, new ItemStack(Items.COAL, 16));
        rig.chest(rig.dest).setItem(0, new ItemStack(Items.COAL, 10));
        // The names that a player would give. "miniera" is a third station that the request does not name.
        rig.runtime.addStation(player, "deposito", rig.level, helper.absolutePos(rig.source), helper.absolutePos(rig.sourceDock));
        rig.runtime.addStation(player, "fornaci", rig.level, helper.absolutePos(rig.dest), helper.absolutePos(rig.destDock));
        rig.runtime.addStation(player, "miniera", rig.level, helper.absolutePos(rig.source), helper.absolutePos(rig.sourceDock));
        Uno.Provider claude = ClaudeProvider.fromEnv();
        List<String> answers = new CopyOnWriteArrayList<>();

        GameTestSequence sequence = rig.begin()
            .thenExecute(() -> {
                boolean easy = true;
                try {
                    new Uno.RuleProvider().propose(REQUEST, Uno.context(rig.runtime, player));
                } catch (IllegalArgumentException expected) {
                    easy = false;
                }
                check(!easy, "the fixed rules read the request: it is too easy for this check");
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
            .thenWaitUntil(() -> check(rig.job().state == State.WAITING && rig.job().delivered == 48, rig.describe()))
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
                    // The sentence must come from the model. A failure of the service has a different answer. See Uno.failure.
                    check(answers.get(index).startsWith("I cannot propose a job: "), answers.get(index));
                });
        }
        sequence.thenSucceed();
    }
}
