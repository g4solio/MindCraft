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

Known limit: a robot waits without an end behind an obstacle that does not go away. The report and the hologram show the wait. The notice after a long wait is ticket #8.

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
