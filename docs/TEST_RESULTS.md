# Test results

Date: 2026-11-12. Environment: Windows 11, Temurin 17.0.21, Forge 47.4.28, CC: Tweaked 1.121.0.

## Types of test

| Type | Meaning |
| --- | --- |
| **In the game, automatic** | A real headless Forge server runs the test with real turtles and chests. No client is connected. Command: `gradlew runGameTestServer` |
| **Code examination** | A reading of the sources of CC: Tweaked at the tag `v1.20.1-1.121.0` |
| **In the game, manual** | A player uses a client. The project owner did these tests. See "Manual test in the game" |

No test uses a simulation of the game. Where a condition is built artificially, the table says so.
One test uses a false server in place of the Anthropic API: `t18_claude_provider`. It verifies the code of the provider, not the language model.
One test calls the real Claude service: `t18_claude_live`. It is not part of the normal run. Command: `gradlew runGameTestServer -Plive`.
Reasons: each run costs four requests on the account of the key owner, and a language model can answer differently at each run.

The test server normally runs ticks without pauses. The computers of CC: Tweaked run in real time.
Thus the test class keeps the server at 20 ticks per second, as in a normal game.

## Test rig

```
x:  0          1      2        3    4      5
    [source]   dock   turtle   .    dock   [destination]
```

- One normal turtle, without upgrades. Two single chests. A straight and free path.
- The source contains 64 items of coal, unless a test says otherwise. The basic request is 24 items.
- **Declared simplification:** the fuel is a number set on the turtle (1000). The turtle does not burn the coal of the job.
- The console (an administrator actor) enrolls the turtle, except in T19.

## Runs

| No. | Result | Cause of the failures |
| --- | --- | --- |
| 1 | 13 of 19 | The test server ran at about 300 ticks per second. The restart of the turtle had a limit in ticks. Corrected: 20 ticks per second and a limit in real time |
| 2 | Not valid | The server restart test was selected by mistake and stopped the server after 27 seconds. Gradle reported success. Corrected: the test has its own namespace and refuses to start without a phase |
| 3 | 17 of 20 | CC: Tweaked ignores `shutdown` while a computer starts. Three turtles did not start again after the enrollment. Corrected: see D09 |
| 4 | 20 of 20 | None |
| 5 | 20 of 20 | None. First run from the final project folder |
| 6 and 7 | 20 of 20 | None. Flat test world. Last runs with the code of S1 only |
| 8 | 26 of 26 | None. Tests of S2: stock and Uno |
| 9 | 27 of 27 | None. With the tablet |
| 10 | 28 of 28 | None. With the projector |
| 11 | Did not start | The game did not start. The Anthropic SDK brings the library `org.jetbrains:annotations`, which Forge already has. Error: `ResolutionException: Modules org.jetbrains.annotations and annotations export package org.intellij.lang.annotations`. Corrected: the library is excluded. See D21 |
| 12 | 29 of 29 | None. With the Claude provider and the test `t18_claude_provider` |
| 13 | 29 of 29 | None. With the production jar enabled |
| 14 | 29 of 29, but the wrong tests | The command had `-Plive`. The build script did not select the real test: `hasProperty('live')` inside the run configuration asked the configuration, not the project. The server ran the 29 normal tests. No request went to Claude. Corrected in `build.gradle` |
| 15 | 1 of 1, with `-Plive` | None. Test `t18_claude_live` with the real Claude service. Log line: `Enabled Gametest Namespaces: [mindcraft_live]` |
| 16 | 29 of 29 | None. After the correction of the build script |
| 17 | 29 of 29 | None. With the new sentence of Uno for a request without a complete order |
| 18 | **29 of 29** | None. Current code, with the English text of Uno. Server line: `All 29 required tests passed :)` |
| 19 | 1 of 1, with `-Plive` | None. Test `t18_claude_live` with the real Claude service and the English text. Log line: `Enabled Gametest Namespaces: [mindcraft_live]` |

Lesson from run 2: the exit code is not sufficient. The log must contain the line `GAME TESTS COMPLETE`.
Lesson from run 14: that line is not sufficient either. The number of tests and the line `Enabled Gametest Namespaces` say which tests ran.

The test world is flat and has no structures. See D14 for the reason.

## S0 — Feasibility

| Question | Type | Result |
| --- | --- | --- |
| Does the toolchain compile the mod with Forge and CC: Tweaked? | Automatic | Yes |
| Does the server load the mod and CC: Tweaked without a client and without a manual EULA step? | In the game, automatic | Yes, with `runGameTestServer` |
| Is there a public interface to add an API to turtles only? | Code examination and in the game | Yes: `registerAPIFactory` with `ComputerComponents.TURTLE` |
| Minimal path of observation, command and result on a turtle | In the game, automatic | Yes: test `s1_transfer` |
| Can a mod add Lua programs to the ROM of the computers? | Code examination and in the game | Yes: resources in `data/computercraft/lua/rom` |
| Can the server stop a program that does not cooperate? | In the game, automatic | Yes: `s0_probe_hard_stop`, with `/computercraft shutdown` |
| Does a loop without `yield` block the computer forever? | In the game, automatic | No: error after 7.45 seconds (`s0_probe_no_yield`) |
| Is a restricted Lua environment a sandbox? | In the game, automatic | Only if it does not contain `load`, `getfenv`, `debug`, `getmetatable` and the original tables of the libraries. See D10 |

Raw result of `s0_probe_environment`:

```
empty_env_reaches_globals=false
empty_env_string_methods=AB
load_reaches_turtle=true
getfenv_exists=true
getfenv_reaches_turtle=true
string_table_write=true
string_table_shared=changed
getmetatable_string_write=true
getmetatable_string_shared=changed
debug_reaches_turtle=true
```

Observation without a measurement: during the 7.45 seconds of the loop without `yield`, the other turtles completed withdrawals and deliveries.
The default configuration has only one thread for the computers (`computer_threads = 1`). A dedicated measurement is necessary before a test with many units.

## S1 — Adversarial matrix

All "passed" rows come from runs 6 and 7. The later runs confirm them, up to run 18.

| ID | Test | What it does | Result | Observed result |
| --- | --- | --- | --- | --- |
| Base | `s1_transfer` | Transfers 24 items | Passed | Source 40, destination 24, robot 0. Record: taken 24, delivered 24 |
| Base | `s1_commands` | The same flow with the `/mindcraft` commands, from the console | Passed | As above |
| T01 | `t01_source_empty` | Empty source for 13 seconds, then 24 items | Passed | `WAITING` with a reason. 3 attempts in 13 seconds. Then a complete delivery |
| T02 | in `s1_transfer` | Reads the state before the first observation | Passed | Stock "unknown", not zero |
| T03 | `t03_destination_partial` | The destination accepts only 4 items | Passed | Delivered 4, on board 20, `WAITING` with a reason. After one slot is emptied: complete delivery |
| T04 | `t04_destination_removed` | Removes the destination chest during the trip | Passed | `BLOCKED` with a reason. The cargo of 24 stays on board. After the chest is put back and `resume`: complete delivery |
| T05 | `t05_obstacle` | A stone block on the path | Passed | The module tries again a limited number of times (10 in the code), then `BLOCKED` ("Movement obstructed"). The stone is intact. After the removal and `resume`: complete delivery |
| T05 | in `t12_second_session` | Another turtle stopped on the path | Passed | `BLOCKED`. The reason names the block in front of the robot: "in front: computercraft:turtle_normal". This result was observed, not forced by the test |
| T06 | `t06_no_fuel` | Zero fuel | Passed | `BLOCKED` ("Out of fuel"). No withdrawal. After the refuel and `resume`: complete delivery |
| T07 | `t07_t08_...` | A second job on the same robot | Passed | The request is refused. The first job stays |
| T08 | `t07_t08_...` | Repeats `take` in the same tick as the first withdrawal | Passed | The repetition moves 0 items |
| T09 | `t09_record_older_than_world` | After the withdrawal, the test sets the recorded cargo to zero | Passed | `RECOVERY` ("holds 24, the record says 0"). The robot stops. After `resume`: delivery, with a warning of inexact totals |
| T10 | `t10_load_lost` | An external actor empties the turtle during the trip | Passed | `RECOVERY` ("holds 0, the record says 24"). No other operation. No delivery is declared |
| T11 | `t11_executor_restart` | Restarts the computer of the turtle during the trip | Passed | The module starts again without state and completes: source 40, destination 24 |
| T11 | `t11_server_restart` | Normal stop and sudden stop of the server during the transport, then restart | Passed | See below |
| T12 | `t12_turtle_moved` | Moves the turtle to another position during the trip | Passed | The robot uses the true position and completes |
| T12 | `t12_second_session` | Starts a second turtle with the same computer ID | Passed | The first session loses the authority: its calls fail. No item is lost or duplicated |
| T13 | `t13_external_withdrawal` | Request 40, reserve 32. An external actor removes 20 items before the withdrawal | Passed | Taken 12. Source 32. `WAITING` ("the reserve is 32") |
| T14 | `t14_pause` | Pause during the trip, then `resume` | Passed | The robot stops within 1.5 seconds. Cargo on board 24. Then it completes |
| T14 | `t14_cancel` | Cancellation during the trip | Passed | `CANCELLED`. The cargo of 24 stays on board and the state shows it. A new job is refused until the turtle is empty |
| T19 | `t19_ownership` | A second player tries to enroll, start, cancel, release and redefine | Passed | Each request is refused. The test builds the actors. They are not real clients |
| T20 | all | No interface and no connected client | Passed by construction | The state is in the server. The `status` command reads it. In S1 there was no view to close |

### Conditions built artificially

- **T09:** the test changes the record in memory. It does not stop the process.
- **T10:** the test empties the inventory of the turtle with a direct call.
- **T19:** the actors "Alice" and "Bob" are identifiers that the test creates. The owner of the turtle is written in its NBT data.
- **T12, second session:** the result depends on the moment when the second turtle starts. The test accepts each result that keeps the items and removes the authority from the first session.

### T11 — Real restart of the server

The test uses two starts of the server on the same world saved on disk. No client is connected. See `RestartTests.java`.
The rig is the same, at a fixed position at y = 100, in a chunk that is kept loaded.

| Phases | What they do | Result | Observed result |
| --- | --- | --- | --- |
| `stop`, then `check` | Normal stop of the server in the tick of the withdrawal. Then a restart | Passed, 6 times | After the restart the record says `RUNNING` with 24 items on board. The robot starts again by itself and delivers. Final: source 40, destination 24, robot 0. No `RECOVERY` |
| `crash`, then `check-crash` | A save after the withdrawal. A delivery. After 15 seconds, a stop of the process without a save. Then a restart | Passed, 6 times | See below |

State on disk after the sudden stop:

| Data | Value |
| --- | --- |
| World | As at the save: source 40, destination 0, robot with 24 items |
| Record | Written at the delivery: `DONE`, taken 24, delivered 24 |

- At the first access after the restart the job goes to `RECOVERY` ("the world is older than the record of this job"). The observed stocks become "unknown" again.
- The runtime does not declare the delivery as a fact. The robot stays stopped with the cargo.
- After `resume` of the owner the robot delivers. Final: source 40, destination 24, robot 0.
- The final record says "delivered 48" and declares "1 recovery. The totals can be inexact". This is the expected limit: the record also counts the delivery that the world lost.

Errors found during this test:

| Error | Where | Correction |
| --- | --- | --- |
| For about one second after the restart the state was `DONE` with a delivery that the world did not contain. The check occurred only at the first contact of the robot | Runtime | The check occurs at the first access to the record after the server start |
| The `stop` phase failed intermittently with "Movement obstructed" | Test | The test world had normal terrain and a random seed. The fixed position was sometimes inside rock. The test world is now flat |
| The test did not find the rig at the second start | Test | `GameTestHelper.relativePos` of Minecraft 1.20.1 mirrors the position. The test calculates the position without that method |

The cause of the second error was found with the reason of the block, which now contains the position, the direction and the block in front of the robot:
`the robot cannot move from 2 100 -1 (west, in front: minecraft:stone) toward 1 100 -1: Movement obstructed`.

Commands:

```
gradlew runGameTestServer -PrestartPhase=stop
gradlew runGameTestServer -PrestartPhase=check
gradlew runGameTestServer -PrestartPhase=crash
gradlew runGameTestServer -PrestartPhase=check-crash
```

## S2 — Restocking and minimal Uno

Status of S2: the restocking, Uno with the deterministic provider, the logic of the screen and the logic of the projector are tested automatically.
The project owner confirmed the screen and the projector in the game.
The Claude provider exists. Its code is tested automatically against a false API.
The test with the real service (`t18_claude_live`) passed two times on 2026-11-12, with the consent of the project owner and with the key of the project owner.

The "passed" rows without another note come from run 18: 29 tests of 29, with the current code.
The "real Claude" rows come from run 19, with the current code. Run 15 was the first run with the real service. At that time Uno and the prompt were in Italian. It gave the same order and three refusals with the same meaning.
The four phases of the restart test (T11) were repeated after run 19, with the current code. They passed, with the same values as before.

| ID | Test | What it does | Result | Observed result |
| --- | --- | --- | --- | --- |
| Stock | `s2_keep_stock` | Keeps 64 items with reserve 32. Source 128, destination 10 | Passed | It carries 54 items, then `SATISFIED`. After a consumption of 20 it carries 20. After a consumption of 40 it carries 22 and stops: `WAITING` ("the reserve is 32"), source 32, destination 46 |
| Reserve | `s2_keep_stock` | The owner sets the reserve to 0 | Passed | The robot carries the 18 missing items. Destination 64, source 14 |
| T16, minimal part | `s2_keep_stock` | A change based on an old revision | Passed | Refused ("changed"). The first change stays |
| T11, computer | `s2_keep_stock` | Restarts the computer with the job `SATISFIED`, then consumes 10 items | Passed | The robot continues with the correct module and brings the stock back to 64 |
| Request | `s2_uno_request` | Written request, proposal, confirmation, work, report | Passed | Proposal with quantity 64 and reserve 32. After the confirmation the robot delivers 48 items. The report gives 48, 32 and 64 from the record |
| Request | `s2_uno_reference_sentence` | The reference request of the brief, word for word, to the deterministic provider. Then the same request in Italian | Passed | Order `keep_stock`, from `depot` to `furnace`, `minecraft:coal`, 64, reserve 32. The Italian request gives the same order with the Italian station names |
| T18 | `t18_provider_failures` | A provider that fails, a slow provider, an answer that is not JSON | Passed | Uno answers "I cannot interpret the request now" or "I did not get a valid configuration". No proposal. A transport in progress completes: source 40, destination 24 |
| T18 | `t18_provider_failures` | A JSON answer with an unknown station, a negative quantity or a module that does not exist | Passed | The ordinary validation refuses the order. No proposal |
| T18 | `t18_provider_failures` | A request without a quantity to the deterministic provider | Passed | Uno answers "I cannot propose a job: the quantity is missing." It does not use the sentence of a failure |
| T18, Claude | `t18_claude_provider` | The Claude provider gets a complete order from the false API | Passed | Uno proposes "carry 8 x minecraft:coal". The sent request contains the model `claude-opus-5-5`, low effort, the JSON schema with 8 required fields, `fallbacks: "default"`, the text of the player and the names of the stations of the player |
| T18, Claude | `t18_claude_provider` | The false API answers with a filled `problem` field | Passed | Uno answers "I cannot propose a job: You did not give the source." No proposal |
| T18, Claude | `t18_claude_provider` | Refusal of the model, cut answer, error 401, error 500, API stopped | Passed | Five different reasons: refusal, incomplete answer, invalid key, error 500, service not reachable. No proposal |
| Cost | `t18_claude_provider` | Two written requests of the same player in the same tick | Passed | The second immediately gets "I am still working on your previous request." and does not call the provider |
| T18, real Claude | `t18_claude_live` | A free request in Italian to the real service: "Fai in modo che alla fornace non manchi mai il carbone: tienine sempre almeno sessantaquattro pezzi. Prendilo dal Deposito, ma lì lasciane trentadue di scorta." (Make sure that the furnace never runs out of coal: always keep at least sixty-four pieces. Take it from the Depot, but leave thirty-two there as a reserve.) The stations of the player are `deposito`, `fornaci` and `miniera` | Passed, 2 times | The deterministic provider cannot read this request. Uno proposes: "keep at least 64 x minecraft:coal in 'fornaci', taken from 'deposito'. Reserve to leave in 'deposito': 32. Robot #0." Before the confirmation the world does not change. After the confirmation the robot delivers 48 items: destination 58, source 32, state `WAITING` |
| T18, real Claude | `t18_claude_live` | "Porta del carbone alle fornaci" (carry some coal to the furnaces): the quantity and the source are missing | Passed, 2 times | No proposal. Uno answers: "I cannot propose a job: The request gives no source station and no quantity, and no robot is free." |
| T18, real Claude | `t18_claude_live` | "Che tempo farà domani?" (what will the weather be tomorrow?): not an order | Passed, 2 times | No proposal. Uno answers: "I cannot propose a job: The request asks about the weather and does not ask for a transport or keep_stock order." |
| T18, real Claude | `t18_claude_live` | "Porta 10 pezzi di carbone dal deposito alla segheria" (carry 10 pieces of coal from the depot to the sawmill): the station does not exist | Passed, 2 times | No proposal. Uno answers: "I cannot propose a job: The destination station "segheria" is not in the context, and no robot is free." |
| T21 | `t21_report_with_old_data` | Turns off the robot, then removes 10 items from the destination. Asks for the report after 5 seconds | Passed | The report gives the last observation (24 items) with its age. It says that the robot is not running and that the data are not the current state. It does not give the real quantity (14) |
| T22 | `s2_uno_request` | Checks the world after the proposal and before the confirmation | Passed | No job, no item moved. A proposal does not start two times |
| T23 | `t22_t23_...` | A second player tries to confirm the proposal and to read the report | Passed | No proposal and no job are visible. The context of the provider for the second player contains no stations or robots of the first |
| T23 | `t22_t23_...` | The robot gets another job between the proposal and the confirmation | Passed | The confirmation is refused: the runtime validates again |
| T23 | `t22_t23_...` | Saves and loads the data of the runtime. Asks for the report and a confirmation | Passed | The report is identical. Uno has no proposal: it does not invent a memory that it did not save |
| Tablet | `s2_tablet_view` | The whole S2 path through the view of the tablet: request, confirmation, work, reserve set to 0 | Passed | The view shows the proposal and the "Confirm" button. After the confirmation the robot delivers 48 items and the view shows "waiting", reserve 32, destination 58 of 64. After "Apply" with reserve 0 the robot completes: destination 64, source 26 |
| T16 | `s2_tablet_view` | "Apply" from an old view, with the previous revision | Passed | Refused. The reserve stays 0. Uno answers "I cannot" |
| T19 | `s2_tablet_view` | A second player asks for the view and sends "Cancel" on the job | Passed | The view of the second player does not contain the job. The action is refused and the job continues |
| T20 | `s2_tablet_view` | Builds the view two times | Passed | The two views are identical. The server keeps no state for an open screen |
| Input | `s2_tablet_view` | A number field with text that is not valid, and an unknown action | Passed | No change. Uno answers "I did not understand the action" |
| Projector | `s2_projector` | A player places the projector | Passed | The runtime registers a place with that owner. The view of the owner counts it. The view of another player does not |
| Projector | `s2_projector` | Checks the range at 5 and at 30 blocks | Passed | At 5 blocks the projector is a contact point. At 30 blocks it is not |
| Projector | `s2_projector` | A transport, then a stock that cannot be restocked | Passed | The block state goes from `idle` to `working`, back to `idle` when the job ends, then to `attention` |
| Projector | `s2_projector` | Saves and loads the data, then removes the block | Passed | The place is in the saved data. After the removal the place does not exist and is not a contact point |

Conditions built artificially:

- **Tablet:** the test calls the functions that the network messages call. It does not use the network and does not open a screen. The check "the player has a tablet" is not tested automatically.
- **Projector:** the test places the block with a mock player of the test system. It reads the block state, not the image of the hologram. The click on the block is not tested automatically.
- **Provider:** `t18_provider_failures` uses false providers written in the test. `t18_claude_provider` uses the real Claude provider and the real SDK, but a local HTTP server answers in place of the Anthropic API. The answers are written in the test. In these two tests no language model is called.
- **Real provider:** `t18_claude_live` calls the Anthropic service with the key of the project owner. It ran two times, one with the Italian text and one with the current English text. Another run can give different answers. The four requests are written in the test, in Italian: they do not come from a player. During the last three requests the only robot already had the job of the first. Thus the sentence "no robot is free" agrees with the sent context.
- **Words of Claude:** the part of the answer after "I cannot propose a job:" is written by the model. In run 19 two sentences used internal words: "keep_stock order" and "the context". A player does not know these words.
- **Times observed with the real service:** each answer arrived in less than 7 seconds. The last three arrived in about 2 or 3 seconds. These are two measurements, read from the times of the log.
- **T21:** the test turns off the computer with `/computercraft shutdown`.
- **T23:** "another contact point" is a second call to the same command and a copy of the data loaded again in memory.
- **Players:** the actors are identifiers that the test creates.

## Manual test in the game

Date: 2026-11-12. Executor: the project owner, with the development client (`gradlew runClient`) and the procedure of the README.
At the time of these tests the text of Uno was in Italian. The answers of the project owner are translated from Italian.

- **Start of the client:** verified from the log. The client loads Forge, CC: Tweaked and MindCraft up to the title screen, without errors. Repeated after the tablet and the projector were added: no error of model, texture or block state.
- **Result that the project owner reported:** "everything works".
- **Not recorded:** the steps that were done, the observed quantities and which adversarial cases the project owner tried.

Second manual test, on the screen of the tablet:

| Defect that the project owner found | Cause | Correction | Status |
| --- | --- | --- | --- |
| The text field for a request to Uno clears itself each second and it is not possible to write in it | The view changes each second, because it contains the age of the observations. At each change the screen created fields and buttons again, and Minecraft removes the focus before it creates them again | Fields and buttons stay the same until the shape of the view changes. Only texts and bars are updated | Corrected. Confirmed by the project owner in the game: "it works" |
| Found during the correction: the text of the field was cut to 32 characters at each update | The length limit of the field was set after the text | The limit is set before the text | Corrected in the code. The project owner did not confirm it explicitly |

The automatic tests could not find these defects: they are only about the drawing on the client.

Third manual test, on the projector and its hologram:

- **Result that the project owner reported:** "it works".
- **Not recorded:** which points the project owner checked. The proposed points were the translucent and animated hologram, the three colours, the screen without a tablet within 8 blocks and the visibility in the dark.

## Not executed

| Case | Reason |
| --- | --- |
| The `/mindcraft` commands sent by a player without operator permissions | The manual test uses a world with commands enabled. Thus the player is an operator. The ownership check is tested only at the level of the runtime (T19) |
| T11: unload and reload of a chunk during the transport | It needs a player or forced chunk loading |
| T11: sudden stop on a normal server | The test server has no idle time in the tick. A normal server can write chunks to disk at different moments |
| T05: a player on the path | It needs a client |
| T08: delayed confirmation | Not applicable: the runtime and the turtle are in the same process, without network messages |
| Inventory not loaded (`WAITING` with unknown stock) | The branch exists in the code. No test reaches it |
| Double chests, inventories with sides (furnaces), hoppers, items with NBT | Outside the S1 rig |
| More turtles on different jobs with shared resources | Outside S1 |
| New world with an old `mindcraft.dat` file | Only the variant in memory (T09) |
| Cost on the server thread | No measurement |
| S2: the screen of the tablet in the client (drawing, mouse, keyboard, network messages) | It needs a person at the game. See D19 |
| S2: each single point of the projector in the client (the three colours, the range without a tablet, the dark) | The project owner confirmed the projector in general, not point by point. See D20 |
| S2: the English text of Uno in a client | The project owner tried the screen only with the Italian text. The English text is tested automatically, through the view that the server builds |
| S2: Claude with requests different from the four of the test `t18_claude_live` | Other languages, ambiguous station names, more than one free robot, a simple transport. Each request costs money to the key owner |
| S2: Uno with Claude used by a player in the client | It needs the key in the environment of the game and a person |
| S2: the fallback on refusals (`fallbacks`) with the real service | No request of the real test was declined. The branch is tested only with the false API |
| The production jar `mindcraft-<version>-all.jar` loaded on an installed Forge | Only built and examined: 25 nested libraries. In the development game the libraries arrive in a different way |
| S2: the `/uno` commands in the game | Tested only through the functions that the commands call |
| T15, T17 and the rest of T16 | They belong to S3 |

## Known limits

- The guarantees apply to the actions that go through `mindcraft.take` and `mindcraft.give`. A different Lua program can use `turtle.suck` and `turtle.drop`.
- An external actor that changes a chest between two visits is not detected. The state shows the last observation and its age.
- After `RECOVERY` and `resume` the totals of the job can be inexact. The state says so.
- The path must be straight along the axes. There is no path finding.
- Outside loaded areas the robot does not work and the job stays `RUNNING`. The state shows the robot as not loaded.
- A chest has no owner. There is no integration with the protections of the server.
- Uno answers only in English, also to a request in another language.
