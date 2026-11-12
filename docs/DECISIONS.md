# Decision log

Each decision has a status.

- **Proposed**: a working assumption. No test supports it yet.
- **Decided**: the project owner confirmed the choice.
- **Verified**: an executed test supports the choice. The choice stays reversible.

The tests are described in [TEST_RESULTS.md](TEST_RESULTS.md). "Sources" means the sources of CC: Tweaked at the tag `v1.20.1-1.121.0`.

## D01 — Versions and toolchain

**Status:** verified on 2026-11-12.

| Component | Version | Evidence |
| --- | --- | --- |
| Minecraft | 1.20.1 | Requirement of the project owner |
| Forge | 47.4.28 | `promotions_slim.json` of Forge: `1.20.1-recommended` = 47.4.28, `1.20.1-latest` = 47.4.29 |
| CC: Tweaked | 1.121.0 | `maven-metadata.xml` on maven.squiddev.cc: the latest version of `cc-tweaked-1.20.1-forge`, published on 2026-11-03 |
| ForgeGradle, Gradle | 6.0.x, 8.8 | Content of the official MDK archive `forge-1.20.1-47.4.28-mdk.zip` |
| Java | Temurin 17.0.21 | The compilation and the in-game tests pass with this JDK |

- **Evidence:** `gradlew compileJava` and `gradlew runGameTestServer` pass. The server loads Forge, CC: Tweaked and MindCraft.
- **Note:** CC: Tweaked 1.121.0 is compiled against Forge 47.1.0 (`gradle/libs.versions.toml` in the sources). It works with 47.4.28.
- **Disproved assumption:** the file `mdk/build.gradle` on the `1.20.1` branch of Forge on GitHub uses ForgeGradle 7. The MDK published for 47.4.28 uses ForgeGradle 6. The published archive is the reference.
- **Not verified:** the two `mixin.env.*` properties in `build.gradle` are a common practice. The build was not tried without them.
- **Discard condition:** a new version of Forge or of CC: Tweaked makes the in-game tests fail.

## D02 — Bridge between the runtime and the turtle: a Lua API in the same process

**Status:** verified.

- **Problem:** the runtime must command and observe a turtle. It must also know with certainty which turtle speaks.
- **Alternatives:**
  1. A Lua API registered with `ComputerCraftAPI.registerAPIFactory`, only on turtles.
  2. A peripheral supplied by a turtle upgrade (`ITurtleUpgrade`).
  3. An external process, connected with HTTP, WebSocket or rednet.
- **Provisional choice:** alternative 1. The API is named `mindcraft`.
- **Evidence:** the Javadoc of `registerAPIFactory` describes this use ("add a new API just to turtles"). `IComputerSystem.getComponent(ComputerComponents.TURTLE)` returns `ITurtleAccess`. All in-game tests use this path.
- **Reason:** the server creates the API instance for each computer. The identity does not depend on a field that the Lua program writes. Alternative 3 does not have this guarantee: the rednet documentation says that messages are not authenticated.
- **Counterexample:** the API is present on each turtle, also if the player does not want it. The documentation of CC: Tweaked prefers peripherals to global APIs.
- **Cost of change:** low. The same Lua functions can move to a peripheral (alternative 2). The Lua module changes only the way it gets the object.
- **Discard condition:** the survival progression needs an item that the player must build to connect a turtle to Uno. Then alternative 2 is better.

## D03 — Lua agent in the ROM, started from `rom/autorun`

**Status:** verified.

- **Problem:** after a restart the turtle must continue the work without an action of the player.
- **Provisional choice:** the mod adds `data/computercraft/lua/rom/autorun/mindcraft.lua` and `rom/modules/main/mindcraft/transport.lua`.
- **Evidence:** in the sources, `ServerContext` mounts the ROM with `createResourceMount("computercraft", "lua/rom")`. Thus the resources of a mod can extend the ROM. `rom/startup.lua` runs the files of `/rom/autorun` before the startup files of the player. The restart tests (T11) confirm that the work continues.
- **Behaviour:** the agent ends immediately on a computer that is not enrolled. On an enrolled turtle it takes control of the terminal. The `startup` files of the player do not run while the turtle is enrolled.
- **Counterexample:** the file runs on each computer of the server. A data pack with a higher priority can replace the agent. The player can stop the agent with Ctrl+T: the job stays `RUNNING` but nothing executes it. The `status` command shows the time since the last contact.
- **Discard condition:** the terminal of an enrolled turtle is necessary, or more than one program in parallel on the same turtle.

## D04 — Authoritative runtime in the server, state saved with the world

**Status:** verified.

- **Provisional choice:** stations, enrolled robots and jobs are in a `SavedData` of the world (`data/mindcraft.dat`). All API functions run on the server thread (`@LuaFunction(mainThread = true)`).
- **Evidence:** T07 (only one open job for each robot), T11 (the state survives a restart and a sudden stop), T12 (only one valid session for each robot).
- **Operating authority:** for each computer ID, only the API instance that started last is valid. The calls of an older instance fail with an error.
- **Counterexample:** two turtles can have the same computer ID, for example after a copy in creative mode. For the runtime they are the same robot. The more recent one takes the job. If the cargo does not agree, the job goes to `RECOVERY`.
- **Cost:** each API call waits for one tick. The cost with many units is not measured.
- **Discard condition:** the cost on the server thread is too high with many turtles.

## D05 — The runtime moves the items. The Lua module moves the turtle

**Status:** verified.

- **Problem:** the result of a transport must contain exact quantities. `success=true` is not sufficient.
- **Alternatives:**
  1. The Lua module uses `turtle.suck` and `turtle.drop`. The runtime records before and after.
  2. The module calls `mindcraft.take()` and `mindcraft.give()`. The runtime moves the items between the adjacent inventory and the turtle.
- **Provisional choice:** alternative 2. The movement stays in the Lua module, with the standard `turtle` API.
- **Reason:** `turtle.suck` takes the first available item and has no filter. With alternative 2 the check, the effect and the record occur in the same tick. No other actor can act between the check and the effect.
- **Evidence:** T03 (exact partial delivery), T08 (a repeated command moves nothing more), T13 (the reserve is calculated again at the moment of the withdrawal).
- **Limits that the runtime applies:** the turtle must be on the dock of the station of the job. The quantity is not more than the request. The reserve stays in the source.
- **Counterexample:** the transfers of the runtime do not go through the `turtle.suck` and `turtle.drop` actions. A protection mod that checks those actions does not see these transfers. Not verified.
- **Limit:** the comparison of items uses only the type. NBT data are ignored.
- **Discard condition:** a second module needs different primitives. Then `take` and `give` become general primitives with explicit limits.

## D06 — Registered stations and true position

**Status:** verified for the simple case. The perception model is **proposed**.

- **Provisional choice:** a station is an inventory and an adjacent dock. The player registers it with a command. The module goes from one dock to the other along the axes (x, then z, then y), without path finding.
- **Position:** the module asks the runtime for position and direction (`mindcraft.pose()`). The runtime reads them from `ITurtleAccess`. The module does not count its own movements.
- **Evidence:** T12 (the turtle is moved and arrives anyway), T05 (obstacle: a limited wait, then a block).
- **Check of the station:** at the registration and at each transfer the runtime checks that an inventory is at the position. If it is missing, the job goes to `BLOCKED` (T04).
- **Counterexample:** a chest that was replaced at the same position is the same station. The robot always knows its exact position, without GPS: this is a product choice to review for the survival progression.
- **Discard condition:** a real path is not straight. Then a registered path with waypoints or a path search is necessary.

## D07 — Inventories are observed only from the dock

**Status:** verified.

- **Provisional choice:** the runtime reads an inventory only when an enrolled robot is on the dock of the station. Each observation has the tick of the reading. Before the first observation the stock is "unknown", not zero.
- **Evidence:** test `s1_transfer` (T02). The `status` command shows "unknown" or the quantity with its age.
- **Reason:** the server could read each chest. The brief asks that the perception of Uno stays tied to its robots.
- **Limit:** between two visits the shown stock is old. The runtime shows the age but does not update the value.

## D08 — Restart based on evidence

**Status:** verified in the controlled case.

- **Provisional choice:** the Lua module keeps no state. At each step it asks the runtime for the state. The next step depends on the observed cargo: with cargo on board the robot goes to deliver, otherwise it goes to withdraw.
- **Check:** at each step the runtime compares the real cargo of the turtle with the recorded cargo. If they are different, the job goes to `RECOVERY` and the robot stops.
- **Exit from `RECOVERY`:** the owner chooses. `resume` accepts the observed cargo. `cancel` closes the job. After a `resume` the totals can be inexact and the state says so.
- **Evidence:** T09, T10, T11 (restart of the computer, normal stop of the server, sudden stop after a save).
- **Limits:** another actor can change a chest between two visits. The runtime cannot reconstruct a delivery from the count of the chest alone.

### D08.1 — Order of the saves on disk

**Status:** verified on the test server. The behaviour on a normal server is **not verified**.

- **Problem:** after a sudden stop the world on disk and the record on disk can have different ages. If the world contains a delivery that the record does not contain, the robot starts again with zero cargo and a zero record. The runtime sees no difference and repeats the delivery.
- **Provisional choice:** the runtime writes its file immediately after each successful transfer. Thus the record is never older than the world. Each job records the game time of the last transfer.
- **Detection:** at the first access after the server start, the runtime compares the game time of the world with the time of the last transfer of each job. If the world is older, the job goes to `RECOVERY`, also if it was `DONE`. The observed stocks become unknown again. The owner decides.
- **Evidence:** T11, phase `crash` followed by `check-crash`. After the sudden stop the world on disk had destination 0 and a robot with 24 items. The record on disk said `DONE`, delivered 24. The runtime put the job in `RECOVERY`.
- **Disproved assumption:** "a check at the first contact of the robot is sufficient". In the first version the state said `DONE` until the robot called the runtime. If the robot is not loaded, that false statement stayed.
- **Not verified:** the statement "Minecraft writes chunks to disk more frequently than `SavedData`" comes from general knowledge of the game. The code of Minecraft 1.20.1 was not read. On the test server, 15 seconds after the delivery, the world on disk did not contain the delivery. The test server has no idle time in the tick and a normal server can behave differently.
- **Counterexample:** the opposite case stays undetectable. If someone restores an old `mindcraft.dat` file on a new world, only the comparison of the cargo applies (T09).
- **Limit:** the write of the file is not atomic, as for each `SavedData` of Minecraft. A stop during the write can corrupt the file. Then Minecraft creates empty data and the runtime loses stations, robots and jobs.
- **Cost:** one file write on the server thread for each transfer. Not measured.

## D09 — Pause, cancellation and forced stop

**Status:** verified.

- **Contract:** pause and cancellation apply from the next step. The movement or transfer in progress ends. The cargo stays on board and the state shows it. There is no automatic return.
- **Forced stop:** the runtime can turn off the computer with the command `/computercraft shutdown #<id>`. It also works with a program that does not cooperate.
- **Evidence:** T14, `s0_probe_hard_stop`.
- **Limit:** after a cancellation, a new job for the same item is refused until the player empties the turtle.
- **Disproved assumption:** "`shutdown` followed by `turn-on` restarts the computer". CC: Tweaked has no restart command. It ignores `turn-on` while the shutdown is in progress and ignores `shutdown` while the computer starts (`ComputerExecutor.queueStart` and `queueStop` in the sources). In the third test run, 3 turtles of 20 did not start again for this reason.
- **Correction:** the runtime repeats `shutdown` at each tick until the old session ends. Then it repeats `turn-on` until a new session starts. The limit is 30 seconds of real time.

## D10 — Managed Lua: no sandbox for now

**Status:** the facts are verified. The design of the managed environment is **proposed**.

Facts found on the real Lua machine (`s0_probe_environment`, `s0_probe_no_yield`):

| Question | Result |
| --- | --- |
| Does a program loaded with an empty environment reach `turtle`, `_G`, `load`, `debug` or `fs`? | No |
| Can the same program use the string methods? | Yes |
| If the environment contains the original `load` function? | It reaches `turtle` |
| If the environment contains `getfenv`? | `getfenv` exists and reaches `turtle` |
| If the environment contains the `debug` library? | It reaches `turtle` through the upvalues of `print` |
| If the environment contains the original `string` table, or `getmetatable`? | The program changes the `string` table of the whole computer |
| A loop without `yield`? | Error "Too long without yielding" after about 7 seconds. `pcall` catches it |

- **Consequence:** the limits of a job apply only to the actions that go through `mindcraft.take` and `mindcraft.give`. A Lua program with access to the `turtle` API can go around them. The `transport` module is code of this project and uses `turtle` only to move.
- **Consequence:** Uno must not run arbitrary Lua until a managed environment passes dedicated tests (T17). The environment must give copies of the libraries, a restricted `load`, and no access to `getfenv`, `debug` and `getmetatable`.
- **Not verified:** the effect of a loop without `yield` on the other computers with `computer_threads = 1`. During the tests the other robots continued to work, but there is no measurement.

## D11 — Ownership and permissions

**Status:** verified at the minimum level that S1 requires.

- **Provisional choice:** the server builds the actor from the command source. Each request checks the owner of the robot, of the stations and of the job. An operator (level 2) and the console can do everything.
- **Enrollment:** a player can enroll only a turtle that the same player placed (`ITurtleAccess.getOwningPlayer`).
- **Evidence:** T19.
- **Counterexample:** a chest has no owner. Any player can register the chest of another player as a station and empty it with a robot. An integration with the protections of the server is necessary. Only an operator can enroll a turtle that a mechanism placed, without a player.

## D12 — Fuel is separate from the material of the job

**Status:** verified.

- **Provisional choice:** the module never burns the item of the job. If the fuel ends, the module tries the other items on board. If they are not sufficient, the job goes to `BLOCKED`.
- **Evidence:** T06. In the tests the fuel is a number set on the turtle.
- **Not covered:** the count of the coal used as fuel when coal is also the item of the job.

## D13 — No chunk loading

**Status:** proposed.

- **Provisional choice:** the work occurs only in areas that are already active. The mod does not load chunks.
- **Partial evidence:** after a server restart, in a chunk that is kept loaded, the robot continues without connected players (T11).
- **Not verified:** unload and reload of a chunk during a transport. The explicit suspension outside active areas is not implemented: the job stays `RUNNING` and `status` shows the robot as not loaded.

## D14 — World and time of the test server

**Status:** verified.

Facts found on the test server of Forge 47.4.28 (`runGameTestServer`):

| Fact | How it was found | Consequence |
| --- | --- | --- |
| It runs ticks without pauses. The computers of CC: Tweaked run in real time | Run 1: 2400 ticks in 8 seconds | The test class keeps the server at 20 ticks per second |
| It creates a world with normal terrain and a random seed | `level.dat` of the test world; in one run, a `minecraft:stone` block in front of the robot at y = 100 | Gradle writes `server.properties` with a flat world and no structures |
| It puts the tests near the origin of the world | Positions in the test logs | A rig at a fixed position stays valid between two starts |
| `GameTestHelper.relativePos` mirrors the position | The requested position (0, 100, 1) became (0, 100, -1) | The tests do not use that method |
| It selects the tests by the namespace of the template, not of the class | Run 2 | The restart test has its own namespace and its own template |
| It does not ask to accept the EULA | All runs | The in-game tests start without manual steps |

- **Counterexample:** a flat world contains no terrain, liquids, block gravity or structures. The tests say nothing about the behaviour in a normal world.
- **Discard condition:** a test of navigation in real terrain is necessary. Then the test must build its own terrain.

## D15 — Language of the repository

**Status:** decided by the project owner on 2026-11-12.

- **Decision:** English is the base language of the repository: documents, code, comments, commit messages and the text that Uno shows in the game.
- **History:** the first versions of the documents and of the text of Uno were in Italian, as the first brief. The Italian text of Uno was removed. It can come back later as a translation.
- **Style:** the sentences follow the clarity principles that the project owner asked for: short sentences, one responsibility for each sentence. There is no claim of conformity to ASD-STE100.
- **Requests of the player:** the fixed rules of Uno still read a few Italian words. The Claude provider accepts a request in any language. Uno always answers in English.
- **Counterexample:** a player who writes in another language gets an English answer. A second language needs translation keys, because the server builds the text of the screen (D19).

## D16 — "Keep stock" composed on the transport

**Status:** verified.

- **Problem:** the reference job has no end. It must keep a condition and respect a reserve.
- **Provisional choice:** each job comes from a structured order: module, robot, source, destination, item, quantity, reserve. For `transport` the quantity is the total to deliver. For `keep_stock` it is the level to keep in the destination.
- **Composition:** the Lua module `keep_stock` uses three functions of the `transport` module: `step`, `goTo` and `active`. It adds only one thing: the robot observes the destination from the dock and waits there while the stock is sufficient.
- **What changed in the abstractions of S1:** the job now has a module and a revision. The runtime calculates the quantity still to carry (`need`). The state `SATISFIED` is new. The primitives `take` and `give` stayed the same. `observe` was added.

Definition of availability:

| Quantity | Definition |
| --- | --- |
| Stock in the destination | The last observation that a robot made from the dock. It has an age |
| Material in transit | The cargo on board the robot. It counts toward the target. It is not stock |
| Available in the source | The stock read in the tick of the withdrawal, minus the reserve |
| Material assigned to other jobs | There is no reservation. Each withdrawal checks the source again in the tick of the effect |

- **Change of parameters:** the quantity and the reserve of an open job change with `update`. Each change increases the revision. A change based on an old revision is refused. The robot uses the new values at the next step, after 5 seconds at most.
- **Evidence:** test `s2_keep_stock`: initial fill, two consumptions, reserve limit, change of the reserve, restart of the computer.
- **Counterexample:** "at least 64" is a target, not a guarantee. Between two trips the stock can go below the level. While the robot waits for material at the source it does not observe the destination. A job occupies a whole robot, also when the stock is sufficient.
- **Counterexample:** two jobs on the same source each respect their own reserve. They do not coordinate.
- **Discard condition:** a job needs more than one robot, or a robot needs more than one job. Then a common coordination with reservations is necessary.

## D17 — Minimal Uno: proposal, confirmation, report

**Status:** verified with the deterministic provider. Verified one time with the Claude provider and the real service. See D21.

- **Provisional choice:** Uno has no authority of its own. A replaceable provider changes the request into an order in JSON format. The order goes through the same validation as the commands. The result is a proposal. The player confirms it with `/uno confirm`. At the confirmation the runtime validates again.
- **Provider:** the interface is `propose(request, context) → JSON`. The provider runs off the server thread, with a limit of 20 seconds. The context contains only the stations and the robots of the actor.
- **Deterministic provider:** it knows a few sentence patterns in English and Italian. It always gives the same answer to the same request. It serves the tests and lets a player try the path in the game without credentials.
- **Memory:** a proposal that is not confirmed is not a decision and is not saved. After a restart Uno says that it has no proposal. The runtime saves the jobs and their limits.
- **Report:** the text comes from a fixed template. Each number comes from the record of the runtime. The observations have their age. If the robot is not running, Uno says that the data are not the current state.
- **Evidence:** tests `s2_uno_request`, `s2_uno_reference_sentence`, `t18_provider_failures`, `t21_report_with_old_data`, `t22_t23_proposals_and_contact_points`.

Example of a report produced in the test, with the real data of the job:

> I delivered 48 items. 32 remain in 's2uno_source': that is the reserve you asked me to keep. To reach 64 in 's2uno_destination' I need more materials.

- **Counterexample:** the deterministic provider understands only the exact names of the stations. "The furnace chest" works only if a station is named `furnace`.
- **Counterexample:** the confirmation is mandatory also for a clear request. It is friction for an expert player.
- **Not done:** the character of Uno (tone, irony, spontaneous proposals). The report is correct but neutral.
- **One request at a time:** Uno accepts only one written request in progress for each player. To the second it answers "I am still working on your previous request." Reason: a provider can cost money for each request. Evidence: test `t18_claude_provider`.
- **A request without a complete order is not a failure:** a provider signals it with `IllegalArgumentException`. Uno answers "I cannot propose a job: …". Each other error of a provider is a failure: "I cannot interpret the request now (…)". Evidence: tests `t18_provider_failures` and `t18_claude_provider`.
- **Decided by the project owner on 2026-11-12:** the first provider with a language model is Claude. See D21.

## D18 — Contact points: tablet and projector, one native screen

**Status:** decided by the project owner on 2026-11-12. The tablet and the projector exist. See D19 and D20.

- **Decision:** Uno appears as a hologram in the places where a dedicated block is placed. In other places the player talks with Uno at a distance with a tablet. The two contact points open the same native Minecraft screen.
- **Decided order:** first the tablet, then the projector with a minimal hologram, last the graphic polish.
- **Discarded alternative:** a program on a CC: Tweaked computer. Its advantage was no code for the client. A hologram needs that code anyway.
- **Consequence:** the identity of the player is certain at both contact points. The server knows the connected player.

Initial values, **proposed** and not yet confirmed one by one:

| Subject | Initial value |
| --- | --- |
| Range of the tablet | It works at any distance and in each dimension |
| What another player sees in the hologram | The presence of Uno and a general state. No data of the jobs |
| The projector as a place | To place it registers a place of the base |
| Look of the hologram | An original shape. The look of the PK character must not be copied |
| How to get the tablet | Only from the creative inventory or with `/give`. No recipe |

## D19 — The screen is a description that the server builds

**Status:** the logic is verified with an automatic in-game test. The project owner tried the screen in the game, found a defect of the text field, and confirmed the correction.

- **Problem:** a native screen can only be tested by hand. The logic behind the screen must stay testable automatically. Other applications, such as logistics and Blueprint, will need screens.
- **Provisional choice:** the server builds for each player a list of widgets in JSON format. There are five types: text, editable number, bar, row of buttons, text field. The client draws the list and sends back the actions with the text of the fields.
- **Network messages:** there are two. The server sends a view. The client sends an action.
- **No state for open screens:** the client asks for a new view each second. The server keeps no list of screens. To close the screen changes nothing.
- **Checks:** the server builds the actor from the connection of the player. It refuses an action if the player has no tablet in the inventory. Each action goes through the ordinary requests of the runtime. The "Apply" action carries the revision of the job: an old view does not overwrite a more recent change.
- **Evidence:** test `s2_tablet_view`. The test calls the same functions that the network messages call. It does not open a screen.
- **Counterexample:** the text comes from the server in English. The client cannot translate it.
- **Counterexample:** the five widget types are not sufficient for Blueprint. Lists with selection and scene views are missing.
- **Counterexample:** the answers of Uno appear with a delay of up to one second. A view cannot be longer than 32767 characters: many jobs need pagination.
- **Not measured:** the cost of building the view again each second for each open screen.
- **Discard condition:** the second application needs widgets that this list cannot describe without exceptions in the client.

## D20 — The projector: place, general state, hologram without client code

**Status:** the logic is verified with an automatic in-game test. The project owner tried the projector and the hologram in the game and reported that they work.

- **Place:** the player who places the projector registers a place of the base. The runtime saves the place with its owner. To remove the block deletes the place.
- **Contact point:** a click on the projector opens the same screen as the tablet. The screen shows the jobs of the player who opens it, not those of the owner of the projector. An action is accepted if the player has a tablet or is within 8 blocks of a projector.
- **General state:** one time each second the server calculates a state from the jobs of the owner only and writes it in the block state. Each player who sees the block sees that state. It contains no data of a job.

| Block state | When | Colour of the hologram |
| --- | --- | --- |
| `idle` | No job in progress | Light blue |
| `working` | At least one `RUNNING` job | Green |
| `attention` | At least one `WAITING`, `BLOCKED` or `RECOVERY` job | Amber |

- **Hologram:** it is part of the block model. Each state has a model with an animated translucent texture. No client code is necessary, thus there is no drawing to maintain in Java. The block emits light and some sparks.
- **Evidence:** test `s2_projector`: registration of the place, range, three states, save, removal.
- **Counterexample:** the hologram does not move. It does not rotate and does not look at the player. A moving hologram needs client code.
- **Counterexample:** the `attention` state tells the other players that a job of the owner is stopped. It is general data, but it is data.
- **Counterexample:** a projector placed by a mechanism or with `/setblock` has no owner. It stays `idle` and registers no place.
- **Counterexample:** if the player moves away from the projector with the screen open and has no tablet, the server stops answering. The screen stays open with old data.
- **Not done:** Uno does not use the places yet. It only counts them in the screen. "I am coming to check" does not exist yet.
- **Discard condition:** a hologram that moves or shows different shapes is necessary. Then a drawing in Java on the client is necessary.

## D21 — First real provider: Claude, with the official Anthropic SDK

**Status:** decided by the project owner on 2026-11-12. The code of the provider is verified in the game against a false server in place of the API. The provider was verified two times with the real service, on 2026-11-12: four requests written in the test, first with the Italian text of Uno and then with the current English text. The production jar was not loaded on an installed Forge.

- **Decision:** Claude is the first provider with a language model. The `Uno.Provider` interface does not change. The deterministic provider stays: it serves the tests and the players without a key.
- **Activation:** at the server start, if the environment of the process contains `ANTHROPIC_API_KEY`, Uno uses Claude. Otherwise it uses the fixed rules. The server log says which provider is active. A test server (`runGameTestServer`) always uses the fixed rules.
- **Key:** the SDK reads it from the environment. The key is not in the repository, in the saved world or in the log.
- **Model:** `claude-opus-5-5` with a low effort. Reason for the low effort: the task is a simple extraction and the player waits in the game. The model is written in the code. It is not configurable.
- **What leaves the server:** the text of the request, the names of the stations of the player, the numbers of the robots of the player and whether they are free. The name of the player, the positions and the content of the chests do not leave. When Claude is active the screen says so with one line.
- **Answer:** structured output with a JSON schema. The schema contains the fields of the order and a `problem` field. If a necessary value is missing, Claude writes one English sentence in `problem` and Uno reports it. Otherwise the order goes through the ordinary validation and becomes a proposal. The player must confirm it.
- **Refusals:** if the safety classifiers decline a request, the API tries it again on a fallback model that Anthropic chooses (`fallbacks: "default"`, a beta feature). If that model also declines, Uno says that the model declined.
- **Errors:** invalid key, request limit, service error, service not reachable, cut answer. Uno says the reason and creates no proposal. The jobs in progress continue.
- **Cost:** each written request is a paid call on the account of the key. There are three limits: 4096 output tokens for each request, one request at a time for each player, 20 seconds. The report, the confirmation and the jobs do not call the model.
- **Dependencies:** the SDK and 24 other libraries (Kotlin, Jackson, OkHttp) go into the jar with the Jar-in-Jar system of Forge. The file to install is `mindcraft-<version>-all.jar`: about 43 MB, of which 38 are the SDK. The jar without the SDK is 0.13 MB.
- **Excluded libraries:** `org.jetbrains:annotations` and `org.slf4j`. Forge already has them. With a second copy the game does not start: the module system refuses a package that is in two modules.
- **Evidence:** test `t18_claude_provider` (in the game, automatic, with a false API). Examination of the jar: 25 nested jars and 25 entries in the Jar-in-Jar metadata.
- **Evidence with the real service:** test `t18_claude_live` (in the game, automatic, two runs). Claude changes a request in Italian, with numbers written as words and station names that are not exact, into the correct order: stock of 64, reserve 32, from `deposito` to `fornaci`. The deterministic provider cannot read that request. Three requests without a valid order do not become proposals. The sentences of Claude are in `TEST_RESULTS.md`.
- **Defect found in the real test, corrected:** when Claude wrote a `problem`, Uno showed it with the sentence of a failure. Now a request without a complete order has its own sentence. See D17.
- **Repeated:** the real test ran again after two changes: the new sentence for a request without a complete order, and the English text of Uno and of the prompt. It passed. A run costs money to the owner of the key.
- **Counterexample found in the second real test:** the model writes the reason for the player. Two of three sentences used internal words: "keep_stock order" and "the context". The prompt does not forbid them.
- **Counterexample:** an internal error of type `IllegalArgumentException` in a provider would look like an incomplete request. The Claude provider changes the known errors of the SDK into a different type. An unexpected error is not covered.
- **Counterexample:** the real test is two runs of the same four requests. It does not say how Claude answers other requests.
- **Counterexample:** 43 MB for one HTTP call. A call written with the classes of Java adds no weight. Current choice: the official SDK, which follows the changes of the API without work in this project.
- **Counterexample:** on a shared server, each player with a contact point spends on the account of the server operator. The mod has no spending limit.
- **Counterexample:** the key can be in the environment for other programs. The mod uses it without a second consent.
- **Counterexample:** the model writes the sentence in `problem`. It does not come from the record of the runtime. The prompt limits it to the request and the context. No check in the code enforces this.
- **Not verified:** the load of the `-all` jar on an installed Forge. The coexistence with other mods that bring Kotlin or Jackson. A favourable sign: in the development game the same libraries load as modules together with those of Forge.
- **Not done:** a check of the license notices of the 25 libraries inside the `-all` jar. The repository does not contain those libraries. Only a built `-all` jar does.
- **Discard condition:** the `-all` jar does not load on an installed Forge, or its weight becomes a problem for the distribution. Then: a direct HTTP call with the same prompt and the same schema.

## D22 — License: MIT

**Status:** decided by the project owner on 2026-11-12.

- **Decision:** the repository is public and its license is MIT. The text is in [LICENSE](../LICENSE). The mod metadata declares the same license.
- **Scope:** the license applies to the code and the documents of this repository. MindCraft uses only the public API of CC: Tweaked and does not contain its code.
- **Alternatives that the project owner did not choose:** MPL-2.0, and all rights reserved.
