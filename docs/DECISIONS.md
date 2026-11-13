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
- **Design discussion of 2026-11-13:** a turtle addon is planned for Lua customization (D36). It is proposed as an addition: this API stays on each turtle. Thus the discard condition does not apply yet.

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
- **Fact from a code examination, 2026-11-13:** the move step accepts each move that brings the robot nearer to the target. A path with one corner is possible. No test has one. See D28.
- **Verified on 2026-11-13 with an automatic in-game test:** a path with one corner works in the two directions (`a_path_with_one_corner`). See D28.
- **Decided on 2026-11-13, not implemented:** an obstacle becomes a wait, not a block. See D34.
- **Implemented and verified in increment A, 2026-11-13:** an obstacle is a wait. The job continues by itself when the path is free. The T05 evidence above describes the earlier behaviour. See D34 and test `t05_obstacle`.

### D06.1 — A computed route

**Status:** proposed on 2026-11-13, from an idea of the project owner. Deferred: see D27.

- **Idea:** the runtime calculates a route one time and keeps it. The route stays the same until the robot finds that it is wrong.
- **Moment of the calculation:** when Uno proposes a job. A route has two ends, and a station gives only one. The robot can also start from any position (T12). The validation of an order already occurs at that moment. Uno can answer "I find no free route from 'depot' to 'furnace'" before the start.
- **When a route is wrong:** a short obstacle does not make it wrong: the wait of D34 applies. After that, one new calculation. If that also fails, the job goes to `BLOCKED` with the reason. Without this limit, a door that opens and closes makes a loop.
- **Advantage:** the robot always uses the same route. The player learns it and keeps it free. The player teaches nothing, unlike registered waypoints.
- **Counterexample:** the calculation reads blocks that no robot observed. This needs a product rule, for example "Uno knows a route from the moment of its calculation". The new calculation reads the world again when the player is not there.
- **Cost:** a search with limits of volume and of nodes, only in loaded areas and only through air. The route saved in the job. Tests that build their own terrain (D14). The four restart phases again.

## D07 — Inventories are observed only from the dock

**Status:** verified.

- **Provisional choice:** the runtime reads an inventory only when an enrolled robot is on the dock of the station. Each observation has the tick of the reading. Before the first observation the stock is "unknown", not zero.
- **Evidence:** test `s1_transfer` (T02). The `status` command shows "unknown" or the quantity with its age.
- **Reason:** the server could read each chest. The brief asks that the perception of Uno stays tied to its robots.
- **Limit:** between two visits the shown stock is old. The runtime shows the age but does not update the value.

## D08 — Restart based on evidence

**Status:** verified in the controlled case.

- **Provisional choice:** the Lua module keeps no state of the work. It keeps only the ID of its job (D26). At each step it asks the runtime for the state. The next step depends on the observed cargo: with cargo on board the robot goes to deliver, otherwise it goes to withdraw.
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
- **Limit until increment A:** the write of the file was not atomic, as for each `SavedData` of Minecraft. A stop during the write could corrupt the file. Then Minecraft creates empty data and the runtime loses stations, robots and jobs. Since increment A the write is atomic. See D37.
- **Cost:** one file write on the server thread for each transfer. Not measured.
- **Changed in the review of 2026-11-12:** at `resume` the runtime now corrects the delivered total by the load that the robot still holds. See D16.1. The guarantees of this section are not larger than before. The tests cover the same specific cases. The save of the world and the save of the record are still not one atomic operation.
- **Changed in increment A (D37):** the runtime writes the record to a temporary file and then moves that file in the place of `mindcraft.dat` in one step. This applies to the write after each transfer and to the save of the world. The order of the saves of this section does not change. The world and the record are still two separate writes.

## D09 — Pause, cancellation and forced stop

**Status:** verified.

- **Contract:** pause and cancellation apply from the next step. The movement or transfer in progress ends. The cargo stays on board and the state shows it. There is no automatic return.
- **Forced stop:** the runtime can turn off the computer with the command `/computercraft shutdown #<id>`. It also works with a program that does not cooperate.
- **Evidence:** T14, `s0_probe_hard_stop`.
- **Limit:** after a cancellation, a new job for the same item is refused until the player empties the turtle.
- **Decided on 2026-11-13, not implemented:** a new job will adopt the load on board. Then this limit ends. See D34.
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
- **Decided on 2026-11-13, not implemented:** Uno can propose Lua that the owner reads and confirms, on a turtle with the addon. The rule becomes: Uno never runs Lua that the owner did not read and confirm. The managed environment stays the condition for code that runs without a confirmation. See D36.

## D11 — Ownership and permissions

**Status:** verified at the minimum level that S1 requires.

- **Provisional choice:** the server builds the actor from the command source. Each request checks the owner of the robot, of the stations and of the job. An operator (level 2) and the console can do everything.
- **Enrollment:** a player can enroll only a turtle that the same player placed (`ITurtleAccess.getOwningPlayer`).
- **Evidence:** T19.
- **Status command:** `/mindcraft job status <robot>` goes through the same check. A player reads only the player's own robots and jobs. An operator and the console read all of them. A different player gets "Robot #N is not yours." and no data of the job.
- **Defect found in the review of 2026-11-12, corrected:** the status command did not check the actor. Each player could read the stations, the quantities and the impediments of the jobs of the other players.
- **Evidence for the command:** test `t19_status_command`. It failed before the correction. The test sends the real commands with the command source of a player entity that the test system creates, with permission level 0 or 2, and as the console. It is not a real client.
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
- **Decided on 2026-11-13:** work while the player is away is wanted. In the next play session the project owner gives `/forceload` at the setup. The mod still loads no chunk. See D28.

### D13.1 — The satellite

**Status:** proposed on 2026-11-13, from an idea of the project owner. Deferred: see D27.

- **Idea:** an item that the player crafts. One satellite keeps one chunk active, and the player chooses the chunk. More satellites keep more chunks active.
- **Reason:** it answers the limits that the brief asks for chunk loading (6.4). One satellite is one chunk. The number of satellites is the limit. The player decides where the area of Uno grows.
- **Use before it exists:** `/forceload` gives the same effect without code. The sessions with it show if this work is important.
- **Open point 1, the choice of the chunk:** an item that the player uses in the chunk, with a list on the tablet to release it. Or a block on the ground: visible, removed by breaking it, less code, but a bad fit for the name.
- **Open point 2, the borders:** a chunk is 16 by 16 blocks, and its borders are not visible. A corridor can be across two chunks. With one satellite the robot stops at the border when the player is away ("Cannot leave loaded world"). Uno could warn in the proposal: "the dock of 'furnace' is outside the area that I keep active".
- **Open point 3, the recipe:** a recipe starts the survival progression. The mod has no recipe now. At the start the item can come as the tablet (D18).
- **Fact from a code examination:** Forge 47.4.28 has `ForgeChunkManager.forceChunk`. It ties a forced chunk to an owner and saves it with the world.
- **Alternative that was considered:** the projector keeps its own area active. It uses a block, an owner and a test that exist. It does not cover a far place without a projector.
- **Counterexample:** in multiplayer each player loads areas of the server. A limit of the server operator is necessary (D37).

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
- **Composition:** the Lua module `keep_stock` uses four functions of the `transport` module: `begin`, `step`, `goTo` and `active`. It adds only one thing: the robot observes the destination from the dock and waits there while the stock is sufficient.
- **What changed in the abstractions of S1:** the job now has a module and a revision. The runtime calculates the quantity still to carry (`need`). The state `SATISFIED` is new. The primitives `take` and `give` stayed the same. `observe` was added.

Definition of availability:

| Quantity | Definition |
| --- | --- |
| Stock in the destination | The last observation that a robot made from the dock. It has an age |
| Material in transit | The cargo on board the robot. It counts toward the target. It is not stock |
| Available in the source | The stock read in the tick of the withdrawal, minus the reserve |
| Material assigned to other jobs | There is no reservation. Each withdrawal checks the source again in the tick of the effect |

- **Change of parameters:** the quantity and the reserve of an open job change with `update`. Each change increases the revision. A change based on an old revision is refused. The robot uses the new values at the next step, after 5 seconds at most. For a lower quantity while a load is on board see D16.1.
- **Evidence:** test `s2_keep_stock`: initial fill, two consumptions, reserve limit, change of the reserve, restart of the computer.
- **Counterexample:** "at least 64" is a target, not a guarantee. Between two trips the stock can go below the level. While the robot waits for material at the source it does not observe the destination. A job occupies a whole robot, also when the stock is sufficient.
- **Counterexample:** two jobs on the same source each respect their own reserve. They do not coordinate.
- **Discard condition:** a job needs more than one robot, or a robot needs more than one job. Then a common coordination with reservations is necessary.
- **Design discussion of 2026-11-13:** the second behaviour needs more than one job for a robot. The discard condition applies in increment C. See D35.

### D16.1 — A lower quantity while a load is on board

**Status:** verified with automatic in-game tests on 2026-11-12. It corrects a defect that the review of 2026-11-12 found.

- **Problem:** the owner lowers the quantity of a job after the robot took the items. Before the correction the robot delivered its whole load. The delivery went above the new quantity without a word. With a destination that accepted only a part, a transport became `DONE` with a load on board.
- **Meaning of the change:**
  - The change is not retroactive. The delivered items stay in the destination. No item goes back to the source.
  - The runtime starts no new physical operation to remove an excess. The owner did not ask for one.
  - From the change on, a delivery never goes above the quantity. For `transport` the limit is the quantity minus the delivered total. For `keep_stock` the limit is the level minus the stock that the runtime counts in the tick of the delivery.
  - The load above the limit stays on board. The record, the state, the reason and the report of Uno show it.
- **`keep_stock`:** the job is `SATISFIED` and the reason gives the load on board. The robot stays at the destination and uses that load when the stock goes down.
- **`transport`:** the job goes to `BLOCKED`. The reason gives the quantity, the delivered total and the load on board. A transport is never `DONE` while the record has a load on board. The owner decides: raise the quantity and `resume`, or `cancel` and empty the robot.
- **Quantity lower than the delivered total:** nothing more is delivered, also after a `resume`. The report says that the deliveries are more than the quantity.
- **Evidence:** tests `t16_quantity_lowered_with_load`, `t16_quantity_lowered_after_partial_delivery`, `t16_quantity_below_delivered` and `t16_level_lowered_with_load`. They check the real content of the two chests and of the turtle, the state and the report. The four failed before the correction.
- **Counterexample:** `keep_stock` now never fills the destination above the level, also without a change by the owner. If a different actor fills the destination during the trip, the robot keeps the rest on board. Before the correction it delivered the whole load.
- **Counterexample:** while a `SATISFIED` job has a load on board, the robot tries a delivery each 5 seconds. Each attempt writes one line in the server log.
- **Counterexample:** after the owner raises the quantity of a `BLOCKED` transport, a second action is necessary: `resume`.
- **Counterexample:** a load that stays on board after `cancel` follows D09. The owner must empty the turtle by hand.
- **Interaction with a recovery, found by the restart test:** the limit uses the delivered total of the record. After a sudden stop the record can count a delivery that the world does not contain (D08.1). With the first version of this correction, the robot then kept its load on board after `resume`. The `check-crash` phase of T11 failed.
- **Correction of that interaction:** at `resume` from `RECOVERY`, if the world is older than the record and the robot holds more than the recorded load, the runtime subtracts the difference from the delivered total. Then the robot delivers the load that it holds, as it did before the review.
- **No new guarantee:** in the tested case the physical result is the same as before the review: source 40, destination 24, robot 0. Only the recorded total is different: "delivered 24" in place of "delivered 48". After a recovery the totals stay inexact and the state says so. If the owner also lowered the quantity, the limit works with these inexact totals.
- **Evidence for the interaction:** test `t11_server_restart`, phases `crash` and `check-crash`, and test `t11_resume_when_the_world_is_older` in the normal run, with an artificial record. Both failed with the first version of the correction.

## D17 — Minimal Uno: proposal, confirmation, report

**Status:** verified with the deterministic provider. Verified with the Claude provider and the real service. See D21. The review of 2026-11-12 found three defects in this decision. They are corrected: see D23, D24 and D25.

- **Provisional choice:** Uno has no authority of its own. A replaceable provider changes the request into an order in JSON format. The order goes through the same validation as the commands. The result is a proposal. The player confirms it with `/uno confirm`. At the confirmation the runtime validates again.
- **Provider:** the interface is `propose(request, context) → JSON`. The provider runs off the server thread, with a limit of 20 seconds. The context contains only the stations and the robots of the actor.
- **Deterministic provider:** it knows a few sentence patterns in English and Italian. It always gives the same answer to the same request. It serves the tests and lets a player try the path in the game without credentials.
- **Memory:** a proposal that is not confirmed is not a decision and is not saved. After a restart Uno says that it has no proposal. The runtime saves the jobs and their limits. A proposal lives only in one session of the server (D24), and only until the next request of the player (D23).
- **Report:** the text comes from a fixed template. Each number comes from the record of the runtime. The observations have their age. If the robot is not running, Uno says that the data are not the current state. For a job that waits, the sentence comes from the cause that the runtime recorded (D25).
- **Evidence:** tests `s2_uno_request`, `s2_uno_reference_sentence`, `t18_provider_failures`, `t21_report_with_old_data`, `t22_t23_proposals_and_contact_points`.

Example of a report produced in the test, with the real data of the job:

> I delivered 48 items. 32 remain in 's2uno_source': that is the reserve you asked me to keep. To reach 64 in 's2uno_destination' I need more materials.

- **Counterexample:** the deterministic provider understands only the exact names of the stations. "The furnace chest" works only if a station is named `furnace`.
- **Counterexample:** the confirmation is mandatory also for a clear request. It is friction for an expert player.
- **Not done:** the character of Uno (tone, irony, spontaneous proposals). The report is correct but neutral.
- **One request at a time:** Uno accepts only one written request in progress for each player. To the second it answers "I am still working on your previous request." Reason: a provider can cost money for each request. The rule applies to each source of requests. It does not depend on a time limit any more: a request ends when its answer arrives, or when its own time limit gives the answer. Evidence: tests `t18_claude_provider` and `t22_confirm_only_the_shown_proposal`.
- **A request without a complete order is not a failure:** a provider signals it with `IllegalArgumentException`. Uno answers "I cannot propose a job: …". Each other error of a provider is a failure: "I cannot interpret the request now (…)". Evidence: tests `t18_provider_failures` and `t18_claude_provider`.
- **Decided by the project owner on 2026-11-12:** the first provider with a language model is Claude. See D21.
- **Decided on 2026-11-13, not implemented:** a provider will give an action from a closed list, not only an order (D29). Uno will speak first to warn (D30). The model will write the answers to written requests, and this fixed template stays as the fallback (D32).
- **Changed on 2026-11-13, increment A:** the interface is `answer(request, context) → action and tokens`. The action `none` with a reason replaces the `IllegalArgumentException`. Each exception is now a failure. See D29.1.

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
- **Checks:** the server builds the actor from the connection of the player. It refuses an action if the player has no tablet in the inventory. Each action goes through the ordinary requests of the runtime.
- **Identity of the actions:** each action of a job carries the number of the robot and the ID of the job. The runtime refuses an action for a job that the robot does not have any more. "Apply" also carries the revision of the job: the runtime refuses a change that comes from an old view of the same job. "Confirm" carries the number of the proposal (D23).
- **Defect found in the review of 2026-11-12, corrected:** the actions named only the robot. Job A ended and job B started on the same robot, both at revision 0. Then the buttons of an old view of A changed, paused, resumed and cancelled B.
- **Commands:** `/mindcraft job pause`, `resume`, `cancel` and `set` name only the robot. They are about the job that the robot has at the moment of the command.
- **Evidence:** tests `s2_tablet_view` and `t16_actions_of_an_old_view`. The second failed before the correction. The tests call the same functions that the network messages call. They do not open a screen.
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
- **Changed on 2026-11-13, increment A:** the schema and the system text are the ones of the seven actions. The `problem` field is now the reason of the action `none`. The counterexample about `IllegalArgumentException` is corrected: each exception is a failure. The new schema is not verified with the real service. See D29.1.
- **Decided on 2026-11-13, not implemented:** the model becomes a setting, and the start is Haiku 5.5 after a measurement (D33). More data will leave the server: the job record and lines of the diary, without coordinates (D32). By default only an operator will use Claude, with a session limit and a counter on the screen (D33).
- **Changed on 2026-11-13, increment A, ticket #11:** only an operator uses Claude, with a limit of 100 requests for each actor in one server session and a counter on the screen. Each other player gets the fixed rules. See D33.1.

## D22 — License: MIT

**Status:** decided by the project owner on 2026-11-12.

- **Decision:** the repository is public and its license is MIT. The text is in [LICENSE](../LICENSE). The mod metadata declares the same license.
- **Scope:** the license applies to the code and the documents of this repository. MindCraft uses only the public API of CC: Tweaked and does not contain its code.
- **Alternatives that the project owner did not choose:** MPL-2.0, and all rights reserved.

## D23 — A confirmation starts only the proposal that the player sees

**Status:** verified with automatic in-game tests on 2026-11-12, through the commands and the view that the server builds. Not tried in a client. It corrects a defect that the review of 2026-11-12 found.

- **Problem:** Uno proposed job A. The player made request B. B failed or gave no order. Proposal A stayed, and the screen showed the answer to B together with the "Confirm" button. A press on the button started A.
- **Choice:** a request that Uno accepts removes the proposal of the player, before the provider answers. Thus a proposal exists only as the answer to the newest request.
- **While a request is open:** a confirmation starts nothing. Uno answers "I am still working on your request. I have no proposal to confirm yet."
- **Late answers:** Uno accepts one request at a time for each player (D17). Thus an answer always belongs to the newest request of its session. For an answer of an earlier session see D24.
- **On the screen:** the proposal is in words above the "Confirm" button, also when the last sentence of Uno is about something else, for example a report. The action of the button carries the number of the proposal. Uno refuses a number that is not the current one. Thus the button of an older view cannot start a newer proposal.
- **One start:** the confirmation removes the proposal before it starts the job. A second confirmation finds no proposal.
- **Discarded alternative:** keep the old proposal and mark it on the screen as an earlier proposal. It needs more state and more text. The player can make the request again.
- **Evidence:** test `t22_confirm_only_the_shown_proposal`: a proposal, a request that gives no order, then a confirmation with the old button and with the command; a confirmation during an open request; the old button after a newer proposal; a double confirmation. Test `t18_claude_provider`: after a request that fails, the proposal of the request before it does not exist. Both failed before the correction.
- **Corrected test:** `t18_claude_provider` expected that the proposal stayed after the next request failed. That expectation described the defect.
- **Counterexample:** the `/uno confirm` command carries no number. It starts the newest proposal of the player, also when a different contact point made it. The chat can still show an older proposal as its last line.
- **Counterexample:** a wrong request removes a good proposal. This is the cost of one meaning for "Confirm".
- **Not verified:** the new line and the new button in a client.
- **Extended on 2026-11-13, ticket #4:** the rule holds for each kind of proposal: a start, a change, a resume and a cancellation. A different player confirms nothing, with the button of the owner or with the command. Evidence: `a_change_through_the_view`, `a_confirmation_of_older_values`. See D29.3.

## D24 — The temporary state of Uno and of the robots belongs to one server session

**Status:** the logic is verified with two automatic in-game tests on 2026-11-12. The link with the life cycle of the server comes from a code examination. No test changes the world in a client. It corrects a defect that the review of 2026-11-12 found. The part about the robots was not in the review: the project owner asked for it after the review.

- **Problem:** in a single-player game the player leaves world A and opens world B without closing the game. The proposals, the last sentences and the open requests were in static maps, with the player as the key. Nothing cleared them. World B got the proposal of world A. With the same robot number and the same station names, a confirmation started it.
- **Choice:** the temporary state is one session object. Each server start replaces it (`ServerAboutToStartEvent`). Each request keeps the session in which it started. Its answer does nothing if that session is not the current one, or if its server stopped.
- **Requests that are open at the stop:** their answer is dropped. Uno does not call the reply of the request.
- **Facts from the code examination** (`javap` on the jar of Forge 47.4.28 with the official mappings):
  - `IntegratedServer.initServer`, `DedicatedServer.initServer` and `GameTestServer.initServer` call `ServerLifecycleHooks.handleServerAboutToStart`. Thus the event occurs each time a world opens in a client.
  - At the stop, `MinecraftServer.runServer` calls in this order: `handleServerStopping`, `stopped = true`, `stopServer()`, `handleServerStopped`.
  - `MinecraftServer.scheduleExecutables()` is false after `stopped = true`. Then `execute(task)` runs the task immediately, on the thread of the caller. Before the correction, an answer for a stopped server ran on the thread of the provider and wrote in the static maps.
- **Evidence:** test `t23_state_of_an_earlier_session`: the same player, the same robot and the same station names in three sessions. A new session has no proposal and no message, and a confirmation starts nothing. An open request of the old session does not block a request in the new one. Its answer is a valid order: it arrives later and changes nothing. The test failed before the correction.
- **Artificial condition:** the test calls `Uno.newSession()`, the function that each server start calls. It does not start a second server. It runs in its own batch: a new session also removes the proposals of the tests that run at the same time.
- **Not verified:** a real change of world in a client. The branch for a stopped server (`server.isStopped()`): no test stops a server while a request is open.
- **The robots:** three other static maps also lived across worlds: the running robot sessions (`RobotApi.LIVE`), the boot counts (`RobotApi.BOOTS`) and the pending computer restarts (`MindCraft.REBOOTS`). The same server start now clears them. No computer of the new server runs at that moment.
- **Facts from the code examination of CC: Tweaked 1.121.0** (`javap` on its jar):
  - At a server stop, `ServerContext.close` queues the stop of each computer. Then it waits one second in total for the computer threads.
  - A worker of the computer thread runs each stop. The stop calls `shutdown()` on each API of the computer. That call removes the robot session from `LIVE`.
  - Thus `LIVE` is normally empty after a server stop. A session stays only if the limit of one second expires. The log then says "Failed to stop computers under deadline."
- **What a session that stayed could do:** the runtime took it for a running robot. It accepted a job for a robot that did not run. At `resume` after a recovery it read the load of a turtle of the old world.
- **What a pending restart could do:** it sent `/computercraft shutdown` or `turn-on` to the computer with the same number in the next world, for 30 seconds at most.
- **Evidence for the robots:** test `t23_robots_of_an_earlier_session`. It calls the listener of the server start with a new event object. Before the correction it failed with five problems. After it: the old session is not a running robot, the boot count and the pending restart do not exist, a job for that robot is refused, and the old session has no authority.
- **Not verified for the robots:** a server stop in which CC: Tweaked exceeds its limit. No test builds one. The examination says that the correction is necessary only in that case, and for the pending restarts.
- **Discard condition:** two servers run at the same time in one Java process. Then one static session is not sufficient.

## D25 — The runtime records the cause of a wait

**Status:** verified with automatic in-game tests on 2026-11-12. One of the three tested conditions is built artificially. It corrects a defect that the review of 2026-11-12 found.

- **Problem:** the report of Uno guessed the cause of `WAITING` from the load. Without a load on board it always said that the reserve stopped the pickup. The same state also means a full robot, an inventory that is not loaded, or an empty source without a reserve. For a source that no robot observed, the report said "-1 remain".
- **Choice:** the job has a `cause` field with fixed values. The runtime writes it together with the `WAITING` state, from what it observed in that tick. Minecraft saves it with the job. Uno chooses its sentence from this value. Uno does not read the text of the reason to decide.

| Cause | The runtime writes it when | Sentence of Uno |
| --- | --- | --- |
| `SOURCE_AT_RESERVE` | The stock of the source is not above the reserve | With a reserve: "N remain in 'x': that is the reserve you asked me to keep." With less than the reserve: "that is less than the reserve of R". Without a reserve: "Nothing remains in 'x'." |
| `ROBOT_FULL` | Nothing moved, and the robot cannot accept one item | "My inventory is full: I cannot take the items from 'x'." |
| `SOURCE_KEEPS` | Nothing moved, and the robot can accept one item | "'x' has the items, but it does not let me take them." |
| `DESTINATION_FULL` | A delivery moved less than the permitted quantity | "The destination is full. I am waiting for free space." |
| `SOURCE_UNLOADED`, `DESTINATION_UNLOADED` | The area of the inventory is not loaded | "The area of 'x' is not loaded: I cannot see its inventory and I do not know its stock." |
| `NONE` | A record of an older version of the mod | "I am waiting. Recorded reason: …", with the saved text and nothing more |

- **Uncertainty:** the number in a sentence is the last observation of the source. The report still gives the age of each observation, and says when the robot is not running. For an inventory that is not loaded Uno says that it does not know the stock.
- **Also corrected in the runtime:** "the destination is full" came from the load on board alone. Now the runtime writes it only when a delivery moved less than the permitted quantity. "The robot inventory is full" came from "nothing moved". Now the runtime writes it only if the robot cannot accept one item.
- **Evidence:** tests `t21_report_source_at_reserve` (empty source without a reserve, then a source at the reserve, then a save and a load, then a record without a cause), `t21_report_robot_full` and `t21_report_inventory_not_loaded`. The three failed before the correction. Test `s2_uno_request` keeps the sentence of the reference request.
- **Artificial condition:** for the inventory that is not loaded, the test moves the inventory of the station record to a far position. The dock stays. The runtime refuses such a station at the registration. In a normal game the chunk next to a working turtle is loaded, thus this state is rare. The cause is still written by the code of the runtime, during a real pickup attempt of the turtle.
- **Not verified:** `SOURCE_KEEPS` and `DESTINATION_UNLOADED`. No test reaches them. The destination uses the same code as the source.
- **Counterexample:** `BLOCKED` and `RECOVERY` still have only a free text. Uno prints it as the recorded reason and does not interpret it.
- **Changed in increment A:** `BLOCKED` has causes too, and an obstacle is a wait with the cause `PATH_OBSTRUCTED`. See D34. `RECOVERY` still has only a free text.

## D26 — One run of a Lua module executes one job

**Status:** verified with an automatic in-game test on 2026-11-12. The defect was not one of the findings of the review: a test of the review run found it.

- **Problem:** a job ends, and a job with a different module starts on the same robot within one tick. The loop of the old module asked for the state, got the new job, and executed it. A `transport` loop ran a `keep_stock` job: it called `take` before an observation, and the job went to `BLOCKED` ("module error: … The destination stock is unknown"). The defect existed before the review. Test `s2_projector` hit it by chance in run 21.
- **Choice:** `mindcraft.attach` returns the ID of the job. A run of a module keeps that ID. A job is "active" for a run only if it is the job of that run. With a different job the run ends, and the agent starts the module of the new job. The agent gives the job that it saw to the module: the run ends at once if the robot already has a different job.
- **Versions:** the modules are now `transport@2` and `keep_stock@2`.
- **Evidence:** test `t15_next_job_with_another_module`: a transport ends and a `keep_stock` job starts in the same tick. Before the correction the job went to `BLOCKED` with the module error. After it, the job reaches `SATISFIED` with the executor `keep_stock@2`.
- **Limit:** the check is in the Lua modules of the mod. The functions `take`, `give`, `observe`, `impediment` and `moved` carry no job ID. In increment A `impediment` replaced `block` (D34). One tick stays between the last state reading of a run and its next call. In that tick a new job can get a call of the old run, for example its impediment.
- **Proposed for T15 in S3:** each call of a module names its job, and the runtime refuses a call for a different job.

## D27 — Plan after the first review: four increments

**Status:** decided by the project owner on 2026-11-13, in a design discussion. Not implemented.

- **Problem:** S3 of the brief is visual composition and Lua customization. After S2, Uno can do one thing with words: create a job. A change, a pause, a question about the state and a report are buttons or commands. Uno never speaks first. An editor does not change this.
- **Decision:** the work on Uno comes before S3. S3 is divided. The order is A, B, C, D.

| Increment | Content | Decisions |
| --- | --- | --- |
| A — Uno understands you and warns you | Actions from words, the job diary, notices and a summary with fixed templates, an obstacle as a wait, protection of the record, limits on the model calls, a test of a path with a corner | D29, D30, D31, D33, D34, D37 |
| B — Uno has a voice | The sentence of the model with its checks, the choice of the model by a measurement, a load on board in a new job, the low fuel notice | D32, D33, D34 |
| C — Two duties | The `drain` module, parameters that each module declares, a round of duties on one robot | D35 |
| D — Lua together | The turtle addon, the shared library, a custom step, code that Uno proposes | D36 |

- **Exit of A:** the automatic in-game tests pass. The project owner plays one session with a written checklist. The steps and the results are recorded in `TEST_RESULTS.md`. In that session, after the setup, the project owner uses no `/mindcraft` command.
- **Reason for A before B:** each part of A has a test that can be repeated, and A needs no paid run. B needs the fixed templates of A anyway: they are its fallback.
- **In A the model routes and the server speaks.** This is the order of the work. It does not change D32.
- **Cost:** the order of the brief changes. The requirement "both the player and Uno can write and change programs" (brief 2.1) stays without evidence until D. The `Order` record keeps the shape of a transport until C.
- **Counterexample:** the visual level is a candidate for the main level in the brief (2.2). After this plan it has no increment. Its seed is the round of C, described as a list.

Deferred without a date. Each item names the condition that opens it again.

| Deferred | It opens again when |
| --- | --- |
| Visual editor | The round of C exists, and a list cannot describe it any more |
| Managed Lua environment and T17 | Uno must run code without a confirmation of the owner |
| Multiplayer | The project owner wants a second player. See the gates in D37 |
| Chunk loading by the mod (the satellite, D13) | The sessions with `/forceload` show that work without the player near is important |
| Computed route (D06) | The corridor is a problem, or Uno must send a robot to a place |
| Inspection round | "I am coming to check" becomes necessary |
| Reserve that belongs to a station | Two robots use the same station |
| Spontaneous voice of the model, remedies that Uno proposes, conversation context | A spending limit exists that the server operator can set (D33) |
| The `-all` jar on an installed Forge, recipes, the language of the voice | The project owner wants to play outside the development client, or the survival progression starts |

## D28 — The next play session

**Status:** decided by the project owner on 2026-11-13. The facts below come from code examinations. Since 2026-11-13 an automatic test supports one of them: the path with one corner. No test supports the others.

- **Decision:**
  - One player: the project owner. The development client (`gradlew runClient`). The Anthropic key in the environment of the game.
  - A normal world. A prepared corner of a base: two chests and a corridor that the player keeps free.
  - No path search. The rule is "the corridor is part of the base".
  - The project owner gives `/forceload` one time at the setup, for the chunks of the corridor. The mod loads no chunk.
  - A second player and the installed jar are deferred (D27).
- **Facts from code examinations:**
  - The move step of `transport.lua` is not "straight only". It tries x, then z, then y, and accepts each move that brings the robot nearer to the target. Thus a path with one corner, or a stair, is possible. A path that needs a move away from the target is not.
  - The server configuration of the test server has `can_push = true`. The comment of the option says that a turtle pushes an entity out of the way if there is space.
  - The move command of CC: Tweaked contains the message "Cannot leave loaded world".
  - `RestartTests` keeps its chunk loaded with `setChunkForced`. T11 shows that the robot works there without a connected player.
- **Verified on 2026-11-13 with an automatic in-game test:** a path with one corner, in the two directions. Test `a_path_with_one_corner`.
  - Stone walls leave one L-shaped corridor between the docks. The robot crosses the corner to the source, then again to the destination.
  - Toward the source the x axis is walled. The move step tries z and continues. Toward the destination the order x, then z fits the corridor.
  - The transport reaches `DONE`: 40 in the source, 24 in the destination, 0 in the robot. The walls are still in place.
  - No correction of the move step was necessary. With a move step that tries only the first axis, the same test fails: the job goes to `BLOCKED` before the corner.
- **Not verified:** a stair, that is a path that also changes y. More than one corner. A player on the path. `/forceload` in a client with one player. A robot at the edge of the loaded area.
- **Out of scope:** a path that needs a move away from the target. The move step cannot make it. The README says which paths work.
- **Counterexample:** `/forceload` needs commands enabled. The mod does not know the forced area. If the player forgets it, the robot stops as before and the job stays `RUNNING`.
- **Counterexample:** if the key is in the environment of the user, the development client uses Claude with no other step. Then each written request is a paid call (D21).

## D29 — Uno acts on words through a closed list of actions

**Status:** decided by the project owner on 2026-11-13. Increment A. Implemented: the contract of a provider (D29.1), the pause and the report (D29.2), and the change, the resume and the cancellation (D29.3) are verified with automatic in-game tests. Not verified with the real Claude service.

- **Problem:** with the code of S2, three requests fail.
  - "Leave only 16 in the depot", for an open job. The provider can only give a new order, and the robot is busy. Uno refuses.
  - "How is the furnace?" The provider answers that this is not an order.
  - A robot stops. Uno says nothing. Only the colour of the hologram changes.
- **Decision:** a provider gives one action from a closed list, with its parameters: start, change, pause, resume, cancel, report, none. The server finds the job from the station name or the robot number. The provider already gets these (D17).
- **Confirmation:** Uno does immediately only an action that stops or reads: pause and report. Each other action becomes a proposal. The server writes the text of each proposal, never the model. The player confirms as in D23.
- **Proposed detail:** a proposal that is about a job carries the job ID and the revision, as the actions of the screen do (D19).
- **Buttons and commands stay.** They are the control surface when the provider fails.
- **Cost:** each change needs two steps. D17 already names this friction.
- **Change:** in D17 a provider gives only an order. D23 holds: one proposal for each player, the newest.
- **Implemented in part:** the contract of a provider. See D29.1. Uno acts on `start` and `none` only. The other five actions are not implemented.
- **Implemented in part, ticket #3:** Uno also acts on `pause` and `report`, and finds the job on the server. See D29.2. The actions `change`, `resume` and `cancel` are not implemented.
- **Implemented, ticket #4:** Uno also acts on `change`, `resume` and `cancel`. Each becomes a proposal. The proposed detail above is implemented: spec #1 decided it. See D29.3.

### D29.1 — The contract of a provider: one action and its tokens

**Status:** implemented and verified with automatic in-game tests on 2026-11-13, with the fixed rules, with providers written in the tests and with a false server in place of the API. Not verified with the real Claude service (D33). Increment A, ticket #2. The flat schema of the Claude provider is a choice of the implementation: **proposed**, not confirmed by the project owner.

- **Contract:** a provider answers `answer(request, context) → Answer`. The answer holds one action as JSON, the input tokens and the output tokens. The context does not change: the station names of the player, the robot numbers and the "free" flag (D21).
- **Actions:** the closed list of D29. Each action has its parameters. A field that the action does not use is null or absent.

| Action | Parameters |
| --- | --- |
| `start` | The order of S2: module, robot, from, to, item, quantity, reserve |
| `change` | A target, and a new quantity, a new reserve or both. Null is a value that does not change |
| `pause`, `resume`, `cancel`, `report` | A target: a robot number, a station name, or nothing |
| `none` | One short reason for the player |

- **Tokens:** the Claude provider reads them from the usage data of the answer. The fixed rules report zero. Uno does not use them yet: the counter is a later part of increment A (D33).
- **What Uno does in this part:** `start` becomes a proposal, as in S2. `none` gives its reason: "I cannot propose a job: …". Each other action gets a fixed sentence, for example "I cannot pause a job from a written request yet. Use the buttons of the screen or the commands. I changed nothing." Uno creates no proposal for it.
- **Validation in Uno:** Uno reads each answer and does not trust it. An action outside the list, a `start` without a complete order and a `none` without a reason get "I did not get a valid configuration. I created no proposal." Uno ignores the case of the action name: structured outputs do not guarantee the case of an enum value.
- **Length limit:** 200 characters for the reason, as in S2. The limit moved from the Claude provider to Uno. Thus it applies to each provider.
- **Failures:** each exception of a provider is a failure: "I cannot interpret the request now (…)". Before, an `IllegalArgumentException` meant a request without a complete order. This corrects a counterexample of D21: an internal error of that type looked like an incomplete request.
- **Fixed rules:** they give `start` or `none`, with the same orders and the same reasons as before. The word patterns of the other actions are later parts of increment A.
- **Claude provider:** a new system text for the seven actions and a new output schema. The model stays `claude-opus-5-5` with a low effort (D21).
- **Proposed, the flat schema:** one object with ten fields. Each field is required, thus the model writes each one. A field that the action does not use is null: eight fields have the type "value or null". The documentation of structured outputs permits 16 parameters with such a union type.
- **Alternative not chosen:** one object for each action, with `anyOf`. It is more exact, but its grammar is more complex to compile.
- **Evidence:** tests `s2_uno_reference_sentence`, `t18_provider_failures`, `t18_claude_provider` and the new test `a_provider_answers_with_an_action`. The four failed before the implementation and passed after it. See "Increment A — The provider answers with an action" in TEST_RESULTS.md. The tests of the S2 flow pass with the same sentences as before: `s2_uno_request`, `s2_tablet_view`, `t22_confirm_only_the_shown_proposal`, `t22_t23_proposals_and_contact_points`, `t23_state_of_an_earlier_session`.
- **Not verified:** the new schema and the new system text with the real service. The test `t18_claude_live` compiles with the new contract. It was not run: a run costs money and needs the consent of the project owner. Its four requests are the requests of S2: they test only `start` and `none`.
- **Counterexample:** the real service can refuse the schema, for example with "Schema is too complex for compilation". Then each request to Claude fails, and Uno says that the model service answered with error 400. The fixed rules do not take over.
- **Counterexample:** until the next parts, "pause the furnace job" gets two different answers. With Claude it gets the fixed sentence of `pause`. With the fixed rules it gets "I cannot propose a job: the quantity is missing."
- **Changed on 2026-11-13, ticket #3:** `pause` and `report` do not get the fixed sentence any more. The fixed rules read them. The counterexample above holds now only for `change`, `resume` and `cancel`. See D29.2.
- **Changed on 2026-11-13, ticket #4:** no action gets the fixed sentence any more. The fixed rules read `change`, `resume` and `cancel`. The counterexample above does not hold any more. See D29.3.

### D29.2 — Report and pause by words

**Status:** the rules below marked "decided" are decided by the project owner on 2026-11-13: spec #1, sections "How Uno finds the job" and "Immediate actions and proposals", and its further note 2. They are implemented and verified with automatic in-game tests on 2026-11-13, with the fixed rules and with providers written in the tests. Not verified with the real Claude service. Increment A, ticket #3. The choices marked "proposed" are choices of the implementation, not confirmed by the project owner.

- **Decided, the search of the job:** Uno finds the job on the server. It looks only at the jobs that the actor owns, with the same check of the actor as each other request.
  - From the robot number, if the request gives one.
  - From a station name, if exactly one open job of the actor uses that station as source or as destination.
  - With no target, if the actor has exactly one open job.
- **Decided, each other case:** Uno says how many jobs fit and asks for the robot number. It does nothing and creates no proposal. The sentences:

| Case | Sentence of Uno |
| --- | --- |
| A station of two open jobs | "2 of your open jobs use the station 'furnace'. Tell me the robot number of the job. I did nothing." |
| A station of no open job | "None of your open jobs uses the station 'mine'. Tell me the robot number of the job. I did nothing." |
| No target, two open jobs | "You have 2 open jobs. Tell me the robot number of the job. I did nothing." |
| No target, no open job | "You have no open job. Tell me the robot number of the job. I did nothing." |
| A robot without a job of the actor | "You have no job on robot #9. Tell me the robot number of the job. I did nothing." |

- **Decided, the report:** it runs immediately. A request about one job gives the report sentence of that job only: the same line as in the report on all jobs. A report request without a target gives the report on all jobs of the actor, as the "Report" button does.
- **Decided, the pause:** it runs immediately, without a proposal. It is the ordinary request of the runtime, with the robot and the job ID. Uno says: "Job 3 on robot #5 is paused. The cargo stays on board." A refusal of the runtime becomes the sentence of Uno: "I cannot pause the job: Job 3 is not running (PAUSED)."
- **Decided, T19:** a different player cannot pause the job and gets no data of it. The search looks only at the jobs of the actor. Thus the sentence for the job of a different player is the sentence for a job that does not exist, and it does not say whether the robot or the station exists.
- **Proposed, a robot number names the job that the robot has now, open or not.** Thus "how is robot 5?" also reports a job that is `DONE` or `CANCELLED`. A pause of such a job gets the refusal of the runtime: "Robot #5 has no open job." A station name and a request without a target look only at open jobs, as the spec says.
- **Proposed, the robot number wins:** if a request gives a robot number and a station, Uno uses the robot number and ignores the station.
- **Proposed, the search ignores the case of a station name.** A model does not guarantee the case of a value. An empty station name is the same as no station.
- **Proposed, the words of the fixed rules:** a request that names two stations of the player stays a start (decided in the spec). With one station or none, the first action that fits decides: first the pause, then the report. Pause: `pause`, `pausa`, `sospendi`. Report: `report`, `status`, `how is`, `how's`, `how are`, `rapporto`, `stato`, `come va`, `come vanno`, `come sta`, `come stanno`. The job: "robot N", or the name of one station. A request without these words goes to the rules of a start, as before. Thus the reasons of "none" do not change. The README lists the words.
- **The provider calls:** a written report request goes to the provider, as each written request. With Claude it is a paid call. The "Report" button and `/uno report` call no provider.
- **No change of the runtime and of the Lua modules.** The pause uses `MindRuntime.control` with the job ID, as the "Pause" button does.
- **Evidence:** tests `a_fixed_rules_report_and_pause`, `a_report_and_pause_by_words`, `a_t19_words_of_another_player`, `a_report_and_pause_through_the_view`, and the changed test `t18_claude_provider`. Each failed with the code before the change and passed after it. See "Increment A — Report and pause by words" in TEST_RESULTS.md.
- **Not verified:** the real Claude service with a pause or a report. The schema and the system text of the Claude provider did not change in this ticket. A run of `t18_claude_live` costs money and needs the consent of the project owner. Its requests test only `start` and `none`.
- **Counterexample:** "How is the furnace?" after the furnace job is `DONE` gets "None of your open jobs uses the station 'furnace'." The player must give the robot number.
- **Counterexample:** the fixed rules read a robot number only after the word "robot". "Pause 5" is a pause without a target. With one open job, Uno pauses that job, also if it is not on robot #5. A pause is reversible with `resume`.
- **Counterexample:** a start request with one station and a word of an action is that action. "Report the coal from the depot" is a report about the depot job.

### D29.3 — Change, resume and cancel by words

**Status:** the rules below marked "decided" are decided by the project owner on 2026-11-13: spec #1, section "Immediate actions and proposals", and its further note 1. They are implemented and verified with automatic in-game tests on 2026-11-13, with the fixed rules and with providers written in the tests. Not verified with the real Claude service. Not tried in a client. Increment A, ticket #4. The choices marked "proposed" are choices of the implementation, not confirmed by the project owner.

- **Decided, proposals:** a start, a change, a resume and a cancellation become a proposal. A pause and a report run immediately (D29.2). One proposal for each player, the newest, with a number (D23). A proposal belongs to one server session and is not saved (D24). The search of the job is the one of D29.2.
- **Decided, what a proposal holds:** its request for the runtime. A start holds the order. A change holds the robot, the job ID, the revision and the two values after the change. A resume and a cancellation hold the robot and the job ID.
- **Decided, the text:** the server writes the text of each proposal, never the model. The screen shows the same text above the "Confirm" button. Sentences from the tests:

| Proposal | Sentence of Uno |
| --- | --- |
| Change of the reserve | "Proposal: change job 6 on robot #7 (carry minecraft:coal from 'a_change_source' to 'a_change_destination'). Quantity to carry: 24, unchanged. Reserve to leave in 'a_change_source': from 20 to 6. I have done nothing yet. Confirm with /uno confirm." |
| Resume of a job in `RECOVERY` | "Proposal: resume job 5 on robot #22 (carry minecraft:coal from 'a_resume_source' to 'a_resume_destination'). The job needs a check: the runtime accepts the load that it observes in the robot, and the totals can be inexact. I have done nothing yet. Confirm with /uno confirm." |
| Cancellation | "Proposal: cancel job 5 on robot #22 (carry minecraft:coal from 'a_resume_source' to 'a_resume_destination'). The cargo stays on board the robot. I have done nothing yet. Confirm with /uno confirm." |

- **Decided, a value that the request does not give:** it stays as it is. The text says "unchanged" for it.
- **Decided, the confirmation:** it sends the ordinary request of the runtime: `update` with the job ID and the revision, or `control` with the job ID. The runtime validates again. A refusal of the runtime becomes the sentence of Uno. The tests check these sentences exactly. The numbers below are examples.

| Case | Sentence of Uno |
| --- | --- |
| The change after a change by a button or a command | "I cannot change the job: Job 3 changed. Read it again." |
| The change after the robot got a different job | "I cannot change the job: Robot #7 has job 8 now, not job 3. Read it again." |
| The cancellation after the robot got a different job | "I cannot cancel the job: Robot #7 has job 9 now, not job 8. Read it again." |

- **Decided, a start for a busy robot:** it stays refused, with the number of the job: "I cannot propose this job: Robot #7 already has job 3 (WAITING)." This was already the behaviour of S2.
- **Decided, the buttons, the number fields and the commands do not change.** The number fields show the values of the record until the confirmation.
- **Proposed, Uno checks before the proposal:** the runtime has two new functions, `checkUpdate` and `checkControl`. Each is the validation part of `update` or `control`, without the change. Uno calls it before the proposal, as it calls `check` before the proposal of a start. `update` and `control` call it too. Thus a resume of a running job gives "I cannot propose to resume the job: Job 3 is already running." and no proposal. A cancellation of a closed job gives "I cannot propose to cancel the job: Robot #7 has no open job."
- **Proposed, a change without a new value:** no proposal. "I cannot propose a change: the request gives no new quantity and no new reserve. I created no proposal."
- **Proposed, the button:** the action of the "Confirm" button names the number of the proposal, as since D23. Its label stays "Confirm". The number is not visible on the screen. The text of the proposal is next to the button.
- **Proposed, the words of the fixed rules:** with one station or none, the first action that fits decides, in this order: resume, cancel, pause, change, report. Resume: `resume`, `continue`, `riprendi`, `continua`. Cancel: `cancel`, `annulla`, `cancella`. Change: the first number after `quantity`, `level`, `quantità` or `livello` is the new quantity. The first number after `leave`, `reserve`, `lascia` or `riserva` is the new reserve. A change needs at least one of the two. The number after "robot" names the job: it is never a new value. The resume comes before the pause: "Riprendi il lavoro in pausa" is a resume. The README lists the words.
- **The runtime changed:** only the two check functions. The refusals of the buttons and of the commands have the same text as before. The Lua modules did not change. The four restart phases ran after the change, and again after the merge with tickets #8 and #11.
- **From a code examination, after the merge with ticket #8:** the confirmation of a resume or of a cancellation calls `control`, as the buttons do. Thus it writes the diary entry as a request of the owner, and it gives no notice (D30). No test checks the notices of an action by words.
- **Evidence:** tests `a_fixed_rules_change_resume_cancel`, `a_change_by_words`, `a_confirmation_of_older_values`, `a_resume_and_cancel_by_words`, `a_change_through_the_view`, and the changed test `a_provider_answers_with_an_action`. See "Increment A — Change, resume and cancel by words" in TEST_RESULTS.md.
- **Not verified:** the real Claude service with a change, a resume or a cancellation. The schema and the system text of the Claude provider did not change in this ticket. The proposal and the button in a client.
- **Counterexample:** a resume proposal does not hold the state of the job. If a `BLOCKED` or `PAUSED` job goes to `RECOVERY` before the confirmation, the confirmation accepts the observed load, but the text did not say so. No test builds this case.
- **Counterexample:** a cancellation holds no revision. A change by a button between the proposal and the confirmation does not stop the cancellation. The spec asks for the revision only in a change.
- **Counterexample:** the fixed rules read a change in a request with one station and "leave N". "Carry 24 coal to the furnace, leave 8" is a change of the reserve of the furnace job, not a start. The proposal shows it. Nothing changes before the confirmation.
- **Counterexample:** the fixed rules take the first number after the word. "Change the quantity and leave 16" gives 16 for the quantity and for the reserve. "Annulla la pausa" is a cancellation. In both cases the proposal shows what the confirmation does.
- **Counterexample:** a change to the values that the job already has is a proposal. The text says "unchanged" for both values. Its confirmation increases the revision. Not tested.

## D30 — Initiative: Uno warns

**Status:** decided by the project owner on 2026-11-13. Increment A. The numbers are initial values, **proposed**. Implemented in part: the notices for `BLOCKED`, `RECOVERY` and `DONE` are **verified** with automatic in-game tests on 2026-11-13 (ticket #8). See "Notices, as built" below. The summary at an opening is **verified** with automatic in-game tests and the four restart phases on 2026-11-13 (ticket #10). See "Summary, as built" below. The notice for a long wait is not implemented.

- **Decision:** Uno speaks first only to warn. It proposes no remedy. It starts no action by itself, except the wait of D34.
- **Attention rule:**

| Event | Notice |
| --- | --- |
| A job goes to `BLOCKED`, `RECOVERY` or `DONE` | One line in the chat of the owner |
| A job stays `WAITING` for 5 minutes | One line, one time |
| `RUNNING`, `SATISFIED` | None |

- **Summary:** when the owner opens the screen, Uno gives a summary of the events since the owner last opened a contact point. The source is the diary (D31).
- **Text:** each notice and the summary come from a fixed template, with the data of the record. They call no model (D33). Thus they also work without a key, and when the service is down.
- **Deferred:** remedies that Uno proposes. Actions that Uno starts by itself. See D27.
- **Counterexample:** a `keep_stock` job changes often between `RUNNING`, `WAITING` and `SATISFIED`. A notice for each change is not acceptable. This is the reason for the rule.
- **Counterexample:** after B, Uno has two voices. The notices are flatter than the answers.

Notices, as built (ticket #8). The choices of detail are **proposed**: the project owner did not confirm them one by one.

| Entry of the diary | Sentence of the notice |
| --- | --- |
| `BLOCKED`, `OUT_OF_FUEL` | "Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') is blocked: the robot has no fuel." |
| `BLOCKED`, `SOURCE_NO_INVENTORY` or `DESTINATION_NO_INVENTORY` | "… is blocked: there is no inventory at 'depot'." The station of the cause |
| `BLOCKED`, `LOAD_ABOVE_QUANTITY` | "… is blocked: the quantity of the job is reached, and 54 stay on board." |
| `BLOCKED`, `NONE` | "… is blocked. Recorded reason: …" |
| `RECOVERY` | "… needs a check: I cannot say how it went. Recorded reason: …" |
| `DONE` | "… is done: 24 delivered." |

- **Attention rule:** an entry of the diary (D31) with the state `BLOCKED`, `RECOVERY` or `DONE`, that the runtime observed. An entry that a request of an actor made is not selected: a start, a pause, a resume or a cancellation. This holds also when the resume of a `RECOVERY` completes the job, and when an operator gives the request.
- **No remedy:** the sentences do not say "give fuel" or "resume". The report still says it, as before.
- **`RECOVERY`** has no cause (D25). The sentence gives the recorded reason. The runtime writes it without coordinates: "the robot holds N, the record says M", or "the world is older than the record of this job".
- **One exit:** `Notices.exit` gets the server, the owner and the text. In the game it sends the system message "Uno › …" to the owner, if the owner is connected. If not, nothing is sent and nothing waits: the diary keeps the event for the summary.
- **Moment:** the notice leaves in the tick of the change, from `MindRuntime.set`, the one function that sets the state of a job.
- **Console:** a job of the console has no player to warn. It gives no notice. Its diary entries exist.
- **Evidence:** tests `a_notices_for_blocked_recovery_and_done`, `a_no_notice_for_a_resume_that_completes_the_job`, `a_no_notice_for_the_ordinary_cycle`, `a_no_notice_for_a_console_job`, `a_notices_call_no_provider`, and the notice checks in `t04_destination_removed`, `t04_source_removed`, `t06_impediment_of_an_unknown_kind` and `t16_quantity_lowered_with_load`. A test replaces the exit with one that records each notice. See "Increment A — the job diary and the notices" in `TEST_RESULTS.md`.
- **Not verified:** a real line in the chat of a client. No test has a connected player. The code of the game exit is examined only. It is a step of the checklist of the play session.
- **Counterexample:** the load of the record can find a `RECOVERY` at the server start ("the world is older than the record"). Its notice leaves before a player can be connected. Thus it gives no chat line. Only the summary can show it.

Summary, as built (ticket #10). The rules of spec #1, section "The summary", are decided by the project owner (2026-11-13). The limit of 10 sentences is an initial value of the spec: **proposed**, to review after the play session. The choices of detail marked "proposed" are choices of the implementation: the project owner did not confirm them.

- **Opening:** the use of the tablet and the click on a projector call `Net.open`. It calls `UnoView.open`, then sends the view. The view that the screen asks for each second, and the view after a button, go through `Net.sendView`: they are not an opening.
- **Last opening:** `MindRuntime.open(actor, now)` records the game time of the overworld for the actor and returns the time of the opening before it. The record of each player is in `mindcraft.dat`. A record of an older version loads without openings: the next opening of each player is a first opening.
- **Events:** the entries of the diary that the actor reads (`MindRuntime.diary`), newer than the last opening, that the attention rule selects (`Notices.selected`). Each line is the sentence of the notice (`Notices.sentence`). Thus a new kind of selected entry, for example the long wait of ticket #9, joins the summary without a change of the summary.
- **Check of the actor:** the diary that the actor reads. It is the check of the list of jobs (`Actor.owns`). An operator also gets the events of the jobs of the other players and of the console.
- **Sentence of Uno:** the summary goes through `Uno.say`, as each answer. With no line to say, `Uno.say` is not called: Uno keeps its sentence.
- **No provider:** the code of the summary has no reference to a provider. A test with a provider that counts its calls confirms it.

| Part | Text |
| --- | --- |
| First line, first opening | "From the start of my memory:" |
| First line, each later opening | "Since you last opened this screen:" |
| Removed entries newer than the last opening | "My memory starts 3 h ago. I do not remember older events." |
| More than 10 events | "I do not show 2 older events." Then the 10 newest |
| One event | "12 min ago: Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') is blocked: the robot has no fuel." |

- **Proposed, the ages:** the game time between the entry and the opening, in seconds, minutes, hours or days, rounded down. The game time does not advance while the server is stopped. An entry newer than the world, after a crash (D08.1), has the age "At an unknown time".
- **Proposed, "where the memory starts":** the age of the newest removed entry (`MindRuntime.forgotten`). For an operator, the newest of all owners.
- **Proposed, removed entries without a selected entry:** the summary has the first line and the line about the memory. Reason: the removed entries can be events that needed attention. The spec says "With no selected entry, Uno keeps its sentence". This choice reads that rule as "with nothing to say".
- **Proposed, the boundary:** an event is in the summary if its game time is later than the game time of the last opening. An event in the same tick as an opening, after it, is not in the next summary. The owner had the screen open in that tick, thus connected: the notice of that event reached the chat. An operator does not get the notices of other owners: such an event of another owner is lost for the summary of the operator.
- **Proposed, a kept summary:** at an opening without an event, Uno keeps its sentence also when that sentence is the summary of an earlier opening. Then the screen shows that summary again, with the ages of its moment, until the next answer of Uno. The spec asks that the second opening shows no summary: it builds none.
- **Evidence:** tests `a_summary_at_an_opening`, `a_summary_shows_the_10_newest`, `a_summary_says_where_the_memory_starts`, `a_t19_summary_of_another_player` and `a_summary_after_a_save_and_a_load`. The four restart phases check the summary of an operator after the restart. See "Increment A — the summary at an opening" in `TEST_RESULTS.md`.
- **Not verified:** the summary on the screen of a client: the length of 12 lines, and a line break in the text of Uno. The tests call the methods of the tablet and of the projector with a fake player of Forge, without a client. It is a step of the checklist of the play session.
- **Counterexample:** the ages do not change while the screen is open. After 10 minutes, "2 min ago" is wrong by 10 minutes.
- **Counterexample:** a player who opens the screen often sees only the events since the last opening. The other events are only in the diary, and no screen shows it.

## D31 — Memory: the job diary

**Status:** decided by the project owner on 2026-11-13. Increment A. Implemented and **verified** with automatic in-game tests and the four restart phases on 2026-11-13 (ticket #8). See "Diary, as built" below. The size limit is an initial value of spec #1, **proposed**.

- **Problem:** the record keeps one job for each robot: the last. A new job replaces the job before it (`MindRuntime.start`). Uno has no history. The summary of D30 has no source.
- **Decision:** the runtime writes a diary: each change of the state of a job, with the time, the cause and the quantities. Minecraft saves the diary with the world. The diary has a size limit. When old entries are removed, Uno says where its memory starts.
- **Reason:** it is a verifiable memory in the sense of the brief (2.4). Each line comes from the runtime.
- **Deferred:** permanent decisions, for example a reserve that belongs to a station. The context of the conversation for the model.
- **Cost:** the owner must write a complete sentence for each change. Uno does not know the sentence before it.
- **Size limit:** 200 entries for each owner. It is the initial value of spec #1, to review after the play session.

Diary, as built (ticket #8). The choices of detail are **proposed**: the project owner did not confirm them one by one.

- **Where:** `MindRuntime.set`, the one function that sets the state of a job. It compares the new state and cause with the old ones. A change writes one entry. The same state with the same cause writes none: a robot that tries again reports the same state, and only the reason can change.
- **The start of a job** writes its first entry, `RUNNING`. Thus the history of a job begins with its start.
- **Content of an entry:** the game time of the overworld, the owner, the job ID, the robot, the two station names, the item, the state, the cause, the recorded reason, a "requested" flag, and the quantities at that moment: quantity, reserve, delivered, on board.
- **The recorded reason** is not in the list of spec #1. The sentence of a `BLOCKED` without a cause and of a `RECOVERY` needs it.
- **"Requested"** says that a request of an actor made the change: the start, a pause, a resume or a cancellation. The other changes come from what the runtime observed. The attention rule of D30 reads it.
- **Limit:** above 200 entries of one owner, the runtime removes the oldest entries of that owner only. For each owner it records how many entries it removed, and the game time of the newest removed one. The summary (ticket #10, D30) says from this where the memory of Uno starts.
- **Read:** `MindRuntime.diary(actor)` and `MindRuntime.forgotten(actor)` give only the data of the owners that the actor owns: the owner, an operator and the console (D37). In this ticket no screen and no command shows the diary. Since ticket #10 the summary at an opening shows the selected entries.
- **Saved:** the diary and the removed counts are in `mindcraft.dat`, with the jobs. A record of an older version has no diary: it loads with an empty one.
- **Evidence:** tests `a_diary_of_a_job` and `a_diary_limit`. The four restart phases check the diary after a real stop and after a halt of the process. See "Increment A — the job diary and the notices" in `TEST_RESULTS.md`.
- **Cost:** each write of the record contains the whole diary, also the write after each transfer (D08.1). No measurement.
- **Counterexample:** a stock job writes two entries or more for each refill (spec #1). Then the memory of a busy job is short.
- **Limit:** a copy of the record that `load` makes outside the server writes its entries with the time 0 and sends no notice. Only the tests make such copies.

## D32 — The model speaks, the server checks

**Status:** decided by the project owner on 2026-11-13. Not implemented. Increment B.

- **Decision:** the model writes the sentences of Uno when the player writes to Uno.
- **Alternative that the project owner did not choose:** the model gives only the action, and the server writes each sentence from fixed templates. It was the recommendation in the discussion. Its advantages: no sentence without evidence, no new data outside the server, tests that can be repeated.
- **One call:** the provider gives the action (D29), its parameters and one sentence.
- **Check of the numbers:** each number in the sentence must be one of the numbers that the server sent. If the check fails, Uno uses the fixed template. The same applies if the model does not answer.
- **Uncertain states:** `RECOVERY`, a robot that is not running, an old observation. The server does not send the uncertain numbers to the model. It sends "uncertain result" and the reason. The fixed sentence about the uncertainty comes first. The sentence of the model follows.
- **When the model speaks:** only as the answer to a written request. Notices and the summary are fixed templates (D30).
- **Data that leave the server:** the record of the jobs of the player (parameters, state, cause, totals, observed stocks with their age) and lines of the diary. No coordinates. For this, `BLOCKED` gets causes with fixed values (D34). This changes D21. The line on the screen about Claude changes too.
- **Limit of the check:** it stops an invented number. It does not stop a wrong description of a state with true numbers. Example: a job is `BLOCKED` with 24 items on board and a quantity of 64. The model writes "All done, I delivered 64". The check passes. The panel of the job next to the sentence shows the state.
- **Cost:** a test cannot repeat the sentences of the model. Automatic tests use providers written in the tests. A test with the real service takes a sample of the T21 case.
- **Cost:** the voice has no memory of the sentence before it (D31). It must say so and must not invent.
- **Open:** the language of the voice. D15 says English.
- **Review condition:** the tests with the real service show sentences that contradict the record.

## D33 — The model and its cost

**Status:** decided by the project owner on 2026-11-13. The limits on the calls of increment A are implemented: see D33.1. The rest is not implemented. The prices are a reference with a date. The cost of a request is an estimate, not a measurement.

The model. Increment B.

- **One model:** one model routes and speaks. The start is Claude Haiku 5.5.
- **Measurement:** the test with the real service runs on Haiku 5.5, Sonnet 5.5 and Opus 5.5 with the same requests. It compares the orders, the sentences and the times. Each run costs money and needs the consent of the project owner.
- **Setting:** the model becomes a setting. It is not a constant in the code any more. Each request writes the used tokens in the server log.
- **In A:** the model of D21 stays. The action list of D29 is not verified with the real service until B.
- **By kind of operation:** when Uno writes code (D36), a different model can do that operation. The kind of operation selects the model. No model judges the difficulty of a request.
- **Discarded for now:** a choice of the model by the difficulty of each request. It needs a judge: one more call, or more rules.

Reference prices, from the model table of the Anthropic API documentation dated 2026-11-03:

| Model | Input, $ for 1 million tokens | Output, $ for 1 million tokens | Refusal |
| --- | --- | --- | --- |
| Opus 5.5 (D21) | 4.00 | 20.00 | Server-side fallback |
| Sonnet 5.5 | 2.00 | 10.00 | Server-side fallback |
| Haiku 5.5 | 0.10 | 0.50 | No server-side fallback |

- **Estimate:** with 1500 input tokens and 500 output tokens for each request, 100 requests cost about 1.60 $ with Opus 5.5 and about 0.04 $ with Haiku 5.5. The token numbers are an assumption.
- **Consequence for refusals:** with Haiku 5.5 a refusal has no fallback. Uno says that the model declined.

Limits on the calls. Increment A.

- **Gate:** no call to the model without an action of the player, until a spending limit exists that the server operator can set. The gate covers a spontaneous voice, remedies that Uno proposes, and code that Uno writes by its own initiative.
- **Session limit:** a maximum number of requests for each player in one server session. The number is fixed in the code.
- **Counter:** the screen shows the requests and the tokens of the session.
- **Operators only:** by default only an operator uses Claude. Other players use the fixed rules.
- **Reason:** `/uno ask` needs no permission, and has no counter and no limit other than one request at a time (code examination). A command block that repeats the command as a player can make one request after the other. This is deduced from the code. It was not tried.
- **Cost:** a fixed session limit can stop a long session. It becomes a setting when multiplayer opens.
- **Not decided:** the number of the session limit.

### D33.1 — Limits on the model calls in increment A

**Status:** implemented and verified with automatic in-game tests on 2026-11-13, with providers written in the tests and with a local stand-in in place of the API. Not verified with the real Claude service or in a client. Increment A, ticket #11. The choices of the spec are decided by the project owner (spec #1, 2026-11-13). The limit of 100 is an initial value of the spec: **proposed**, to review after the play session. Two points are choices of the implementation: **proposed**, see below.

- **Counter:** for each actor in one server session, Uno counts the requests that it sent to a model, the input tokens and the output tokens. The console is an actor too. The counters are in memory and are not saved. A new server session starts them again.
- **When a request counts:** when Uno sends it, before the answer. The tokens arrive with the answer. A request that fails or reaches the time limit counts with zero tokens. A request that finds an open request of the same player is not sent, thus it does not count.
- **Limit:** 100 requests to a model for each actor in one server session. The number is fixed in the code (`Uno.MODEL_REQUESTS`).
- **At the limit:** the fixed rules read each later request of that actor until the session ends. The first answer after the limit starts with "You reached the limit of 100 model requests in this server session." The later answers do not repeat it. The fixed rules have no limit.
- **Operators only:** a provider with a model serves only an operator. "Operator" is the `admin` flag that the server already puts in the actor: permission level 2, or the console. Each other player gets the fixed rules, without a counter and without the sentence about the limit.
- **Screen:** with a model on the server, an operator sees "Your model requests in this session: N of 100. Tokens: X in, Y out.", also at the limit. A player who is not an operator sees no counter. With Claude on the server, the line "Written requests go to Claude, a model of Anthropic." appears only when the requests of this player go to Claude. Otherwise one line says "Uno uses the fixed rules for you", with the reason: only an operator uses Claude, or the limit. With the fixed rules on the server the screen has none of these lines.
- **Unchanged:** one request at a time for each player. The report, the confirmation and the buttons do not call `Uno.ask`, thus they call no provider (code examination). The test of this with a provider that counts its calls belongs to the notices and the summary (tickets #8 and #10).
- **Proposed, which provider has a model:** the provider says so (`Uno.Provider.callsModel`). The default is no. The Claude provider says yes. Reason: the providers written in the older tests serve players who are not operators. Cost: a new provider with a model that forgets to say so has no limit and no operator rule.
- **Proposed, the request after the limit:** the limit sentence comes before the answer of the fixed rules to the same request. Thus the player gets an answer, not only a refusal.
- **Evidence:** automatic in-game tests `a_model_requests_count`, `a_model_limit`, `a_model_only_for_operators`, `a_new_session_restarts_the_counters` and `a_screen_model_lines`. The five failed before the implementation and passed after it: normal run 50 of 50, and 58 of 58 after the merge with tickets #2, #3, #5, #6 and #7 of increment A. See "Increment A — Limits on the model calls" in [TEST_RESULTS.md](TEST_RESULTS.md).
- **Not verified:** the counter with the tokens of the real service. The test `t18_claude_live` now uses an operator and checks the counter after its four requests. It compiles. It was not run: a run costs money and needs the consent of the project owner. The screen in a client.
- **Counterexample:** an operator owns each station and each robot (D11). Thus the context that goes to Claude holds the station names and the robot numbers of each player of the server, not only of the operator. With one player these are the data of D21.
- **Counterexample:** the limit counts requests, not tokens or money. The estimate of D33 holds only for its token numbers. The counter shows the tokens, not a price.
- **Counterexample:** the limit is for each session, not for each day. A restart of the server starts the counters again. In a single-player game each opening of the world is a new session.
- **Counterexample:** the server builds the actor from the command source. A command block has the permission level 2, thus `/uno ask` from a command block counts as a request of an operator: the console, or the player of `execute as`. Only an operator can write a command block. This is deduced from the code and from the permission level of a command block. It was not tried.
- **Counterexample:** a request that reaches the time limit can still reach the service and cost tokens. The counter shows zero tokens for it. Thus the counter of the tokens can be lower than the real use. The counter of the requests is not.
- **Still open (D33):** a spending limit that the server operator can set. The gate of D33 stays.

## D34 — Waits, blocks and a load on board

**Status:** decided by the project owner on 2026-11-13. It changes a verified behaviour of D06 and replaces a limit of D09. The part of increment A (the obstacle and the causes of `BLOCKED`) is implemented and **verified** with automatic in-game tests on 2026-11-13. See "Increment A, as built" below. The part of increment B is not implemented.

- **Rule:** a condition that can end by itself is a wait. A condition that needs the hands of the owner is a block.
- **Obstacle (increment A):** a robot that cannot move goes to `WAITING` with a cause of its own, and tries again slowly. The attention rule of D30 applies. Now the job goes to `BLOCKED` after 10 attempts and stays there until `resume`, also after the obstacle goes away. The test `t05_obstacle` expects `BLOCKED`: it must change.
- **Causes of `BLOCKED` (increment A):** `BLOCKED` gets causes with fixed values, as `WAITING` in D25. Uno chooses its sentence from the cause. The reason with the coordinates stays in the record. It does not go to the model (D32).
- **Fuel (increment B):** fuel at zero stays `BLOCKED`. The robot tells the runtime its fuel level at each contact. Uno warns one time when the fuel is sufficient for few trips. The number of trips comes from the distance between the docks: it is approximate.
- **A load on board (increment B):** a new job adopts the load that the robot already holds, if it is the item of the job. The proposal says so: "The robot holds 24: I count them as the first load." One confirmation cancels the old job and starts the new one. This replaces the limit of D09.
- **Until B:** a change of the destination with a load on board is refused, as now.
- **Counterexample:** the runtime does not know where a load comes from. If the owner keeps coal in the turtle as fuel, a coal job adopts it and delivers it. The proposal shows the number.
- **Counterexample:** two robots that face each other in a corridor one block wide both wait, without an end. This is deduced from the code. It was not tried.
- **Not decided:** how many trips are "few". When an observation is "old" (D32).
- **Initial value, proposed:** a robot that cannot move tries 10 times at 1 second, as before. Then the job waits, and the robot tries one move every 5 seconds: the wait interval of the module. To review after the play session (spec #1).

Increment A, as built (ticket #5). The choices of detail are **proposed**: the project owner did not confirm them one by one.

| Cause | State | The runtime writes it when | Sentence of Uno in the report |
| --- | --- | --- | --- |
| `PATH_OBSTRUCTED` | `WAITING` | A module reports the impediment "obstacle" | "My path is obstructed. I try to move again every few seconds, and I continue by myself when the path is free." |
| `OUT_OF_FUEL` | `BLOCKED` | A module reports the impediment "fuel": the move failed for fuel, and the robot has no fuel item | "I am stopped: I have no fuel. Give me fuel, then resume the job." |
| `SOURCE_NO_INVENTORY`, `DESTINATION_NO_INVENTORY` | `BLOCKED` | The robot is at the dock, and the station has no inventory | "I am stopped: I find no inventory at 'x'. Put it back, then resume the job." |
| `LOAD_ABOVE_QUANTITY` | `BLOCKED` | A transport reached its quantity with a load on board (D16.1) | "I am stopped: the quantity of the job is reached, and N stay on board. Raise the quantity and resume, or cancel the job." |
| `NONE` | `BLOCKED` | An impediment of an unknown kind, a module error, or a record of an older version | "I am stopped and I need your help. Recorded reason: …", as before |

- **Lua API:** `mindcraft.impediment(kind, detail)` replaces `mindcraft.block(reason)`. It returns the new state. The module sleeps 5 seconds only if the state is `WAITING`: the runtime decides, not the module.
- **Kinds:** "obstacle" and "fuel". Each failed move that is not a lack of fuel is an "obstacle", also "Cannot leave loaded world". An unknown kind keeps its name in the reason: "kind: detail". The agent reports a module error as the kind "module error". Thus its reason is the same text as before.
- **Return to work:** `mindcraft.moved()`. The module calls it after a move that succeeds while the job is `WAITING`. The runtime changes only a `WAITING` with `PATH_OBSTRUCTED` to `RUNNING`. Each other wait stays.
- **Each attempt reports again.** During the wait, each failed attempt reports the impediment again. Thus the reason stays current: the block in front of the robot can change. The state and the cause stay the same.
- **Two causes for "station without an inventory":** the source and the destination, as for an inventory that is not loaded (D25). Thus Uno names the station.
- **The screen** shows the recorded reason for `BLOCKED`, `RECOVERY` and a wait with `PATH_OBSTRUCTED`. It does not show it for the other waits. Reason: a stock job often changes between `RUNNING` and those waits, and each new line changes the shape of the screen. Then the screen creates its fields again (see the second manual test in `TEST_RESULTS.md`).
- **The state machine of the runtime:** each change of the state goes through one function, with its cause. A state without a cause gets `NONE`.
- **Versions:** `transport@3` and `keep_stock@3`.
- **Evidence:** tests `t05_obstacle`, `t05_pause_and_cancel_during_the_wait`, `t05_obstacle_keep_stock`, `t06_no_fuel`, `t06_impediment_of_an_unknown_kind`, `t04_destination_removed`, `t04_source_removed`, and the three `t16_quantity_*` tests with a load above the quantity. The four phases of the restart test. See `TEST_RESULTS.md`, "Increment A — waits and blocks".
- **Observed, not built on purpose:** in `t12_second_session` the second turtle now waits without an end, behind the first turtle that lost its authority. This is the counterexample above, with one robot that does not move any more.
- **Not verified:** a player on the path (`can_push`), and "Cannot leave loaded world". No test builds them. A wait of 5 minutes and its notice: ticket #8.

## D35 — The second behaviour and the first composition

**Status:** decided by the project owner on 2026-11-13. Not implemented. Increment C. The English names `drain` and "round" are working names.

- **Second behaviour:** `drain` (in the discussion: "svuota"). The robot keeps one inventory empty of an item and carries the items to a different station. Example: the ingots from the chest of the furnaces to the depot. With `keep_stock` it closes the cycle of the furnaces.
- **Reason:** it makes the first real conflict: two duties without an end, and one robot. That conflict is the reason for a composition. A second behaviour that is only similar to a transport does not justify an editor.
- **Parameters:** "quantity" already has two meanings: the total to deliver, and the level to keep (D16). `drain` needs a third: the smallest batch that starts a trip. Thus each module declares its parameters, as the brief asks in 5.1. The `Order` record with fixed fields ends here.
- **Alternatives that were not chosen:** "return the load": D34 covers most of the need. An inspection round: it moves no item, and it needs observations that belong to a station, not to a job. See D27.
- **First composition:** a round (in the discussion: "turno"): an ordered list of duties on one robot. The robot changes duty only with no load on board, and only when the current duty has nothing to do. The list is a structured definition. The screen can show a list with the widgets of D19. It cannot show a graph.
- **Consequence:** the discard condition of D16 applies: "a robot needs more than one job". Now each job has its robot as the key.
- **Not chosen:** two robots with one job each in one corridor. See the last counterexample of D34.
- **Until C:** one duty at a time. The player changes it with words.
- **Cost:** the next play session does not show the whole cycle of the furnaces.

## D36 — Lua customization: a turtle addon, a shared library, code that Uno proposes

**Status:** the direction is decided by the project owner on 2026-11-13. Two points are **proposed**: they are a reading of the answer of the project owner, without an explicit confirmation. Not implemented. Increment D. The API facts come from a code examination.

- **Decision:** Lua customization enters with an addon for turtles: a turtle upgrade. Through the addon, Uno can write Lua for the turtle, and the turtles share the existing programs.
- **Custom step:** a Lua program that a round (D35) calls between two duties. It is not managed: inside the step the limits of a job do not apply (D10). The comparison of the real load with the record still applies (D08): if the step changes the load of a job, the job goes to `RECOVERY`. The runtime records the version of the step at each call. First example, distributed with the mod: refuel from a fuel chest.
- **Code that Uno proposes:** Uno writes a draft. The screen shows the code. The step runs only after the owner confirms. From that moment the step belongs to the owner. Only a turtle with the addon runs it.
- **Amended rule of D10:** Uno never runs Lua that the owner did not read and confirm. A managed environment with the tests T17 stays the condition for each code that runs without a confirmation.
- **Change to the brief (4.2):** the brief asks for a verified execution environment before any Lua that Uno generates. The confirmation of the owner on each code replaces that condition, only on a turtle with the addon.
- **Proposed, reading 1:** the programs are in a library of the runtime, saved with the world, one for each owner. A turtle with the addon sees the library as a directory. Uno can list the programs, describe them and propose one. It generates no code for this.
- **Proposed, reading 2:** the addon is an addition. A turtle without it works as now, with the modules of the mod. Thus D02 does not open again. The addon marks the turtles where custom code can run. It has no recipe at the start, as the tablet (D18).
- **Facts from a code examination of the CC: Tweaked jar:** the public API has `IComputerAccess.mount` and `mountWritable`, `ComputerCraftAPI.createSaveDirMount` and `ITurtleUpgrade`. The type `IComputerSystem`, which the `mindcraft` API already gets, refers to `IComputerAccess`. Not tried: a shared directory, and an upgrade, in this mod.
- **Counterexample:** a player who cannot read Lua confirms without understanding. One wrong line passes, for example `turtle.dig()` in front of a chest. The mod declares no guarantee inside a step.
- **Counterexample:** a turtle has two sides for upgrades. The addon uses one.
- **Counterexample:** in multiplayer the code can change the constructions of other players. See D37.
- **Cost:** the owner and Uno change the same program. The library must detect conflicts (T16). To write code probably needs a stronger model than to speak (D33). This is not measured.
- **Open:** where the owner writes Lua: the agent holds the terminal of an enrolled turtle (D03). The list of the functions of a managed environment.

## D37 — Protection of the record, one player only, gates for multiplayer

**Status:** decided by the project owner on 2026-11-13. The atomic write is implemented: see "Status of the atomic write" below. The rest is not implemented.

- **Atomic write (increment A):** the runtime writes a temporary file and then puts it in the place of `mindcraft.dat`.
- **Reason:** D08.1 says that a stop during the write can corrupt the file. Then the runtime loses stations, robots and jobs. After D31 the file also holds the memory of Uno. With work while the player is away (D28), more writes occur that nobody sees.
- **Verification:** no automatic test stops the process during a write. The evidence will be a code examination only.
- **Status of the atomic write:** decided and implemented in increment A. The protection against a stop is not verified: no test stops the process during a write. Its evidence is a code examination. See "Increment A — atomic write of the record" in [TEST_RESULTS.md](TEST_RESULTS.md).
  - The write is in `MindRuntime.save(File)`. The record goes to `mindcraft.dat.tmp` in the same folder, then `Files.move` with `ATOMIC_MOVE` puts it in the place of `mindcraft.dat`. The ordinary save of the world and the write after each transfer both use this method.
  - Verified: the new write gives a record that loads. The normal run (43 of 43) and the four restart phases passed with it. No temporary file stayed after these runs.
  - Verified in one run with a condition built by hand: when the write fails, `mindcraft.dat` stays identical and the server log has the error. This run is not a test of the repository.
- **Provisional choice (proposed):** after a failed write the record stays dirty. The next save of the world or the next transfer tries again. Minecraft instead marks its data as clean after a failed write.
- **Limits of the atomic write:**
  - The runtime does not force the data to the disk before the move. A stop of the operating system or a loss of power is not covered.
  - On Windows the JDK uses `MoveFileEx`. Microsoft does not document it as atomic.
  - While the writes fail, the record on the disk is older than the world. Then the guarantee of D08.1 does not hold. The log shows each failure.
- **Gate for two robots:** before two robots use the same station, a reserve must belong to the station (deferred in D31). A round on one robot does not need it: its duties are in sequence.
- **One player only:** the mod is for one player now. The README says so.
- **Invariant, from increment A on:** each new kind of data (the diary, the notices, the proposals of a change, the library) has an owner, goes through the same check of the actor, and has a test of the T19 kind.
- **The invariant for the diary and the notices (ticket #8), verified with automatic in-game tests:** each entry has the owner of its job. A read of the diary goes through the check of the actor: a different player reads no entry of the owner (`a_diary_of_a_job`). The limit removes only entries of the same owner (`a_diary_limit`). A notice goes to the owner of the job only: the operator who resumed the job gets none (`a_notices_for_blocked_recovery_and_done`).
- **The invariant for the summary and the last opening (ticket #10), verified with automatic in-game tests:** a last opening belongs to the player who opened. The summary reads the diary through the check of the actor. A different player gets no event of the owner, and that opening does not change the summary of the owner (`a_t19_summary_of_another_player`).

Gates before a second player:

1. Transfers that respect the protections of the server. The runtime moves the items without the turtle actions, and a protection mod does not see them (D05, not verified).
2. A rule for the ownership of a station. A chest has no owner (D11).
3. A spending limit for each player (D33).
4. The permissions, tried with a real client that is not an operator.
5. A limit on the chunks that stay loaded (D13).
6. Lua that Uno writes (D36).
7. The data that a hologram shows (D20).

- **Cost:** the criterion of the brief "an interlocutor does not automatically get information or control of the jobs of other players" (2.4) stays tested only with actors that the tests build.
