package io.github.g4solio.mindcraft;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import io.github.g4solio.mindcraft.MindRuntime.Actor;
import io.github.g4solio.mindcraft.MindRuntime.Job;
import io.github.g4solio.mindcraft.MindRuntime.Order;
import io.github.g4solio.mindcraft.MindRuntime.Refusal;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod(MindCraft.MOD_ID)
public class MindCraft {
    public static final String MOD_ID = "mindcraft";
    static final Logger LOGGER = LogUtils.getLogger();

    /** Computer ID to {boot count to reach, time limit in milliseconds}. Computers run in real time, not in ticks. */
    static final Map<Integer, long[]> REBOOTS = new HashMap<>();
    private static final long REBOOT_LIMIT_MS = 30_000;

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    /** The projector: a contact point with Uno at a fixed place of the base. See ProjectorBlock. */
    static final RegistryObject<Block> PROJECTOR = BLOCKS.register("projector", ProjectorBlock::new);
    private static final RegistryObject<Item> PROJECTOR_ITEM = ITEMS.register("projector", () -> new BlockItem(PROJECTOR.get(), new Item.Properties()));

    /** The tablet: a contact point with Uno that the player carries. It opens the screen of Uno at any distance. */
    static final RegistryObject<Item> TABLET = ITEMS.register("tablet", () -> new Item(new Item.Properties().stacksTo(1)) {
        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (player instanceof ServerPlayer serverPlayer) Net.sendView(serverPlayer);
            return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    });

    public MindCraft(FMLJavaModLoadingContext context) {
        BLOCKS.register(context.getModEventBus());
        ITEMS.register(context.getModEventBus());
        Net.register();
        context.getModEventBus().addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
            event.accept(TABLET);
            event.accept(PROJECTOR_ITEM);
        });
        context.getModEventBus().addListener((FMLCommonSetupEvent event) -> RobotApi.register());
        MinecraftForge.EVENT_BUS.addListener(MindCraft::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(MindCraft::tick);
        MinecraftForge.EVENT_BUS.addListener(MindCraft::aboutToStart);
    }

    /**
     * A server starts. In a client this occurs each time the player opens a world, in the same Java process.
     * Uno starts a new session: the messages, the proposals and the open requests of the world before it do not count.
     * The same applies to the robots: a robot session, a boot count and a pending computer restart of the world before it.
     * No computer of the new server runs at this moment.
     * <p>
     * Uno asks Claude only if the server has an API key in its environment. Without a key Uno uses the fixed rules.
     * Also with a key, only an operator uses Claude, with a limit of requests in each server session (D33).
     * A game test server always uses the fixed rules. Thus the checks give the same result on each computer and cost nothing.
     */
    static void aboutToStart(ServerAboutToStartEvent event) {
        Uno.newSession();
        RobotApi.newSession();
        REBOOTS.clear();
        String key = System.getenv(ClaudeProvider.KEY_VARIABLE);
        boolean claude = key != null && !key.isBlank() && !(event.getServer() instanceof GameTestServer);
        Uno.provider = claude ? ClaudeProvider.fromEnv() : new Uno.RuleProvider();
        LOGGER.info("Uno interprets requests with {}.", claude
            ? "Claude (" + ClaudeProvider.MODEL + ") for the operators, at most " + Uno.MODEL_REQUESTS + " requests for each of them in this session. "
                + "The fixed rules read the requests of the other players"
            : "the fixed rules, without a language model");
    }

    /**
     * Restarts a computer through the /computercraft commands. This also stops a program that does not cooperate.
     * CC: Tweaked has no restart command. It ignores "shutdown" while a computer starts, and "turn-on" while a
     * computer stops. Thus the tick handler repeats "shutdown" until the old session ends, then "turn-on" until a
     * new session starts.
     */
    public static void reboot(MinecraftServer server, int computerId) {
        REBOOTS.put(computerId, new long[]{RobotApi.boots(computerId) + 1, Util.getMillis() + REBOOT_LIMIT_MS});
    }

    static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        REBOOTS.entrySet().removeIf(reboot -> {
            int id = reboot.getKey();
            if (RobotApi.boots(id) >= reboot.getValue()[0] || Util.getMillis() > reboot.getValue()[1]) return true;
            command(event.getServer(), "computercraft " + (RobotApi.live(id) != null ? "shutdown" : "turn-on") + " #" + id);
            return false;
        });
    }

    // --- /mindcraft: the debug interface of S1 ---------------------------------------------------------------------

    private interface Request {
        String run(CommandContext<CommandSourceStack> context, MindRuntime runtime, Actor actor) throws Exception;
    }

    static Actor actor(ServerPlayer player) {
        return new Actor(player.getUUID(), player.hasPermissions(2));
    }

    /** The server builds the actor from the command source. The client cannot choose it. */
    private static Actor actor(CommandSourceStack source) {
        return source.getEntity() instanceof Player player
            ? new Actor(player.getUUID(), source.hasPermission(2))
            : new Actor(Util.NIL_UUID, true);
    }

    private static int handle(CommandContext<CommandSourceStack> context, Request request) {
        CommandSourceStack source = context.getSource();
        Actor actor = actor(source);
        try {
            String answer = request.run(context, MindRuntime.get(source.getServer()), actor);
            source.sendSuccess(() -> Component.literal(answer), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal(String.valueOf(e.getMessage())));
            return 0;
        }
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> robotArgument() {
        return argument("robot", IntegerArgumentType.integer(0));
    }

    private static int robotId(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "robot");
    }

    /** The runtime checks the actor, as for each other request. A player reads only the player's own robots and jobs. */
    private static String status(CommandContext<CommandSourceStack> context, MindRuntime runtime, Actor actor) {
        return runtime.status(actor, robotId(context), context.getSource().getLevel().getGameTime());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> control(String action) {
        return literal(action).then(robotArgument().executes(c -> handle(c, (context, runtime, actor) -> {
            runtime.control(actor, robotId(context), action);
            return status(context, runtime, actor);
        })));
    }

    private static String addStation(CommandContext<CommandSourceStack> context, MindRuntime runtime, Actor actor) throws Exception {
        String name = StringArgumentType.getString(context, "name");
        runtime.addStation(actor, name, context.getSource().getLevel(),
            BlockPosArgument.getLoadedBlockPos(context, "inventory"), BlockPosArgument.getLoadedBlockPos(context, "dock"));
        return "Station '" + name + "' is registered.";
    }

    private static String enroll(CommandContext<CommandSourceStack> context, MindRuntime runtime, Actor actor) throws Exception {
        RobotApi robot = RobotApi.at(context.getSource().getLevel(), BlockPosArgument.getLoadedBlockPos(context, "pos"));
        if (robot == null) throw new Refusal("No running turtle at this position. Turn it on first.");
        runtime.enroll(actor, robot);
        reboot(context.getSource().getServer(), robot.id());
        return "Robot #" + robot.id() + " is enrolled. It restarts now and works for Uno.";
    }

    private static String release(CommandContext<CommandSourceStack> context, MindRuntime runtime, Actor actor) {
        runtime.release(actor, robotId(context));
        reboot(context.getSource().getServer(), robotId(context));
        return "Robot #" + robotId(context) + " is released. It restarts now as an ordinary turtle.";
    }

    private static String order(CommandContext<CommandSourceStack> context, MindRuntime runtime, Actor actor, String module, int reserve) {
        Job job = runtime.start(actor, new Order(module, robotId(context), StringArgumentType.getString(context, "from"),
            StringArgumentType.getString(context, "to"), ItemArgument.getItem(context, "item").getItem(),
            IntegerArgumentType.getInteger(context, "quantity"), reserve));
        return "Job " + job.id + " started on robot #" + robotId(context) + ".";
    }

    /** The arguments of an order: robot, from, to, item, quantity and an optional reserve. */
    private static LiteralArgumentBuilder<CommandSourceStack> order(String literal, String module, RegisterCommandsEvent event) {
        var quantity = argument("quantity", IntegerArgumentType.integer(1))
            .executes(c -> handle(c, (context, runtime, actor) -> order(context, runtime, actor, module, 0)))
            .then(argument("reserve", IntegerArgumentType.integer(0))
                .executes(c -> handle(c, (context, runtime, actor) ->
                    order(context, runtime, actor, module, IntegerArgumentType.getInteger(context, "reserve")))));
        return literal(literal).then(robotArgument()
            .then(argument("from", StringArgumentType.word())
                .then(argument("to", StringArgumentType.word())
                    .then(argument("item", ItemArgument.item(event.getBuildContext())).then(quantity)))));
    }

    /** A direct change by the owner. It uses the current revision, thus it always applies. */
    private static String set(CommandContext<CommandSourceStack> context, MindRuntime runtime, Actor actor) {
        Job job = runtime.jobs.get(robotId(context));
        if (job == null) throw new Refusal("Robot #" + robotId(context) + " has no job.");
        runtime.update(actor, robotId(context), job.revision,
            IntegerArgumentType.getInteger(context, "quantity"), IntegerArgumentType.getInteger(context, "reserve"));
        return status(context, runtime, actor);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        var station = literal("station").then(literal("add")
            .then(argument("name", StringArgumentType.word())
                .then(argument("inventory", BlockPosArgument.blockPos())
                    .then(argument("dock", BlockPosArgument.blockPos())
                        .executes(c -> handle(c, MindCraft::addStation))))));

        var robot = literal("robot")
            .then(literal("enroll").then(argument("pos", BlockPosArgument.blockPos()).executes(c -> handle(c, MindCraft::enroll))))
            .then(literal("release").then(robotArgument().executes(c -> handle(c, MindCraft::release))));

        var set = literal("set").then(robotArgument()
            .then(argument("quantity", IntegerArgumentType.integer(1))
                .then(argument("reserve", IntegerArgumentType.integer(0)).executes(c -> handle(c, MindCraft::set)))));

        var job = literal("job")
            .then(order("transport", MindRuntime.TRANSPORT, event))
            .then(order("keep", MindRuntime.KEEP_STOCK, event))
            .then(set)
            .then(literal("status").then(robotArgument().executes(c -> handle(c, MindCraft::status))))
            .then(control("pause"))
            .then(control("resume"))
            .then(control("cancel"));

        event.getDispatcher().register(literal(MOD_ID).then(station).then(robot).then(job));

        // /uno: the first contact point with Uno. The answer of "ask" arrives later, when the provider is done.
        event.getDispatcher().register(literal("uno")
            .then(literal("ask").then(argument("request", StringArgumentType.greedyString()).executes(c -> {
                CommandSourceStack source = c.getSource();
                Uno.ask(source.getServer(), actor(source), StringArgumentType.getString(c, "request"),
                    answer -> source.sendSuccess(() -> Component.literal(answer), false));
                return 1;
            })))
            .then(literal("confirm").executes(c -> handle(c, (context, runtime, actor) -> Uno.confirm(context.getSource().getServer(), actor))))
            .then(literal("report").executes(c -> handle(c, (context, runtime, actor) -> Uno.report(context.getSource().getServer(), actor)))));
    }
}
