package io.github.g4solio.mindcraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.g4solio.mindcraft.MindCraftTests.Rig;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.Cause;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.Order;
import io.github.g4solio.mindcraft.MindRuntime.Refusal;
import io.github.g4solio.mindcraft.MindRuntime.State;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.g4solio.mindcraft.MindCraftTests.check;

/**
 * The harness of the measurement of the models (D33, and D42 points 7 and 20). One fixed set of requests, each with its expected action.
 * The harness builds the world that the set needs, with real turtles and chests. It sends each request through Uno, one after the other.
 * For each request it keeps the context that the provider got, the record of the request and the answer of Uno.
 * Then it writes one Markdown section for TEST_RESULTS.md: to a file of the folder of the game test server, and to the server log.
 * <p>
 * Two tests run it. {@code LiveTests.b_measurement} gives it the Claude provider with the real service: a paid run.
 * {@code MindCraftTests.b_measurement_with_the_stand_in} gives it the Claude provider against a local stand-in of the service.
 */
final class Measurement {
    /**
     * The player of the measurement. Not an operator: an operator owns each station, robot and job of the server (D33.1).
     * Thus the context of each request holds only the world of the measurement, also in the normal run, where other tests run.
     * A provider with a model serves only an operator (D33). Thus the provider of each request says that it calls no model, as in
     * t18_claude_provider, and the harness keeps the record of each request itself. See Measured.
     */
    static final Actor PLAYER = MindCraftTests.player("measurement");

    private Measurement() {
    }

    /** A robot of the world and its two stations, in one row of the test area. The rig gives the positions, the enrollment and the job. */
    record Post(Rig rig, String from, String to) {
        int robot() {
            return rig.id;
        }

        Job job() {
            return rig.job();
        }
    }

    /**
     * The world of the set: seven robots, each with a job in the state that its requests need, and a third station for a new destination.
     * The stations have the names that a player gives. Each row is 3 blocks from the next one: two chests are never side by side.
     * <ul>
     * <li>quarry to kiln: a transport of 16, DONE. Its robot is the only free robot.</li>
     * <li>depot to furnace: keep 64 in the furnace, with a reserve of 32. The depot has 32: WAITING at the reserve.</li>
     * <li>mine to smithy: a transport of 24, with a robot without fuel: BLOCKED, out of fuel.</li>
     * <li>warehouse to kitchen: a transport of 24. The harness empties the robot after its pickup: RECOVERY, the load differs.</li>
     * <li>cellar to bakery: a transport of 32 from an empty cellar: WAITING. Then the computer of the robot shuts down: not running.</li>
     * <li>storage to workshop: a transport of 64. The storage has 24, and the workshop has no chest any more: BLOCKED with 24 on board.
     * This is the adversarial case of D32.</li>
     * <li>harbor to tower: a transport of 40. The harbor has 16: the robot delivers them, then waits, and the owner pauses the job.
     * Then the harness moves the last observation of the tower back by more than JobAccount.OLD_TICKS: an old observation.</li>
     * <li>smelter: a chest and its dock. No job uses it.</li>
     * </ul>
     * A robot without an active job still calls the server every 10 seconds (the agent of mindcraft.lua): no robot is silent.
     */
    static final class World {
        final Post quarry, depot, mine, warehouse, cellar, storage, harbor;
        final List<Post> posts;
        private final GameTestHelper helper;
        private final MindRuntime runtime;

        World(GameTestHelper helper) {
            this.helper = helper;
            this.runtime = MindRuntime.get(helper.getLevel().getServer());
            quarry = post(0, "quarry", "kiln", 40, 0, 1000);
            depot = post(1, "depot", "furnace", 32, 10, 1000);
            mine = post(2, "mine", "smithy", 64, 0, 0);
            warehouse = post(3, "warehouse", "kitchen", 24, 0, 1000);
            cellar = post(4, "cellar", "bakery", 0, 0, 1000);
            storage = post(5, "storage", "workshop", 24, 0, 1000);
            harbor = post(6, "harbor", "tower", 16, 0, 1000);
            posts = List.of(quarry, depot, mine, warehouse, cellar, storage, harbor);
            // The workshop stays a registered station, without an inventory.
            helper.setBlock(storage.rig().dest, Blocks.AIR);
            BlockPos smelter = new BlockPos(5, 1, 1 + 3 * 7);
            helper.setBlock(smelter, Blocks.CHEST);
            runtime.addStation(PLAYER, "smelter", helper.getLevel(), helper.absolutePos(smelter), helper.absolutePos(smelter.west()));
        }

        /** Two chests with their coal, a turtle with this fuel, and the two stations. The rig registers no station of its own. */
        private Post post(int row, String from, String to, int sourceCoal, int destinationCoal, int fuel) {
            Rig rig = new Rig(helper, from, PLAYER, new BlockPos(0, 1, 1 + 3 * row));
            helper.setBlock(rig.source, Blocks.CHEST);
            helper.setBlock(rig.dest, Blocks.CHEST);
            if (sourceCoal > 0) rig.chest(rig.source).setItem(0, new ItemStack(Items.COAL, sourceCoal));
            if (destinationCoal > 0) rig.chest(rig.dest).setItem(0, new ItemStack(Items.COAL, destinationCoal));
            MindCraftTests.placeTurtle(helper, rig.start, -1, fuel, PLAYER.id());
            runtime.addStation(PLAYER, from, rig.level, helper.absolutePos(rig.source), helper.absolutePos(rig.sourceDock));
            runtime.addStation(PLAYER, to, rig.level, helper.absolutePos(rig.dest), helper.absolutePos(rig.destDock));
            return new Post(rig, from, to);
        }

        /** The steps that bring each job to its state. The last step checks the world before the first request. */
        GameTestSequence build(GameTestSequence sequence) {
            MinecraftServer server = helper.getLevel().getServer();
            return sequence
                // The robot of the warehouse comes first: the harness empties it between its pickup and its delivery.
                .thenExecute(() -> start(warehouse, MindRuntime.TRANSPORT, 24, 0))
                .thenWaitUntil(() -> warehouse.rig().carried(24))
                .thenExecute(() -> {
                    RobotApi robot = warehouse.rig().robot();
                    check(robot != null, "the robot of the warehouse is not running");
                    robot.turtle.getInventory().clearContent();
                    start(quarry, MindRuntime.TRANSPORT, 16, 0);
                    start(depot, MindRuntime.KEEP_STOCK, 64, 32);
                    start(mine, MindRuntime.TRANSPORT, 24, 0);
                    start(cellar, MindRuntime.TRANSPORT, 32, 0);
                    start(storage, MindRuntime.TRANSPORT, 64, 0);
                    start(harbor, MindRuntime.TRANSPORT, 40, 0);
                })
                .thenWaitUntil(() -> {
                    reached(warehouse, State.RECOVERY, Cause.LOAD_DIFFERS);
                    reached(quarry, State.DONE, Cause.NONE);
                    reached(depot, State.WAITING, Cause.SOURCE_AT_RESERVE);
                    reached(mine, State.BLOCKED, Cause.OUT_OF_FUEL);
                    reached(cellar, State.WAITING, Cause.SOURCE_AT_RESERVE);
                    reached(storage, State.BLOCKED, Cause.DESTINATION_NO_INVENTORY);
                    reached(harbor, State.WAITING, Cause.SOURCE_AT_RESERVE);
                    check(storage.job().carried == 24 && harbor.job().delivered == 16, storage.rig().describe() + "\n" + harbor.rig().describe());
                })
                .thenExecute(() -> {
                    MindCraft.command(server, "computercraft shutdown #" + cellar.robot());
                    try {
                        runtime.control(PLAYER, harbor.robot(), "pause");
                    } catch (Refusal refusal) {
                        throw new GameTestAssertException("the runtime refused the pause of the harbor: " + refusal.getMessage());
                    }
                })
                .thenWaitUntil(() -> check(RobotApi.live(cellar.robot()) == null && harbor.job().state == State.PAUSED,
                    "the shutdown or the pause is not complete: " + cellar.rig().describe() + "\n" + harbor.rig().describe()))
                .thenExecute(() -> {
                    long now = helper.getLevel().getGameTime();
                    harbor.job().toSeenAt = now - JobAccount.OLD_TICKS - 1;
                    // The world of the set, as the account of each job gives it. A paid request goes out only after this check.
                    check(runtime.jobs(PLAYER).stream().filter(job -> !job.state.open()).toList().equals(List.of(quarry.job())),
                        "the robot of the quarry is not the only free robot");
                    check(JobAccount.of(harbor.job(), now).facts().destination() == null, "the observation of the tower is not old");
                    check(Integer.valueOf(24).equals(JobAccount.of(storage.job(), now).facts().onBoard()), storage.rig().describe());
                    check(!JobAccount.of(cellar.job(), now).running(), "the robot of the cellar is running");
                });
        }

        /** A start by the owner, without a proposal. A refusal fails the test: an exception of another kind would stop the server. */
        private void start(Post post, String module, int quantity, int reserve) {
            try {
                runtime.start(PLAYER, new Order(module, post.robot(), post.from(), post.to(), Items.COAL, quantity, reserve));
            } catch (Refusal refusal) {
                throw new GameTestAssertException("the runtime refused the job of the " + post.from() + ": " + refusal.getMessage());
            }
        }

        private static void reached(Post post, State state, Cause cause) {
            Job job = post.job();
            check(job != null && job.state == state && job.cause == cause, "the job of the " + post.from() + " is not " + state + " " + cause
                + " yet: " + post.rig().describe());
        }
    }

    /** The expected "none": the kind is all that the harness compares. */
    private static final Uno.Action NONE = new Uno.Action.None("no action of the list");

    /** One request of the set: its case, its text, and the action that the world asks for. */
    record Case(String name, String request, Function<World, Uno.Action> expected) {
    }

    /**
     * The fixed set of the measurement (D42, point 7): the seven actions, the T21 cases, the adversarial case of D32, a question about
     * the diary, a change of the destination, a question about the fuel and a request in Italian. Two more requests are a "none": one
     * without a necessary value, and one question that the context answers (D42, point 15).
     * Only a pause and a report act at once (D29). Each other action is a proposal, and the harness confirms none. Thus only the pause
     * changes the world: it is the last request. No request names a robot number: the numbers can change between two runs.
     */
    static final List<Case> SET = List.of(
        new Case("The seven actions: report", "How are my jobs going?", world -> report(null)),
        new Case("T21: a wait at the reserve", "Why does the furnace get no more coal?", world -> report(world.depot)),
        new Case("T21: a block without fuel", "What is wrong with the robot of the mine?", world -> report(world.mine)),
        new Case("T21: a job in RECOVERY", "How did the job of the warehouse go?", world -> report(world.warehouse)),
        new Case("T21: a robot that is not running", "Is the robot of the cellar still working?", world -> report(world.cellar)),
        new Case("T21: an old observation", "How much coal is in the tower now?", world -> report(world.harbor)),
        new Case("D32: BLOCKED with a load on board", "Is the job of the workshop done?", world -> report(world.storage)),
        new Case("A question about the diary", "What happened while I was away?", world -> report(null)),
        new Case("A question about the fuel", "How much fuel does the robot of the depot have left?", world -> report(world.depot)),
        new Case("The seven actions: start", "Carry 16 coal from the quarry to the kiln.",
            world -> start(MindRuntime.TRANSPORT, world.quarry, "quarry", "kiln", 16, 0)),
        new Case("The seven actions: change", "Leave only 16 coal in the depot.",
            world -> new Uno.Action.OnJob(Uno.Kind.CHANGE, target(world.depot), null, 16)),
        new Case("The seven actions: resume", "Resume the job of the harbor.", world -> onJob(Uno.Kind.RESUME, world.harbor)),
        new Case("The seven actions: cancel", "Cancel the job of the mine.", world -> onJob(Uno.Kind.CANCEL, world.mine)),
        new Case("A change of the destination", "Bring the coal of the depot to the smelter instead of the furnace.",
            world -> start(MindRuntime.KEEP_STOCK, world.depot, "depot", "smelter", 64, 32)),
        new Case("The seven actions: none", "What will the weather be like tomorrow?", world -> NONE),
        new Case("None: a value is missing", "Carry some coal to the smithy.", world -> NONE),
        new Case("None: a question from the context", "Which of my robots are free?", world -> NONE),
        new Case("A request in Italian", "Com'è andato il lavoro della quarry?", world -> report(world.quarry)),
        new Case("The seven actions: pause", "Pause the job of the depot for a while.", world -> onJob(Uno.Kind.PAUSE, world.depot)));

    private static Uno.Target target(Post post) {
        return new Uno.Target(post == null ? null : post.robot(), null);
    }

    private static Uno.Action onJob(Uno.Kind kind, Post post) {
        return new Uno.Action.OnJob(kind, target(post), null, null);
    }

    /** A report about the job of the post, or about each job. */
    private static Uno.Action report(Post post) {
        return onJob(Uno.Kind.REPORT, post);
    }

    private static Uno.Action start(String module, Post post, String from, String to, int quantity, int reserve) {
        return new Uno.Action.Start(new Order(module, post.robot(), from, to, Items.COAL, quantity, reserve));
    }

    /**
     * The record of one request. The record of the request has the values of Uno.ModelRequest: the model, the tokens, the time, the action
     * of the model and the result of the checks. "Right" says whether that action is the expected one. The sentence is the sentence of the
     * model, or null. The answer is the answer of Uno. The context is the context that the provider got. The note says why the record has
     * no answer of the provider, or it is null.
     */
    record Result(Case test, Uno.Action expected, Uno.ModelRequest record, boolean right, String sentence, String answer, Uno.Context context,
                  String note) {
    }

    /** The records of the requests, in the order of the set, and the Markdown section that the harness wrote. */
    record Report(List<Result> results, String markdown) {
    }

    /**
     * Builds the world, sends each request of the set and writes the record. The provider of the run gets the world: a stand-in reads the
     * robot numbers there. The title and the note open the Markdown section. The file is in the folder of the game test server.
     * After the record, "after" gets the report. The returned sequence continues after it: the caller ends it.
     */
    static GameTestSequence run(GameTestHelper helper, Function<World, Uno.Provider> provider, String title, String note, String file,
                                Consumer<Report> after) {
        MinecraftServer server = helper.getLevel().getServer();
        MindRuntime runtime = MindRuntime.get(server);
        World world = new World(helper);
        GameTestSequence sequence = helper.startSequence();
        for (Post post : world.posts) sequence = sequence.thenWaitUntil(post.rig()::enroll);
        for (Post post : world.posts) sequence = sequence.thenWaitUntil(post.rig()::restarted);
        sequence = world.build(sequence);
        Uno.Provider[] chosen = new Uno.Provider[1];
        sequence = sequence.thenExecute(() -> chosen[0] = provider.apply(world));
        List<Result> results = new ArrayList<>();
        for (Case test : SET) {
            Measured[] measured = new Measured[1];
            Uno.Action[] expected = new Uno.Action[1];
            String[] answer = new String[1];
            sequence = sequence
                .thenExecute(() -> {
                    expected[0] = test.expected().apply(world);
                    measured[0] = new Measured(chosen[0]);
                    // The answer of Uno comes on the server thread, as the steps of the sequence.
                    Uno.ask(server, PLAYER, test.request(), measured[0], Uno.TIMEOUT_MS, said -> answer[0] = said);
                })
                .thenWaitUntil(() -> check(answer[0] != null, "Uno did not answer yet: " + test.request()))
                .thenExecute(() -> {
                    results.add(measured[0].result(test, expected[0], answer[0], runtime));
                    MindCraft.LOGGER.info("The measurement, request {} of {}: {}", results.size(), SET.size(),
                        row(results.size(), results.get(results.size() - 1)));
                });
        }
        return sequence.thenExecute(() -> {
            String markdown = markdown(title, note, chosen[0].model(), results);
            MindCraft.LOGGER.info("The measurement, the record for TEST_RESULTS.md:\n{}", markdown);
            try {
                Files.writeString(server.getServerDirectory().toPath().resolve(file), markdown, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new GameTestAssertException("cannot write " + file + ": " + e);
            }
            MindCraft.LOGGER.info("The measurement wrote {} in the folder of the game test server", file);
            after.accept(new Report(List.copyOf(results), markdown));
        });
    }

    /**
     * The provider of one request: it gives the request to the provider of the run, and keeps the context and the record of the request.
     * It says that it calls no model: the player of the measurement is not an operator (see PLAYER). The record has the values that Uno
     * writes on the route of the model (D42, choices of the implementation): the result of the checks is Uno.voice, and a failure or an
     * answer that Uno cannot read has no valid action. The tokens of a failure are Uno.billed. The time is the time of the answer of the provider.
     */
    private static final class Measured implements Uno.Provider {
        private final Uno.Provider provider;
        private volatile Uno.Context context;
        private volatile Uno.ModelRequest record;
        private volatile String failure;

        Measured(Uno.Provider provider) {
            this.provider = provider;
        }

        @Override
        public Uno.Answer answer(String request, Uno.Context context) throws Exception {
            this.context = context;
            long sent = System.nanoTime();
            try {
                Uno.Answer answer = provider.answer(request, context);
                Uno.Action action = answer.action();
                record = new Uno.ModelRequest(provider.model(), answer.inputTokens(), answer.outputTokens(), since(sent), action,
                    action instanceof Uno.Action.Unreadable ? Uno.Voice.NO_VALID_ACTION : Uno.voice(action, request, context));
                return answer;
            } catch (Exception e) {
                // The failure first: a reader that finds the record finds its failure too.
                failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                // A refusal and a cut answer have the tokens that the service billed. Each other failure has none.
                Uno.Usage billed = Uno.billed(null, e);
                record = new Uno.ModelRequest(provider.model(), billed.inputTokens(), billed.outputTokens(), since(sent), null,
                    Uno.Voice.NO_VALID_ACTION);
                throw e;
            }
        }

        @Override
        public String model() {
            return provider.model();
        }

        /** The record of the request, after the answer of Uno. Without a record Uno answered at its time limit: then the record of Uno. */
        Result result(Case test, Uno.Action expected, String answer, MindRuntime runtime) {
            Uno.ModelRequest done = record;
            String note = done == null ? "the request reached the time limit" : failure != null ? "the provider failed: " + failure
                : done.action() instanceof Uno.Action.Unreadable ? "Uno cannot read the answer of the provider" : null;
            if (done == null) done = new Uno.ModelRequest(provider.model(), 0, 0, Uno.TIMEOUT_MS, null, Uno.Voice.NO_VALID_ACTION);
            return new Result(test, expected, done, right(expected, done.action(), runtime), sentence(done.action()), answer, context, note);
        }

        private static long since(long sent) {
            return (System.nanoTime() - sent) / 1_000_000;
        }
    }

    /**
     * Whether the action of the model is the expected action. A start: the same order. A "none": a "none". A change, a pause, a resume
     * and a cancellation: the job that Uno finds for the target is the expected job, and a change gives the same new values.
     * A report reads: its target selects only the fixed sentences before the voice (D42, point 16). Thus a report without a job,
     * or with the robot or a station of the expected job, is right. A report about each job is right only without a job.
     */
    static boolean right(Uno.Action expected, Uno.Action given, MindRuntime runtime) {
        if (expected instanceof Uno.Action.Start start) return given instanceof Uno.Action.Start order && order.order().equals(start.order());
        if (expected instanceof Uno.Action.None) return given instanceof Uno.Action.None;
        Uno.Action.OnJob job = (Uno.Action.OnJob) expected;
        if (!(given instanceof Uno.Action.OnJob on) || on.kind() != job.kind() || on.target() == null) return false;
        if (job.target().none()) return on.target().none();
        Job named = runtime.jobs.get(job.target().robot());
        if (job.kind() == Uno.Kind.REPORT) {
            if (on.target().none()) return true;
            if (on.target().robot() != null) return on.target().robot() == named.robot;
            return on.target().station().equalsIgnoreCase(named.from) || on.target().station().equalsIgnoreCase(named.to);
        }
        return Uno.find(runtime, PLAYER, on.target()).job() == named
            && Objects.requireNonNullElse(on.quantity(), named.quantity).equals(Objects.requireNonNullElse(job.quantity(), named.quantity))
            && Objects.requireNonNullElse(on.reserve(), named.reserve).equals(Objects.requireNonNullElse(job.reserve(), named.reserve));
    }

    /** The sentence of an action of the model: only a report and a "none" have one. */
    private static String sentence(Uno.Action action) {
        if (action instanceof Uno.Action.None none) return none.sentence();
        return action instanceof Uno.Action.OnJob job && job.kind() == Uno.Kind.REPORT ? job.sentence() : null;
    }

    /** An action in words, for the record. */
    static String describe(Uno.Action action) {
        if (action == null) return "no answer";
        if (action instanceof Uno.Action.Start start) return "start: " + Uno.describe(start.order());
        if (action instanceof Uno.Action.None) return "none";
        if (!(action instanceof Uno.Action.OnJob job)) return "an answer that Uno cannot read";
        Uno.Target target = job.target();
        String text = job.kind().name().toLowerCase(Locale.ROOT);
        if (target == null) text += ", a target that is not valid";
        else if (target.none()) text += ", without a job";
        else text += (target.robot() == null ? "" : ", robot #" + target.robot()) + (target.station() == null ? "" : ", station '" + target.station() + "'");
        if (job.quantity() != null) text += ", quantity " + job.quantity();
        if (job.reserve() != null) text += ", reserve " + job.reserve();
        return text;
    }

    /** The row of the table for one request. */
    static String row(int number, Result result) {
        Uno.ModelRequest record = result.record();
        return "| " + number + " | " + cell(result.test().name()) + " | " + cell(result.test().request()) + " | " + cell(describe(result.expected()))
            + " | " + cell(describe(record.action())) + " | " + (result.right() ? "yes" : "no") + " | " + record.voice() + " | " + record.millis()
            + " | " + record.inputTokens() + " | " + record.outputTokens() + " |";
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }

    /**
     * The Markdown section of a run: a heading of level 4, the type of the run, a table with one row for each request, the totals,
     * and for each request the sentence of the model next to its facts. The facts are a part of the context that the provider got:
     * the job that the expected action names, each job and the lines of the diary for a request about each job, and the stations and
     * the robots for a "none". A start also gets the robots. Each value is the JSON that the provider got.
     */
    static String markdown(String title, String note, String model, List<Result> results) {
        StringBuilder out = new StringBuilder("#### ").append(title).append("\n\n");
        out.append(note).append(" Date: ").append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append(". Model: `").append(model).append("`. ")
            .append(results.size()).append(" requests, one after the other, in one world.\n\n");
        out.append("| No. | Case | Request | Expected action | Action of the model | Right | Checks | Time, ms | Input tokens | Output tokens |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (int i = 0; i < results.size(); i++) out.append(row(i + 1, results.get(i))).append('\n');
        long millis = 0, input = 0, output = 0;
        for (Result result : results) {
            millis += result.record().millis();
            input += result.record().inputTokens();
            output += result.record().outputTokens();
        }
        out.append("\nTotals: right actions: ").append(results.stream().filter(Result::right).count()).append(" of ").append(results.size())
            .append(". Failed checks: ").append(count(results, Uno.Voice.FAILED)).append(". Passed checks: ").append(count(results, Uno.Voice.PASSED))
            .append(". Requests without a valid action: ").append(count(results, Uno.Voice.NO_VALID_ACTION)).append(". Time: ").append(millis)
            .append(" ms. Tokens: ").append(input).append(" in, ").append(output).append(" out.\n");
        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            out.append("\n**").append(i + 1).append(". ").append(result.test().name()).append(".** ").append(result.test().request()).append("\n\n```text\n");
            out.append("The sentence of the model: ").append(result.sentence() != null ? result.sentence()
                : result.note() != null ? "none: " + result.note() + "." : "none: the server writes the answer of this action.").append('\n');
            out.append("The answer of Uno: ").append(result.answer()).append('\n');
            facts(out, result);
            out.append("```\n");
        }
        return out.toString();
    }

    private static long count(List<Result> results, Uno.Voice voice) {
        return results.stream().filter(result -> result.record().voice() == voice).count();
    }

    /** The part of the context that the provider got, for this request. See markdown. */
    private static void facts(StringBuilder out, Result result) {
        if (result.context() == null) {
            out.append("The facts: the provider got no context.\n");
            return;
        }
        JsonObject context = JsonParser.parseString(result.context().toJson()).getAsJsonObject();
        Integer robot = result.expected() instanceof Uno.Action.Start start ? Integer.valueOf(start.order().robot())
            : result.expected() instanceof Uno.Action.OnJob job ? job.target().robot() : null;
        if (result.expected() instanceof Uno.Action.None) out.append("The stations: ").append(context.get("stations")).append('\n');
        if (!(result.expected() instanceof Uno.Action.OnJob)) out.append("The robots: ").append(context.get("robots")).append('\n');
        if (result.expected() instanceof Uno.Action.None) return;
        for (JsonElement element : context.getAsJsonArray("jobs")) {
            JsonObject job = element.getAsJsonObject();
            if (robot != null && job.get("robot").getAsInt() != robot) continue;
            out.append("The facts of job ").append(job.get("job")).append(" on robot #").append(job.get("robot")).append(": ").append(job).append('\n');
        }
        if (robot == null) {
            for (JsonElement line : context.getAsJsonArray("diary")) out.append("A line of the diary: ").append(line).append('\n');
        }
    }
}
