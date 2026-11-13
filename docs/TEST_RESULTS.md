# Test results

Date: 2026-11-12. Environment: Windows 11, Temurin 17.0.21, Forge 47.4.28, CC: Tweaked 1.121.0.

## Types of test

| Type | Meaning |
| --- | --- |
| **In the game, automatic** | A real headless Forge server runs the test with real turtles and chests. No client is connected. Command: `gradlew runGameTestServer` |
| **Code examination** | A reading of the sources of CC: Tweaked at the tag `v1.20.1-1.121.0`, or of the compiled code of Minecraft and Forge with `javap`. A code examination is not a test: it says what the code does, not that it was seen to do it |
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
- One test has a different rig, with a corner: see "Increment A — a path with one corner".
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
| 18 | 29 of 29 | None. The code of the commit `b6ae2b7`, with the English text of Uno. Server line: `All 29 required tests passed :)` |
| 19 | 1 of 1, with `-Plive` | None. Test `t18_claude_live` with the real Claude service and the English text. Log line: `Enabled Gametest Namespaces: [mindcraft_live]` |
| 20 | 28 of 40, expected | The code of the commit `b6ae2b7` with the 11 new tests of the review. The 11 new tests failed, and `t18_claude_provider` failed with its corrected expectation. Each failure shows one finding. See "Review of 2026-11-12" |
| 21 | 39 of 40 | The corrected code. `s2_projector` failed: the Lua module of a job that ended executed the next job. The defect existed before the review. See D26 |
| 22 | 39 of 41 | With the new test `t15_next_job_with_another_module`, before its correction: it failed as expected. `t21_report_source_at_reserve` failed for an error of the test: it compared the report of the running data with the report of a loaded copy, and a copy has no time of the last contact. The test was corrected |
| 23, 24, 25 | 41 of 41, three times | None in these runs. But the restart test, repeated after run 25, failed in the `check-crash` phase: after the sudden stop and `resume`, the job went to `BLOCKED` with 24 items on board. Cause: the first version of the correction of finding 3. See D16.1 |
| 26 | 41 of 42 | With the new test `t11_resume_when_the_world_is_older`, before its correction: it failed as expected, with the same message as the `check-crash` phase |
| 27 | 42 of 42 | None |
| 28 | 41 of 42 | `t18_claude_provider` failed for a race in the test, not in the mod. The deterministic provider answered before the second request arrived. Thus the first request was not open any more, and Uno accepted the second. The race existed before the review. The test now holds the first request open on purpose |
| 29, 30 | 42 of 42, two times | None |
| 31 | 42 of 43 | With the new test `t23_robots_of_an_earlier_session`, before its correction: it failed as expected |
| 32, 33 | **43 of 43**, two times | None. Current code. Server line: `All 43 required tests passed :)` |

Lesson from run 2: the exit code is not sufficient. The log must contain the line `GAME TESTS COMPLETE`.
Lesson from run 14: that line is not sufficient either. The number of tests and the line `Enabled Gametest Namespaces` say which tests ran.
Lesson from run 21: a test that passed 12 times can hide a defect that depends on one tick. A test must build such a moment on purpose.
Lesson from the restart test after run 25: the normal run does not contain the restart test. A change of the transfer rules needs the four restart phases too. One case of them is now also in the normal run.
Lesson from run 28: a test must not depend on which thread is faster. A fast provider can answer inside the call that sends the request.

The real Claude service was not called after run 19. The corrections of the review were tested with the deterministic provider and with providers written in the tests.

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

All "passed" rows come from runs 6 and 7. The later runs confirm them, up to run 33.
Increment A changed T04, T05 and T06: an obstacle is a wait, and each block has a cause. The rows T04, T05 and T06 below describe the behaviour of S1. See "Increment A — waits and blocks".

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
| `stop`, then `check` | Normal stop of the server in the tick of the withdrawal. Then a restart | Passed, 9 times | After the restart the record says `RUNNING` with 24 items on board. The robot starts again by itself and delivers. Final: source 40, destination 24, robot 0. No `RECOVERY` |
| `crash`, then `check-crash` | A save after the withdrawal. A delivery. After 15 seconds, a stop of the process without a save. Then a restart | Passed, 8 times. Failed 1 time, after run 25, with a first version of a correction. See "Review of 2026-11-12" | See below |

State on disk after the sudden stop:

| Data | Value |
| --- | --- |
| World | As at the save: source 40, destination 0, robot with 24 items |
| Record | Written at the delivery: `DONE`, taken 24, delivered 24 |

- At the first access after the restart the job goes to `RECOVERY` ("the world is older than the record of this job"). The observed stocks become "unknown" again.
- The runtime does not declare the delivery as a fact. The robot stays stopped with the cargo.
- After `resume` of the owner the robot delivers. Final: source 40, destination 24, robot 0.
- Until run 19 the final record said "delivered 48" and declared "1 recovery. The totals can be inexact": the record also counted the delivery that the world lost.
- Since the review of 2026-11-12 the final record says "delivered 24", with the same declaration. At `resume` the runtime subtracts the load that the robot still holds from the delivered total. See D16.1. The physical result is the same.

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

The "passed" rows without another note come from run 18: 29 tests of 29, with the code of the commit `b6ae2b7`.
The "real Claude" rows come from run 19, with the same code. The real service was not called with the corrected code of the review. Run 15 was the first run with the real service. At that time Uno and the prompt were in Italian. It gave the same order and three refusals with the same meaning.
The four phases of the restart test (T11) were repeated after run 19. They passed, with the same values as before.
Runs 32 and 33 confirm all the rows of this table with the final code, except the "real Claude" rows.
The four phases of the restart test were repeated after run 33, with the final code. They passed.
One sentence of the report changed in the review: for a transport that waits at the reserve, Uno now says "I must carry N more" in place of "To reach N in …". The sentence for a stock is the same as before.

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

## Review of 2026-11-12 — six findings and three more defects

A review of the commit `b6ae2b7` found six problems by a code examination, without tests.
Method for each finding: a test that shows it, a run on the old code, the smallest correction, a run on the new code.
Run 20 is the run on the old code: the tests of the review with the code of `b6ae2b7`. Runs 32 and 33 are the runs on the final code. Runs 23 to 25, 27, 29 and 30 gave the same results for these tests.
All tests of this section are in the game and automatic, with the deterministic provider or with providers written in the tests.

| No. | Finding | Verdict | Test | Run 20, old code | Runs 32 and 33, final code |
| --- | --- | --- | --- | --- | --- |
| 1 | "Confirm" starts an old proposal after a new request failed | Confirmed and corrected. D23 | `t22_confirm_only_the_shown_proposal` | Failed: "the view offers Confirm below the answer to a failed request", "the button started the old proposal" | Passed. No job starts. During an open request Uno answers "I am still working on your request". The button of an older proposal starts nothing. A proposal starts one time |
| 1 | The same, at the level of the Claude provider | Confirmed and corrected | `t18_claude_provider`, with a corrected expectation | Failed: "wrong proposal state after answer 6". The proposal of answer 5 stayed after the next request failed | Passed |
| 2 | The temporary state of Uno goes from one world to the next | Confirmed and corrected. D24 | `t23_state_of_an_earlier_session` | Failed: the new session had the proposal and the message of the old one, and a confirmation started job 37 | Passed. The new session has no proposal and no message. An open request of the old session does not block the new one, and its late answer changes nothing |
| 3 | A lower quantity with a load on board is exceeded | Confirmed and corrected. D16.1 | `t16_quantity_lowered_with_load` | Failed: quantity 10, but destination 64, robot 0, `DONE` | Passed. Destination 10, robot 54, `BLOCKED` with the reason. No delivery in the next 7 seconds. After a raise to 64 and `resume`: destination 64, robot 0, `DONE` |
| 3 | The same, after a partial delivery | Confirmed and corrected | `t16_quantity_lowered_after_partial_delivery` | Failed: quantity 10, but destination 84 (60 + 24), robot 0, `DONE` | Passed. Destination 70 (60 + 10), robot 14, source 40, `BLOCKED` |
| 3 | The same, with a quantity lower than the delivered total | Confirmed and corrected | `t16_quantity_below_delivered` | Failed: `DONE` with 20 items on board | Passed. `BLOCKED`, destination 64, robot 20. The report says that the 4 delivered items are more than the quantity of 2. After room in the destination and `resume`: nothing more is delivered. After `cancel`: `CANCELLED`, 20 on board |
| 3 | The same, for a stock | Confirmed and corrected | `t16_level_lowered_with_load` | Failed: level 20, but destination 64, robot 0 | Passed. Destination 20, robot 44, source 74, `SATISFIED` with "44 stay on board". After a consumption of 15: destination 20 again, robot 29 |
| 4 | The actions of an old view change the next job of the robot | Confirmed and corrected. D19 | `t16_actions_of_an_old_view` | Failed: "Apply changed job B to 50, reserve 7", "Pause changed job B to PAUSED", "Resume changed job B to RUNNING", "Cancel changed job B" | Passed. Job B does not change and Uno answers "I cannot". The actions of the current view work. Final: source 40, destination 24 |
| 5 | Each player reads the jobs of the others with the status command | Confirmed and corrected. D11 | `t19_status_command` | Failed: a different player got result 1 and the whole status of the job | Passed. The owner, an operator and the console read the status. A different player gets "Robot #N is not yours." and no data. A `cancel` command of that player changes nothing |
| 6 | The report gives a wrong cause for a wait | Confirmed and corrected. D25 | `t21_report_robot_full` | Failed: the robot was full, but the report said "64 remain in 't21full_source': that is the reserve you asked me to keep", with a reserve of 0 | Passed: "My inventory is full: I cannot take the items from 't21full_source'." After one free slot: complete delivery |
| 6 | The same, for an inventory that is not loaded | Confirmed and corrected | `t21_report_inventory_not_loaded` | Failed: "-1 remain in 't21far_source': that is the reserve you asked me to keep" | Passed: "The area of 't21far_source' is not loaded: I cannot see its inventory and I do not know its stock." The stock stays "never observed" |
| 6 | The same, for an empty source without a reserve, and for the reserve | Confirmed and corrected | `t21_report_source_at_reserve` | Failed: "0 remain in 't21reserve_source': that is the reserve you asked me to keep", with a reserve of 0 | Passed: "Nothing remains in 't21reserve_source'." Then, with 44 items and a reserve of 32: 12 delivered and "32 remain in 't21reserve_source': that is the reserve you asked me to keep." A save and a load keep the cause. A record without a cause gives "I am waiting. Recorded reason: …" |

Three more defects, not in the review. The runs of the review found the first two. The third has the same kind as finding 2: the project owner asked for its correction after the review.

| Defect | Test | Before the correction | After the correction |
| --- | --- | --- | --- |
| It existed before the review. A job ends and a job with a different module starts in the same tick. The Lua module of the first job executes the second. See D26 | `t15_next_job_with_another_module` | Run 22, failed: `BLOCKED` ("module error: … transport.lua:99: The destination stock is unknown. Observe it first.") | Runs 23 to 33, passed: `SATISFIED`, executor `keep_stock@2`, destination 16, source 48 |
| The first correction of finding 3 caused it. After a sudden stop the record says "delivered 24" but the robot still holds the 24 items. After `resume` the new limit kept the load on board. See D16.1 | `t11_server_restart`, phases `crash` and `check-crash`. In the normal run: `t11_resume_when_the_world_is_older`, with an artificial record | Failed after run 25 and in run 26: `BLOCKED` ("the quantity is 24 and 24 are delivered: 24 stay on board") | Passed: source 40, destination 24, robot 0, `DONE`, "1 recovery. The totals can be inexact" |
| The same kind as finding 2, for the robots. The running robot sessions, the boot counts and the pending computer restarts were static, and lived from one world to the next. See D24 | `t23_robots_of_an_earlier_session` | Run 31, failed: "the robot session of the old server is a running robot in the new one", "the new server has the boot count of the old one: 2", "the new server has a pending computer restart of the old one", "the runtime started a job on a robot session of the old server", "the robot session of the old server still has authority" | Runs 32 and 33, passed. A job for that robot is refused: "is not running" |

### What each statement of the review rests on

| Kind of evidence | Statements |
| --- | --- |
| **Verified automatically, in the game** | All rows of the two tables above. The older tests also pass with the final code: 43 tests of 43 in runs 32 and 33, and the four phases of the restart test |
| **Verified manually** | Nothing. No person tried the corrected code in a client |
| **Deduced from a code examination** | A server start in a client calls the function that starts a new session of Uno: `IntegratedServer.initServer` calls `ServerLifecycleHooks.handleServerAboutToStart`. After a server stop, `MinecraftServer.execute` runs a task immediately on the thread of the caller: this is how an answer of a stopped world wrote in the static state. The test system runs the batches one after the other, thus the session test does not disturb the others. The causes `SOURCE_KEEPS` and `DESTINATION_UNLOADED`. At a server stop, CC: Tweaked stops each computer and calls `shutdown()` on its APIs, with a limit of one second in total: a robot session stays only if that limit expires |
| **Not verified** | A real change of world in a client. A request that is open when a real server stops. The screen in a client with the corrected code: the line of the proposal, and the buttons with the new actions. A real client without operator permissions that sends the commands. A server stop in which CC: Tweaked exceeds its limit and leaves a robot session (D24). The Claude provider with the real service after the corrections |

Conditions built artificially in the tests of the review:

- **Session (finding 2):** the test calls `Uno.newSession()`, the function that each server start calls. It does not start a second server. The player, the robot and the stations are the same before and after, because the test uses one rig.
- **Robots of an earlier session:** the test calls the listener of the server start event with a new event object. It does not post the event, because CC: Tweaked also listens to it. It does not start a second server. It has its own batch: the listener ends the robot sessions of all running tests.
- **Slow provider (findings 1 and 2):** a provider written in the test waits for a signal of the test, then gives a valid order.
- **Old view (finding 4):** the test reads the actions of the buttons from the view of job A and sends them after job B starts. It calls the function that the network message calls.
- **Players of the status command (finding 5):** the owner and the other player are player entities of the test system, not clients. The operator is the second player with permission level 2.
- **Inventory not loaded (finding 6):** the test moves the inventory of the station record to a position 1,000,000 blocks away. The dock stays. The runtime refuses such a station at the registration.
- **Full robot (finding 6):** the test fills the 16 slots of the turtle with cobblestone.
- **Record of an older version (finding 6):** the test removes the cause from the saved data of the job, then loads them.
- **World older than the record (`t11_resume_when_the_world_is_older`):** the test writes in the record that the load is delivered, with a time of the last transfer in the future. It does not stop the server. The real sudden stop is in the `crash` and `check-crash` phases.

Changes to the older tests:

- `t18_claude_provider`: the expectation "the proposal stays after the next request fails" described finding 1. It now expects the opposite.
- `t18_provider_failures`: the test sent three and four requests of one player in the same tick. Uno now accepts one request at a time from each source. The test sends them one after the other.
- `s2_tablet_view`: the test reads the "Confirm" action from the view, and uses the actions with the job ID. It also sends a "Confirm" without the number of the proposal: nothing starts.
- `s1_transfer` and `s2_keep_stock`: the module versions are `transport@2` and `keep_stock@2`.
- `t18_claude_provider`, second change: the first of two requests in the same tick now has a provider that waits for the test. See run 28.

## Increment A — The provider answers with an action

Ticket #2 of spec #1, on 2026-11-13. See D29.1.
All tests of this section are in the game and automatic. They use the fixed rules, providers written in the tests, or the Claude provider with a false server in place of the API.
No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.

| Run | Code | Result |
| --- | --- | --- |
| Red | The new and changed tests, with the old code of Uno and of the providers | 40 of 44. The four tests below failed as expected. The other 40 passed |
| Green | The code of ticket #2 | **44 of 44**. Server line: `All 44 required tests passed :)` |
| After the merge | The code of ticket #2, merged with tickets #6 and #7 of increment A | **45 of 45**. Server line: `All 45 required tests passed :)` |

| Test | What it does | Red run, old code | Green run, new code |
| --- | --- | --- | --- |
| `s2_uno_reference_sentence` | The reference request of S2 in English and in Italian, and a transport, to the fixed rules. Then a request without a quantity | Failed: the answer was an order without an action | Passed. Each answer is the action `start` with the same order as in S2, and zero tokens. The request without a quantity gives the action `none` with the reason "the quantity is missing", and zero tokens |
| `t18_provider_failures` | The cases of S2, and two more: an action outside the list, and a provider that throws an `IllegalArgumentException` | Failed: the exception gave "I cannot propose a job: a defect of the provider." | Passed. The exception gives "I cannot interpret the request now (a defect of the provider)." The action `dig` gives "I did not get a valid configuration". The fixed rules still give "I cannot propose a job: the quantity is missing." |
| `t18_claude_provider` | The false API answers with actions in the shape of the new schema. The test calls the provider directly one time, then through Uno | Failed: the provider reported 0 input tokens and 0 output tokens. The later checks of the test did not run | Passed. The direct call gives the action of the answer, with 1234 input tokens and 56 output tokens. Through Uno: `none` gives its reason, `pause` gives the fixed sentence, `start` gives the proposal "carry 8 x minecraft:coal". A refusal, a cut answer, error 401, error 500 and a stopped API give their failure sentences, as before |
| `t18_claude_provider` | Reads the request that went to the false API | Not reached | Passed. The schema has the seven actions and ten required fields. The system text names the seven actions. The context is the same as before: exactly the two stations of the player and its robot with the "free" flag |
| `a_provider_answers_with_an_action` | A job waits for materials. A provider answers `change`, `pause`, `resume`, `cancel` and `report`. Then three answers with `none` | Failed: `change` gave "I did not get a valid configuration" | Passed. Each of the five actions gives its fixed sentence, for example "I cannot pause a job from a written request yet. Use the buttons of the screen or the commands. I changed nothing." No proposal. The job keeps its ID, the state `WAITING`, quantity 24, reserve 0 and its revision. A reason of 300 characters is cut to 200. `None` with a capital letter works. A `none` with a blank reason gives "I did not get a valid configuration" |
| `s2_uno_request`, `s2_tablet_view`, `t22_confirm_only_the_shown_proposal`, `t22_t23_proposals_and_contact_points`, `t23_state_of_an_earlier_session` | The S2 flow, with the action `start` of the fixed rules or of providers of the tests | Passed | Passed. The same proposal, confirmation and job as before |

Ticket #2 changed no part of the runtime and no Lua module. Thus the four restart phases were not repeated for it.

Code examination, not a test: `LiveTests.java` compiles with the new contract. Its check "the fixed rules cannot read this request" now reads the action `none`. Before, it waited for an exception.

Changes to the older tests:

- `s2_uno_reference_sentence`: it expects the action `start` and zero tokens. It also asks for a request without a quantity.
- `t18_provider_failures` and the providers written in the other tests: they answer the action `start` with the order.
- `t18_provider_failures`: two more cases. A provider that throws an `IllegalArgumentException` now expects the sentence of a failure.
- `t18_claude_provider`: the false API answers with actions. Two more cases: a direct call for the tokens, and a `pause`. The check of the schema and of the system text is new. The check of the context is exact now.

## Increment A — Report and pause by words

Ticket #3 of spec #1, on 2026-11-13. See D29.2.
All tests of this section are in the game and automatic. They use the fixed rules, providers written in the tests, or the Claude provider with a false server in place of the API.
No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.

Rig: two tests use one rig. `a_report_and_pause_by_words` uses two rigs of one player, side by side: the second is 3 blocks to the south of the first.
The sources are empty. Thus each job waits for materials, and its state does not change by itself during the test.

| Run | Code | Result |
| --- | --- | --- |
| Red | The new and changed tests, with the code before the change of Uno | 44 of 49. The five tests below failed as expected. The other 44 passed. Server line: `5 required tests failed :(` |
| Green, first | The code of ticket #3 | 48 of 49. `a_t19_words_of_another_player` failed for an error of the test, not of the mod. The test built the text "Pause robot N" before the enrollment, when the rig had no robot number yet: the text was "Pause robot -1". The fixed rules read it as robot 1, and the sentence named robot #1. The test now builds the text when its step runs |
| Green | The code of ticket #3, with the corrected test | **49 of 49**. Server line: `All 49 required tests passed :)` |
| After the merge | The code of ticket #3, merged with tickets #2, #5, #6 and #7 of increment A | **53 of 53**. Server line: `All 53 required tests passed :)` |

| Test | What it does | Red run, old code | Green run, new code |
| --- | --- | --- | --- |
| `a_fixed_rules_report_and_pause` | Twelve requests to the fixed rules: a report and a pause in English and in Italian, with a station, with "robot N" and with no target. Then a request with a pause word and two stations | Failed: each of the twelve requests gave `none` with "the quantity is missing" | Passed. Each request gives the expected action and target, for example "How is the furnace?" gives `{"action":"report","robot":null,"station":"furnace"}` and "Sospendi il robot 7" gives `{"action":"pause","robot":7,"station":null}`. Zero tokens. The request with two stations stays a `start` |
| `a_report_and_pause_by_words` | Two jobs of one player wait. Both use one destination. A provider of the test gives reports and pauses with each kind of target | Failed: the first report gave "I cannot report on a job from a written request yet" | Passed. A report by a station of one job and a report by a robot number give the line of that job only. A report without a target gives the two lines. A station of two jobs, no target with two jobs, a station of no job and a robot of no job give the count and the question, for example "2 of your open jobs use the station '…'. Tell me the robot number of the job. I did nothing." No job changed and no proposal. A pause by a station pauses that job only: "Job … on robot #… is paused. The cargo stays on board." Its state is `PAUSED` with the reason "paused by the owner". A second pause gives "I cannot pause the job: Job … is not running (PAUSED)." After the cancel of the second job, a pause without a target pauses the first job |
| `a_t19_words_of_another_player` | A different player names the job of the owner by its robot number, by its two stations and with no target, in a report and in a pause. Then "Pause robot N" through the fixed rules | Failed: the first pause gave "I cannot pause a job from a written request yet" | Passed. Each sentence is the sentence for a job that does not exist, for example "You have no job on robot #…". A report without a target gives "I have no record of a job of yours." No sentence contains the job ID, a delivery or a pause. The job of the owner stays `WAITING`. No proposal |
| `a_report_and_pause_through_the_view` | The "ask" action of the screen, with the fixed rules of the server: "How is a_view_destination?", "Metti in pausa", "Pause robot N", "Rapporto" | Failed: the screen showed "I cannot propose a job: the quantity is missing." | Passed. The screen shows the report of the job, then "Job … on robot #… is paused. The cargo stays on board." The job is `PAUSED`, the screen has the "Resume" button and no "Confirm" button. The second pause gives the refusal of the runtime. "Rapporto" gives the report: "I am paused at your request." |
| `t18_claude_provider` | The false API answers `pause` for the robot of the test, which has no job | Failed: "I cannot pause a job from a written request yet." | Passed: "You have no job on robot #…. Tell me the robot number of the job. I did nothing." No proposal. The other cases give the same sentences as before |

Ticket #3 changed no part of the runtime and no Lua module. Thus the four restart phases were not repeated for it. The merge brought the runtime and the Lua modules of ticket #5. Ticket #5 ran the four phases on its merged code. This merge did not repeat them.

Changes to the older tests:

- `a_provider_answers_with_an_action`: the cases `pause` and `report` are gone. The new tests check them. The cases `change`, `resume` and `cancel` keep their fixed sentence.
- `t18_claude_provider`: the case `pause` expects the sentence of the search, not the fixed sentence.

## Increment A — Change, resume and cancel by words

Ticket #4 of spec #1, on 2026-11-13. See D29.3.
All tests of this section are in the game and automatic. They use the fixed rules or providers written in the tests.
No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.

Rig: the ordinary rig of one robot. `a_change_by_words` has 30 coal in the source. `a_resume_and_cancel_by_words` has 64.
`a_confirmation_of_older_values` and `a_change_through_the_view` have an empty source: each job waits for materials, and its state does not change by itself.

| Run | Code | Result |
| --- | --- | --- |
| Red 1 | The test of the fixed rules, with the rules before the change | 53 of 54. `a_fixed_rules_change_resume_cancel` failed as expected: 13 requests gave `none`, and "Riprendi il lavoro in pausa" gave `pause`. The report and the start of the test already gave the expected answer. Server line: `1 required tests failed :(` |
| Green 1 and Red 2 | The new fixed rules. The two tests of the change by words | 54 of 56. `a_fixed_rules_change_resume_cancel` passed. `a_change_by_words` and `a_confirmation_of_older_values` failed as expected: "I cannot change a job from a written request yet." |
| Green 2 | The change by words | 55 of 56. `a_change_by_words` passed. `a_confirmation_of_older_values` passed its cases about the change. It failed, as expected, at the cancellation: "I cannot cancel a job from a written request yet." |
| Red 3 | The tests of the resume, the cancellation and the screen | 56 of 58. `a_resume_and_cancel_by_words` and `a_confirmation_of_older_values` failed as expected: "I cannot resume a job …", "I cannot cancel a job …". `a_change_through_the_view` passed at its first run: after Green 2 the screen already showed the text of each proposal. Thus no run shows it failing |
| Green 3 | The code of ticket #4 | **58 of 58**. Server line: `All 58 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash` | The code of Green 3. The runtime has the two new check functions | Passed, each phase. `check`: the record says `RUNNING` with 24 on board, then `DONE`, source 40, destination 24. `check-crash`: `RECOVERY` ("the world is older than the record of this job"), then `DONE` after `resume`, delivered 24, "1 recovery". After the four phases the data folder has no temporary file |
| After the merge | The code of ticket #4, merged with tickets #8 and #11 of increment A. One conflict in `MindRuntime.control`: the pause of ticket #8 now writes its state in `control`, with the flag of a request of the owner, and the check stays in `checkControl` | **70 of 70**. Server line: `All 70 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash`, after the merge | The merged code | Passed, each phase, with the same results as above. After the four phases the data folder has no temporary file |

| Test | What it does | Observed result |
| --- | --- | --- |
| `a_fixed_rules_change_resume_cancel` | Sixteen requests to the fixed rules: a change, a resume and a cancellation in English and in Italian, with a station, with "robot N" and with no target | Each request gives the expected action. "Leave only 16 in the depot" gives `{"action":"change","robot":null,"station":"depot","quantity":null,"reserve":16}`. "Porta la quantità del robot 7 a 30" gives the quantity 30 for robot 7. "Riprendi il lavoro in pausa" is a resume. "How is the reserve of robot 7?" stays a report: the robot number is not a reserve. A request with two stations and "leave 8" stays a start with the reserve 8. Zero tokens |
| `a_change_by_words` | A transport of 24 with the reserve 20. The source has 30: the robot delivers 10 and waits. Then "Leave only 6 in the a_change_source" through the fixed rules | The proposal: "Proposal: change job 6 on robot #7 (carry minecraft:coal from 'a_change_source' to 'a_change_destination'). Quantity to carry: 24, unchanged. Reserve to leave in 'a_change_source': from 20 to 6. I have done nothing yet. Confirm with /uno confirm." 6 seconds later: reserve 20, quantity 24, revision 0, `WAITING`, source 20, destination 10. `/uno confirm` gives "Job 6 on robot #7 changed. Quantity to carry: 24. Reserve to leave in 'a_change_source': 6. The robot uses the new values at its next step." Reserve 6, revision 1. Then `DONE`: source 6, destination 24, robot 0 |
| `a_confirmation_of_older_values` | A start for the busy robot. A change of the quantity, then the "Apply" button, then `/uno confirm`. A new request after a proposal. A change, then a different job on the robot with the same revision. A cancellation, then a different job | The start: "I cannot propose this job: Robot #… already has job … (WAITING)." No proposal. The proposal of the quantity says "Quantity to carry: from 24 to 30. Reserve to leave in '…': 0, unchanged." After "Apply" (reserve 5, revision 1) the confirmation gives "I cannot change the job: Job … changed. Read it again." The job keeps quantity 24, reserve 5, revision 1. A report request removes the proposal of a change: `/uno confirm` gives "I have no pending proposal". The change after a new job gives "I cannot change the job: Robot #… has job … now, not job …. Read it again." The new job keeps its values. The cancellation after a new job gives "I cannot cancel the job: Robot #… has job … now, not job …. Read it again." The new job stays open |
| `a_resume_and_cancel_by_words` | A different actor empties the robot after the pickup: `RECOVERY`. "Resume the job of a_resume_destination" through the fixed rules. Then "Continua". The robot takes a new load and the owner pauses. Then "Annulla il lavoro di a_resume_source". Then a cancellation of the closed job | The proposal: "Proposal: resume job 5 on robot #22 (carry minecraft:coal from 'a_resume_source' to 'a_resume_destination'). The job needs a check: the runtime accepts the load that it observes in the robot, and the totals can be inexact. …" The job stays `RECOVERY` until `/uno confirm`. Then: "I resume job 5 on robot #22.", 1 recovery, 0 on board. "Continua" gives "I cannot propose to resume the job: Job 5 is already running." No proposal. The cancellation proposal: "… The cargo stays on board the robot. …" The job stays `PAUSED` until the confirmation. Then: "Job 5 on robot #22 is cancelled. The cargo stays on board the robot." `CANCELLED`, 24 in the robot. The last request gives "I cannot propose to cancel the job: Robot #22 has no open job." No proposal |
| `a_change_through_the_view` | "Lascia solo 5 nel a_viewchg_source" through the "ask" action of the screen. A different player. Then the "Confirm" button of the owner | The view has "Waiting for your confirmation: change job … (carry …). Quantity to carry: 24, unchanged. Reserve to leave in 'a_viewchg_source': from 0 to 5." The action of the button is `confirm:` with the number of the proposal. The number field of the reserve shows 0. The view of the other player has no "Confirm" button and no station of the owner. The other player presses the button of the owner and gives `/uno confirm`: "I have no pending proposal". A change request of the other player for the robot of the owner gives "You have no job on robot #…. Tell me the robot number of the job. I did nothing." The job does not change. The button of the owner: reserve 5, quantity 24, revision 1. The view has "Uno › Job … on robot #… changed." The number field shows 5 and the "Confirm" button is gone |
| `a_provider_answers_with_an_action` | A change without a new value, with the quantity "many", with the quantity -5. Then the three cases of `none` | "I cannot propose a change: the request gives no new quantity and no new reserve. I created no proposal." "I did not get a valid configuration. I created no proposal." "I cannot propose this change: The quantity must be positive. The reserve must not be negative." No proposal. The job does not change. The three cases of a change were added in Green 3 and passed at their first run |
| The other 52 tests | The S1 and S2 flows, the buttons, the number fields and the commands | Passed. Among them `s2_tablet_view`, `t16_actions_of_an_old_view` and `s1_commands`: the buttons, the number fields and the commands work as before |

Changes to the older tests:

- `a_provider_answers_with_an_action`: the cases `change`, `resume` and `cancel` with the fixed sentence are gone. The new tests check these actions. Three cases of a change that Uno must refuse are new.

| Kind of evidence | Statements |
| --- | --- |
| **Verified automatically, in the game** | All rows of the tables above |
| **Deduced from a code examination** | A proposal of a change, of a resume or of a cancellation is not saved and belongs to one server session: Uno keeps it in the same session object as the proposal of a start. `t23_state_of_an_earlier_session` tests this object with a start, not with a change. `/uno confirm` calls the same function `Uno.confirm` as the tests |
| **Not verified** | The real Claude service with a change, a resume or a cancellation. The proposal and the button in a client. A resume proposal for a job that goes to `RECOVERY` before the confirmation (D29.3) |

## Design discussion of 2026-11-13 — code examinations, no test

No test ran in this discussion. The facts below come from code examinations. They support D28, D33, D34, D36 and the proposals D06.1 and D13.1.

| Fact | Where | What it does not prove |
| --- | --- | --- |
| `can_push = true`. The comment of the option says that turtles "push entities out of the way instead of stopping if there is space to do so" | `computercraft-server.toml` in the world of the test server | That a robot pushes a player in a corridor. Not tried |
| The move command contains the message "Cannot leave loaded world" | The text constants of `TurtleMoveCommand` in the CC: Tweaked jar | The behaviour of a robot of this mod at the edge of a loaded area |
| The move step tries x, then z, then y. It accepts each move that brings the robot nearer to the target | `transport.lua`, the first function `step` | That a path with a corner works. No test had one. A later test has one: see "Increment A — a path with one corner" |
| Forge has `ForgeChunkManager.forceChunk`. The restart test keeps its chunk loaded with `setChunkForced` | The Forge 47.4.28 jar, `RestartTests.java` | `/forceload` in a client |
| The public API of CC: Tweaked has `IComputerAccess.mount`, `mountWritable`, `ComputerCraftAPI.createSaveDirMount` and `ITurtleUpgrade` | The CC: Tweaked 1.121.0 jar | That a shared directory or a turtle upgrade works in this mod |
| `/uno ask` has no permission requirement, no counter, and no limit other than one request at a time for each player | `MindCraft.java`, `Uno.java` | The rate of requests that a command block can make |

## Increment A — atomic write of the record (ticket #7)

Date: 2026-11-13. The runtime now writes `mindcraft.dat` through a temporary file `mindcraft.dat.tmp` in the same folder. One move then puts that file in the place of the record. See D37.

No automatic test stops the process during a write. The protection against such a stop rests on a code examination only.

### Runs

| Run | Type | Result | Observed result |
| --- | --- | --- | --- |
| Normal run | In the game, automatic | **43 of 43** | 92 transfers wrote the record through the new write. No write error in the log. After the run the data folder contains `mindcraft.dat` and no temporary file |
| `stop`, `check`, `crash`, `check-crash` | In the game, automatic | Passed, each phase | The same results as before the change. `check`: the record says `RUNNING` with 24 on board, then `DONE`, source 40, destination 24. `check-crash`: the record written at the delivery loads, the job goes to `RECOVERY` ("the world is older than the record of this job"), then `DONE` after `resume`. After each phase the data folder contains no temporary file |
| `stop`, then `check` with a failure of the write | In the game, automatic, with a condition built by hand | Passed | See below |

The failure of the write, condition built by hand: after the `stop` phase, a directory with one file inside was put at the place of `mindcraft.dat.tmp`. Thus the runtime cannot write the temporary file. Then the `check` phase ran.

- The test passed. The robot delivered: its state is in memory.
- The server log has two errors: one at the delivery and one at the save of the stop. Text of the second: "MindCraft could not write its record to .\world\data\mindcraft.dat. The earlier record stays on the disk.", with the cause `FileNotFoundException ... mindcraft.dat.tmp (Access is denied)`.
- After the run `mindcraft.dat` was identical, byte for byte, to the copy taken after the `stop` phase.
- This is one run, not a test of the repository. The directory was removed after the run.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The ordinary save of the world calls `SavedData.save(File)` for each data of the world: `DimensionDataStorage.save()`. `flush()`, after each transfer, calls the same method. `MindRuntime` overrides it. Thus one method does both writes | `javap` of the Forge 47.4.28 jar (official mappings), `MindRuntime.java` | |
| The earlier write, `SavedData.save(File)` of Minecraft 1.20.1, opens a `FileOutputStream` on the record itself. That stream empties the file first. A stop during the write left a partial file. Minecraft logs the error of a later load and creates empty data | `javap` of `SavedData`, `NbtIo` and `DimensionDataStorage` | |
| The new write builds the same content as Minecraft (the `data` tag and the data version). It writes it to `mindcraft.dat.tmp` and closes the file. Then it calls `Files.move` with `ATOMIC_MOVE` | `MindRuntime.save(File)` | That the operating system does the move in one step |
| On Windows, `Files.move` with `ATOMIC_MOVE` calls `MoveFileEx` with `MOVEFILE_REPLACE_EXISTING` and nothing else | `sun/nio/fs/WindowsFileCopy.java` in the `src.zip` of Temurin 17.0.21 | Microsoft does not document `MoveFileEx` as atomic. On Linux the JDK uses `rename`, which POSIX defines as atomic. This is general knowledge: the Unix sources of the JDK were not read |
| A stop during the write leaves the complete `mindcraft.dat` and a partial `mindcraft.dat.tmp`. Minecraft reads only `mindcraft.dat`. The next write opens the temporary file with `FileOutputStream`, which empties it first | `DimensionDataStorage.getDataFile`, `NbtIo.writeCompressed` (`javap`) | |
| A failure of the write or of the move does not touch `mindcraft.dat`. The runtime deletes the temporary file, logs an error, and stays dirty. Thus the next save or transfer tries again. Minecraft instead marks its data as clean after a failed write | `MindRuntime.save(File)`, `javap` of `SavedData` | |
| The runtime does not force the data to the disk before the move | `MindRuntime.save(File)` | A stop of the operating system or a loss of power during a write. Not covered |

### Not covered

- A stop of the process during a write. No test can stop the process at that moment.
- A stop of the operating system or a loss of power during a write.
- A program that holds `mindcraft.dat` open on Windows, for example a backup program. Then the move can fail: the earlier record stays and the log shows the error. General knowledge, not tried.

## Increment A — a path with one corner

Ticket #6. Test `a_path_with_one_corner`. Type: in the game, automatic. It verifies the fact of D28 about the corner.

Rig of this test. Each object is at y = 1. `#` is a stone wall. The walls leave one corridor, with one corner.

```
z \ x:  0          1      2   3   4   5        6
0       [source]   dock   .   .   .   corner
1                  #      #   #   #   turtle
2                  #      #   #   #   dock     [destination]
```

- The robot starts between the corner and the destination dock. The request is 24 coal of 64. The fuel is 1000, as in the other rig.
- Toward the source, the first axis of the move step (x) is walled. The step must try z. Toward the destination, the order x, then z fits the corridor.
- The test checks that the robot is at the corner with 0 on board (toward the source), then at the corner with 24 on board (toward the destination).
- At the end: `DONE`, 40 in the source, 24 in the destination, 0 in the robot, the robot on the destination dock, the walls still in place.

Runs of this ticket. They are not numbered in the table "Runs": other tickets of increment A ran at the same time.

| Run | Code | Result |
| --- | --- | --- |
| Red | The move step changed by hand for this run only: it tries only the first axis that differs. The change was not kept | 43 of 44. `a_path_with_one_corner` failed as expected: "expected the robot at 27, -59, 16 with 0 on board, but it is at 27, -59, 17". The first position is the corner, the second is the start. Job `BLOCKED`, "in front: minecraft:stone", "Movement obstructed". The other 43 tests passed: their paths are straight |
| Green | The move step of `transport.lua`, unchanged | **44 of 44**. Server line: `All 44 required tests passed :)` |

- No correction of the move step was necessary. No Lua module changed. Thus the four restart phases were not repeated.
- Not tested: a stair (a path that also changes y), more than one corner, a corridor without walls. In an open space the robot goes along x first, and it does not follow a corridor.
- Out of scope: a path that needs a move away from the target. The README says which paths work.

## Increment A — waits and blocks

Ticket #5 of spec #1, on 2026-11-13. See D34.
All tests of this section are in the game and automatic. No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.

| Run | Code | Result |
| --- | --- | --- |
| Red 1 | The new and changed tests of the obstacle, with the old code | 39 of 45. `t05_obstacle`, `t05_pause_and_cancel_during_the_wait` and `t05_obstacle_keep_stock` failed as expected: the job went to `BLOCKED` ("Movement obstructed"), not to `WAITING`. `s1_transfer`, `s2_keep_stock` and `t15_next_job_with_another_module` failed as expected: they wait for the new versions of the modules |
| Green 1 | The obstacle is a wait | **45 of 45**. Server line: `All 45 required tests passed :)` |
| Red 2 | The tests of the causes of `BLOCKED`, before the causes existed | 41 of 47. `t04_destination_removed`, `t04_source_removed`, `t06_no_fuel`, `t16_quantity_lowered_with_load`, `t16_quantity_lowered_after_partial_delivery` and `t16_quantity_below_delivered` failed as expected: the job was `BLOCKED` without a cause. `t06_impediment_of_an_unknown_kind` passed already: the code of Green 1 needed that branch for the module error of the agent |
| Green 2 | The causes of `BLOCKED` | **47 of 47**. Server line: `All 47 required tests passed :)` |
| After the merge | The code of ticket #5, merged with tickets #2, #6 and #7 of increment A | **49 of 49**. Server line: `All 49 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash` | The code of Green 2 | Passed, each phase. `check`: the record says `RUNNING` with 24 on board, then `DONE`, source 40, destination 24, executor `transport@3`. `check-crash`: `RECOVERY` ("the world is older than the record of this job"), then `DONE` after `resume`, source 40, destination 24, "1 recovery" |
| `stop`, `check`, `crash`, `check-crash`, after the merge | The merged code, with the atomic write of ticket #7 | Passed, each phase, with the same results. After the four phases the data folder has no temporary file |

| Test | What it does | Observed result |
| --- | --- | --- |
| `t05_obstacle` | A stone on the path, after the pickup. Then a window of 15 seconds. Then the test removes the stone | `WAITING` with the cause `PATH_OBSTRUCTED`, 235 ticks after the pickup (run "After the merge"; the test asks for at least 160). 24 on board, the stone intact. The reason with the position is in the record, in the view of the screen and in the `status` text: "the robot cannot move from … (east, in front: minecraft:stone) toward …: Movement obstructed". The report: "My path is obstructed. I try to move again every few seconds, and I continue by myself when the path is free." It has no recorded reason. In the window the robot did not move. The longest time without a call of the robot was 100 ticks (the test accepts 80 to 150): one attempt every 5 seconds. After the removal: `RUNNING`, then `DONE`, source 40, destination 24, robot 0. No `resume` |
| `t05_pause_and_cancel_during_the_wait` | During the wait: a pause, the removal of the stone, a `resume` with the stone back, then a cancellation and the removal of the stone | After the pause the job stays `PAUSED` for 14 seconds. The robot does not move, also without the stone. After `resume` the job waits again with `PATH_OBSTRUCTED`. After the cancellation the job stays `CANCELLED`. The robot does not move. 24 stay on board |
| `t05_obstacle_keep_stock` | A stock job. A stone on the path after the pickup | `WAITING` with `PATH_OBSTRUCTED`, 24 on board. After the removal: `SATISFIED`, destination 24, source 40, robot 0. No `resume` |
| `t06_no_fuel` | A turtle with fuel 0 and no fuel item | `BLOCKED` with the cause `OUT_OF_FUEL`. Report: "I am stopped: I have no fuel. Give me fuel, then resume the job." After the test sets the fuel: still `BLOCKED` 7 seconds later, nothing taken. After `resume`: `DONE`, source 40, destination 24 |
| `t06_impediment_of_an_unknown_kind` | The test reports the impediment "landslide" for the robot | The function returns `BLOCKED`. The job is `BLOCKED` without a cause. Reason: "landslide: the floor fell at 1 2 3". Report: "I am stopped and I need your help. Recorded reason: landslide: the floor fell at 1 2 3." 3 seconds later still `BLOCKED`, nothing taken |
| `t04_destination_removed` | The destination chest disappears while the robot carries 24 | `BLOCKED` with the cause `DESTINATION_NO_INVENTORY`. 24 on board. Report: "I am stopped: I find no inventory at 't04_destination'. Put it back, then resume the job." A save and a load keep the cause. Without the cause in the saved data, the loaded record gives "I am stopped and I need your help. Recorded reason: the destination station has no inventory at …", as before. After the chest and `resume`: `DONE` |
| `t04_source_removed` | The source chest disappears before the start | `BLOCKED` with the cause `SOURCE_NO_INVENTORY`, 0 on board. Report: "I am stopped: I find no inventory at 't04src_source'. …". After a chest with 24 and `resume`: `DONE` |
| `t16_quantity_lowered_with_load`, `t16_quantity_lowered_after_partial_delivery`, `t16_quantity_below_delivered` | The owner lowers the quantity of a transport with a load on board | `BLOCKED` with the cause `LOAD_ABOVE_QUANTITY`. Report, for example: "I am stopped: the quantity of the job is reached, and 54 stay on board. Raise the quantity and resume, or cancel the job." The other results are the same as before |
| `t12_second_session` | A second turtle with the same computer ID | Runs Green 1, Green 2 and "After the merge": the second turtle waits behind the first, with `PATH_OBSTRUCTED` and "in front: computercraft:turtle_normal". The first turtle lost its authority and does not move: the wait has no end. No coal lost |
| `s1_transfer`, `s2_keep_stock`, `t15_next_job_with_another_module` | The versions of the modules | `transport@3` and `keep_stock@3` |

Conditions built artificially:

- **Interval of the attempts:** the test does not count the moves. It reads the game time of the last call of the robot (`lastContact`, in the `status` text as "last contact"). Each attempt calls the runtime.
- **Unknown kind:** the test calls the Lua function `impediment` from Java, in the tick of the start. No module of the mod sends that kind.
- **Record of an older version:** the test removes the cause from the saved data of a blocked job, then loads them.
- **No fuel:** the test sets the fuel level of the turtle with a direct call.
- **Source removed:** the source chest is empty when the test removes it. A chest with items drops them.

Changes to the older tests:

- `t05_obstacle`: it expects `WAITING`, and the end without `resume`. It uses a player, for the report.
- `t04_destination_removed`, `t06_no_fuel` and the three `t16_quantity_*` tests above: they expect their cause. `t04_destination_removed` and `t06_no_fuel` use a player, for the report. `t06_no_fuel` waits 7 seconds with fuel before the `resume`.
- `t12_second_session`: a wait for a free path is also an accepted end of the second turtle.
- `s1_transfer`, `s2_keep_stock`, `t15_next_job_with_another_module`: the versions `transport@3` and `keep_stock@3`.

| Kind of evidence | Statements |
| --- | --- |
| **Verified automatically, in the game** | All rows of the tables above |
| **Deduced from a code examination** | The hologram shows the attention colour during the wait: `MindRuntime.mood` gives `ATTENTION` for each `WAITING`. `s2_projector` tests the colour with a wait for materials, not with an obstacle. The mod uses only `dan200.computercraft.api`: the new Lua functions are in `RobotApi`, with the same imports as before |
| **Not verified** | A player on the path (`can_push = true`). A robot at the edge of a loaded area ("Cannot leave loaded world" is also an obstacle). Two robots that face each other. The screen in a client during the wait |

Known limit: a robot waits without an end behind an obstacle that does not go away. The report and the hologram show the wait. The notice after a long wait is ticket #9: see "Increment A — a notice for a long wait".

## Increment A — the job diary and the notices

Ticket #8 of spec #1, on 2026-11-13. See D30 and D31.
All tests of this section are in the game and automatic. No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.
No test has a connected player. The tests replace the exit of the notices with one that records the owner and the text of each notice, then sends the notice as before.

| Run | Code | Result |
| --- | --- | --- |
| Red 1 | The test of the diary, with a diary that stays empty | 49 of 50. `a_diary_of_a_job` failed as expected: the diary of the job was empty |
| Run 2: green 1, red 2 and 3 | The diary, without the limit and without notices. The tests of the limit and of the notices | 52 of 56. `a_diary_of_a_job` passed. `a_diary_limit` failed as expected: "the owner has 221 entries". `a_notices_for_blocked_recovery_and_done`, `a_no_notice_for_a_resume_that_completes_the_job` and `a_notices_call_no_provider` failed as expected: no notice. `a_no_notice_for_the_ordinary_cycle` and `a_no_notice_for_a_console_job` passed: without notices they cannot fail. They guard against a notice that must not come |
| Run 3: green 2 and 3, red 4 | The limit, and the notices with the templates of `OUT_OF_FUEL`, `RECOVERY` and `DONE`. The notice checks in four older tests | 53 of 56. `t04_destination_removed`, `t04_source_removed` and `t16_quantity_lowered_with_load` failed as expected: their notice gave the recorded reason, not the cause. Example: "… is blocked. Recorded reason: the source station has no inventory at 55, -59, 9." `t06_impediment_of_an_unknown_kind` passed: it has no cause |
| Green | The templates of the other causes of `BLOCKED` | **56 of 56**. Server line: `All 56 required tests passed :)`. No event of a robot was dropped |
| `stop`, `check`, `crash`, `check-crash` | The code of Green, with the checks of the diary in the restart test | Passed, each phase. `check`: the diary after the restart is `RUNNING`, and at the end `RUNNING, DONE`. `check-crash`: after the restart `RUNNING, DONE, RECOVERY`, at the end `RUNNING, DONE, RECOVERY, RUNNING, DONE`. After the four phases the data folder has no temporary file |
| After the merge | The code of ticket #8, merged with tickets #2, #3, #5, #6, #7 and #11 of increment A. `a_notices_call_no_provider` uses `CountingModel` of ticket #11 | **65 of 65**. Server line: `All 65 required tests passed :)` |

| Test | What it does | Observed result |
| --- | --- | --- |
| `a_diary_of_a_job` | A transport of a player. The source is empty: the job waits. 7 seconds later the test fills the robot with cobblestone and puts 24 coal in the source: the robot is full. Then the test frees one slot. After `DONE` it starts a second job on the robot | The entries of the first job: `RUNNING`, `WAITING SOURCE_AT_RESERVE`, `WAITING ROBOT_FULL`, `RUNNING`, `DONE`. The robot tried two times or more during the first wait: one entry. Each entry has the owner, the job, the robot, the two stations, coal, quantity 24 and reserve 0. Delivered and on board: 0 and 0 three times, then 0 and 24, then 24 and 0. The times grow, from the start to the end. The reason of the third entry: "the robot inventory is full". After the second job the five entries of the first job stay, and the second job has its entry `RUNNING`. A different player reads no entry of the owner. The console reads the same entries. A save and a load give the same diary. Without the diary in the saved data, the load gives an empty diary |
| `a_diary_limit` | An operator starts a job in its own name and cancels it. Then the player starts a job and gives 110 pauses and 110 resumes in one tick | The player has 200 entries: the oldest is a `PAUSED`, the newest a `RUNNING`. The runtime records 21 removed entries, with the game time of that tick. The two older entries of the operator stay: `RUNNING`, `CANCELLED`. Nothing of the operator is removed. A save and a load give the same 200 entries and the same record of the removal |
| `a_notices_for_blocked_recovery_and_done` | A transport of a player, with fuel 0. Then the test gives fuel and an operator resumes. After the pickup the test empties the robot. The owner resumes, pauses and resumes. After `DONE` the owner starts a second job and cancels it | Three notices to the owner, in this order: "Job N on robot #R (minecraft:coal from 'a_notice_source' to 'a_notice_destination') is blocked: the robot has no fuel.", "… needs a check: I cannot say how it went. Recorded reason: the robot holds 0, the record says 24.", "… is done: 24 delivered." 3 seconds in `BLOCKED` give no second notice. The resume of the operator, the three requests of the owner, the second job and its cancellation give none. The operator gets none. The notices about the two jobs go to the owner only |
| `a_no_notice_for_a_resume_that_completes_the_job` | The destination accepts 4 of 24. The test empties the robot. The owner lowers the quantity to 4 and resumes | One notice: "… needs a check: I cannot say how it went. Recorded reason: the robot holds 0, the record says 20." After the resume the job is `DONE`, and no second notice comes. The entries: `RUNNING`, `WAITING DESTINATION_FULL`, `RECOVERY`, `DONE` |
| `a_no_notice_for_the_ordinary_cycle` | A stock job. The destination has 10 and needs 24. The source has 40 and a reserve of 30. The robot brings 10 and waits at the reserve. The test adds 8. Then the owner pauses, resumes and cancels | The entries: `RUNNING`, `WAITING SOURCE_AT_RESERVE`, `RUNNING`, `SATISFIED`, `PAUSED`, `RUNNING`, `CANCELLED`. The destination has 24. No notice |
| `a_no_notice_for_a_console_job` | A transport of the console, with fuel 0. Then fuel and a resume | The entries: `RUNNING`, `BLOCKED OUT_OF_FUEL`, `RUNNING`, `DONE`. No notice about the job, and no notice to the console |
| `a_notices_call_no_provider` | A batch of its own. The provider of the server counts its calls. After the merge it is `CountingModel` of ticket #11: a provider with a model. A job of a player goes to `BLOCKED` without fuel, then to `DONE` | Two notices. The provider got zero calls |
| `t04_destination_removed`, `t04_source_removed`, `t16_quantity_lowered_with_load`, `t06_impediment_of_an_unknown_kind` | The older tests, with one more check in the `BLOCKED` state | One notice each: "… is blocked: there is no inventory at 't04_destination'.", "… is blocked: there is no inventory at 't04src_source'.", "… is blocked: the quantity of the job is reached, and 54 stay on board.", "… is blocked. Recorded reason: landslide: the floor fell at 1 2 3." |
| `t11_server_restart`, four phases | The restart test, with the diary | See the table of the runs above. Minecraft saved the diary with the world at the normal stop, and the runtime wrote it at the delivery before the halt |

Code examination, not a test:

| Fact | Where | What it does not prove |
| --- | --- | --- |
| In the game the exit calls `PlayerList.getPlayer(owner)`. If the owner is connected, it calls `ServerPlayer.sendSystemMessage` with "Uno › " and the text. If not, it sends nothing | `Notices.CHAT` | That a client shows the line. No test has a connected player |
| Only `MindRuntime.set` and the load of the record assign the state and the cause of a job. Thus each change of the state goes through the diary | `MindRuntime.java`, `RobotApi.java` | |
| The code of the notices has no reference to Uno or to a provider | `Notices.java` | |

Not covered:

- A notice in the chat of a client. It is a step of the checklist of the play session.
- A notice to an owner who is connected in a different dimension.
- A `RECOVERY` that the load of the record finds at the server start, for a job of a player. Its notice leaves before a player can be connected. The phase `check-crash` writes such an entry, but its job belongs to the console.

Changes to the older tests:

- `t04_destination_removed`, `t04_source_removed`, `t16_quantity_lowered_with_load` and `t06_impediment_of_an_unknown_kind`: one more check, the notice of the `BLOCKED` state.
- `t11_server_restart`: it checks the diary after the restart and at the end.

The merge brought no change of the runtime (`MindRuntime.java`, `RobotApi.java`) and of the Lua modules. Thus the four restart phases were not repeated after it.

## Increment A — a notice for a long wait

Ticket #9 of spec #1, on 2026-11-13. See D30.
All tests of this section are in the game and automatic. No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.
No test has a connected player. The tests read the notices through the exit of the notices, as in ticket #8.
No test waits 5 minutes. The tests give the moment of the check as a parameter: `MindRuntime.checkWait(job, now)`.

| Run | Code | Result |
| --- | --- | --- |
| Red | The new tests, with a check of the long wait that does nothing | 64 of 67. `a_long_wait` failed as expected: no notice 6000 ticks after the start of the wait. `a_notices_call_no_provider` failed as expected: two notices, not three, at the time limit of the test. `a_screen_model_lines` failed as a consequence: the failed test did not give back the provider of the server, thus the view showed a counter. `a_no_long_wait_for_the_stock_cycle` passed: without a long wait it cannot fail. It guards against a notice that must not come |
| Green | The long wait: the start of the wait in `MindRuntime.set`, the check, the entry, the templates, and the check in the server tick. One more check in `t05_obstacle`, added with this code: it has no red run | **67 of 67**. Server line: `All 67 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash` | The code of Green. The server tick now reads the record of the runtime at each tick, also before the first robot calls | Passed, each phase. `check`: the diary after the restart is `RUNNING`, and at the end `RUNNING, DONE`. `check-crash`: after the restart `RUNNING, DONE, RECOVERY`, at the end `RUNNING, DONE, RECOVERY, RUNNING, DONE`. After the four phases the data folder has no temporary file |
| After the merge | The code of ticket #9, merged with tickets #2, #3, #4, #5, #6, #7, #8 and #11 of increment A | **72 of 72**. Server line: `All 72 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash`, after the merge | The merged code. Ticket #4 also changed `MindRuntime.java` | Passed, each phase, with the same results. After the four phases the data folder has no temporary file |

| Test | What it does | Observed result |
| --- | --- | --- |
| `a_long_wait` | A transport of 24 for a player. The source is empty: the job waits. Then the test fills the robot with cobblestone and puts 12 coal in the source: the cause changes. Checks 5999 and 6000 ticks after the start of the wait, then 7200. Checks on copies of the record. Then the test frees one slot: the robot delivers 12 and waits again. Checks 5999 and 6000 ticks after the start of the new wait. Then the owner cancels | The entries before the check: `RUNNING`, `WAITING SOURCE_AT_RESERVE`, `WAITING ROBOT_FULL`. At 5999 ticks: no entry, no notice. At 6000 ticks: one long-wait entry with the cause `ROBOT_FULL`, the time of the check, 0 delivered and 0 on board, not requested. One notice: "Job N on robot #R (minecraft:coal from 'a_long_wait_source' to 'a_long_wait_destination') has waited for 5 minutes: the robot is full." At 7200 ticks: no second entry, no second notice. A copy of the record after the notice: a check at 12000 ticks writes no entry. A copy of the record before the notice: nothing at 5999 ticks, the long wait at 6000. A copy without the start of the wait, as a record of an older version: the check at 6000 starts the time, nothing at 11999, the long wait at 12000. The new wait: `RUNNING`, `WAITING SOURCE_AT_RESERVE`. Nothing at 5999 ticks. At 6000 ticks a second notice: "… has waited for 5 minutes: nothing remains in 'a_long_wait_source'." The cancellation gives no notice. Both notices go to the owner only |
| `a_no_long_wait_for_the_stock_cycle` | A stock job. The destination has 10 and needs 24. The source has 40 and a reserve of 30. The robot brings 10 and waits. The test adds 8: `SATISFIED`. A check 6000 ticks after the start of the first wait. Then the test empties the destination: the robot brings 4 and waits again. A check 6000 ticks after the start of the first wait, and one 5999 ticks after the start of the second. Then the owner cancels | The entries: `RUNNING`, `WAITING SOURCE_AT_RESERVE`, `RUNNING`, `SATISFIED`, `RUNNING`, `WAITING SOURCE_AT_RESERVE`, `CANCELLED`. No long wait. No notice |
| `t05_obstacle` | One more check during the wait for a free path, after the window of 15 seconds: the robot tried again during the window. A check 6000 ticks after the start of the wait | The entries: `RUNNING`, `WAITING PATH_OBSTRUCTED`: the attempts wrote no entry and did not start the time again. One notice: "… has waited for 5 minutes: the path of the robot is obstructed." |
| `a_notices_call_no_provider` | After `DONE`, the test empties the source and starts a second job: it waits. The test moves the start of the wait back by 6000 ticks. The check of the server tick runs with the real game time | The third notice comes from the check of the server tick: "… has waited for 5 minutes: nothing remains in 'a_notice_provider_source'." The provider got zero calls |

Conditions built artificially:

- **The moment of the check:** a parameter of the check. The long-wait entry gets that moment as its time. Thus in `a_long_wait` the time of the long-wait entry is later than the times of the entries after it.
- **The start moved back:** in `a_notices_call_no_provider` the test changes the start of the wait in the record of the job. Thus the server tick finds a long wait without a wait of 5 minutes.
- **A record of an older version:** the test removes the start of the wait from the saved data.
- **The copies of the record:** a copy that `load` makes in a test has no server. It sends no notice. Its diary shows whether it would send one: each notice comes from an entry.

Code examination, not a test:

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The server tick calls the check for each job at each tick, with the game time of the overworld | `MindCraft.tick` | That a wait of 5 real minutes gives a chat line |
| Only `MindRuntime.set` starts and ends a wait. A change of the cause keeps the start. Each other state clears it | `MindRuntime.java` | |
| The templates of `SOURCE_KEEPS`, `DESTINATION_FULL`, `SOURCE_UNLOADED`, `DESTINATION_UNLOADED`, `SOURCE_AT_RESERVE` with a reserve, and `NONE` | `Notices.java` | Their text in a run. No test builds these long waits |
| The code of the long wait has no reference to Uno or to a provider | `MindRuntime.checkWait`, `Notices.java` | |

Not covered:

- A wait of 5 real minutes, and its line in the chat of a client. They are for the checklist of the play session.
- A long wait across a real restart of the server. The restart phases build no wait. The save and the load in `a_long_wait` keep the start and the flag.

Changes to the older tests:

- `t05_obstacle`: one more check, the notice of a long wait.
- `a_notices_call_no_provider`: a second job that waits, and its notice. The test expects three notices.
- The helper `states` of the tests writes a long-wait entry as `LONG_WAIT` and its cause.

## Increment A — Limits on the model calls

Ticket #11 of spec #1, on 2026-11-13. See D33.1.
All tests of this section are in the game and automatic. They use providers written in the tests. One test uses the Claude provider with a local stand-in in place of the API.
No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.

| Run | Code | Result |
| --- | --- | --- |
| Red | The new tests, with the old behaviour of Uno and of the screen. The provider interface already had the model flag, and Uno had a counter that always gave zero | 44 of 50. The five new tests failed as expected. `t23_state_of_an_earlier_session` failed too, as a consequence of an error of a new test: see below |
| Green | The code of ticket #11 | **50 of 50**. Server line: `All 50 required tests passed :)` |
| After the merge | The code of ticket #11, merged with tickets #2, #5, #6 and #7 of increment A | **54 of 54**. Server line: `All 54 required tests passed :)` |
| After the second merge | The same, merged also with ticket #3 | **58 of 58**. Server line: `All 58 required tests passed :)` |

The error of the test in the red run: the first version of `a_screen_model_lines` made the Claude stand-in the provider of the server, and put the provider of before back only at its end. It failed before its end. Thus the stand-in stayed the provider of the server. `t23_state_of_an_earlier_session` runs in a later batch: its request through the screen went to the stand-in, and it got no proposal ("no proposal yet"). The test now changes the provider of the server only inside each step, and puts the provider of before back in a `finally`. The green run used this version.

| Test | Batch | What it does | Red run, old code | Green run, new code |
| --- | --- | --- | --- | --- |
| `a_model_requests_count` | Normal | An operator sends requests to a provider of the test with a model: an answer with 120 and 30 tokens, a request held open while a second one arrives, an exception, a time limit of 300 ms. Then a request to the fixed rules, and one request of the console | Failed: "expected 1 requests, 120 input tokens and 30 output tokens, but: Usage[requests=0, inputTokens=0, outputTokens=0]" | Passed. The open request counts before its answer. The second request gets "I am still working on your previous request.", does not reach the provider and does not count. The exception and the time limit count with zero tokens. The late provider returns 999 and 999 tokens after the time limit: the counter does not change. The fixed rules do not count. A different operator has zero. The console gets a counter of its own |
| `a_model_limit` | Normal | An operator sends 100 requests to the provider of the test, then three more requests: two of the same operator, one of a different operator | Failed: "the request after the limit went to the model" | Passed. 100 calls, counter 100 requests, 1000 and 200 tokens. Request 101 does not reach the provider. Its answer: "You reached the limit of 100 model requests in this server session. Until the server restarts, I read your requests with the fixed rules: they understand only simple sentences. I cannot propose a job: the quantity is missing." Request 102 gets only the answer of the fixed rules. The different operator still reaches the provider |
| `a_model_only_for_operators` | Normal | A player who is not an operator sends a request to the provider of the test with a model, then to a provider of the test without a model | Failed: the answer came from the provider with a model | Passed. The provider with a model gets no call. The answer is the answer of the fixed rules. No counter. The provider without a model answers as before |
| `a_new_session_restarts_the_counters` | Its own: `a_model_session` | An operator reaches the limit. Then the function that each server start calls | Failed: request 101 still went to the model | Passed. After the new session the counter is zero, and the next request goes to the model |
| `a_screen_model_lines` | Its own: `a_server_provider` | Views with the fixed rules on the server, then with the Claude provider against a local stand-in. An operator, a player who is not an operator, and an operator at the limit. Each one writes through the screen | Failed: the view of the operator had the line about Claude and no counter | Passed. With the fixed rules: no counter and no line about a provider. With Claude: the operator sees "Written requests go to Claude, a model of Anthropic." and "Your model requests in this session: 0 of 100. Tokens: 0 in, 0 out.", then "1 of 100. Tokens: 1234 in, 56 out." after one request to the stand-in. The other player sees no counter and "Uno uses the fixed rules for you: only an operator uses Claude." The operator at the limit sees "100 of 100", no line about Claude, and the line about the limit. The requests of the other player and of the operator at the limit do not reach the stand-in |
| `t18_claude_provider` | Normal | Changed: the requests through Uno use the code of the provider without its model flag | Passed | Passed |

This ticket changed no part of the runtime and no Lua module. Thus the four restart phases were not repeated for it. The merges brought the runtime and the Lua modules of ticket #5 without a change: ticket #5 ran the four phases on them. Ticket #3 changed no runtime and no Lua module.

Changes to the older tests:

- `t18_claude_provider`: its player is not an operator. With the rule of this ticket, its requests through Uno would go to the fixed rules. An operator owns each station of the server, and the exact check of the context needs the stations of one player. Thus the requests through Uno use `claude::answer`: the code of the provider, without its model flag. The direct call of the provider did not change.
- `t18_claude_live` (`LiveTests.java`): its player is an operator now. At the end it checks the counter: four requests, and more than zero tokens in and out. It compiles. It was not run: a run costs money and needs the consent of the project owner.

Code examination, not a test:

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The report, the confirmation and the buttons of a job do not call `Uno.ask`. Only the "ask" action of the screen and `/uno ask` do | `UnoView.act`, `MindCraft.registerCommands` | A test with a provider that counts its calls. It belongs to tickets #8 and #10 |
| At the time limit, `orTimeout` completes the request with an exception. The thread of the provider continues, and Uno does not read its answer | `Uno.ask` | The tokens that the real service bills for such a request. The counter shows zero for them |
| The actor of a command source has the operator flag of the source. A command block has the permission level 2 | `MindCraft.actor(CommandSourceStack)`. The level of a command block is general knowledge, not read in the sources | That `/uno ask` from a command block counts as a request of an operator. Not tried |

Not covered:

- The counter with the tokens of the real service.
- The screen in a client: the counter, the lines about the provider, and their length on the screen.
- A real client with permission level 0, and a single-player world without commands. The tests build the actors.
- The limit of the console: one test counts one request of the console. The limit itself is tested with an operator.

## Increment A — the summary at an opening

Ticket #10 of spec #1, on 2026-11-13. See D30, "Summary, as built".
All tests of this section are in the game and automatic. No test called the real Claude service.
The runs of this ticket are not numbered in the table "Runs": other tickets of increment A ran at the same time.
No test has a client. One test calls the methods of the tablet and of the projector with a fake player of Forge (`FakePlayerFactory`): a server player without a connection. The other tests call `UnoView.open`, the function that those methods call, with the moment of the opening as a parameter.

| Run | Code | Result |
| --- | --- | --- |
| Red 1 | `UnoView.open` builds the view and does nothing more. The tablet and the projector call it | 65 of 66. `a_summary_at_an_opening` failed as expected: "the first opening: Tell me what you need." |
| Run 2: green 1, red 2, 3 and 4 | The summary without the limit, without the line about the memory, and with the openings in memory only | 66 of 70. `a_summary_shows_the_10_newest` failed as expected: it showed the 12 events. `a_summary_says_where_the_memory_starts` failed as expected: "only removed entries: Tell me what you need." `a_summary_after_a_save_and_a_load` failed as expected: the loaded copy gave "From the start of my memory:" with both events. `a_t19_summary_of_another_player` passed: it guards against an event that must not come. `a_summary_at_an_opening` failed for an error of the test, after its check of the summary passed: see below |
| `stop`, `check`, red | The limit and the line about the memory. The openings are not saved yet. The restart test has the checks of the summary | `stop` passed. `check` failed as expected: "the summary after the restart: From the start of my memory: 0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." The diary checks before it passed |
| Green | The openings in the saved record | **70 of 70**. Server line: `All 70 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash` | The code of Green | Passed, each phase. `check`: the summary of the operator after the restart is "Since you last opened this screen:" and one line, "0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." `check-crash`: the same first line and three lines: the `DONE` of the first run, the `RECOVERY` "… needs a check: I cannot say how it went. Recorded reason: the world is older than the record of this job.", and the `DONE` after the resume. After the four phases the data folder has no temporary file |

The error of the test in run 2: the first version of `a_summary_at_an_opening` looked for the sentence of Uno in the text of the view. The view is JSON: Gson writes the line break as `\n` and the quote as `'`. The test now reads the text of the widget of Uno. The green run used this version.

| Test | What it does | Observed result |
| --- | --- | --- |
| `a_summary_at_an_opening` | A batch of its own: the provider of the server is a `CountingModel`. A transport of a player with fuel 0: `BLOCKED`. Then fuel and a resume of the owner: `DONE`. The player uses the tablet. Then the "Report" button. Then a click on the projector. A second job goes to `BLOCKED`. The screen asks for a new view ("refresh"). Then a second click on the projector | The first opening, with the tablet: "From the start of my memory:", "2 s ago: Job N on robot #R (…) is blocked: the robot has no fuel.", "0 s ago: … is done: 24 delivered." The start and the resume of the owner are not in it. The view shows the summary as the sentence of Uno. The report replaces it. The click on the projector without a new event keeps the report. The view after "refresh" keeps the report too. The second click: "Since you last opened this screen:" and only the block of the second job. The provider got zero calls |
| `a_summary_shows_the_10_newest` | In one tick, 12 impediments of unknown kinds through the function that the Lua modules call, each with a reason of its own. The owner resumes after each one. The opening is 12 minutes of game time later | "From the start of my memory:", "I do not show 2 older events.", then the events 3 to 12, the oldest first, each as "12 min ago: … is blocked. Recorded reason: kindN: event N." |
| `a_summary_says_where_the_memory_starts` | A first opening without events. Two ticks later, in one tick: a start, 110 pauses and 110 resumes of the owner. The runtime removes 21 entries. A copy of the record at that moment. Then one impediment. The openings are 3 hours later. Then the "Report" button and an opening 1 minute later | The first opening without events keeps "Tell me what you need." The copy: "Since you last opened this screen:" and "My memory starts 3 h ago. I do not remember older events." The running record after the impediment: the same two lines and "3 h ago: … is blocked. Recorded reason: kind: event." The opening after the report keeps the report |
| `a_t19_summary_of_another_player` | A block of the job of the owner. A different player opens, then the owner | The different player: Uno keeps "Tell me what you need.", and the view has no station and no event of the owner. Then the owner: "From the start of my memory:" and "0 s ago: … is blocked. Recorded reason: kind: event." |
| `a_summary_after_a_save_and_a_load` | One block before the first opening, one block two ticks after it. A save and a load of the record, then the openings two days later: on the loaded copy and on the running record. Then a load without the saved openings | The loaded copy and the running record give the same summary: "Since you last opened this screen:" and "2 days ago: … is blocked. Recorded reason: late: event." Without the saved openings: "From the start of my memory:" and both events, each "2 days ago" |
| `t11_server_restart`, four phases | An operator opens the screen after the start of the job, in the first run. After the restart and the end of the job, the operator opens again | See the table of the runs above. After the normal stop the summary has the first line of a later opening: the last opening of the first run survived the restart |

Code examination, not a test:

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The tablet and the projector call `Net.open`. The answer to an action of the screen, also the view of each second, calls `Net.sendView`. Only `Net.open` calls `UnoView.open` | `MindCraft.TABLET`, `ProjectorBlock.use`, `Net.onAction` | That a client gets the view. The fake player has no connection |
| The code of the summary has no reference to a provider and does not call `Uno.ask` | `Notices.summary`, `UnoView.open` | |
| The game time of the opening is the time of the overworld, as the time of the diary | `Net.open`, `MindRuntime.write` | |

Not covered:

- The summary on the screen of a client: 12 lines in the text of Uno, the line breaks, and the space of the screen. It is a step of the checklist of the play session.
- A real player who goes away and comes back. The tests set the moment of the opening as a parameter.
- An event in the same tick as an opening, after it. See the boundary in D30.
- The summary of an operator in the normal run: the events of the console jobs of the tests that run at the same time would change it. The restart test has one console job only, and checks the summary of an operator there.

Changes to the older tests:

- `t11_server_restart`: in the first run an operator opens the screen after the start of the job. At the end of the second run the operator opens again and the test checks the summary.
- The function that names a job in a notice (`subject`) is now visible to the restart test.

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
| The `/mindcraft` commands sent by a real client without operator permissions | The manual test uses a world with commands enabled. Thus the player is an operator. The `status` and `cancel` commands are tested with a player entity of the test system (`t19_status_command`). The other commands are tested only at the level of the runtime (T19) |
| T11: unload and reload of a chunk during the transport | It needs a player or forced chunk loading |
| T11: sudden stop on a normal server | The test server has no idle time in the tick. A normal server can write chunks to disk at different moments |
| T05: a player on the path | It needs a client. The configuration says that a turtle pushes an entity if there is space (`can_push = true`). Not tried |
| A stair (a path that also changes y), or more than one corner | The rigs have none. The move step should permit them when each move brings the robot nearer to the target (code examination). The path with one corner is tested: `a_path_with_one_corner` |
| `/forceload` by a player in a client, and a robot that works while the player is far | It needs a person at the game. Planned for the next play session (D28) |
| Two robots in one corridor that is one block wide | Outside the rig. From the code, each robot is an obstacle for the other |
| T08: delayed confirmation | Not applicable: the runtime and the turtle are in the same process, without network messages |
| Inventory not loaded (`WAITING` with unknown stock) in a real area that is not loaded | One test reaches the branch with an artificial station record (`t21_report_inventory_not_loaded`). No test unloads a chunk |
| A change of world in a client: two servers, one after the other, in one Java process | It needs a person at the game. The logic is tested with an artificial session change (`t23_state_of_an_earlier_session`). See D24 |
| A request to Uno that is open when a real server stops | It needs a server stop during a test. The branch comes from a code examination. See D24 |
| The screen in a client after the review: the line of the proposal, "Confirm" and the buttons of a job with the new actions | It needs a person at the game. The view that the server builds is tested automatically |
| A source that has the items but does not release them (`SOURCE_KEEPS`) | It needs an inventory that refuses the extraction. The chests of the rig do not |
| A robot session that CC: Tweaked does not stop in time at a real server stop, then a second world | It needs a person at the game and a stop that exceeds the limit of one second. The logic is tested with an artificial server start (`t23_robots_of_an_earlier_session`). See D24 |
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
| Increment A: the schema and the system text of the seven actions with the real service | A run of `t18_claude_live` costs money and needs the consent of the project owner (D33). Its four requests test only `start` and `none`. The new schema is tested only with the false API |
| The production jar `mindcraft-<version>-all.jar` loaded on an installed Forge | Only built and examined: 25 nested libraries. In the development game the libraries arrive in a different way |
| S2: the `/uno` commands in the game | Tested only through the functions that the commands call |
| T17 and the rest of T15 and T16 | They belong to S3. T15 has one minimum test: `t15_next_job_with_another_module` |

## Known limits

- The guarantees apply to the actions that go through `mindcraft.take` and `mindcraft.give`. A different Lua program can use `turtle.suck` and `turtle.drop`.
- An external actor that changes a chest between two visits is not detected. The state shows the last observation and its age.
- After `RECOVERY` and `resume` the totals of the job can be inexact. The state says so.
- There is no path finding. A path works only when each of its moves brings the robot nearer to the target dock. A straight path and a path with one corner are tested.
- Outside loaded areas the robot does not work and the job stays `RUNNING`. The state shows the robot as not loaded.
- A chest has no owner. There is no integration with the protections of the server.
- Uno answers only in English, also to a request in another language.
- The `/uno confirm` command starts the newest proposal of the player, also when a different contact point made it. Only the screen names the proposal that its button confirms.
- A lower quantity does not bring items back. The load above the new quantity stays on board until the owner decides.
- The Lua calls of a module carry no job ID. A module of the mod stops when the robot gets a different job, but one tick stays open. See D26.
