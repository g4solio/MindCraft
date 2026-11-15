# Test results

Dates: 2026-11-12 (S0 to S2 and the review), 2026-11-13 and 2026-11-14 (increment A), and 2026-11-15 (increment B). Environment: Windows 11, Temurin 17.0.21, Forge 47.4.28, CC: Tweaked 1.121.0.

## Types of test

| Type | Meaning |
| --- | --- |
| **In the game, automatic** | A real headless Forge server runs the test with real turtles and chests. No client is connected. Command: `gradlew runGameTestServer`. The provider is the fixed rules, a provider written in the test, or a local stand-in of the service. No request leaves the computer |
| **In the game, automatic, with the real service** | The same server runs a test of `LiveTests` that calls the real Claude service: `gradlew runGameTestServer -Plive` or `-Pmeasure`. Each run costs money and needs the consent of the project owner. It is not part of the normal run. Runs 15 and 19 of S2, and runs 76, 78 and 81 of increment A, are of this type. No run of increment B is of this type |
| **Code examination** | A reading of the sources of CC: Tweaked at the tag `v1.20.1-1.121.0`, or of the compiled code of Minecraft and Forge with `javap`. A code examination is not a test: it says what the code does, not that it was seen to do it |
| **In the game, manual** | A player uses a client. The project owner did these tests. See "Manual test in the game" and "Increment A — the play session" |
| **Observed in one manual run** | A condition built by hand around an automatic run. No command of the repository repeats it. Only ticket #7 has one |
| **Static check** | A program reads the files of the repository without a start of the game. Command: `gradlew checkRules`, a part of `gradlew check` |

No test uses a simulation of the game. Where a condition is built artificially, the table says so.
Four tests use a false server in place of the Anthropic API: `t18_claude_provider`, since increment A `a_screen_model_lines`, and since increment B `b_model_of_the_setting` and `b_measurement_with_the_stand_in`. They verify the code of the provider, of the screen and of the harness of the measurement, not the language model.
Two tests call the real Claude service: `t18_claude_live`, with `gradlew runGameTestServer -Plive`, and since ticket #26 the measurement `b_measurement`, with `gradlew runGameTestServer -Pmeasure -Pmodel=<model>`. They are not part of the normal run.
Reasons: each run costs requests on the account of the key owner, four or 19, and a language model can answer differently at each run.

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

Work in parallel: each ticket writes its runs in a section of its own, at the end of the file. The numbers of this table come at the merge.

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
| 32, 33 | **43 of 43**, two times | None. The code after the review of 2026-11-12. Server line: `All 43 required tests passed :)` |
| 34 | 43 of 43 | None. Increment A, ticket #7: the atomic write of the record |
| 35 | 43 of 44, expected | Ticket #6, red run. A move step changed by hand for this run only. `a_path_with_one_corner` failed as expected |
| 36 | 44 of 44 | None. Ticket #6: a path with one corner |
| 37 | 40 of 44, expected | Ticket #2, red run: four new and changed tests, before the code |
| 38 | 44 of 44 | None. Ticket #2: the provider answers with an action |
| 39 | 45 of 45 | None. Ticket #2, merged with tickets #6 and #7 |
| 40 | 39 of 45, expected | Ticket #5, red run 1: three tests of the obstacle, and three tests that expect the new versions of the modules |
| 41 | 45 of 45 | None. Ticket #5: the obstacle is a wait |
| 42 | 41 of 47, expected | Ticket #5, red run 2: six tests of the causes of `BLOCKED` |
| 43 | 47 of 47 | None. Ticket #5: the causes of `BLOCKED` |
| 44 | 49 of 49 | None. Ticket #5, merged with tickets #2, #6 and #7 |
| 45 | 44 of 49, expected | Ticket #3, red run: five new tests |
| 46 | 48 of 49 | Ticket #3. `a_t19_words_of_another_player` failed for an error of the test: it built its text before the enrollment. The test was corrected |
| 47 | 49 of 49 | None. Ticket #3: report and pause by words |
| 48 | 53 of 53 | None. Ticket #3, merged with tickets #2, #5, #6 and #7 |
| 49 | 44 of 50, expected | Ticket #11, red run: five new tests. `t23_state_of_an_earlier_session` failed too: a new test left the Claude stand-in on the server. The test was corrected |
| 50 | 50 of 50 | None. Ticket #11: limits on the model calls |
| 51 | 54 of 54 | None. Ticket #11, merged with tickets #2, #5, #6 and #7 |
| 52 | 58 of 58 | None. The same, merged also with ticket #3 |
| 53 | 49 of 50, expected | Ticket #8, red run 1: `a_diary_of_a_job`, with a diary that stays empty |
| 54 | 52 of 56, expected | Ticket #8, run 2: the diary passed. The test of the limit and three tests of the notices failed as expected |
| 55 | 53 of 56, expected | Ticket #8, run 3: three older tests expected the notice with the cause |
| 56 | 56 of 56 | None. Ticket #8: the diary and the notices |
| 57 | 65 of 65 | None. Ticket #8, merged with tickets #2, #3, #5, #6, #7 and #11 |
| 58 | 53 of 54, expected | Ticket #4, red run 1: the test of the fixed rules |
| 59 | 54 of 56, expected | Ticket #4, green run 1 and red run 2: the two tests of the change by words |
| 60 | 55 of 56, expected | Ticket #4, green run 2: `a_confirmation_of_older_values` failed as expected at the cancellation |
| 61 | 56 of 58, expected | Ticket #4, red run 3: the tests of the resume and of the cancellation |
| 62 | 58 of 58 | None. Ticket #4: change, resume and cancel by words |
| 63 | 70 of 70 | None. Ticket #4, merged with tickets #8 and #11 |
| 64 | 64 of 67, expected | Ticket #9, red run: two new tests. `a_screen_model_lines` failed as a consequence: the failed test did not give back the provider of the server |
| 65 | 67 of 67 | None. Ticket #9: a notice for a long wait |
| 66 | 72 of 72 | None. Ticket #9, merged with tickets #2, #3, #4, #5, #6, #7, #8 and #11 |
| 67 | 65 of 66, expected | Ticket #10, red run 1: `a_summary_at_an_opening` |
| 68 | 66 of 70, expected | Ticket #10, run 2: three tests failed as expected. `a_summary_at_an_opening` failed for an error of the test: it read the sentence in the JSON of the view. The test was corrected |
| 69 | 70 of 70 | None. Ticket #10: the summary at an opening |
| 70 | 75 of 75 | None. Ticket #10, merged with ticket #4 |
| 71 | 78 of 78 | None. The same, merged also with ticket #9 |
| 72 | 76 of 81, expected | The code review of increment A, red run: the five tests of the defects |
| 73 | 81 of 81 | None. The corrections of the code review |
| 74 | 81 of 81 | None. The same, with one more check in `a_diary_of_a_job` |
| 75 | **81 of 81** | None. Ticket #12: the final code of increment A. Server line: `All 81 required tests passed :)` |
| 76 | 0 of 1, with `-Plive` | Test `t18_claude_live` with the real Claude service, on 2026-11-14, with the consent of the project owner. Log line: `Enabled Gametest Namespaces: [mindcraft_live]`. The service answered the first request with error 400. The log did not show the body of the error. Then the test stopped the server with a `NullPointerException`: a later check read a job that did not exist |
| 77 | 81 of 81 | None. The provider writes the body of an error of the service in the server log. The log of `t18_claude_provider` shows it for the error 500 of the false API: `The model service answered with error 500: {type=error, error={type=api_error, message=test}}` |
| 78 | 0 of 1, with `-Plive` | Test `t18_claude_live` with the real Claude service, on 2026-11-14, with the consent of the project owner. Log line: `Enabled Gametest Namespaces: [mindcraft_live]`. The service answered the first request with error 400. The log now has the reason: `output_config.format.schema: Invalid schema: Enum value 'transport' does not match declared type '['string', 'null']'`. The test failed without a stop of the server |
| 79 | 80 of 81, expected | `t18_claude_provider` has a new check: no field of the schema has an `enum` next to a list of types. It failed on the schema of run 78 |
| 80 | 81 of 81 | None. The field `module` of the schema uses `anyOf`: a string from the enum, or null |
| 81 | 1 of 1, with `-Plive` | None. Test `t18_claude_live` with the real Claude service and the corrected schema, on 2026-11-14, with the consent of the project owner. Log line: `Enabled Gametest Namespaces: [mindcraft_live]`. The counter: 4 requests, 5708 input tokens, 309 output tokens |
| 82 | 81 of 81 | None. D41: the account and the behaviour of a job. Server line: `All 81 required tests passed :)` |
| 83 | 82 of 82 | None. D41, decision 3: the typed seam of the provider. One new test: `a_parse_of_an_answer` |
| 84 | 83 of 83 | None. D41, decision 4: the buttons of a job go through the requests of the words. One new test: `a_cancel_button_asks_a_confirmation` |
| 85 | Not valid | Ticket #17, first red run. A failed check of the new test `b_adoption_by_the_command` let its sequence continue. The next step read a job that did not exist, and the `NullPointerException` stopped the server. The test sequence catches only an assertion. Corrected in the tests: see "Increment B — a job adopts the load on board" |
| 86 | 82 of 86, expected | Ticket #17, red run: three new tests and `t14_cancel`, with the old code |
| 87 | 85 of 86, expected | Ticket #17, green run of the runtime, red run of the proposal: `b_adoption_in_the_proposal` failed as expected |
| 88 | 86 of 86 | None. Ticket #17: a job adopts the load on board. Server line: `All 86 required tests passed :)` |
| 89 | 81 of 83, expected | Ticket #16, red run 1: the kind of a diary entry, without the conversion of a record of increment A. `a_long_wait` and `a_summary_has_the_long_wait` failed |
| 90 | 82 of 84, expected | Ticket #16, green run 1 and red run 2: the conversion passed. The new test `b_causes_of_recovery` and the changed test `a_no_notice_for_a_resume_that_completes_the_job` failed: the runtime did not write the cause of `RECOVERY` yet |
| 91 | 84 of 84 | None. Ticket #16: the kinds of a diary entry and the causes of `RECOVERY`. Server line: `All 84 required tests passed :)` |
| 92 | 87 of 87 | None. The integration branch after the merge of tickets #17 and #16. Server line: `All 87 required tests passed :)` |
| 93 | 84 of 91, expected | Ticket #18, red run: five new tests and two changed tests, with the old code |
| 94 | 91 of 91 | None. Ticket #18: a job replaces a job. Server line: `All 91 required tests passed :)` |
| 95 | 92 of 92 | None. Ticket #18, with one more test of the replacement in each open state. Server line: `All 92 required tests passed :)` |
| 96 | 92 of 93 | The integration branch after the merge of ticket #18. `b_replacement_in_recovery` failed: `[RUNNING, RECOVERY LOAD_DIFFERS, CANCELLED]`. Ticket #16 adds the cause of `RECOVERY` to the diary, and the test of ticket #18 did not expect it. The expectation of the test changed, not the code |
| 97 | 93 of 93 | None. The integration branch after the merge of ticket #18, with the corrected expectation. Server line: `All 93 required tests passed :)` |
| 98 | 87 of 88, expected | Ticket #19, red run 1: `b_facts_in_the_context`, with a context without facts |
| 99 | Not valid | Ticket #19, green run 1 and red run 2. `b_facts_of_uncertain_numbers` failed as expected. Then a `NullPointerException` in `JobAccount.facts` stopped the server: a conditional expression unboxed a null. Corrected: see "Increment B — the facts of the jobs go to the model" |
| 100 | 87 of 89, expected | Ticket #19, green run 2 and red run 3: `t18_claude_provider` and `a_screen_model_lines`, with the old JSON and the old line |
| 101 | 89 of 89 | None. Ticket #19: the facts of the jobs go to the model. Server line: `All 89 required tests passed :)` |
| 102 | 89 of 89 | None. Ticket #19, the code of the commit: the same, with one more null check in `b_facts_in_the_context`. Server line: `All 89 required tests passed :)` |
| 103 | 95 of 95 | None. The integration branch after the merge of ticket #19. Server line: `All 95 required tests passed :)` |
| 104 | 91 of 93, expected | Ticket #21, red run: two changed tests, with the old code of Uno. Runs 96 and 97 are runs of the integration branch |
| 105 | 93 of 93 | None. Ticket #21: the fixed rules read a request when the model gives no valid action. Server line: `All 93 required tests passed :)` |
| 106 | 93 of 93 | None. Ticket #21, the committed code: one comment changed after run 105. Server line: `All 93 required tests passed :)` |
| 107 | 95 of 95 | None. The integration branch after the merge of ticket #21. Server line: `All 95 required tests passed :)` |
| 108 | 95 of 96, expected | Ticket #20, red run 1: the new test `b_voice_of_a_report`, with the old code of Uno |
| 109 | 97 of 98, expected | Ticket #20, green run 1 and red run 2: `b_voice_of_none`, with the old answer to "none" |
| 110 | 97 of 100, expected | Ticket #20, green run 2 and red run 3: two new tests of the fixed sentences, and `t18_claude_provider` with the old system text |
| 111 | 100 of 100 | None. Ticket #20: the voice of Uno and its checks. Server line: `All 100 required tests passed :)` |
| 112 | 100 of 100 | None. Ticket #20, the committed code `cb6161f`. Server line: `All 100 required tests passed :)` |
| 113 | 100 of 101, expected | Ticket #23, red run: the new test `b_record_of_a_request`, and nothing writes the record |
| 114 | 101 of 101 | None. Ticket #23: each request is in the server log. Server line: `All 101 required tests passed :)` |
| 115 | 101 of 101 | None. Ticket #23, the code of the commit. Server line: `All 101 required tests passed :)` |
| 120 | 95 of 96, expected | Ticket #24, red run 1: the new test `b_low_fuel`, with an empty check of the fuel |
| 121 | 93 of 96 | Ticket #24, green run 1. Three tests whose turtle starts without fuel did not expect the low fuel entry at the level 0. Their expectations changed, not the code |
| 122 | 94 of 98, expected | Ticket #24, red run 2: two new tests and three changed tests about the fuel in the report, the status, the facts and the screen |
| 123 | 96 of 98 | Ticket #24, green run 2. `b_fuel_in_the_report`: a defect of the test. `t22_t23_proposals_and_contact_points`: the record did not save the fuel of a job |
| 124 | 98 of 98 | None. Ticket #24: the low fuel notice. Server line: `All 98 required tests passed :)` |
| 125 | 98 of 98 | None. Ticket #24, the committed code `97cd020`. Server line: `All 98 required tests passed :)` |
| 126 | 98 of 98 | None. Ticket #24, one more step in `a_notices_call_no_provider` (`dfa1d29`). Server line: `All 98 required tests passed :)` |
| 127 | 103 of 103 | None. The integration branch after the merge of tickets #20 and #24. Server line: `All 103 required tests passed :)` |
| 128 | 104 of 104 | None. The integration branch after the merge of ticket #23. Server line: `All 104 required tests passed :)` |
| 129 | 103 of 104, expected | Ticket #22, red run: the new test `b_model_of_the_setting`, with the model still a constant of the provider |
| 130 | 104 of 104 | None. Ticket #22: the model is a setting. Server line: `All 104 required tests passed :)` |
| 131 | 104 of 104 | None. Ticket #22, the committed code `260efdc`. Server line: `All 104 required tests passed :)` |
| 132 | 105 of 105 | None. The integration branch after the merge of ticket #22. The merge joined the two accessors of the model of `ClaudeProvider` of tickets #22 and #23 into one. Server line: `All 105 required tests passed :)` |
| 140 | 104 of 105, expected | Ticket #25, red run: the new test `b_conversation_first`, with the old order of the view |
| 141 | 105 of 105 | None. Ticket #25: the conversation part comes first on the screen. Server line: `All 105 required tests passed :)` |
| 142 | 105 of 105 | None. Ticket #25, the committed code `a2b66fd`. Server line: `All 105 required tests passed :)` |
| 143 | 106 of 106 | None. The integration branch after the merge of ticket #25. Server line: `All 106 required tests passed :)` |
| 150 | 105 of 106, expected | Ticket #26, red run: the new test `b_measurement_with_the_stand_in`, with a harness that sends no request |
| 151 | 106 of 106 | None. Ticket #26: the harness of the measurement. Server line: `All 106 required tests passed :)` |
| 152 | 106 of 106 | None. Ticket #26: the build script reads `-Pmodel` from the command line, and one more note in the record. Server line: `All 106 required tests passed :)` |
| 153 | 106 of 106 | None. Ticket #26, the code of the commit: one comment of the test changed after run 152. Server line: `All 106 required tests passed :)` |
| 154 | 107 of 107 | None. The integration branch after the merge of ticket #26. Server line: `All 107 required tests passed :)` |
| 155 | 106 of 107 | Ticket #28, the final code of increment B. `b_replacement_refused` failed one time: "the computer is not off yet". See "Increment B — final runs and evidence (ticket #28)" |
| 156 | 107 of 107 | None. Ticket #28, the same code. Server line: `All 107 required tests passed :)` |
| 157 | 107 of 107 | None. Ticket #28, the same code, one more time. Server line: `All 107 required tests passed :)` |

Runs 34 to 75 are the runs of increment A, on 2026-11-13.

Runs 82 to 84 are the runs of the architecture review after increment A (D41).

Runs 85 to 88 are the runs of ticket #17 of increment B. The tickets of increment B run at the same time, each in a working folder of its own. The numbers can change at the merge.
Runs 89 to 91 are the runs of ticket #16 of increment B. See "Increment B — the kinds of a diary entry and the causes of RECOVERY".
Run 92 is the run of the integration branch of increment B after the merge of tickets #17 and #16.

Runs 93 to 95 are the runs of ticket #18 of increment B.
Runs 96 and 97 are the runs of the integration branch after the merge of ticket #18.
Runs 98 to 102 are the runs of ticket #19 of increment B, on the branch with tickets #16 and #17. See "Increment B — the facts of the jobs go to the model". No run of ticket #19 called the real Claude service.
Run 103 is the run of the integration branch after the merge of ticket #19.
Runs 104 to 106 are the runs of ticket #21. Run 107 is the run of the integration branch after its merge.
Runs 108 to 112 are the runs of ticket #20. Its merge changed no file, thus run 112 is also the run of the integration branch after its merge.
Runs 113 to 115 are the runs of ticket #23, on the integration branch at `bbe9478`.
Runs 120 to 126 are the runs of ticket #24, on the integration branch at `8df1f39`. See "Increment B — the low fuel notice (ticket #24)". The numbers start at 120: other tickets run at the same time. No run of ticket #24 called the real Claude service.
Run 127 is the run of the integration branch after the merge of ticket #24.
Run 128 is the run of the integration branch after the merge of ticket #23.
Runs 129 to 131 are the runs of ticket #22, on the integration branch at `c63d75c`.
Run 132 is the run of the integration branch after the merge of ticket #22.
Runs 140 to 142 are the runs of ticket #25, on the integration branch at `c075de7`.
Run 143 is the run of the integration branch after the merge of ticket #25.
Runs 150 to 153 are the runs of ticket #26, on the integration branch at `002c636`.
Run 154 is the run of the integration branch after the merge of ticket #26.
Runs 155 to 157 are the runs of ticket #28, on the integration branch at `8b0d3e2`. Ticket #28 changed only documents.

Run 76 is the first run of the schema of increment A with the real service. Run 78 gave the reason of its error. Run 81 passed with the corrected schema. See D29.1.

The answers of run 81, from the log:

- The free request in Italian: "Proposal: keep at least 64 x minecraft:coal in 'fornaci', taken from 'deposito'. Reserve to leave in 'deposito': 32. Robot #0." After the confirmation the robot delivered 48 items.
- "Porta del carbone alle fornaci": "I cannot propose a job: The request gives no quantity and no source station, and no robot is free."
- "Che tempo farà domani?": "I cannot propose a job: The request asks about the weather, which is not an action of the list."
- "Porta 10 pezzi di carbone dal deposito alla segheria": "I cannot propose a job: The destination station "segheria" is not in the context, and no robot is free."
- The counter of the session: 4 requests, 5708 input tokens, 309 output tokens. With the prices of `claude-opus-5-5` this is about 0.03 dollars.

- The tickets ran at the same time, each in a working folder of its own. Thus the numbers do not give the order in time.
- The numbers follow the order of the merges into the branch `increment-a`: tickets #7, #6, #2, #5, #3, #11, #8, #4, #9 and #10, then the code review and ticket #12. Inside a ticket they follow the order of its section below.
- "Expected" marks a red run: the failed tests were new or changed tests, and their code did not exist yet. Each section below names the tests.
- This table has only the normal runs. The restart phases are in the next table. No run of increment A called the real Claude service.

Restart phases of increment A. Type: in the game, automatic. Each row is the four phases in their order, unless the row says otherwise.

| Code | Phases | Result |
| --- | --- | --- |
| Run 34, ticket #7 | All four | Passed |
| Run 43, ticket #5 | All four | Passed |
| Run 44, ticket #5 after the merge | All four | Passed |
| Run 56, ticket #8 | All four | Passed |
| Run 62, ticket #4 | All four | Passed |
| Run 63, ticket #4 after the merge | All four | Passed |
| Run 65, ticket #9 | All four | Passed |
| Run 66, ticket #9 after the merge | All four | Passed |
| Between runs 68 and 69, ticket #10 | `stop`, `check` | Red: `stop` passed. `check` failed as expected: the openings were not saved yet |
| Run 69, ticket #10 | All four | Passed |
| Run 70, ticket #10 after the merge with ticket #4 | All four | Passed |
| Run 71, ticket #10 after the merge with ticket #9 | All four | Passed |
| Run 72, code review | `crash`, `check-crash` | Red: `crash` passed. `check-crash` failed as expected: the summary missed the `RECOVERY` of the load |
| Run 73, code review | All four | Passed |
| Run 75, ticket #12 | All four | **Passed**. See "Increment A — final runs and evidence" |
| Run 82, D41 | All four | Passed. See "The architecture review after increment A" |
| Run 84, D41, decision 4 | All four | Passed |
| Run 88, ticket #17 | All four | Passed. See "Increment B — a job adopts the load on board (ticket #17)" |
| Run 91, ticket #16 of increment B | All four | Passed. `check-crash`: the diary after the restart is `RUNNING, DONE, RECOVERY WORLD_OLDER` |
| Run 92, tickets #16 and #17 after the merge | All four | Passed |
| Run 95, ticket #18 | All four | Passed. See "Increment B — a job replaces a job (ticket #18)" |
| Run 96, ticket #18 after the merge | All four | Passed. Run 97 changed only the expectation of one test of the normal run |
| Run 101, ticket #19 of increment B | All four | Passed. See "Increment B — the facts of the jobs go to the model (ticket #19)" |
| Run 125, ticket #24 of increment B | All four | Passed. See "Increment B — the low fuel notice (ticket #24)" |
| Run 127, tickets #20 and #24 after the merge | All four | Passed |
| Run 156, ticket #28: the final code of increment B | All four | Passed. See "Increment B — final runs and evidence (ticket #28)" |

Tickets #6, #2, #3 and #11 changed no runtime and no Lua module. Thus they did not run the four phases.
Not in this table: one run of ticket #7 with a failure of the write built by hand. Its type is "observed in one manual run".

Lesson from run 2: the exit code is not sufficient. The log must contain the line `GAME TESTS COMPLETE`.
Lesson from run 14: that line is not sufficient either. The number of tests and the line `Enabled Gametest Namespaces` say which tests ran.
Lesson from run 21: a test that passed 12 times can hide a defect that depends on one tick. A test must build such a moment on purpose.
Lesson from the restart test after run 25: the normal run does not contain the restart test. A change of the transfer rules needs the four restart phases too. One case of them is now also in the normal run.
Lesson from run 28: a test must not depend on which thread is faster. A fast provider can answer inside the call that sends the request.

The real Claude service was not called between run 19 and run 76. The corrections of the review were tested with the deterministic provider and with providers written in the tests. No test of increment A called it: not runs 34 to 75, and not the restart phases. Runs 76, 78 and 81 called it, with `-Plive`. No run of increment B called it: not runs 85 to 157, and not the restart phases.

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

All "passed" rows come from runs 6 and 7. The later runs confirm them, up to run 75.
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

The counts above end with the review of 2026-11-12. Increment A ran the phases again after each change of the runtime or of a Lua module: see "Restart phases of increment A" in "Runs".

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
Increment A changed three things in these rows. The rows describe S2.

- A provider gives an action (D29.1). The schema of `t18_claude_provider` now has ten required fields. The `problem` field is now the reason of the action `none`. See "Increment A — The provider answers with an action".
- The "Confirm" button has the label "Confirm #N" (D29.3).
- The "real Claude" rows used the schema of S2. The schema of increment A passed the same test in run 81, after a correction. See "Runs".

Run 75, the final run of increment A, confirms the other rows.

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
The table "Runs" gives the runs of this ticket the numbers 37 to 39.

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
The table "Runs" gives the runs of this ticket the numbers 45 to 48.

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
The table "Runs" gives the runs of this ticket the numbers 58 to 63.

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
| **Not verified** | The real Claude service with a change, a resume or a cancellation. The proposal and the button in a client: steps 3, 4, 7, 8 and 12 of the checklist of the play session. A resume proposal for a job that goes to `RECOVERY` before the confirmation (D29.3), until the code review of increment A. Since then `a_resume_proposal_holds_the_state` tests it |

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
| Normal run (run 34) | In the game, automatic | **43 of 43** | 92 transfers wrote the record through the new write. No write error in the log. After the run the data folder contains `mindcraft.dat` and no temporary file |
| `stop`, `check`, `crash`, `check-crash` | In the game, automatic | Passed, each phase | The same results as before the change. `check`: the record says `RUNNING` with 24 on board, then `DONE`, source 40, destination 24. `check-crash`: the record written at the delivery loads, the job goes to `RECOVERY` ("the world is older than the record of this job"), then `DONE` after `resume`. After each phase the data folder contains no temporary file |
| `stop`, then `check` with a failure of the write | Observed in one manual run. Not a test of the repository, and not one of the types above: a condition built by hand around an automatic phase | Passed | See below |

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

Runs of this ticket: runs 35 and 36 in the table "Runs".

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
The table "Runs" gives the runs of this ticket the numbers 40 to 44.

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
| **Not verified** | A player on the path (`can_push = true`). A robot at the edge of a loaded area ("Cannot leave loaded world" is also an obstacle). Two robots that face each other. The screen in a client during the wait. Steps 5 and 6 of the checklist of the play session have the player on the path and the screen during the wait |

Known limit: a robot waits without an end behind an obstacle that does not go away. The report and the hologram show the wait. The notice after a long wait is ticket #9: see "Increment A — a notice for a long wait".

## Increment A — the job diary and the notices

Ticket #8 of spec #1, on 2026-11-13. See D30 and D31.
All tests of this section are in the game and automatic. No test called the real Claude service.
The table "Runs" gives the runs of this ticket the numbers 53 to 57.
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

- A notice in the chat of a client. Steps 5, 7, 9 and 11 of the checklist of the play session have one: [PLAY_SESSION_A.md](PLAY_SESSION_A.md).
- A notice to an owner who is connected in a different dimension.
- A `RECOVERY` that the load of the record finds at the server start, for a job of a player. Its notice leaves before a player can be connected. The phase `check-crash` writes such an entry, but its job belongs to the console.

Changes to the older tests:

- `t04_destination_removed`, `t04_source_removed`, `t16_quantity_lowered_with_load` and `t06_impediment_of_an_unknown_kind`: one more check, the notice of the `BLOCKED` state.
- `t11_server_restart`: it checks the diary after the restart and at the end.

The merge brought no change of the runtime (`MindRuntime.java`, `RobotApi.java`) and of the Lua modules. Thus the four restart phases were not repeated after it.

## Increment A — a notice for a long wait

Ticket #9 of spec #1, on 2026-11-13. See D30.
All tests of this section are in the game and automatic. No test called the real Claude service.
The table "Runs" gives the runs of this ticket the numbers 64 to 66.
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

- A wait of 5 real minutes, and its line in the chat of a client. They are step 5 of the checklist of the play session.
- A long wait across a real restart of the server. The restart phases build no wait. The save and the load in `a_long_wait` keep the start and the flag.

Changes to the older tests:

- `t05_obstacle`: one more check, the notice of a long wait.
- `a_notices_call_no_provider`: a second job that waits, and its notice. The test expects three notices.
- The helper `states` of the tests writes a long-wait entry as `LONG_WAIT` and its cause.

## Increment A — Limits on the model calls

Ticket #11 of spec #1, on 2026-11-13. See D33.1.
All tests of this section are in the game and automatic. They use providers written in the tests. One test uses the Claude provider with a local stand-in in place of the API.
No test called the real Claude service.
The table "Runs" gives the runs of this ticket the numbers 49 to 52.

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
| The report, the confirmation and the buttons of a job do not call `Uno.ask`. Only the "ask" action of the screen and `/uno ask` do | `UnoView.act`, `MindCraft.registerCommands` | A test with a provider that counts its calls. Since tickets #8 and #10, `a_notices_call_no_provider` and `a_summary_at_an_opening` count zero calls for the notices, the summary and the "Report" button. No test counts the calls for a confirmation or for the other buttons |
| At the time limit, `orTimeout` completes the request with an exception. The thread of the provider continues, and Uno does not read its answer | `Uno.ask` | The tokens that the real service bills for such a request. The counter shows zero for them |
| The actor of a command source has the operator flag of the source. A command block has the permission level 2 | `MindCraft.actor(CommandSourceStack)`. The level of a command block is general knowledge, not read in the sources | That `/uno ask` from a command block counts as a request of an operator. Not tried |

Not covered:

- The counter with the tokens of the real service. Steps 1 and 11 of the checklist of the play session read it, with the key.
- The screen in a client: the counter, the lines about the provider, and their length on the screen. Steps 1 and 11 of the checklist.
- A real client with permission level 0, and a single-player world without commands. The tests build the actors.
- The limit of the console: one test counts one request of the console. The limit itself is tested with an operator.

## Increment A — the summary at an opening

Ticket #10 of spec #1, on 2026-11-13. See D30, "Summary, as built".
All tests of this section are in the game and automatic. No test called the real Claude service.
The table "Runs" gives the runs of this ticket the numbers 67 to 71.
No test has a client. One test calls the methods of the tablet and of the projector with a fake player of Forge (`FakePlayerFactory`): a server player without a connection. The other tests call `UnoView.open`, the function that those methods call, with the moment of the opening as a parameter.

| Run | Code | Result |
| --- | --- | --- |
| Red 1 | `UnoView.open` builds the view and does nothing more. The tablet and the projector call it | 65 of 66. `a_summary_at_an_opening` failed as expected: "the first opening: Tell me what you need." |
| Run 2: green 1, red 2, 3 and 4 | The summary without the limit, without the line about the memory, and with the openings in memory only | 66 of 70. `a_summary_shows_the_10_newest` failed as expected: it showed the 12 events. `a_summary_says_where_the_memory_starts` failed as expected: "only removed entries: Tell me what you need." `a_summary_after_a_save_and_a_load` failed as expected: the loaded copy gave "From the start of my memory:" with both events. `a_t19_summary_of_another_player` passed: it guards against an event that must not come. `a_summary_at_an_opening` failed for an error of the test, after its check of the summary passed: see below |
| `stop`, `check`, red | The limit and the line about the memory. The openings are not saved yet. The restart test has the checks of the summary | `stop` passed. `check` failed as expected: "the summary after the restart: From the start of my memory: 0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." The diary checks before it passed |
| Green | The openings in the saved record | **70 of 70**. Server line: `All 70 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash` | The code of Green | Passed, each phase. `check`: the summary of the operator after the restart is "Since you last opened this screen:" and one line, "0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." `check-crash`: the same first line and three lines: the `DONE` of the first run, the `RECOVERY` "… needs a check: I cannot say how it went. Recorded reason: the world is older than the record of this job.", and the `DONE` after the resume. After the four phases the data folder has no temporary file |
| After the merge with ticket #4 | The code of Green, merged with ticket #4 of increment A. The merge changed `MindRuntime.java` | **75 of 75**. Server line: `All 75 required tests passed :)`. The four restart phases passed. No temporary file |
| After the merge with ticket #9 | The same, merged also with ticket #9. One more test: `a_summary_has_the_long_wait`. It needs the long wait of ticket #9, thus it has no red run | **78 of 78**. Server line: `All 78 required tests passed :)`. The four restart phases passed. No temporary file |

The error of the test in run 2: the first version of `a_summary_at_an_opening` looked for the sentence of Uno in the text of the view. The view is JSON: Gson writes the line break as `\n` and the quote as `'`. The test now reads the text of the widget of Uno. The green run used this version.

| Test | What it does | Observed result |
| --- | --- | --- |
| `a_summary_at_an_opening` | A batch of its own: the provider of the server is a `CountingModel`. A transport of a player with fuel 0: `BLOCKED`. Then fuel and a resume of the owner: `DONE`. The player uses the tablet. Then the "Report" button. Then a click on the projector. A second job goes to `BLOCKED`. The screen asks for a new view ("refresh"). Then a second click on the projector | The first opening, with the tablet: "From the start of my memory:", "2 s ago: Job N on robot #R (…) is blocked: the robot has no fuel.", "0 s ago: … is done: 24 delivered." The start and the resume of the owner are not in it. The view shows the summary as the sentence of Uno. The report replaces it. The click on the projector without a new event keeps the report. The view after "refresh" keeps the report too. The second click: "Since you last opened this screen:" and only the block of the second job. The provider got zero calls |
| `a_summary_shows_the_10_newest` | In one tick, 12 impediments of unknown kinds through the function that the Lua modules call, each with a reason of its own. The owner resumes after each one. The opening is 12 minutes of game time later | "From the start of my memory:", "I do not show 2 older events.", then the events 3 to 12, the oldest first, each as "12 min ago: … is blocked. Recorded reason: kindN: event N." |
| `a_summary_says_where_the_memory_starts` | A first opening without events. Two ticks later, in one tick: a start, 110 pauses and 110 resumes of the owner. The runtime removes 21 entries. A copy of the record at that moment. Then one impediment. The openings are 3 hours later. Then the "Report" button and an opening 1 minute later | The first opening without events keeps "Tell me what you need." The copy: "Since you last opened this screen:" and "My memory starts 3 h ago. I do not remember older events." The running record after the impediment: the same two lines and "3 h ago: … is blocked. Recorded reason: kind: event." The opening after the report keeps the report |
| `a_t19_summary_of_another_player` | A block of the job of the owner. A different player opens, then the owner | The different player: Uno keeps "Tell me what you need.", and the view has no station and no event of the owner. Then the owner: "From the start of my memory:" and "0 s ago: … is blocked. Recorded reason: kind: event." |
| `a_summary_after_a_save_and_a_load` | One block before the first opening, one block two ticks after it. A save and a load of the record, then the openings two days later: on the loaded copy and on the running record. Then a load without the saved openings | The loaded copy and the running record give the same summary: "Since you last opened this screen:" and "2 days ago: … is blocked. Recorded reason: late: event." Without the saved openings: "From the start of my memory:" and both events, each "2 days ago" |
| `a_summary_has_the_long_wait` | Added after the merge with ticket #9. The source is empty: the job waits. The check of the long wait is 6000 ticks after the start of the wait. The opening is 2 minutes after the check | "From the start of my memory:" and "2 min ago: … has waited for 5 minutes: nothing remains in 'a_summary_wait_source'." The summary needed no change for the long wait |
| `t11_server_restart`, four phases | An operator opens the screen after the start of the job, in the first run. After the restart and the end of the job, the operator opens again | See the table of the runs above. After the normal stop the summary has the first line of a later opening: the last opening of the first run survived the restart |

Code examination, not a test:

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The tablet and the projector call `Net.open`. The answer to an action of the screen, also the view of each second, calls `Net.sendView`. Only `Net.open` calls `UnoView.open` | `MindCraft.TABLET`, `ProjectorBlock.use`, `Net.onAction` | That a client gets the view. The fake player has no connection |
| The code of the summary has no reference to a provider and does not call `Uno.ask` | `Notices.summary`, `UnoView.open` | |
| The game time of the opening is the time of the overworld, as the time of the diary | `Net.open`, `MindRuntime.write` | |

Not covered:

- The summary on the screen of a client: 12 lines in the text of Uno, the line breaks, and the space of the screen. Steps 7, 10 and 11 of the checklist of the play session read a short summary in a client. The checklist has no summary with 12 lines.
- A real player who goes away and comes back. The tests set the moment of the opening as a parameter. Steps 9 and 10 of the checklist do it.
- An event in the same tick as an opening, after it. See the boundary in D30.
- The summary of an operator in the normal run: the events of the console jobs of the tests that run at the same time would change it. The restart test has one console job only, and checks the summary of an operator there.

Changes to the older tests:

- `t11_server_restart`: in the first run an operator opens the screen after the start of the job. At the end of the second run the operator opens again and the test checks the summary.
- The function that names a job in a notice (`subject`) is now visible to the restart test.

## Increment A — corrections of the code review

The code review of the branch `increment-a` (spec #1), on 2026-11-13. It found five defects against the spec (P1, P2, P3, P4, P6), two documents that broke the standard of the repository (S1, S2), and code smells. See D29.3, D30, D31, D34 and D37.
The tests of this section are in the game and automatic, unless a row says otherwise. No test called the real Claude service.
The table "Runs" gives the runs of this section the numbers 72 to 74.

| Run | Code | Result |
| --- | --- | --- |
| Red | The new and changed tests, with the code of `increment-a` before the corrections | 76 of 81. The five tests of the defects failed as expected. Server line: `5 required tests failed :(`. The rewritten `a_notices_call_no_provider` passed: it is a change of a test, not of the code |
| Red, `crash` and `check-crash` | The changed restart test, with the code before the corrections | `crash` passed. `check-crash` failed as expected: "the summary after the restart: Since you last opened this screen: 0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." The `RECOVERY` of the load is missing |
| Green | The corrections | **81 of 81**. Server line: `All 81 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash` | The code of Green | Passed, each phase. See below |
| Final | The code of Green, and one more check in `a_diary_of_a_job`: a diary without the numbers, as before the corrections, gets its numbers at the load. The check was added with the code: it has no red run | **81 of 81**. Server line: `All 81 required tests passed :)` |

| Test | Finding | What it does | Red run | Green run |
| --- | --- | --- | --- | --- |
| `a_summary_after_the_time_went_back` | P1 | Built artificially, in one tick. A job waits. A long wait at a moment later than the world. An opening at a moment later than that entry. `RECOVERY` at the time of the world, as the load of the record writes it. Then two openings at the time of the world | Failed. The opening after the "crash" kept the first summary: the `RECOVERY` was missing. The second opening showed the long wait again, "At an unknown time" | Passed. The opening after the "crash": "Since you last opened this screen:" and "0 s ago: … needs a check: I cannot say how it went. Recorded reason: the world is older than the record of this job." The second opening: "Tell me what you need." |
| `a_summary_at_an_opening` | P2 | Changed: a second click on the projector at once, after the click with a summary | Failed: "a second opening kept the summary of the first: Since you last opened this screen: 0 s ago: … is blocked: the robot has no fuel." | Passed: "Tell me what you need." The provider got zero calls |
| `a_resume_proposal_holds_the_state` | P3 | A transport with 24 on board. The owner pauses it. "Resume the job of a_resume_state_destination" through the fixed rules. Then the test empties the robot and the owner resumes with the button: `RECOVERY`. Then `/uno confirm` | Failed: "I resume job 52 on robot #20." The confirmation accepted the observed load | Passed. The proposal: "Proposal: resume job … (carry …). I have done nothing yet. …", without the sentence about a check. The confirmation: "I cannot resume the job: Job … changed: its state is RECOVERY now, not PAUSED. Read it again." The job stays `RECOVERY`, 0 recoveries, 24 on board in the record |
| `a_change_through_the_view` | P4 | Changed: one more check of the label of the button | Failed: `{"action":"confirm:9","label":"Confirm"}` | Passed: the label is "Confirm #N" with the number of the action, for example `{"action":"confirm:8","label":"Confirm #8"}` |
| `a_notice_for_a_resume_by_an_operator` | P6 | The rig of `a_no_notice_for_a_resume_that_completes_the_job`: the destination accepts 4, then the robot loses the rest. An operator lowers the quantity to 4 and resumes the job of the player | Failed: the player had only the notice of `RECOVERY` | Passed. Two notices to the player: "… needs a check: … the robot holds 0, the record says 20." and "… is done: 4 delivered." The operator got none |

The four restart phases with the code of Green:

| Phase | Result |
| --- | --- |
| `stop` | Passed |
| `check` | Passed. The diary after the restart is `RUNNING`, at the end `RUNNING, DONE`. Source 40, destination 24, robot 0. The summary: "Since you last opened this screen:" and "0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." |
| `crash` | The test halted the process at the game time 420, as expected. The opening after the delivery gave "Since you last opened this screen:" and the delivery |
| `check-crash` | Passed. The record loads at the game time 83. The diary after the restart is `RUNNING, DONE, RECOVERY`, at the end `RUNNING, DONE, RECOVERY, RUNNING, DONE`. The summary: "Since you last opened this screen:", "2 s ago: … needs a check: I cannot say how it went. Recorded reason: the world is older than the record of this job." and "0 s ago: … is done: 24 delivered." |

After the four phases the data folder has no temporary file.

Conditions built artificially:

- **`a_summary_after_the_time_went_back`:** the test gives the moments of the openings and of the check of the long wait as parameters. It writes the `RECOVERY` with the function that the load of the record calls (`MindRuntime.set`). It does not stop the server.
- **The `crash` phase:** after the delivery, the operator opens the screen, and the test writes the record to the disk. In the game the next transfer of any robot writes the record. Thus the record on the disk holds an opening later than the saved world.
- **`a_notices_call_no_provider`:** the long wait, the block and the `RECOVERY` occur in one step of the test, through the functions that call them in the game: `MindRuntime.checkWait`, the Lua function `impediment` and `MindRuntime.set`. The test moves the start of the wait back by 6000 ticks.

Changes to the older tests:

- `a_summary_at_an_opening`: a second click at once, and its check. The provider of the server is the counting provider only inside each opening and each action of the screen (finding S6).
- `a_notices_call_no_provider`: rewritten (finding S6). Before, it changed the provider of the server for many ticks, without a `finally`: a failure left the counting provider on the server, and `a_screen_model_lines` failed too. Now each change of the provider is inside one step. The notices are a long wait, `BLOCKED` and `RECOVERY`. Before, they were `BLOCKED`, `DONE` and a long wait, from the robot and from the server tick.
- `a_change_through_the_view`: the check of the label.
- `a_diary_limit`: it also checks the number of the newest removed entry.
- `a_summary_after_a_save_and_a_load`: the saved openings have the key "openingEntries".
- `t11_server_restart`: in the `crash` phase a second opening after the delivery, and the write of the record. In `check-crash` the summary after the restart has the `RECOVERY` and the delivery after it, not the delivery of the first run: the second opening of the first run had it.

Code examination, not a test:

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The refactoring of the smells changed no sentence. One place turns a target that is not valid into the sentence of Uno (S4). One function names a job, "job N on robot #R" (S5). The search result has no second robot number (S8). New names: `Notices.warn`, `UnoView.providerLines`, `RuleProvider.jobAction`, `Uno.Session.pending`, `MindRuntime.recordOpening`, the parameters `before` and `after` (S9). The confirmation of a change uses the job that `update` returns (S11) | `Uno.java`, `Notices.java`, `UnoView.java`, `MindRuntime.java` | The tests of D29 check the sentences exactly. They passed in the green run |
| A request of an operator to pause or to cancel the job of a player gives no notice: the attention rule does not select `PAUSED` and `CANCELLED` | `Notices.selected` | No test builds it |
| The type of the run with a failure of the write (ticket #7) is now "observed in one manual run" (finding S1). D34 describes the obstacle before and after ticket #5 (finding S2) | `TEST_RESULTS.md`, `DECISIONS.md` | |

Smells of the review without a change. A change was not small, or did not make the code shorter:

- S3, four switches from a cause to a sentence: the voices differ on purpose (D30). One switch with four voices is not shorter.
- S7, actions as strings: an enum needs a change of the runtime, of the commands and of the actions of the screen.
- S8, the 16 fields of a diary entry: the entry copies the job on purpose (D31). A shorter save and load needs a codec of its own.
- S10, the many reasons of change of `MindRuntime`: a split into new files is out of the scope of these corrections.
- S11, the search of the job in Uno: it holds the sentences of D29.2. A walk in the runtime adds a method and saves no line.

Not covered:

- The button "Confirm #N" in a client: its width with a number of two or three digits. Step 3 and check C5 of the checklist of the play session look at it.
- A real crash with a job of a player: the restart test has a job of the console. The summary of the operator shows its events.
- A whole record of the format of increment A before the corrections, from a real world. Such a record exists only if somebody played the branch `increment-a` before the corrections. Its load gives numbers to the entries, in their order: `a_diary_of_a_job` checks this on a copy. It ignores the openings of that record, saved under the key "openings": the next opening of each player is a first opening. That part is a code examination.

## Increment A — final runs and evidence (ticket #12)

Ticket #12 of spec #1, on 2026-11-13. It changed only documents. The code of its runs is the branch `increment-a` after the corrections of the code review: commit `6673fac`.

| Run | Type | Result |
| --- | --- | --- |
| 75, the normal run | In the game, automatic | **81 of 81**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `81 GAME TESTS COMPLETE`, `All 81 required tests passed :)`. Uno used the fixed rules: "Uno interprets requests with the fixed rules, without a language model." |
| `stop` | In the game, automatic | Passed. The test stopped the server at the game time 81 |
| `check` | In the game, automatic | Passed. After the restart, at the game time 83, the record says `RUNNING` with 24 on board. The diary is `RUNNING`. At the end: `DONE`, source 40, destination 24, robot 0. The diary is `RUNNING, DONE`. The summary of the operator: "Since you last opened this screen:" and "0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." |
| `crash` | In the game, automatic | The test halted the process at the game time 420, as expected. The record said `DONE`, delivered 24 |
| `check-crash` | In the game, automatic | Passed. After the restart, at the game time 83: `RECOVERY` ("the world is older than the record of this job"), source 40, destination 0, robot 24. The diary is `RUNNING, DONE, RECOVERY`. At the end: `DONE`, delivered 24, "1 recovery. The totals can be inexact." The diary is `RUNNING, DONE, RECOVERY, RUNNING, DONE`. The summary: "Since you last opened this screen:", "2 s ago: … needs a check: I cannot say how it went. Recorded reason: the world is older than the record of this job." and "0 s ago: … is done: 24 delivered." |

After the four phases the data folder has `mindcraft.dat` and no temporary file.

Until run 75 no test of increment A called the real Claude service. No run used `-Plive`. `LiveTests.java` compiles with the code of increment A: each run compiles it.

Runs 76 and 78, on 2026-11-14, used `-Plive` with the consent of the project owner. The real service refused the first request with error 400: the schema had an `enum` next to a list of types. Run 81 passed with the corrected schema and the real service. See D29.1.

The evidence of increment A, by kind:

| Kind | What it covers |
| --- | --- |
| **In the game, automatic** | The 81 tests of run 75. Among them, 38 tests are new in increment A: 34 with the prefix `a_`, and `t04_source_removed`, `t05_pause_and_cancel_during_the_wait`, `t05_obstacle_keep_stock` and `t06_impediment_of_an_unknown_kind`. Increment A also changed older tests: each section above lists them. The four restart phases |
| **Code examination** | The protection of the record against a stop during a write (D37). The exit of the notices in the game: a line only for a connected owner (`Notices.CHAT`). The check of the long wait in the server tick. The templates of the long waits that no test builds. The confirmation and the buttons of a job call no provider. The hologram has the attention colour during a wait for a free path (`MindRuntime.mood`). The mod uses only `dan200.computercraft.api` |
| **Observed in one manual run** | A failure of the write, built by hand: the earlier record stays on the disk (ticket #7) |
| **In the game, manual** | The play session of ticket #13, on 2026-11-14. See the next section |
| **Not executed** | The rows of increment A in "Not executed" below |

## Increment A — the play session

Ticket #13 of spec #1. Date: 2026-11-14. Type: in the game, manual. Executor: the project owner, in the development client (`gradlew runClient`), with the checklist [PLAY_SESSION_A.md](PLAY_SESSION_A.md).

- The code is the commit `07bbf44` of the branch `increment-a`. Its runtime and its Lua modules are those of `1e83bf4`. The CI passed on both commits.
- The key went from the environment of the user into the process of the client, through a script that never printed it.
- During the session the agent read the log of the client, the record `mindcraft.dat` and the saved chunks. It compared each screenshot of the project owner with the checklist. These readings are part of this manual test. They are not tests of their own.
- Each step reached its expected behaviour. No command was used after the setup.
- Two differences of the product are deferred to increment B by the project owner. The other differences come from the procedure or from the rig. See D40 in [DECISIONS.md](DECISIONS.md).

| Part of the checklist | Result | Difference and decision |
| --- | --- | --- |
| B1 | Done before the session. Runs 76 and 78 got error 400. Run 81 passed with the corrected schema (D29.1) | None |
| B2 | The commit `07bbf44` | None |
| B3 | The key was in the environment of the client process only. It is in no file and in no message | None |
| B4 | Accepted. The session used 13 requests: 18169 input tokens and 1039 output tokens | None |
| S1 | The title screen. No error of MindCraft in the log | None |
| S2 | The log line "Uno interprets requests with Claude (claude-opus-5-5) for the operators, at most 100 requests for each of them in this session. …" | None |
| S3 | Built | The rig is rotated and shorter. The depot chest is at 1042 71 275, with its dock to the south. The corner is at 1042 71 279. The furnace dock is at 1039 71 279, with the chest to the west. The path has 3 + 3 moves, thus a round trip costs 12 fuel, not 16. The wall fills x 1038 to 1041, z 275 to 278, two blocks high. The saved chunks showed two tufts of grass in the corridor. The project owner removed them before S4. Decision: none. The case of a path with one corner is the same |
| S4 | Fuel 80, inventory empty | The first turtle got 160 fuel: two items burned. A new turtle replaced it before the enrolment. Decision: none |
| S5, S6 | As expected. R = 1 | None |
| S7 | Two chunks: [64, 17] and [65, 17] | None |
| S8 | A light blue hologram. The projector is at 1045 71 277, to the east of the corridor: the place of "north" in the rotated rig | None |
| S9, S10 | Done | None |
| Step 1 | As expected. The three lines before the request. Claude answered in less than 5 seconds. The proposal word for word. The counter after it: 1 of 100, 1428 in, 71 out. Job 1. Green, then light blue. The record: `SATISFIED`, 64 delivered, depot 192, furnace 64. No chat line | None |
| Step 2 | As expected: "Job 1, robot #1: keep at least 64 items in 'furnace', reserve 32 in 'depot'. I delivered 64 items. At the last observation the stock was at the requested level. Last observations: 'depot' 192 items, 94 seconds ago, 'furnace' 64 items, 0 seconds ago." No proposal. The counter: 2 of 100 | The optional question in Italian was not asked |
| Step 3 | As expected. The proposal word for word. The field showed 32 before the confirmation. "Confirm #2" fits the button. After it: "Job 1 on robot #1 changed. Level to keep in 'furnace': 64. Reserve to leave in 'depot': 16. The robot uses the new values at its next step." The field showed 16. The counter stayed 3 of 100 after the confirmation | In a window of about 850 × 510 pixels the screen is taller than the window. It scrolls, and its top or bottom lines are out of view. Decision of the project owner: deferred to increment B. Reason: the screen scrolls, and a larger window shows everything (D40) |
| Step 4 | As expected. The pause at once: "Job 1 on robot #1 is paused. The cargo stays on board." The button "Resume". The resume as a proposal, "Confirm #3", then "I resume job 1 on robot #1." and "stock at level". No chat line. The counter: 5 of 100 | None |
| Before step 5 | Job 1 became `CANCELLED`, "cancelled by the owner". No chat line | The project owner pressed the button "Cancel" of the job by mistake. It is next to "Apply", and the project owner read it as the cancel of the screen. The button cancels the job at once, without a confirmation (D19). The recovery: a new stock job by words, with the reserve 16 (job 2). Decision of the project owner: deferred to increment B. Reason: spec #1 keeps the buttons unchanged in A, and B changes the screen (D34, D40) |
| Step 5 | As expected. The job waited 434 ticks after the pickup, with the cause "path obstructed" and 16 on board. "Recorded reason: the robot cannot move from 1042 71 278 (south, in front: minecraft:stone) toward 1039 71 279: Movement obstructed". An amber hologram. The report: "… My path is obstructed. I try to move again every few seconds, and I continue by myself when the path is free. …" One chat line, 6000 ticks after the start of the wait: "Uno › Job 2 on robot #1 (minecraft:coal from 'depot' to 'furnace') has waited for 5 minutes: the path of the robot is obstructed." No second line. The stone stayed. After the stone was broken: `RUNNING` without a resume, the delivery, furnace 64. No chat line. The failed moves cost no fuel: 68 before, 56 after one round trip | The stone was at the corner, 1042 71 279, not between the corner and the furnace dock. The robot met it on the way back, with its load. Decision: none |
| Step 6 | The robot pushed the project owner along the corridor and continued. The configuration of the world has `can_push = true`. No wait, no damage, no lost item. Furnace 64. No chat line | The project owner took a whole stack, 64, not 16. The robot carried 64. Decision: none |
| Step 7 | As expected. On the 4th trip the robot stopped at 1042 71 278 with 64 on board, as computed from the fuel 44. One chat line: "Uno › Job 2 on robot #1 (minecraft:coal from 'depot' to 'furnace') is blocked: the robot has no fuel." An amber hologram. The summary at the opening had the long wait of step 5 and the block (the project owner remembered it; no screenshot). "Recorded reason: the robot cannot move from 1042 71 278 (south, in front: no block) toward 1039 71 279: Out of fuel". The report: "… I am stopped: I have no fuel. Give me fuel, then resume the job. …" With 8 charcoal on board the job stayed `BLOCKED` for 136 seconds. The resume as a proposal, "Confirm #5", then "I resume job 2 on robot #1." The robot burned the 8 charcoal, not the coal: fuel 636, inventory empty. It delivered its 64. No chat line for the resume | The project owner took 64 coal for each trip; the depot stayed above the reserve. The robot stopped loaded in the corridor, not empty at the dock: this follows from the shorter round trip. Decision: none |
| Step 8 | As expected. The proposal said "The cargo stays on board the robot." After it: "Job 2 on robot #1 is cancelled. The cargo stays on board the robot." The state "cancelled", without the buttons of a job. No chat line. The counter: 10 of 100 | None |
| Step 9 | As expected. The project owner was at x 1424, about 380 blocks from the rig. The proposal: "Proposal: carry 16 x minecraft:coal from 'depot' to 'furnace'. Reserve to leave in 'depot': 0. Robot #1. …" Then "Job 3 started on robot #1." `DONE` 9 seconds after the start, and the chat line "… is done: 16 delivered." | The screen stayed open until the job was complete. An open screen is not an opening, and the summary of step 10 came. Decision: none |
| Step 10 | As expected, except the age. The projector: "Since you last opened this screen:" and "1 min ago: Job 3 on robot #1 (minecraft:coal from 'depot' to 'furnace') is done: 16 delivered." The text fits, one event on a line. The second click: "Tell me what you need." The project owner flew back, without a command | The event was about 117 seconds old. The age rounds down to whole minutes: "1 min", not "2 min". The number agrees with the time. Decision: no change, proposed (D40) |
| Step 11 | As expected. The chat line "… Job 4 … is done: 8 delivered." After "Save and Quit to Title" and a new opening of the world: a new log line "Uno interprets requests with Claude …". The turtle showed "MindCraft: this turtle works for Uno." The projector: "Since you last opened this screen:" and "45 s ago: Job 4 on robot #1 (minecraft:coal from 'depot' to 'furnace') is done: 8 delivered." Not "From the start of my memory:". The counter: "0 of 100. Tokens: 0 in, 0 out." | The project owner pressed Esc 4 seconds after the chat line, not 1 minute later. The world stayed closed 14 seconds, not 1 minute. The age counts only the game time: about 4 seconds before the close and 41 seconds after the opening. Decision: none |
| Step 12 | As expected. Job 5 waited with 32 on board. "Cancel the job of robot 1" gave a proposal with "The cargo stays on board the robot." and "Confirm #2": the numbers start again in a new server session. After it: "Job 5 on robot #1 is cancelled. The cargo stays on board the robot." The state "cancelled", 32 on board. After the stone was gone, the saved world had the robot still at 1042 71 278 with 32 coal. No chat line | None |
| C1 | No command after S10 | None |
| C2 | English in each screenshot | None |
| C3 | No problem reported | None |
| C4 | No chat line for the own pause, resume and cancellations, by the button and by words | None |
| C5 | "Confirm #2" to "Confirm #7" fit the button | A number of two digits was not reached. Not verified |
| C6 | One more for each written request. No change for a confirmation, a notice or a summary | None |
| C7 | The first answer in less than 5 seconds. No slow answer reported. The slowest time was not measured | None |
| C8 | No error of MindCraft in the log of the client | None |
| C9 | Steps 7, 10 and 11: the next opening after a chat line gave a summary with the same sentence. A second opening without a new event gave "Tell me what you need." (README, after finding P2) | None |

The cost of the session: 11 requests with 15377 input tokens and 911 output tokens in the first server session; 2 requests with 2792 and 128 in the second. One request had about 1390 input tokens and 60 to 140 output tokens.

The session verified these points for the first time, in the game, manually:

- start, change, pause, resume, cancel and report with the real Claude service, in a client;
- the counter with the real tokens, and its new start in a new server session;
- the proposal, the button "Confirm #N" and the buttons of a job in a client;
- the chat lines of the notices to a connected owner: `BLOCKED`, `DONE`, and a long wait after 6000 ticks, one time;
- the summary of one or two events on the screen of a client, also after a new opening of the world;
- `/forceload`, and a robot that works while the player is about 380 blocks away;
- a player on the path, pushed by the robot;
- the English text of Uno in a client.

Not verified by the session: the notice for `RECOVERY` in a client, a summary with more than 10 events in a client, a player who is not an operator, "Confirm #N" with two digits, a request in Italian.

## The architecture review after increment A (D41)

Date: 2026-11-14. Type: in the game, automatic. Code: the branch `increment-a` at `0d801e7`, with the changes of D41: the account of a job (`JobAccount`) and its behaviour (`Behaviour`).

- Run 82: `Enabled Gametest Namespaces: [mindcraft]`. Server line: `All 81 required tests passed :)`. The tests did not change, except one comparison of two records: it compares the behaviour, not the name of the module.
- The four restart phases passed after run 82: `stop`, `check`, `check-crash` with `All 1 required tests passed :)`, and `crash` with its halt. The status at the halt gives the ages in seconds: `source stock: 40 (seen 17 s ago)`, `last contact: 4 s ago`.
- The changes keep the fields of the record and the texts for the player. The one exception is the unit of the ages in the status command.
- Not tested: a record of an older version with a module that the runtime does not know. The load makes it a transport, as before (code examination).
- Run 83, decision 3: `All 82 required tests passed :)`. The new test `a_parse_of_an_answer` checks the one reader of the JSON of an action, without a robot. The cases of answers that Uno cannot read moved there from `a_provider_answers_with_an_action` and `t18_provider_failures`. The tests of the fixed rules compare typed actions, not JSON text. The log line of the provider is the same: `Uno interprets requests with the fixed rules, without a language model.`
- Not run for decision 3: the restart phases, because the runtime and the Lua modules did not change. `-Plive`, because the schema and the system text of the Claude provider did not change. The JSON of the context is the same: `t18_claude_provider` compares it with the text of S2.
- Run 84, decision 4: `All 83 required tests passed :)`. The new test `a_cancel_button_asks_a_confirmation` presses "Pause", "Resume" and "Cancel" of a job. "Pause" and "Resume" act at once, with the sentences of the words. "Cancel" gives a proposal: the job stays open until "Confirm #N", then it is `CANCELLED`. The tests of an old view and of another player still get a refusal. The four restart phases passed after run 84.
- Not verified for decision 4: the proposal of "Cancel" on the screen of a client. The project owner decided that these changes get no manual test.

## Increment B — the kinds of a diary entry and the causes of RECOVERY (ticket #16)

Date: 2026-11-15. Ticket #16 of spec #15. See D41 (decision 5), D42 (points 2 and 12), D25, D30 and D31.
A prefactoring of the record: no sentence of Uno changes.
All tests of this section are in the game and automatic. No test called the real Claude service. No test has a connected player: the tests read the notices through the exit of the notices.
The table "Runs" gives the runs of this ticket the numbers 89 to 91.

| Run | Code | Result |
| --- | --- | --- |
| 89: red 1 | The module of the one-time warnings, the kind of a diary entry, and the new keys of the record. The load reads only the new keys | 81 of 83. `a_long_wait` failed as expected: "a record of increment A: [RUNNING, WAITING SOURCE_AT_RESERVE, WAITING ROBOT_FULL, WAITING ROBOT_FULL]". The long wait loaded as a change, and a later check wrote a second long wait. `a_summary_has_the_long_wait` failed as expected: "a record of increment A: Tell me what you need." |
| 90: green 1, red 2 | The load converts the flags of a record of increment A. The two causes of `RECOVERY` exist, but the runtime does not write them | 82 of 84. The two tests of run 89 passed. `b_causes_of_recovery` failed as expected: "RECOVERY (the robot holds 0, the record says 24)", without a cause. `a_no_notice_for_a_resume_that_completes_the_job` failed as expected: "[RUNNING, WAITING DESTINATION_FULL, RECOVERY, DONE]" |
| 91: green 2 | The runtime writes the cause together with `RECOVERY` | **84 of 84**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `84 GAME TESTS COMPLETE`, `All 84 required tests passed :)` |
| `stop`, `check`, `crash`, `check-crash` | The code of run 91 | Passed, each phase. `stop` and `check`: `All 1 required tests passed :)`. `check`: the diary after the restart is `RUNNING`, at the end `RUNNING, DONE`. `crash` ended with its halt. `check-crash`: after the restart `RUNNING, DONE, RECOVERY WORLD_OLDER`, at the end `RUNNING, DONE, RECOVERY WORLD_OLDER, RUNNING, DONE`, and `All 1 required tests passed :)`. The summary after the restart has the `RECOVERY` with its recorded reason, then the delivery. After the four phases the data folder has no temporary file |

| Test | What it does | Observed result |
| --- | --- | --- |
| `b_causes_of_recovery` (new) | A transport of 24 for a player. After the pickup the test empties the robot. Then it reads the job, the diary, the report, the notice and the screen. It saves and loads the record. It loads the record in the format of increment A | The job is `RECOVERY` with the cause `LOAD_DIFFERS` and the reason "the robot holds 0, the record says 24". The diary is `RUNNING`, `RECOVERY LOAD_DIFFERS`, and the entry has the reason. The report has "I cannot say how it went. Recorded reason: the robot holds 0, the record says 24." The one notice: "Job N on robot #R (minecraft:coal from 'b_recovery_source' to 'b_recovery_destination') needs a check: I cannot say how it went. Recorded reason: …". The screen has "Recorded reason: …". After a save and a load: the same cause and the same diary. The record of increment A: the cause is `NONE`, the diary is `RUNNING`, `RECOVERY`, and the report and the sentence of the notice are the same |
| `a_long_wait` (one more check) | The record after the long wait, in the format of increment A: the flag "longWait" on each entry and on the job. The test loads it, then checks the wait 12000 ticks after its start | The loaded diary is the same as the diary of the server: the long wait has the kind "long wait". The later check writes no second entry. The other expectations of the test did not change |
| `a_summary_has_the_long_wait` (one more check) | The opening of the screen on a copy of the record in the format of increment A | The same summary as on the server: "From the start of my memory:" and "2 min ago: … has waited for 5 minutes: nothing remains in 'a_summary_wait_source'." |
| `a_no_notice_for_a_resume_that_completes_the_job` (changed) | As before | The diary is `RUNNING`, `WAITING DESTINATION_FULL`, `RECOVERY LOAD_DIFFERS`, `DONE`. The test expected `RECOVERY` without a cause before. The notice is the same |
| `t11_server_restart`, phase `check-crash` (changed) | As before | The `RECOVERY` of the load has the cause `WORLD_OLDER` in the job and in the diary. The phase expected `RECOVERY` without a cause before |

Conditions built artificially:

- **A record of increment A:** the test helper `incrementA` changes a saved record. Each diary entry gets the flag "longWait" in place of its kind. Each job gets the flag "longWait" in place of the list of its given warnings. A `RECOVERY` gets the cause `NONE`. No world of increment A was loaded.
- **The moment of a check and of an opening:** a parameter, as in ticket #9.
- **The stand-ins of a `RECOVERY`:** `t11_resume_when_the_world_is_older`, `a_notices_call_no_provider` and `a_summary_after_the_time_went_back` set `RECOVERY` themselves. They now call the function of the runtime with the cause: `MindRuntime.recovery`. Their expectations did not change.

Code examination, not a test:

| Fact | Where | What it does not prove |
| --- | --- | --- |
| One module holds the one-time warnings: `Warnings`. `MindRuntime.warn` writes the entry of a warning. The long wait is its only kind now | `Warnings.java`, `MindRuntime.java` | That the low fuel notice can use it. Ticket #24 adds that kind |
| The attention rule selects each entry whose kind is not a change. The sentence of a long wait comes from its kind | `Notices.selected`, `Notices.sentence` | |
| The two writes of `RECOVERY` in the runtime give a cause: `RobotApi.status` and `MindRuntime.get` | `RobotApi.java`, `MindRuntime.java` | |
| The report, the screen and the status command still read the recorded reason of a `RECOVERY`. No sentence reads its cause | `JobAccount.java`, `UnoView.java` | |
| A change from `RECOVERY LOAD_DIFFERS` to `RECOVERY WORLD_OLDER` writes one more entry and gives its notice. Before the ticket it wrote none | `MindRuntime.set` | Its effect in a run. No test builds it. See D25 |

Not covered:

- A real world of increment A. The test builds the older format from a saved record.
- A long wait across a real restart. The restart phases build no wait, as in ticket #9.
- The kind "low fuel": ticket #24. The facts for the model, that read the kind and the cause: ticket #19.

## Increment B — a job adopts the load on board (ticket #17)

Date: 2026-11-15. A new job counts the items of its item that the robot holds as its first load. See D42, point 9, and D09.
The code is the branch of increment B at `9d13c95`, with the changes of the ticket. No Lua module changed. No run called the real Claude service.

### Automatic in-game tests

Each test of this section is in the game and automatic, with real turtles and real chests. Each one uses the fixed rules or no provider.

| Test | What it does | Result in run 88 |
| --- | --- | --- |
| `b_adoption_by_the_command` | The console sends the real command `/mindcraft job transport … 64` to a turtle with 24 coal on board, and reads the answer | Passed. The answer: "Job N started on robot #M. It counts the 24 x minecraft:coal on board as its first load." The record has 24 on board and 0 taken. The first diary entry is `RUNNING` with 24 on board. The robot delivers 24 while the source still has 64. At the end: `DONE`, source 24, destination 64, robot 0, taken 40, delivered 64, no recovery. The diary is `RUNNING`, `DONE` |
| `b_adoption_keep_stock` | A stock of 64, a destination with 50, a turtle with 24 coal | Passed. The robot delivers 14 from its load: `SATISFIED`, destination 64, robot 10, source 64, taken 0, "10 stay on board". After a consumption of 30, the robot delivers its 10, then takes 20: destination 64, source 44, robot 0. No `RECOVERY` in the diary |
| `t14_cancel`, changed | The cancellation test of S1, with 24 on board. Before: a new job of 8 was refused with "Empty it first". Now the new job adopts the 24 | Passed. The new job has 24 on board and 0 taken. It delivers 8, then it is `BLOCKED` with the cause `LOAD_ABOVE_QUANTITY`: source 40, destination 8, robot 16 (D16.1) |
| `b_adoption_in_the_proposal` | Five written requests of a player to the fixed rules. Then a confirmation | Passed. See below |

The proposals of `b_adoption_in_the_proposal`, in their order. The robot is #62 in run 88. The test compares the whole text.

1. Nothing on board: the text of increment A. "Proposal: carry 24 x minecraft:coal from 'b_proposal_source' to 'b_proposal_destination'. Reserve to leave in 'b_proposal_source': 0. Robot #62. I have done nothing yet. Confirm with /uno confirm."
2. 5 cobblestone on board: after "Robot #62.", "5 items that are not minecraft:coal stay on board: the job does not count them. When its fuel ends, the robot burns those that can burn."
3. Also 10 coal: "The robot holds 10 x minecraft:coal: the job counts them as its first load.", then the sentence of the cobblestone.
4. A stock with a level of 6: after the coal sentence, "That is more than the level: the rest stays on board for the next need."
5. A transport of 8, from the log of run 88: "… Robot #62. The robot holds 10 x minecraft:coal: the job counts them as its first load. That is more than the quantity: the job delivers at most 8, then it stops with the rest on board for your decision. 5 items that are not minecraft:coal stay on board: the job does not count them. When its fuel ends, the robot burns those that can burn. I have done nothing yet. Confirm with /uno confirm."

- No proposal started a job.
- The confirmation of proposal 5: "Job N started on robot #62. It counts the 10 x minecraft:coal on board as its first load." The record has 10 on board, not 15.
- At the end: `BLOCKED` with `LOAD_ABOVE_QUANTITY`, delivered 8, 2 on board. Source 64, destination 8, robot 2 coal and 5 cobblestone. The destination has no cobblestone.

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 85, red 1 | The new tests of the runtime, with the old code | Not valid. `b_adoption_by_the_command` failed its check: "the command did not start the job". The sequence continued, and its next step read a job that did not exist. The `NullPointerException` stopped the server: the test sequence catches only an assertion. The log has no result for the other tests. Correction in the tests: each wait of the new tests reads the job only when it exists, and the helper `accepted` changes a refusal of a start into a failed check |
| 86, red 2 | The four new and changed tests, with the old code | 82 of 86, expected. `b_adoption_by_the_command`: "the command did not start the job: [Robot #7 already holds 24 of this item. Empty it first.]". `b_adoption_keep_stock` and `t14_cancel`: "the runtime refused a request that it must accept: … Empty it first." `b_adoption_in_the_proposal` failed at proposal 2: the text of A, without the sentence about the cobblestone |
| 87, green 1, red 3 | The runtime: no refusal, the record counts the load, the answer of a start gives it | 85 of 86, expected. The three tests of the runtime passed. `b_adoption_in_the_proposal` failed at proposal 2, as in run 86 |
| 88, green 2 | The sentences of the proposal | **86 of 86**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `86 GAME TESTS COMPLETE`, `All 86 required tests passed :)` |

The four restart phases after run 88, one at a time, on the same world. Type: in the game, automatic.

| Phase | Result |
| --- | --- |
| `stop` | Passed. `All 1 required tests passed :)`. The status at the stop: `RUNNING`, taken 24, delivered 0, on board 24 |
| `check` | Passed. `All 1 required tests passed :)`. The diary after the restart: `RUNNING`. Then source 40, destination 24, robot 0, and the diary `RUNNING`, `DONE` |
| `crash` | Passed: the process halted, as the phase does. The status at the halt: `DONE`, delivered 24, on board 0 |
| `check-crash` | Passed. `All 1 required tests passed :)`. The diary after the restart: `RUNNING`, `DONE`, `RECOVERY`. The first stable state: source 40, destination 0, robot 24. After `resume`: the diary `RUNNING`, `DONE`, `RECOVERY`, `RUNNING`, `DONE` |

The restart test starts its job with an empty turtle. Thus it does not test an adoption across a restart.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| `transport.lua` delivers when the status gives a load on board (`job.carried > 0`). `keep_stock.lua` does a step of the transport when it has a load, also before the first observation of the destination. Thus both modules deliver an adopted load first | `transport.lua`, `keep_stock.lua`, unchanged | |
| When a move fails for fuel, `transport.lua` burns each item on board that is not the item of the job | `refuel` in `transport.lua` | That a given item burns. No automatic test burns an item of another kind. The play session of increment A saw it once, with charcoal: step 7 in "Increment A — the play session" |
| `GameTestSequence` of Minecraft 1.20.1 catches only `GameTestAssertException` in a step. Each other exception of a step stops the server | `javap -c` of `GameTestSequence.executeWithoutFail` and `tick`, Forge 47.4.28 jar (official mappings) | |

### Not covered

- An adoption across a restart of the server.
- The proposal and the answers on the screen of a client. The play session of increment B has a step for an adoption.
- The singular in the sentences: "1 items" for one item.

## Increment B — a job replaces a job (ticket #18)

Date: 2026-11-15. A start by words for a robot with an open job is one proposal: cancel that job, and start the new one. One confirmation does both, in one tick. Thus the player changes the destination of a job with words. See D42, point 10, and D34.
The code is the branch of increment B at `60443b5`, with the changes of the ticket. No Lua module changed. No run called the real Claude service.

### Automatic in-game tests

Each test of this section is in the game and automatic, with real turtles and real chests, except `b_fixed_rules_choose_the_robot`: it needs no robot and no world. Each one uses the fixed rules or a provider written in the test. `t18_claude_provider` uses a false server in place of the API.

| Test | What it does | Result in run 95 |
| --- | --- | --- |
| `b_replacement_changes_the_destination` | The destination is full: the transport of 24 waits with 24 on board (`DESTINATION_FULL`). The console sends `/mindcraft job transport` for the busy robot. Then the player writes "Carry 24 coal from … to …" with a third station and no robot number. The player has one robot. The confirmation comes 20 ticks after an attempt of the robot to deliver | Passed. The command is refused: "Robot #N already has job M (WAITING)." The words give the proposal of a replacement, with "The robot holds 24 x minecraft:coal". Before the confirmation the job, the robot and the chests do not change. The answer: "Job M on robot #N is cancelled. Job K started on robot #N. It counts the 24 x minecraft:coal on board as its first load." The diary of the old job is `RUNNING`, `WAITING DESTINATION_FULL`, `CANCELLED`. The cancellation is a request, and the first entry of the new job has the same game time and the next number. At the end: `DONE`, source 40, third station 24, destination 0 coal, robot 0, taken 0, delivered 24. No notice about the old job |
| `b_replacement_in_recovery` | The turtle loses 14 of its 24 coal: `RECOVERY`. The player writes a transport of 10 without a robot number. Before the confirmation the test leaves 6 coal in the turtle | Passed. The proposal says "The robot holds 10 x minecraft:coal". The answer says "It counts the 6 x minecraft:coal on board as its first load." The diary of the old job is `RUNNING`, `RECOVERY`, `CANCELLED`. The new job delivers 6, takes 4 and is `DONE`: source 36, destination 10, robot 0, no recovery |
| `b_replacement_in_each_open_state` | Four replacements in a row, from a provider of the test. The old job is `SATISFIED` (a stock at its level), `PAUSED`, `BLOCKED` (an impediment of an unknown kind), and `RUNNING` (a resume in the tick of the confirmation) | Passed. Each answer: "Job M on robot #N is cancelled. Job K started on robot #N. The robot holds no minecraft:coal: the new job starts without a load." Each old job ends `CANCELLED` after its state. Each cancellation has the game time of the first entry of the new job |
| `b_replacement_refused` | Three replacements that must change nothing. 1: a new order with an unknown station. 2: a proposal, then the robot gets a different job. 3: a proposal, then the computer of the robot shuts down | Passed. 1: "I cannot propose this job: Unknown station '…'." No proposal, and the job stays. 2: "I cannot replace the job: Robot #N has job K now, not job M. Read it again." The different job stays. 3: "I cannot replace the job: Robot #N is not running. Load its area and turn it on." The job stays open, and its diary has no `CANCELLED` |
| `b_replacement_through_the_view` | The "ask" action of the screen, with the fixed rules of the server, and no robot number. A different player looks at the screen, presses the button of the owner, sends `/uno confirm`, and asks a start for the robot of the owner. Then the owner presses the button | Passed. The view has "Waiting for your confirmation: cancel job M on robot #N (…), and start in its place: carry 8 x …" and the button "Confirm #P". The other player sees no button and no station of the owner, and confirms nothing: "I have no pending proposal". Its start: "I cannot propose this job: Robot #N is not yours." The proposal of the owner stays. After the button of the owner: the old job is `CANCELLED`, and the view says "… is cancelled. Job K started on robot #N. The robot holds no minecraft:coal: the new job starts without a load." |
| `b_fixed_rules_choose_the_robot` | A start without a robot number, with six sets of robots | Passed. One busy robot: that robot. One busy and one free: the free one. One free: that one. Two busy, two free, or none: "you have N free robots: write 'robot N'" |
| `a_confirmation_of_older_values`, changed | Before: a start for the busy robot was refused. Now the test expects the proposal of a replacement, and the next request replaces it | Passed. The proposal names the job, and the job does not change |
| `t18_claude_provider`, changed | The request that goes to the false server of the API | Passed. The system text has the rule of a request without a robot number, and the sentence about the replacement. The schema keeps its ten fields |

The texts of the proposals, from the log of run 94. The test compares the whole text.

- `b_replacement_changes_the_destination`: "Proposal: cancel job 38 on robot #45 (carry minecraft:coal from 'b_replace_source' to 'b_replace_destination'), and start in its place: carry 24 x minecraft:coal from 'b_replace_source' to 'b_replace_other'. Reserve to leave in 'b_replace_source': 0. Robot #45. The robot holds 24 x minecraft:coal: the job counts them as its first load. The load can change until the confirmation: the new job counts the load that the robot holds then. I have done nothing yet. Confirm with /uno confirm."
- Its confirmation: "Job 38 on robot #45 is cancelled. Job 86 started on robot #45. It counts the 24 x minecraft:coal on board as its first load."
- `b_replacement_through_the_view`, a robot that holds nothing: "… Reserve to leave in 'b_replace_view_source': 0. Robot #60. The load can change until the confirmation: the new job counts the load that the robot holds then." The button: "Confirm #25".

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 93, red | The new and changed tests, without the new code. `b_replacement_in_each_open_state` did not exist yet | 84 of 91, expected. `b_fixed_rules_choose_the_robot`: one busy robot gave "you have 0 free robots". `b_replacement_changes_the_destination`, `b_replacement_in_recovery` and `b_replacement_through_the_view`: "I cannot propose a job: you have 0 free robots: write 'robot N'." `b_replacement_refused` and `a_confirmation_of_older_values`: "I cannot propose this job: Robot #… already has job … (WAITING)." `t18_claude_provider`: the system text without the new rule |
| 94, green | The runtime, Uno, the fixed rules and the system text | **91 of 91**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `91 GAME TESTS COMPLETE`, `All 91 required tests passed :)` |
| 95 | One more test, `b_replacement_in_each_open_state`, and the name of a local variable in `Uno.Replace` | **92 of 92**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `92 GAME TESTS COMPLETE`, `All 92 required tests passed :)` |

`b_replacement_in_each_open_state` passed at its first run: the code of run 94 already had the behaviour. It has no red run.

The four restart phases after run 95, one at a time, on the same world. Type: in the game, automatic.

| Phase | Result |
| --- | --- |
| `stop` | Passed. `All 1 required tests passed :)`. The status at the stop: `RUNNING`, taken 24, delivered 0, on board 24 |
| `check` | Passed. `All 1 required tests passed :)`. The diary after the restart: `RUNNING`. Then source 40, destination 24, robot 0, and the diary `RUNNING`, `DONE` |
| `crash` | Passed: the process halted, as the phase does. The status at the halt: `DONE`, delivered 24, on board 0 |
| `check-crash` | Passed. `All 1 required tests passed :)`. The diary after the restart: `RUNNING`, `DONE`, `RECOVERY`. The first stable state: source 40, destination 0, robot 24. After `resume`: the diary `RUNNING`, `DONE`, `RECOVERY`, `RUNNING`, `DONE` |

The restart test does not replace a job. A proposal is not saved (D24): thus no replacement crosses a restart.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| A confirmation runs on the server thread: the command `/uno confirm` and the action of a button. `MindRuntime.replace` checks, cancels and starts in one call. Thus no step of a robot comes between the cancellation and the start | `Uno.confirm`, `UnoView.act`, `MindRuntime.replace` | |
| The cancellation of `control` has no rule that depends on the state of an open job. Thus a replacement works in each open state | `MindRuntime.checkControl` | |
| If the open job ends by itself before the confirmation, `checkReplace` refuses: "Robot #N has no open job." Nothing starts | `MindRuntime.openJob` | No test builds it |
| A call of the old Lua run can reach the new job. The module reads the job, and then it moves or transfers. One or two ticks are between the two. At a dock of a different station that call fails, and the agent reports a module error for the new job. This is the limit of D26 | `transport.lua`, `keep_stock.lua`, the agent `mindcraft.lua`, `RobotApi` | That it occurs. No run saw it. `b_replacement_changes_the_destination` confirms while the old module sleeps |

### Not covered

- The new system text with the real Claude service. No `-Plive` run used it. CLAUDE.md asks for one run before the merge into `main`. Ticket #27 has the paid runs of increment B.
- A change of the destination that a model builds from the facts of the job. The facts come with a later ticket of increment B.
- The proposal, the button and the answers on the screen of a client. The play session of increment B has a step for a replacement.
- An operator who replaces the job of a different player.

## Increment B — the facts of the jobs go to the model (ticket #19)

Date: 2026-11-15. With each written request, a provider gets the facts of the jobs of the player and the 20 newest lines of the diary. See D42, points 2, 3, 4 and 18, and D41, decision 6.
The code is the branch of increment B at `39a2afc`, with tickets #16 and #17, and the changes of this ticket. No Lua module changed. The answers of Uno did not change.
No run called the real Claude service. No run with the real service sent the facts. The system text and the output schema of the Claude provider did not change.

### Automatic in-game tests

Each test of this section is in the game and automatic. Each one uses a provider written in the test, the fixed rules or a local stand-in of the service.

| Test | What it does | Result in run 101 |
| --- | --- | --- |
| `b_facts_in_the_context` (new) | A player and a real turtle. A provider of the test keeps the context that Uno gives it, and answers `none`. First a transport of 24 with a reserve of 10 ends `DONE`. A different player writes too. Then the test removes the destination chest, and a transport of 16 is `BLOCKED`: its recorded reason has the position of the destination. Then the owner empties the robot and resumes: `RECOVERY` | Passed. See below |
| `b_facts_of_uncertain_numbers` (new) | The account gets a record that the test writes, the flag of the robot and a game time. No robot and no world. Then the diary lines of a diary that the test writes | Passed. See below |
| `t18_claude_provider` (changed) | The robot first does a transport of 8 to `DONE`. The test reads the body of the request that reaches the stand-in of the service | Passed. The body has the stations and the robots as before. It also has the facts of the job: `{"job":N,"robot":R,"module":"transport","item":"minecraft:coal","from":…,"to":…,"quantity":8,"reserve":0,"state":"DONE","cause":"NONE","delivered":8,"onBoard":0,"sourceStock":{"count":56,"age":…},"destinationStock":{"count":8,"age":…},"stillToCarry":0,"aboveReserve":56,"uncertain":[]}`. It has the two lines of the diary, `RUNNING` and `DONE`. Each of the four ages is "N s ago". The test compares the JSON without the ages with a fixed text. The pause of that robot now gets "I cannot pause the job: Robot #R has no open job.": the robot has a job that is done. Before, the robot had no job |
| `a_screen_model_lines` (changed) | As before, with the new line | Passed. The screen of an operator has "Written requests go to Claude, a model of Anthropic, with the data of your jobs and the newest lines of my diary. No position leaves this server." A player who is not an operator and an operator at the limit do not see it |

The contexts of `b_facts_in_the_context` in run 101, from the log. The robot is #22. The first job is 44, the second is 81.

1. The job that is done: `DONE`, delivered 24, on board 0, source 40 "3 s ago", destination 29 "0 s ago", still to carry 0, above the reserve 30, nothing uncertain. The lines: `44 RUNNING` with 0 delivered and 0 on board, `44 DONE` with 24 delivered. Each line has the age, the robot, the kind `CHANGE`, the cause, the quantity 24 and the reserve 10.
2. The different player: no station, no robot, no job and no line. The answers of Uno to both players are the answers of increment A: "I cannot propose a job: The test read the context."
3. The job that is blocked: `BLOCKED DESTINATION_NO_INVENTORY`, delivered 0, on board 16, source 24 "2 s ago", still to carry 16, above the reserve 14. No destination stock, with "No robot observed 'b_facts_destination' yet. Its stock is unknown." The lines of job 44 stay after job 81 replaced it on the robot. The text of the context and its JSON have no position, no recorded reason and no ID of the owner.
4. The job that needs a check: `RECOVERY LOAD_DIFFERS`, with no delivered total, no load on board, no stock and no derived number. The sentence: "The result of the job is uncertain: the load in the robot differs from the record. Not given: the delivered total, the load on board and the stocks." The four lines of job 81 have no delivered total and no load on board. The two lines of job 44 keep their numbers.

The cases of `b_facts_of_uncertain_numbers`. The record: a transport of 24 with a reserve of 10, 6 delivered, 4 on board. The source had 50 at 6000 ticks before "now". The destination had 6 at 240 ticks before "now". The robot called at 300 ticks before "now". The test compares the whole facts.

| Case | The facts that the account gave |
| --- | --- |
| The record as it is: at the limit of an old observation and of a silent robot | Each number: delivered 6, on board 4, source 50 "5 min ago", destination 6 "12 s ago", still to carry 18, above the reserve 40. Nothing uncertain |
| The source observed 6001 ticks before | No source stock and no stock above the reserve. "The last observation of 'depot' is old. Not given: its stock." |
| The robot called 301 ticks before, and no call since the server start | No load on board and no stock. "The robot has not contacted the server for a while. Not given: the load on board and the stocks." |
| The robot is not running | The same, with "The robot is not running or not loaded." The delivered total stays |
| `RECOVERY` with `LOAD_DIFFERS`, with `WORLD_OLDER`, and without a cause (a record of an older version) | No delivered total, no load on board, no stock, no derived number. The sentence gives the cause: "the load in the robot differs from the record", "the world is older than the record", or no cause |
| After a recovery | No delivered total and no quantity still to carry. "After a recovery the delivered total can be inexact. Not given: the delivered total." |
| A stock job with a level of 64, and a source of 8 | Still to carry 58. Above the reserve 0 |
| A stock job before its first observation of the destination | No destination stock and no quantity still to carry. "No robot observed 'furnace' yet. Its stock is unknown." This is the case of run 99 |

- The diary lines, with job 900 in `RECOVERY`, the robot of job 600 not running, and job 800 with a `RECOVERY` line but no facts: `800 RUNNING` and `800 DONE` have no delivered total. `800 RECOVERY` has no delivered total and no load on board. `700 DONE` keeps its 8. `600 RUNNING` keeps its delivered total and has no load on board. The lines of job 900 have neither.
- A diary of 25 entries gives 20 lines: the newest, the oldest first. The first is "20 min ago", the last "1 min ago".

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 98, red 1 | The new test `b_facts_in_the_context`, without its last part. The typed context exists, but `Uno.context` gives no facts and no lines | 87 of 88, expected. `b_facts_in_the_context` failed: "Context[…, jobs=[], diary=[]]" |
| 99, green 1, red 2 | The facts and the lines, without the doubts. The new test `b_facts_of_uncertain_numbers`, and the last part of `b_facts_in_the_context` | Not valid. `b_facts_of_uncertain_numbers` failed as expected: each case gave each number. Then a `NullPointerException` in `JobAccount.facts` stopped the server. A conditional expression had an `Integer` and an `int`: for a stock job with an unknown destination, Java unboxed a null. The log has no result for the other tests. The red of the last part of `b_facts_in_the_context` was not observed. Correction: both branches give an `Integer`, and `b_facts_of_uncertain_numbers` has the case of a stock job with an unknown destination |
| 100, green 2, red 3 | The doubts. The tests of the body of the request and of the screen line, with the old JSON and the old line | 87 of 89, expected. The two new tests passed. `t18_claude_provider` failed: "the body has no facts". `a_screen_model_lines` failed: the view of an operator has the old line |
| 101, green 3 | The JSON of the context with the facts and the lines. The new line of the screen | **89 of 89**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `89 GAME TESTS COMPLETE`, `All 89 required tests passed :)` |
| 102 | The code of the commit. Only the test changed after run 101: one more null check before the test empties the robot, the same form for one step of the sequence, and its comment | **89 of 89**. The same three log lines |

The four restart phases after run 101, one at a time, on the same world. Type: in the game, automatic. The code of the mod did not change after run 101: only the test of run 102.

| Phase | Result |
| --- | --- |
| `stop` | Passed. `All 1 required tests passed :)`. The status at the stop: `RUNNING`, taken 24, delivered 0, on board 24 |
| `check` | Passed. `All 1 required tests passed :)`. The diary after the restart: `RUNNING`. Then source 40, destination 24, robot 0, and the diary `RUNNING`, `DONE` |
| `crash` | Passed: the process halted, as the phase does. The status at the halt: `DONE`, delivered 24, on board 0 |
| `check-crash` | Passed. `All 1 required tests passed :)`. The diary after the restart: `RUNNING`, `DONE`, `RECOVERY WORLD_OLDER`. The first stable state: source 40, destination 0, robot 24. At the end: `RUNNING`, `DONE`, `RECOVERY WORLD_OLDER`, `RUNNING`, `DONE` |

After the four phases the data folder has no temporary file.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| `Uno.context` reads the jobs only through `MindRuntime.jobs(actor)`, and the diary through `MindRuntime.diary(actor)`. Both keep only what the actor owns | `Uno.java`, `MindRuntime.java` | That no other code gives the model a job. The map of the jobs is still public (D41, decision 6) |
| The "free" flag of a robot still reads the job of each robot of the actor, as in increment A | `Uno.context` | |
| No field of the facts or of a line holds a position, the owner or a recorded reason. The records `Uno.Context.Facts` and `Uno.Context.Line` have no such field | `Uno.java`, `JobAccount.java` | That a station name holds no position: the player chooses the name |
| `ClaudeProvider` sends `context.toJson()` in the user message, as before. The system text and the schema are the same | `ClaudeProvider.java` | How the real model reads the facts. No `-Plive` run sent them |
| The fixed report uses the same doubt about the robot as the facts: one function, `JobAccount.silent` | `JobAccount.java` | |

### Not covered

- A request with the facts to the real service. The paid runs of increment B need the consent of the project owner (ticket #27).
- The line of the screen in a client.
- A job that the owner cancelled in `RECOVERY`: its facts give its delivered total. See D42, point 3.
- A diary entry of an older job whose `RECOVERY` the diary limit removed: its later lines give their delivered total.

## Increment B — the fixed rules read a request when the model gives no valid action (ticket #21)

Date: 2026-11-15. A provider with a model gives no valid action: a failure of the service, the time limit, a refusal, or an answer that Uno cannot read. Then the fixed rules read the same request, and Uno says so first, with the reason. See D42, point 17, D29.1 and D33.1.
The code is the branch of increment B at `a807f3a`, with the change of `b_replacement_in_recovery` of the integration branch (`5e3439a`), and the changes of the ticket. No part of the runtime and no Lua module changed. The schema and the system text of the Claude provider did not change. No run called the real Claude service.

### Automatic in-game tests

Each test of this section is in the game and automatic, with real turtles and real chests. Each one uses providers written in the test. A provider of the test with the model flag (`CountingModel`) calls no model and costs nothing. Its requester is an operator: only an operator uses a provider with a model (D33.1).

| Test | What it does | Result in run 105 |
| --- | --- | --- |
| `t18_provider_failures`, changed | An operator has a transport of 24 in progress. Five requests go to providers of the test with the model flag. 1: an exception "model offline", for the request "x". 2: a provider that sleeps 3 s, with a time limit of 300 ms, for "How is robot N?". 3: the exception that the Claude provider gives for a refusal, "the model declined the request", for "Carry 8 coal from … to … with robot N". 4: an answer that Uno cannot read, with 7 input tokens and 3 output tokens. 5: an `IllegalArgumentException`. Then two requests go to providers of the test without a model: an exception "provider offline", and an answer that Uno cannot read. At the end the fixed rules get "carry coal" | Passed. Each of the five answers starts with "The model gave no valid answer (…), thus I read your request with the fixed rules: they understand only simple sentences." The reasons, in order: "model offline", "the request reached the time limit", "the model declined the request", "I cannot read its answer", "a defect of the provider". Then the answer of the fixed rules to the same request: "I cannot propose a job: the quantity is missing." for "x", the report of the job for the question, and the proposal of a replacement for the start. The counter: 5 requests, 7 input tokens, 3 output tokens. The providers without a model give the sentences of increment A: "I cannot interpret the request now (provider offline). The jobs in progress continue." and "I did not get a valid configuration. I created no proposal." The counter does not change for them. At the end no proposal stays. The job ends `DONE`: source 40, destination 24, robot 0 |
| `a_model_requests_count`, changed | As before. An operator sends a request to a provider with the model flag that throws "model offline", then one to a provider that answers after the time limit of 300 ms | Passed. The two answers are "The model gave no valid answer (model offline), thus I read your request with the fixed rules: they understand only simple sentences. I cannot propose a job: the quantity is missing." and the same with "the request reached the time limit". The counter is 3 requests after the exception and 4 after the time limit, with the same tokens as before. The late answer of the provider changes nothing |

The texts of `t18_provider_failures`, from the log of run 105. The test compares the start of the second and of the third answer, and the whole text of the others.

- The question about the robot: "The model gave no valid answer (the request reached the time limit), thus I read your request with the fixed rules: they understand only simple sentences. Job 71, robot #42: carry 24 items from 't18_source' to 't18_destination', reserve 0. I delivered 0 items. I am working. …"
- The start: "The model gave no valid answer (the model declined the request), thus I read your request with the fixed rules: they understand only simple sentences. Proposal: cancel job 71 on robot #42 (carry minecraft:coal from 't18_source' to 't18_destination'), and start in its place: carry 8 x minecraft:coal from 't18_source' to 't18_destination'. … I have done nothing yet. Confirm with /uno confirm."

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 104, red | The two changed tests, with the old code of Uno | 91 of 93, expected. `a_model_requests_count`: "I cannot interpret the request now (model offline). The jobs in progress continue." `t18_provider_failures`: "the proposal of the fixed rules is missing", after three answers with the failure sentence of increment A. `b_replacement_in_recovery` passed with the change of `5e3439a` |
| 105, green | The code of the ticket | **93 of 93**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `93 GAME TESTS COMPLETE`, `All 93 required tests passed :)` |
| 106 | The commit `edb70a3`: the code of run 105, with one comment changed | **93 of 93**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `93 GAME TESTS COMPLETE`, `All 93 required tests passed :)` |

The two branches of the code came in one step: an exception or the time limit, and an answer that Uno cannot read. Thus run 104 is the red run of both. Its first failed check stopped `t18_provider_failures` before the case of an answer that Uno cannot read.

This ticket changed no part of the runtime and no Lua module. Thus the four restart phases did not run for it.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The sentence comes only on the route of the model. A provider without a model, a request after the limit of the session, and a player who is not an operator do not get it | `Uno.ask`, `Uno.route` | |
| The fixed rules run on the server thread, after the answer of the provider. They call no model and no network. They read the context of the request: the context that the model got | `Uno.ask`, `Uno.RuleProvider` | Their cost on the server thread. No measurement |
| The Claude provider gives a refusal, a cut answer, an error of the service and a service that is not reachable as an exception with a reason. It gives an answer that is not the JSON of an action as `Unreadable` | `ClaudeProvider.answer`, `Uno.parse` | `t18_claude_provider` sends these answers to Uno with a provider without the model flag: thus they get the sentences of increment A. No test sends them to Uno with the model flag |
| After the time limit the thread of the provider continues. Uno does not read its late answer | `Uno.ask` | The tokens that the real service bills for it (D33.1) |

### Not covered

- A failure of the real Claude service. No run with the real service tested a failure: an error, a refusal, the time limit, or an answer that Uno cannot read.
- The Claude provider with its model flag against the false server of the API, with a failure. See the code examination above.
- The sentence on the screen of a client. The play session of increment B.

## Increment B — the voice of Uno and its checks (ticket #20)

Date: 2026-11-15. A provider gives one sentence with a report and with "none": the voice of Uno. The server checks the sentence. A fixed sentence gives the uncertain numbers before the voice. See D42, points 1, 3, 4, 5, 15 and 16, and D32.
The code is the branch of increment B at `8df1f39`, with tickets #16 to #19 and #21, and the changes of this ticket. No part of the runtime and no Lua module changed. The system text of the Claude provider is new. Its output schema did not change: ten fields.
No run called the real Claude service. **No test verifies a sentence of the real model.** Each sentence of these tests is written in the test.

### Automatic in-game tests

Each test of this section is in the game and automatic. Each one uses a provider written in the test, the fixed rules, or the local stand-in of the service. The tests with robots use real turtles and real chests.

| Test | What it does | Result in run 111 |
| --- | --- | --- |
| `b_voice_of_a_report` (new) | Two jobs of one player wait for materials, as in `a_report_and_pause_by_words`. Both use the destination of the first rig. 13 requests to a provider of the test that gives a report with a sentence. Nothing is uncertain: the robots run and call, and the observations are recent | Passed. See below |
| `b_voice_of_none` (new) | A player without a station, a robot or a job: the context has no digit. Six requests to a provider of the test that gives "none" with a sentence | Passed. See below |
| `b_no_voice_for_an_action` (new) | A job waits for materials. A provider of the test gives a pause, a resume, a change of the reserve, a cancellation and a start, each with the sentence "Done, as you asked." | Passed. Each answer is the text of the server, compared with a fixed text: the answer of the pause, then four proposals. The start is the proposal of a replacement. No answer has the sentence of the provider |
| `b_fixed_sentences_of_uncertain_numbers` (new) | The account of a record that the test writes, with the flag of the robot and a game time. No robot and no world. The record and the limits of `b_facts_of_uncertain_numbers` | Passed. See below |
| `b_voice_after_uncertain_numbers` (new) | Two jobs of one player. Job A waits for materials. Job B is done. Built artificially: the test calls the function of the runtime that sets `RECOVERY` for job A, and it moves the observation of the destination of job B back by 6001 ticks in the record. Five reports. Then the owner resumes job A, and the computer of robot B shuts down. One more report | Passed. See below |
| `t18_claude_provider` (changed) | One more answer of the stand-in: a report with the sentence "The job is done: I delivered 8 items." The test reads the system text | Passed. "none" gives "You did not give the source." alone. The report gives its sentence alone. The system text has each rule of the voice: the character, the first person, "two sentences and 300 characters at most", "Use only the numbers of the context and of the request", "Do not calculate", "You do not remember an earlier", the field "reason" for "report" and "none" only, and "For each other action, "reason" is empty text." It keeps the two rules of ticket #18. The schema has its ten fields, as before |
| `a_parse_of_an_answer` (changed) | The reader of the JSON of an action | Passed. "reason" is the sentence of a report and of "none". A sentence of 300 characters is not cut. A "none" with an empty sentence is a "none": the check of the length refuses it later. A "none" with a null reason is an answer that Uno cannot read. A pause with a reason has no sentence |
| `a_provider_answers_with_an_action`, `b_facts_in_the_context`, `a_model_only_for_operators` (changed) | The answer to "none" of a provider of the test | Passed. The answer is the sentence alone, for example "The test read the context." Before: "I cannot propose a job: The test read the context." |

Tests that did not change and that cover parts of this ticket, in run 111:

- `a_report_and_pause_by_words` and `a_t19_words_of_another_player`: their reports have an empty sentence. They get the fixed report and the sentences of D29.2, as before.
- `a_fixed_rules_report_and_pause`, `s2_uno_reference_sentence` and `b_fixed_rules_choose_the_robot`: the fixed rules give actions without a sentence. The tests compare them with such actions.
- `a_summary_at_an_opening`: the "Report" button gives the fixed report, and the provider of the server gets 0 calls.
- `t18_provider_failures` and `a_model_requests_count`: after a model without a valid action, the fixed rules read the request. Their answers have no voice.

The cases of `b_voice_of_a_report`, in their order. Job A is job 81 on robot #62 in run 111. Job B carries 16 to the destination of job A. "Fixed report" is the line of the job in the fixed report of increment A. The test compares its start: "Job 81, robot #62: carry 24 items from 'b_voice_source' to 'b_voice_destination', reserve 0. I delivered 0 items."

| Target | Sentence of the provider | Answer of Uno |
| --- | --- | --- |
| The source of job A | "Job 81 on robot #62 waits for coal: 0 of 24 delivered." | That sentence |
| The source of job A | "I delivered 987654 items." | The fixed report of job A |
| The source of job A, with the request "Did you deliver 987654 items?" | "No: 0 of 24, not 987654." | That sentence |
| The source of job A | "" and " " | The fixed report of job A |
| The source of job A | 300 characters | That sentence |
| The source of job A | 301 characters | The fixed report of job A |
| None | "Both jobs wait for coal. Their sources are empty." | That sentence |
| None | The same, with a third sentence: "Bring coal." | The fixed report on all jobs: two lines |
| The destination of both jobs | "Both robots wait for coal." | That sentence |
| The destination of both jobs | "I delivered 987654 items." | "2 of your open jobs use the station 'b_voice_destination'. Tell me the robot number of the job. I did nothing." |
| A station of no job | "No job of yours uses that station." | That sentence |
| A station of no job | "I delivered 987654 items." | "None of your open jobs uses the station 'b_voice_nothing'. Tell me the robot number of the job. I did nothing." |

After each case, the two jobs are `WAITING` with their revision 0, and the player has no proposal.

The cases of `b_voice_of_none`. Each answer is compared with a fixed text.

| Request | Sentence of the provider | Answer of Uno |
| --- | --- | --- |
| "Which stations do I have?" | "You have no station yet: register one with the station command." | That sentence |
| "x" | "You have 3 stations." | "I cannot answer that. I did nothing." |
| "Do I have 3 stations?" | "You do not have 3 stations: you have none." | That sentence |
| "x" | "", "One. Two. Three.", 301 characters | "I cannot answer that. I did nothing." |

The cases of `b_fixed_sentences_of_uncertain_numbers`. The record: a transport of 24 from 'depot' to 'furnace' with a reserve of 10, job 900 on robot #9, 6 delivered, 4 on board. 'depot' had 50 at 6000 ticks before "now", 'furnace' had 6 at 240 ticks before. The robot called at 300 ticks before. The test compares the whole list of sentences.

| Case | The fixed sentences |
| --- | --- |
| The record as it is: at the limit of an old observation and of a silent robot | None |
| 'depot' observed 6001 ticks before | "I last observed 'depot' 5 min ago: it had 50 then." |
| Also 'furnace' observed 7200 ticks before | The same, then "I last observed 'furnace' 6 min ago: it had 6 then." |
| The robot called 301 ticks before, and no call since the server start | "Robot #9 of job 900 has not contacted me for a while: I do not know if it is working. The last recorded data, not the current state: 4 on board, 50 in 'depot' (seen 5 min ago) and 6 in 'furnace' (seen 12 s ago)." |
| The robot is not running | "Robot #9 of job 900 is not running or not loaded." and the same data |
| `RECOVERY` with `LOAD_DIFFERS`, and the robot is not running | One sentence: "Job 900 on robot #9: I cannot say how it went. Recorded reason: the robot holds 0, the record says 4. The recorded data can be wrong: 6 delivered, 4 on board, 50 in 'depot' (seen 5 min ago) and 6 in 'furnace' (seen 12 s ago). You decide whether to resume or cancel." |
| After a recovery | "After a recovery of job 900 on robot #9 the totals can be inexact: the record says 6 delivered." |
| After a recovery, the robot is not running, and 'furnace' never observed | The sentence of the recovery, then the sentence of the robot with "4 on board and 50 in 'depot' (seen 5 min ago)" |
| 'furnace' never observed, the robot runs and calls | None: unknown is not old, and it has no number |

The answers of `b_voice_after_uncertain_numbers`, from the log of run 111. Job A is job 78 on robot #24. Job B is job 79 on robot #25. The voice is "Something here needs your eyes." The test reads an age in seconds as "N s ago": it changes at each tick.

1. A report about robot #24: "Job 78 on robot #24: I cannot say how it went. Recorded reason: the test changed the record. The recorded data can be wrong: 0 delivered, 0 on board and 0 in 'b_uncertain_source' (seen 3 s ago). You decide whether to resume or cancel. Something here needs your eyes."
2. A report about robot #25: "I last observed 'b_uncertain2_destination' 5 min ago: it had 8 then. Something here needs your eyes."
3. A report without a target, and a report about the destination of job B: no open job uses it. The sentence of job A, the sentence of job B, then the voice.
4. A report about robot #24 with "I delivered 987654 items.": the fixed report of job A. It has "I cannot say how it went.", and no fixed sentence before it.
5. After the resume and the shutdown, a report without a target: "After a recovery of job 78 on robot #24 the totals can be inexact: the record says 0 delivered. Robot #25 of job 79 is not running or not loaded. The last recorded data, not the current state: 0 on board, 56 in 'b_uncertain2_source' (seen 6 s ago) and 8 in 'b_uncertain2_destination' (seen 5 min ago). Something here needs your eyes."

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 108, red 1 | The new test `b_voice_of_a_report`, with the old code of Uno | 95 of 96, expected. `b_voice_of_a_report` failed at its first case: the fixed report of job A in place of the sentence |
| 109, green 1, red 2 | The sentence of a report and its checks. The new tests `b_voice_of_none` and `b_no_voice_for_an_action` | 97 of 98, expected. `b_voice_of_a_report` passed. `b_voice_of_none` failed at its first case: "I cannot propose a job: You have no station yet: …". `b_no_voice_for_an_action` passed: the code of increment A already ignored the sentence of these actions. Thus it had no red run. It guards the rule |
| 110, green 2, red 3 | The sentence of "none". `JobAccount.cautions` gives no sentence yet. The new tests `b_fixed_sentences_of_uncertain_numbers` and `b_voice_after_uncertain_numbers`, and the changed tests | 97 of 100, expected. `b_fixed_sentences_of_uncertain_numbers`: each case gave no sentence. `b_voice_after_uncertain_numbers`: the voice without the fixed sentence of `RECOVERY`. `t18_claude_provider`: the sentences of "none" and of the report passed, then "the system text has not" the seven new rules |
| 111, green 3 | The fixed sentences and the new system text | **100 of 100**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `100 GAME TESTS COMPLETE`, `All 100 required tests passed :)` |
| 112 | The commit `cb6161f`: the code of run 111, with one line of the comment of the helpers of the tests | **100 of 100**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `100 GAME TESTS COMPLETE`, `All 100 required tests passed :)` |

Before run 110, one start of the build stopped at the compiler: a parenthesis too many in a new test. No test ran. It has no number.

This ticket changed no part of the runtime and no Lua module. Thus the four restart phases did not run for it.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The check of the numbers reads the JSON of the context (`Context.toJson`) and the text of the request. The Claude provider sends the same JSON. A number that the facts do not give is not in the JSON | `Uno.passes`, `ClaudeProvider.answer` | That the model reads the JSON as the server means it |
| The facts come from the moment of the request. The fixed sentences come from the moment of the answer, as the fixed report. Between the two moments a case can begin or end | `Uno.ask`, `Uno.voiced` | No test builds a case that changes in that time |
| `parse` reads "reason" only for a report and for "none". `act` reads the sentence only of a report and of "none". A provider written in Java can give a sentence with a pause: it is not used | `Uno.parse`, `Uno.act` | |
| The fixed rules build each action without a sentence | `Uno.RuleProvider` | |
| `Uno.voice` gives the result of the checks: passed, failed, or no sentence needed. `Uno.noValidAction` gives the fourth result: no valid action. Nothing records the result yet: ticket #23 | `Uno.java` | |
| The end of a sentence is a full stop, a question mark or an exclamation mark before a space, with a closing quote or bracket between them. Thus "e.g. this" counts as two sentences, and "Done.Next" as one | `Uno.SENTENCE_END` | |
| A digit is a digit of each script (`\p{Nd}`). A number in words is not a run of digits: it passes (D32) | `Uno.DIGITS` | |

### Not covered

- A sentence of the real model. No `-Plive` run used the new system text. CLAUDE.md asks for one before the merge into `main`. Ticket #27 has the paid runs of increment B.
- The voice on the screen of a client. The play session of increment B.
- A silent robot through the conversation: the robot calls each 10 s at most, and the test cannot stop the calls without a stop of the computer. `b_fixed_sentences_of_uncertain_numbers` covers the case with a record that the test writes.

## Increment B — each request is in the server log (ticket #23)

Date: 2026-11-15. Uno records each written request to a provider with a model, and the server log gets one line for each such request. See D33, and the choices of the implementation at the end of D42.
The code is the branch of increment B at `bbe9478`, with tickets #16 to #21, and the changes of this ticket. No part of the runtime and no Lua module changed. The schema and the system text of the Claude provider did not change. No run called the real Claude service.

### Automatic in-game tests

The test is in the game and automatic. It uses providers written in the test, with the model flag (`CountingModel`): they call no model and cost nothing. Their model is `test-model`. The requester is an operator: only an operator uses a provider with a model (D33.1). The test needs no robot.

| Test | What it does | Result in run 114 |
| --- | --- | --- |
| `b_record_of_a_request` (new) | An operator sends five requests, one after the other. 1: "Which stations do I have?", and a "none" with a sentence without a digit, after 300 ms, with 120 input tokens and 30 output tokens. 2: "x", and a "none" with "You have 987654 stations.", with 10 and 2 tokens. 3: "x", and a pause of robot #999999 with the sentence "Done, as you asked.", with 10 and 2 tokens. 4: "x", and an answer that Uno cannot read, with 7 and 3 tokens. 5: "x", and the exception "model offline". Then the operator sends "x" to the fixed rules, and a player who is not an operator sends "x" to a provider with the model flag | Passed. See below |

The records of `b_record_of_a_request`. The test compares the model, the tokens, the action, the result of the checks and the unused sentence with the values of the table. The model of each record is `test-model`. The time of the answer is at least 300 ms for request 1, and less than 5 s for each request.

| Request | Answer of Uno | Action in the record | Tokens: in, out | Result of the checks | Unused sentence |
| --- | --- | --- | --- | --- | --- |
| 1 | The sentence of the provider | "none" with that sentence | 120, 30 | `PASSED` | None |
| 2 | "I cannot answer that. I did nothing." | "none" with that sentence | 10, 2 | `FAILED` | "You have 987654 stations." |
| 3 | "You have no job on robot #999999. Tell me the robot number of the job. I did nothing." | A pause of robot #999999, without a sentence | 10, 2 | `NOT_NEEDED` | None |
| 4 | "The model gave no valid answer (I cannot read its answer), thus I read your request with the fixed rules: they understand only simple sentences. I cannot propose a job: the quantity is missing." | An answer that Uno cannot read (`Unreadable`) | 7, 3 | `NO_VALID_ACTION` | None |
| 5 | The same, with "model offline" | None: the provider gave no answer | 0, 0 | `NO_VALID_ACTION` | None |

After the request to the fixed rules, the record of the operator is the record of request 5: the same object. The player who is not an operator has no record. The counter of the operator: 5 requests, 147 input tokens, 37 output tokens. Thus the record does not change the counter.

Tests that did not change and that cover parts of this ticket, in run 114:

- `a_model_requests_count`, `a_model_limit`, `a_new_session_restarts_the_counters` and `a_screen_model_lines`: the counter of the session and the lines of the screen, as before.
- `t18_provider_failures`: the answers after a model without a valid action, as before.

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 113, red | The new test. `Uno` has the record and its reader, but nothing writes the record | 100 of 101, expected. `b_record_of_a_request` failed at its first request: "the record of the request: null" |
| 114, green | The code of the ticket | **101 of 101**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `101 GAME TESTS COMPLETE`, `All 101 required tests passed :)` |
| 115 | The code of the commit: the code of run 114, with one comment of `Uno.Voice` changed | **101 of 101**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `101 GAME TESTS COMPLETE`, `All 101 required tests passed :)` |

This ticket changed no part of the runtime and no Lua module. Thus the four restart phases did not run for it.

### Code examination

The log line as text has a code examination only (spec #15). The record behind it has the test above.

| Fact | Where | What it does not prove |
| --- | --- | --- |
| Uno writes the record and the line on the server thread, before the answer of Uno. Thus the consumer of the answer reads the record of its request | `Uno.ask` | |
| The line is at the level INFO. It has the model, the input tokens, the output tokens, the time in milliseconds, the kind of the action, the result of the checks, and the text of the request of the player. After a failed check it also has the unused sentence. Each text is in JSON quotes: a line break in a text does not start a new line of the log | `Uno.line` | No test reads the server log |
| The line has no API key. The model is a value of the provider. The key is only in the client of the SDK (D21) | `Uno.line`, `ClaudeProvider.model` | |
| The line has no coordinate. It has no data of the context. The model gets no coordinate (D42, point 2): thus the unused sentence has none either | `Uno.line` | The request is the text of the player: a player can write a coordinate in it |
| Only the route of the model gives a record and a line. A provider without a model, a request after the limit of the session, a player who is not an operator, and a request that finds an open request get none | `Uno.ask`, `Uno.route` | |
| The time of the answer: from the send of the request to the answer of the provider, or to the time limit. The wait for the server thread does not count | `Uno.ask` | |
| The model of the record is the model that the provider names (`Uno.Provider.model`). The Claude provider names its model of each request | `ClaudeProvider.model` | With the server-side fallback of D21, a different model can answer a request that the first one declined. The record still names the first model. Not seen in a run |
| The record is in the session of Uno: one record for each actor, the newest. A new server session starts without a record (D24) | `Uno.Session`, `Uno.modelRequest` | No test starts a new session after a record |
| The counter and the screen did not change | `Uno.ask`, `UnoView` | |

Examples of lines from the log of run 114: an output of automatic tests. No test reads them. The first five lines are the requests of `b_record_of_a_request`. The last line is from `a_screen_model_lines`: the Claude provider with the local stand-in of the service.

```
Uno request: model test-model, 120 input tokens, 30 output tokens, 300 ms, action none, checks PASSED, request "Which stations do I have?"
Uno request: model test-model, 10 input tokens, 2 output tokens, 0 ms, action none, checks FAILED, unused sentence "You have 987654 stations.", request "x"
Uno request: model test-model, 10 input tokens, 2 output tokens, 0 ms, action pause, checks NOT_NEEDED, request "x"
Uno request: model test-model, 7 input tokens, 3 output tokens, 0 ms, action unreadable, checks NO_VALID_ACTION, request "x"
Uno request: model test-model, 0 input tokens, 0 output tokens, 0 ms, action no answer, checks NO_VALID_ACTION, request "x"
Uno request: model claude-opus-5-5, 1234 input tokens, 56 output tokens, 4 ms, action none, checks PASSED, request "x"
```

### Not covered

- A line with the tokens and the time of the real service. Ticket #27 has the paid runs of increment B.
- The log line as text: see the code examination above.
- The record after the time limit: the time of a late answer, and its tokens. A request that reaches the time limit can still cost tokens (D33.1). The record shows zero, as the counter.

## Increment B — the model is a setting (ticket #22)

Date: 2026-11-15. The model of the Claude provider is the value `model` of `config/mindcraft-common.toml`. The start value is `claude-haiku-5-5`. Only a model with a server-side fallback for a refusal gets the fallback parameter. See D42, point 6, D33 and D21.
The code is the integration branch of increment B at `c63d75c`, with tickets #16 to #21 and #24, and the changes of this ticket. No part of the runtime and no Lua module changed. The system text and the schema of the Claude provider did not change.
No run called the real Claude service. **No run with the real service used Haiku 5.5 yet.** It is still not known from a run whether the service refuses the `fallbacks` parameter for Haiku 5.5. The provider no longer sends it.

### Automatic in-game tests

Each test of this section is in the game and automatic. Each one uses the local stand-in of the service in the place of the API: no request reaches the real service.

| Test | What it does | Result in run 130 |
| --- | --- | --- |
| `b_model_of_the_setting` (new), in the batch `b_model_setting` | The test reads the start value of the setting. It builds three Claude providers, with the setting at `claude-sonnet-5-5`, at `claude-haiku-5-5` and at `claude-haiku-9-9`. It puts the value of before back after each one. An operator sends "x" four times through Uno. The stand-in answers: a "none" with a sentence for Sonnet, the same for Haiku, a refusal for Haiku, and an answer 404 `not_found_error` with "model: claude-haiku-9-9" for the third model | Passed. The start value is `claude-haiku-5-5`. The setting has its value of before after the three providers, and at the end. The names for the log line: "Claude (claude-sonnet-5-5)" and "Claude (claude-haiku-5-5)". The four requests name `claude-sonnet-5-5`, `claude-haiku-5-5`, `claude-haiku-5-5` and `claude-haiku-9-9`, each with the effort `low`. Only the request for Sonnet has `fallbacks: "default"` and the beta header `server-side-fallback-2026-07-01`. The answers: "You did not give the source." two times. Then "The model gave no valid answer (the model declined the request), thus I read your request with the fixed rules: they understand only simple sentences. I cannot propose a job: the quantity is missing." Then the same with "(the model service does not know the model 'claude-haiku-9-9')" |
| `t18_claude_provider` (changed) | As before. Its check of the model and of the fallback moved to `b_model_of_the_setting` | Passed. The request names the model of the setting of the test server. Each other check is as before |

The file of the setting. Type: in the game, automatic: the game test server of the normal run wrote it, and the agent read it after the run. It is the file of a test server, not of a real installation.

- Before runs 130 and 131 the agent deleted `run-gametest/config/mindcraft-common.toml`. At the start of each run Forge wrote it again. Its log line "Configuration file … is not correct. Correcting" is the message of Forge for a new file.
- After each run the file had three lines of comment and `model = "claude-haiku-5-5"`. `b_model_of_the_setting` changed the value six times during the run, and Forge saved the file at each change. Thus the file after the run also shows that the test put the value back.

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 129, red | The new test `b_model_of_the_setting`. The setting exists in the Forge file, but the provider still has the constant `claude-opus-5-5` | 103 of 104, expected. `b_model_of_the_setting` failed at its check of the names: "Claude (claude-opus-5-5), Claude (claude-opus-5-5)" |
| 130, green | The provider reads the setting. The fallback only for Opus 5.5 and Sonnet 5.5. The reason of an answer 404 | **104 of 104**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `104 GAME TESTS COMPLETE`, `All 104 required tests passed :)` |
| 131 | The commit `260efdc`: the code of run 130 | **104 of 104**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `104 GAME TESTS COMPLETE`, `All 104 required tests passed :)` |

The three branches of the code came in one step: the read of the setting, the fallback and the reason of a 404. Thus run 129 is the red run of all three. Its first failed check stopped the test before the requests.

This ticket changed no part of the runtime and no Lua module. Thus the four restart phases did not run for it.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| The server builds the Claude provider at its start, only with a key and never on a game test server. Thus the server reads the setting at its start. The log line "Uno interprets requests with …" uses the name of the provider: it names the model | `MindCraft.aboutToStart`, `ClaudeProvider.fromEnv` | The log line with a key: no run had a key |
| The key stays in the environment: the SDK reads it in `fromEnv`. The file of the setting holds no key | `ClaudeProvider.fromEnv`, `MindCraft.MODEL` | |
| Forge loads the file again after a change while the server runs. The provider keeps the model of the start | `ConfigFileTypeHandler.ConfigWatcher` of Forge 47.4.28, with `javap` | No test changes the file while a server runs |
| `t18_claude_live` gets its provider through `fromEnv`: it uses the model of the setting, and its log names it | `LiveTests.java` | It did not run |

### Not covered

- A request to the real service with the setting. No run with the real service used Haiku 5.5 yet. The paid runs of increment B are ticket #27.
- Whether the service refuses the `fallbacks` parameter for Haiku 5.5. The provider no longer sends it.
- The answer of the real service to a model that it does not know. The stand-in gives the shape of the guide of the API: 404, `not_found_error`, "model: <id>".
- The file on a real installation, a change of the setting by a person, and the log line with a key. The play session of B.

## Increment B — the low fuel notice (ticket #24)

Date: 2026-11-15. Uno warns one time when the fuel of a robot is sufficient for fewer than 5 trips between the docks of its job. The report, the status command, the facts and the screen give the fuel. See D42, points 11, 12 and 19, D41 decision 5, D34 and D30.
The code is the branch of increment B at `8df1f39`, with tickets #16, #17, #18, #19 and #21, and the changes of this ticket. No Lua module changed. The schema and the system text of the Claude provider did not change. The facts in the body of a request have two more fields. No run called the real Claude service.

### Automatic in-game tests

Each test of this section is in the game and automatic, with real turtles and real chests, unless the row says otherwise. A trip of the rig is 6: 2 times the 3 blocks between its docks. Thus 5 trips are 30.
The tests set the fuel level of the turtle with `ITurtleAccess.setFuelLevel`, as `t06_no_fuel`. Then the robot makes a contact through `mindcraft.status`: the function that the Lua modules call before each move.

| Test | What it does | Result in run 124 |
| --- | --- | --- |
| `b_low_fuel` (new) | A player and a transport of 24 from an empty source: the job waits at the source dock, and the robot does not move. The test sets the level and makes a contact: 30, 29, 29 again, then a save and a load and a check of the copy at 29, then 30, then 12. Then the need of fuel is a parameter of the check: 5 without a need. Then an opening of the screen by the owner and by a different player | Passed. At 30: no entry and no notice. At 29: one entry `LOW_FUEL` with the cause `SOURCE_AT_RESERVE`, the fuel 29, 4 trips, the state `WAITING`, not requested, the owner. One notice: "Job N on robot #R (minecraft:coal from 'b_low_fuel_source' to 'b_low_fuel_destination') has little fuel: 29, about 4 trips between its stations." At the next contact: no second entry, and the state and the cause did not change. The loaded copy has the same diary, and its check writes no entry. 30 arms the warning: no entry. At 12: a second entry and a second notice, "…: 12, about 2 trips between its stations." Without a need of fuel: no entry. The summary of the owner: "From the start of my memory:" and the two sentences. The different player gets no summary, no notice and no entry. The two notices went to the owner only |
| `b_low_fuel_at_the_start` (new) | A player and a transport of 24. The turtle starts with 20: 3 trips. The robot makes each contact itself. The test registers a place of the owner, and reads its general state when the entry comes | Passed. The diary: `RUNNING`, `LOW_FUEL`, then `DONE`. The notices: "…has little fuel: 20, about 3 trips between its stations." and "…is done: 24 delivered." The later contacts are below the threshold too: no second entry. When the entry comes, the job is `RUNNING` and the general state of the place is `WORKING`. At the end: source 40, destination 24, robot 0 |
| `b_fuel_in_the_report` (new) | As `b_low_fuel`: a job that waits at the source dock. At 30 and at 29: the fixed report, the status command and the view of the screen. At 29 a written request: a provider of the test keeps the context. Then a check without a need of fuel, and the same reads in the same tick | Passed. The report has "My fuel level is 30: about 5 trips between the stations." and then the same with 29 and 4. The status has the line "fuel: 30, about 5 trips between the stations", then 29 and 4. The view has no line about the fuel at 30. At 29 it has the text widget "Little fuel: 29, about 4 trips between the stations.". The facts of the context have the fuel 29 and the trips 4. The JSON has `"fuel":29,"fuelForTrips":4`. Without a need of fuel: no fuel in the report, "fuel: unknown" in the status, no line in the view, and no fuel in the facts |
| `b_facts_of_uncertain_numbers` (changed) | As before, without a robot. One more part: a record with the fuel 29 and 4 trips | Passed. The facts give the fuel and the trips for a robot that runs and that called 300 ticks before, also in `RECOVERY`. They do not give them for a robot that is not running, and for a robot that called 301 ticks before. The sentence of these two cases is now "… Not given: the load on board, the stocks and the fuel." |
| `b_facts_in_the_context` (changed) | As before | Passed. The facts of each of the three jobs also have the fuel level of the turtle at the moment of the check, and that level divided by 6. The robot does not move after the job is done, blocked or in `RECOVERY`. The fuel is in the facts in `RECOVERY` too |
| `t18_claude_provider` (changed) | As before | Passed. The facts in the body of the request also have `"fuel":L,"fuelForTrips":T` after `"aboveReserve":56`. L is the level of the turtle, and T is L divided by 6 |
| `t06_no_fuel` (changed) | As before: the turtle starts with 0 | Passed. The diary at the block: `RUNNING`, `LOW_FUEL`, `BLOCKED OUT_OF_FUEL`. The cause, the report and the resume after a refuel are as before |
| `a_notices_for_blocked_recovery_and_done` (changed) | As before: the turtle starts with 0 | Passed. The first notice is now "…has little fuel: 0, about 0 trips between its stations.", before "…is blocked: the robot has no fuel." Four notices about the jobs, each to the owner |
| `a_no_notice_for_a_console_job` (changed) | As before: the turtle starts with 0 | Passed. The diary: `RUNNING`, `LOW_FUEL`, `BLOCKED OUT_OF_FUEL`, `RUNNING`, `DONE`. No notice |
| `a_summary_at_an_opening` (changed) | As before: the turtle starts with 0, two times | Passed. Each of the two summaries has the low fuel sentence of its job before the sentence of the block |
| `a_notices_call_no_provider` (changed, run 126) | As before, with one more step: a contact at 29, between the long wait and the block | Passed. The notices: the long wait, "…has little fuel: 29, about 4 trips between its stations.", the block and the recovery. The provider of the server got no call |

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 120, red 1 | The new test `b_low_fuel`. The kind `LOW_FUEL` and the fuel of an entry exist. The check of the fuel is empty | 95 of 96, expected. `b_low_fuel` failed at 29: "[]", no notice |
| 121, green 1 | The check of the fuel, the read at each contact with an active job, and the sentence of the notice | 93 of 96. `b_low_fuel` passed. Three tests whose turtle starts without fuel failed: `a_notices_for_blocked_recovery_and_done`, `a_no_notice_for_a_console_job` and `a_summary_at_an_opening`. Their notices, their diary or their summary had the low fuel entry at the level 0. A level of 0 is below 5 trips: the expectations changed, not the code |
| 122, red 2 | The new tests `b_low_fuel_at_the_start` and `b_fuel_in_the_report`. The fuel in `b_facts_of_uncertain_numbers`, `b_facts_in_the_context` and `t18_claude_provider`. The changed expectations of the four tests whose turtle starts without fuel. The facts have the two fields, but the account gives none. The report, the status and the screen are as before | 94 of 98, expected. `b_facts_of_uncertain_numbers`, `b_fuel_in_the_report`, `b_facts_in_the_context` and `t18_claude_provider` failed: no fuel. `b_low_fuel_at_the_start` and the four changed tests passed |
| 123, green 2 | The fuel in the facts, the report, the status and the view | 96 of 98. `b_fuel_in_the_report` failed: a defect of the test. Its station names hold the word "fuel", and its check of a report without fuel looked for that word. Correction: the check looks for "My fuel level". `t22_t23_proposals_and_contact_points` failed: "the report after a save and a load is different". The record did not save the fuel of the job. Correction: the record saves the fuel and the trips of a job |
| 124, green 3 | The two corrections | **98 of 98**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `98 GAME TESTS COMPLETE`, `All 98 required tests passed :)` |
| 125 | The commit `97cd020`: the code of run 124. The check of the fuel now marks the record dirty at each contact. One comment changed | **98 of 98**. The same three log lines |
| 126 | The commit `dfa1d29`: one more step in `a_notices_call_no_provider`. The code of the mod is the code of run 125 | **98 of 98**. The same three log lines. No red run for this step: the code existed |

The four restart phases after run 125, one at a time, on the same world. Type: in the game, automatic. The code of the mod did not change after run 125: only the test of run 126.

| Phase | Result |
| --- | --- |
| `stop` | Passed. `All 1 required tests passed :)`. The status at the stop: `RUNNING`, taken 24, delivered 0, on board 24, "fuel: 999, about 166 trips between the stations" |
| `check` | Passed. `All 1 required tests passed :)`. The record after the restart has "fuel: 999, about 166 trips between the stations": the saved level. The diary after the restart: `RUNNING`. Then source 40, destination 24, robot 0, and the diary `RUNNING`, `DONE` |
| `crash` | Passed: the process halted, as the phase does. The status at the halt: `DONE`, delivered 24, on board 0, "fuel: 996, about 166 trips between the stations" |
| `check-crash` | Passed. `All 1 required tests passed :)`. The diary after the restart: `RUNNING`, `DONE`, `RECOVERY WORLD_OLDER`. The record after the restart has the fuel of the delivery: 996. At the end: `RUNNING`, `DONE`, `RECOVERY WORLD_OLDER`, `RUNNING`, `DONE` |

The restart test does not check the fuel. The fuel lines above are observations in the log of each phase.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| At each contact with an active job, `RobotApi.contact` gives the runtime `ITurtleAccess.getFuelLevel` and `ITurtleAccess.isFuelNeeded` of the public API of CC: Tweaked | `RobotApi.java` | That a turtle on a server without a need of fuel answers `false`. No test changes that setting of CC: Tweaked: its public API has no function for it. `b_low_fuel` and `b_fuel_in_the_report` give the need as a parameter of `MindRuntime.checkFuel` |
| No Lua module changed | `git diff` of the ticket | |
| The way of a notice of low fuel: `MindRuntime.checkFuel`, `warn`, `write`, `Notices.warn`, the exit. No step has a reference to a provider | `MindRuntime.java`, `Notices.java` | `a_notices_call_no_provider` counts the calls in one case |
| A trip is 2 times `BlockPos.distManhattan` of the two docks. Two docks at the same place give no fuel and no entry | `MindRuntime.checkFuel` | No test builds two docks at the same place |
| The state of the job and the general state of a projector do not read the fuel. `MindRuntime.mood` reads only the states of the jobs | `MindRuntime.java` | |

### Not covered

- A turtle on a server where turtles need no fuel. See the code examination.
- The notice in the chat of a client, the line of the screen in a client, and the report with the fuel in a client. The play session of increment B has a step for the notice.
- The fuel in a request to the real service. No `-Plive` run sent it.
- A robot that burns the fuel items on board at the level 0. `transport.lua` does it (code examination, D12).

## Increment B — the conversation part comes first on the screen (ticket #25)

Date: 2026-11-15. The view that the server builds puts the conversation part first: the sentence of Uno, the proposal, the text field and the row of buttons. The lines about the provider follow them. The list of the jobs comes last. See D42, point 13, D40 and D19.
The code is the branch of increment B at `c075de7`, with tickets #16 to #21, #23 and #24, and the change of this ticket. Only `UnoView.build` changed. The client `UnoScreen` did not change. No part of the runtime and no Lua module changed: thus the four restart phases did not run. The schema and the system text of the Claude provider did not change. No run called the real Claude service.

### Automatic in-game tests

Each test of this section is in the game and automatic. No test opens a client screen. Each test reads the view as data, and calls the functions that the network messages call. `b_conversation_first` has a real turtle and real chests.

| Test | What it does | Result in run 141 |
| --- | --- | --- |
| `b_conversation_first` (new) | A player and a transport of 24 from an empty source: the job waits, and its state does not change by itself. A provider of the test gives one line about the provider, only to build a view. The test is in the batch of `a_screen_model_lines`. It reads the position of the first widget with each text or kind. First a view with no job. Then the job, the button "Cancel" of its panel, and the view with the proposal. Then the button "Confirm #N" of that view | Passed. With no job: the sentence of Uno at 0, the text field at 1, the row with "Send" at 2, the line about the provider at 3, and "I have no record of a job of yours." at 4. With the job and the proposal: the sentence of Uno at 0, "Waiting for your confirmation: cancel job N" at 1, the text field at 2, the row with "Send" at 3, the line about the provider at 4, and the heading of the job at 5. "Confirm #N" cancelled the job |
| `s2_tablet_view`, `a_change_through_the_view`, `a_cancel_button_asks_a_confirmation`, `t16_actions_of_an_old_view`, `b_replacement_through_the_view` and `t22_confirm_only_the_shown_proposal` (unchanged) | As before: send, confirm, report, apply, pause, resume and cancel through the view. Each test finds a button by its action, not by its place | Passed without a change |
| `a_screen_model_lines`, `b_fuel_in_the_report` and `s2_projector` (unchanged) | As before: the lines about the provider, the line about low fuel and the line about the places of the base, each by its text | Passed without a change |

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 140, red | The new test `b_conversation_first`, with the old order of the view | 104 of 105, expected. `b_conversation_first` failed with no job: "I have no record of a job of yours." was the first widget, and the sentence of Uno the second |
| 141, green | The new order in `UnoView.build` | **105 of 105**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `105 GAME TESTS COMPLETE`, `All 105 required tests passed :)` |
| 142 | The commit `a2b66fd`: the code of run 141 | **105 of 105**. The same three log lines |

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| `UnoScreen` draws the widgets from the top down, in the order of the view. The whole column scrolls together. The class did not change | `UnoScreen.layout`, and the `git diff` of the ticket | That a small window shows the conversation part without a scroll |
| The widgets of the view, the identifiers of the fields and the actions of the buttons did not change. Only their order changed | `UnoView.build`, and the `git diff` of the ticket | |
| The counter of an operator and the line of the route are written in one place, one after the other: `UnoView.providerLines`. The new test has only the line of the route | `UnoView.java` | No test reads the position of the counter |
| The line about the places of the base with a projector stays after the jobs, as before | `UnoView.build` | No test reads its position |

### Not covered

- The look of the screen in a client. No automatic test opens a client screen. The play session of increment B has the step "Open the screen in a small window".
- The height in a window of about 850 × 510 pixels (D40). A long sentence of Uno can make the conversation part as tall as the window (D42, point 13).

## Increment B — the harness of the measurement (ticket #26)

Date: 2026-11-15. The harness of the measurement of the models: one fixed set of requests, the world that they need, and a record for this file. See D42, points 7, 8 and 20, D33 and D32.
The code is the integration branch of increment B at `002c636`, with tickets #16 to #24, and the changes of this ticket. No part of the runtime and no Lua module changed. The schema and the system text of the Claude provider did not change.
**No paid run occurred.** No run of this ticket called the real Claude service. The measurement itself is ticket #27.

### How to run the measurement

- Each run calls the real Claude service with the key of the project owner. It costs money. **Each run needs the consent of the project owner** (CLAUDE.md).
- The budget: 5 dollars for all paid runs of increment B: three passes of the measurement at most, and the play session (D42, point 8).
- **Estimate, not a measurement:** one pass of about 20 requests on the three models costs about 0.45 dollars: Opus 0.29, Sonnet 0.15, Haiku 0.01 (D42, point 8).
- The key is `ANTHROPIC_API_KEY` in the environment. It is not in a file or in the command.
- One run for each model. The command:

```bash
./gradlew runGameTestServer -Pmeasure -Pmodel=claude-haiku-5-5
./gradlew runGameTestServer -Pmeasure -Pmodel=claude-sonnet-5-5
./gradlew runGameTestServer -Pmeasure -Pmodel=claude-opus-5-5
```

- `-Pmeasure` selects the namespace `mindcraft_measure`: the run holds only the test `b_measurement`. The log must say `Enabled Gametest Namespaces: [mindcraft_measure]`.
- `-Pmodel` names the model of the run. Without it, the run uses the setting `model` of `run-gametest/config/mindcraft-common.toml`. The log line `b_measurement uses Claude (…)` names the model. `-Pmodel` also works with `-Plive`.
- The output: the file `run-gametest/measurement-<model>.md`, for example `run-gametest/measurement-claude-haiku-5-5.md`. The server log has the same text after the line "The measurement, the record for TEST_RESULTS.md:". Git ignores the folder.
- The server log also has one line for each request at its answer: "The measurement, request N of 19: …", the row of the table.
- The file is a section with a heading of level 4. It goes under "The record of the measurement (ticket #27)" below, without a change by hand. Then the agent marks each sentence that can contradict the record, and the project owner decides (D42, point 20).
- The test `b_measurement` does not judge the model. It fails only when the harness cannot build the world or write the record. A run must still show `All 1 required tests passed :)`.
- A run sends 19 requests, one after the other. The time limit of the test is 12000 ticks: 10 minutes.

### The set

The 19 requests of `Measurement.SET`, in their order. Type: the code of the set, read by the agent. The automatic test below runs each one.
The world has seven robots with real turtles and chests, and a third station. The robot numbers come at the run. No request names a robot number.

| No. | Case | Request | Expected action | The state that it needs |
| --- | --- | --- | --- | --- |
| 1 | The seven actions: report | How are my jobs going? | A report about each job: no job named | Each job of the world |
| 2 | T21: a wait at the reserve | Why does the furnace get no more coal? | A report about the job of the depot | depot to furnace: keep at least 64, reserve 32. The depot has 32: `WAITING`, `SOURCE_AT_RESERVE` |
| 3 | T21: a block without fuel | What is wrong with the robot of the mine? | A report about the job of the mine | mine to smithy: a transport of 24 with a robot without fuel: `BLOCKED`, `OUT_OF_FUEL` |
| 4 | T21: a job in `RECOVERY` | How did the job of the warehouse go? | A report about the job of the warehouse | warehouse to kitchen: a transport of 24. The harness empties the robot after its pickup: `RECOVERY`, `LOAD_DIFFERS` |
| 5 | T21: a robot that is not running | Is the robot of the cellar still working? | A report about the job of the cellar | cellar to bakery: a transport of 32 from an empty cellar, `WAITING`. Then the computer of the robot shuts down |
| 6 | T21: an old observation | How much coal is in the tower now? | A report about the job of the harbor | harbor to tower: a transport of 40. The robot delivers 16 and waits. The owner pauses the job. The last observation of the tower is 6001 ticks old: the harness writes that time in the record |
| 7 | D32: `BLOCKED` with a load on board | Is the job of the workshop done? | A report about the job of the storage | storage to workshop: a transport of 64. The storage has 24 and the workshop has no chest: `BLOCKED`, `DESTINATION_NO_INVENTORY`, 24 on board |
| 8 | A question about the diary | What happened while I was away? | A report about each job: no job named | The diary of the seven jobs |
| 9 | A question about the fuel | How much fuel does the robot of the depot have left? | A report about the job of the depot | Row 2. The robot starts with a fuel level of 1000 |
| 10 | The seven actions: start | Carry 16 coal from the quarry to the kiln. | A start: carry 16 x `minecraft:coal` from 'quarry' to 'kiln', reserve 0, with the robot of the quarry | quarry to kiln: a transport of 16, `DONE`. Its robot is the only free robot |
| 11 | The seven actions: change | Leave only 16 coal in the depot. | A change of the job of the depot: reserve 16 | Row 2 |
| 12 | The seven actions: resume | Resume the job of the harbor. | A resume of the job of the harbor | Row 6: `PAUSED` |
| 13 | The seven actions: cancel | Cancel the job of the mine. | A cancellation of the job of the mine | Row 3 |
| 14 | A change of the destination | Bring the coal of the depot to the smelter instead of the furnace. | A start: keep at least 64 x `minecraft:coal` in 'smelter', taken from 'depot', reserve 32, with the robot of the depot. It is a replacement (D42, point 10) | Row 2, and the station 'smelter' |
| 15 | The seven actions: none | What will the weather be like tomorrow? | "none" | None |
| 16 | None: a value is missing | Carry some coal to the smithy. | "none": the request gives no quantity and no source | None |
| 17 | None: a question from the context | Which of my robots are free? | "none": the voice answers from the context (D42, point 15) | The robots of the world |
| 18 | A request in Italian | Com'è andato il lavoro della quarry? | A report about the job of the quarry | Row 10 |
| 19 | The seven actions: pause | Pause the job of the depot for a while. | A pause of the job of the depot | Row 2. The last request: a pause acts at once, and it is the only request that changes the world |

Rows 16 and 17 are added in the implementation: the spec does not name them. See D42, point 7.

When the action of the model is the expected action ("right" in the record):

- A start: the same order. A "none": a "none".
- A change, a pause, a resume and a cancellation: the search of Uno (D29.2) finds the expected job for the target. A change also gives the same new values.
- A report: no job named, or the robot or a station of the expected job. Its target selects only the fixed sentences before the voice (D42, point 16). A report about each job is right only with no job named.

### Automatic in-game tests

The test is in the game and automatic. It uses the Claude provider against the local stand-in of the service: no request reaches the real service.

| Test | What it does | Result in run 151 |
| --- | --- | --- |
| `b_measurement_with_the_stand_in` (new), in the batch `b_measurement` | The harness of the measurement builds its world and sends the 19 requests. The stand-in reads the request in the body of each call. It gives the expected action, with the sentence "I read the facts of this request." for a report and a "none". Five answers differ. Row 7: a report with "All done: I delivered 64.". Row 9: a report with "I have 987654 fuel left.". Row 14: a change of the reserve to 0, not a start. Row 15: text that is not JSON. Row 17: the answer 500 of the service. Each answer with the status 200 has 2000 input tokens and 100 output tokens | Passed. See below |

What `b_measurement_with_the_stand_in` checked in runs 151 to 153:

- The set: 19 results, 19 calls of the stand-in, and the seven actions among the expected actions. Each call names the model of the setting.
- The actions: 16 right. Rows 14, 15 and 17 are not right.
- The checks: 10 `PASSED`, 1 `FAILED` (row 9, with its unused sentence), 2 `NO_VALID_ACTION` (rows 15 and 17), and 6 `NOT_NEEDED` (the start, the change, the resume, the cancellation, the wrong change of row 14 and the pause). Row 17 has no action and zero tokens.
- The answers of Uno. Row 7: "All done: I delivered 64.": **the check passes, because 64 is the quantity of the job.** This is the limit of D32: the record shows the sentence next to the facts, with `BLOCKED` and 24 on board. Row 9: the fixed report of the job, without 987654. Row 14: the proposal of a change. Row 15: "I did not get a valid configuration. I created no proposal.". Row 17: "I cannot interpret the request now (the model service answered with error 500). The jobs in progress continue.". Row 10: the proposal of the start.
- The world, in the facts that the provider got. Each context has 15 stations, 7 robots and 7 jobs: only the world of the measurement. Row 2: `WAITING`, `SOURCE_AT_RESERVE`, 0 above the reserve, 54 still to carry. Row 3: `BLOCKED`, `OUT_OF_FUEL`, fuel 0. Row 4: `RECOVERY`, `LOAD_DIFFERS`, no delivered total and no load on board. Row 5: no load on board, and one uncertain sentence: "The robot is not running or not loaded. …". Row 6: `PAUSED`, 16 delivered, no stock of the tower, and "The last observation of 'tower' is old. Not given: its stock.". Row 7: `BLOCKED`, `DESTINATION_NO_INVENTORY`, 24 on board of 64, 0 delivered. Row 9: a fuel level and its trips. Row 18: `DONE`, 16 delivered. Row 10: the robot of the quarry is the only free robot. Row 8: lines of the diary of the seven jobs.
- After the set: the job of the depot is `PAUSED`, and the player has no proposal.
- The record: the heading, the table with 19 rows, the totals, and the blocks of rows 7, 8 and 15. The file `run-gametest/measurement-stand-in.md` holds the same text as the record.

The record of run 152, a part of it. Type: an output of the automatic test above. The robot numbers and the times are those of that run.

````text
#### The harness with the stand-in of the service

Type: in the game, automatic, with the Claude provider against the local stand-in of the service. No request reached the real service. Date: 2026-11-15T14:47:36Z. Model: `claude-haiku-5-5`. 19 requests, one after the other, in one world.

| No. | Case | Request | Expected action | Action of the model | Right | Checks | Time, ms | Input tokens | Output tokens |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 7 | D32: BLOCKED with a load on board | Is the job of the workshop done? | report, robot #93 | report, robot #93 | yes | PASSED | 2 | 2000 | 100 |
| 9 | A question about the fuel | How much fuel does the robot of the depot have left? | report, robot #89 | report, robot #89 | yes | FAILED | 2 | 2000 | 100 |
| 14 | A change of the destination | Bring the coal of the depot to the smelter instead of the furnace. | start: keep at least 64 x minecraft:coal in 'smelter', taken from 'depot'. Reserve to leave in 'depot': 32. Robot #89 | change, robot #89, reserve 0 | no | NOT_NEEDED | 1 | 2000 | 100 |
| 17 | None: a question from the context | Which of my robots are free? | none | no answer | no | NO_VALID_ACTION | 2 | 0 | 0 |

Totals: right actions: 16 of 19. Failed checks: 1. Passed checks: 10. Requests without a valid action: 2. Time: 36 ms. Tokens: 36000 in, 1800 out.

**7. D32: BLOCKED with a load on board.** Is the job of the workshop done?

```text
The sentence of the model: All done: I delivered 64.
The answer of Uno: All done: I delivered 64.
The facts of job 112 on robot #93: {"job":112,"robot":93,"module":"transport","item":"minecraft:coal","from":"storage","to":"workshop","quantity":64,"reserve":0,"state":"BLOCKED","cause":"DESTINATION_NO_INVENTORY","delivered":0,"onBoard":24,"sourceStock":{"count":0,"age":"4 s ago"},"stillToCarry":64,"aboveReserve":0,"fuel":996,"fuelForTrips":166,"uncertain":["No robot observed 'workshop' yet. Its stock is unknown."]}
```
````

The whole record of run 152 is about 28 KB. A request about each job gives the facts of the seven jobs and the lines of the diary.

### The tests with the real service

These tests call the real Claude service. **None of them ran for this ticket.** Each run costs money and needs the consent of the project owner.

| Test | What it does | Result |
| --- | --- | --- |
| `b_measurement` (new, `LiveTests`, namespace `mindcraft_measure`) | The same harness, with the real Claude provider and the model of the run. It writes `run-gametest/measurement-<model>.md` | Not run. It compiles |
| `t18_claude_live` (changed, `LiveTests`, namespace `mindcraft_live`) | As before. Its provider now has the model of the run: `-Pmodel`, or the setting | Not run. It compiles |

### The check of the build script

Type: a static check. Gradle evaluated `build.gradle` with an init script of the agent. The init script was in a scratch folder, not in the repository. It printed two properties of the run configuration `gameTestServer`. No game started.

| Flags | `forge.enabledGameTestNamespaces` | `mindcraft.model` |
| --- | --- | --- |
| None | `mindcraft` | None |
| `-Plive` | `mindcraft_live` | None |
| `-Pmeasure` | `mindcraft_measure` | None |
| `-Pmeasure -Pmodel=claude-sonnet-5-5` | `mindcraft_measure` | `claude-sonnet-5-5` |
| `-Plive -Pmodel=claude-opus-5-5` | `mindcraft_live` | `claude-opus-5-5` |
| `-Pmodel=claude-opus-5-5` | `mindcraft` | None |

The first version of the script read the value with `findProperty('model')`. The evaluation failed for each flag: "Could not find method property() for arguments [mindcraft.model, root project …]". The Gradle project has a property `model` of its own. The script now reads `-Pmodel` from the command line (`gradle.startParameter.projectProperties`).

### Runs

| Run | Code | Result |
| --- | --- | --- |
| 150, red | The new test. The harness has its interface only: it builds no world and sends no request | 105 of 106, expected. `b_measurement_with_the_stand_in` failed: "0 results, 0 calls of the stand-in" |
| 151, green | The harness of the measurement, the two tests and the flags of the build script | **106 of 106**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `106 GAME TESTS COMPLETE`, `All 106 required tests passed :)` |
| 152 | The code of run 151, with the build script that reads `-Pmodel` from the command line, and one more note in the record: "Uno cannot read the answer of the provider" | **106 of 106**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `106 GAME TESTS COMPLETE`, `All 106 required tests passed :)` |
| 153 | The code of the commit: the code of run 152, with one comment of the test changed | **106 of 106**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `106 GAME TESTS COMPLETE`, `All 106 required tests passed :)` |

This ticket changed no part of the runtime and no Lua module. Thus the four restart phases did not run for it.

### Code examination

| Fact | Where | What it does not prove |
| --- | --- | --- |
| Forge selects a test by the namespace of `@GameTest(templateNamespace)` before the namespace of the class. Thus one class has the two namespaces `mindcraft_live` and `mindcraft_measure` | `ForgeGameTestHooks.getTemplateNamespace` in the sources of Forge 47.4.28, and `GameTestRegistry.register` with `javap` | No run with `-Pmeasure` started a game |
| The player of the measurement is not an operator. The provider of each request says that it calls no model: Uno sends the request to it on the route without a model | `Measurement.PLAYER`, `Measurement.Measured`, `Uno.route` | |
| The record of a request has the values that Uno records on the route of the model: the result of the checks is `Uno.voice`, and a failure or an answer that Uno cannot read is `NO_VALID_ACTION` | `Measurement.Measured`, `Uno.ask` | The time limit: see "Not covered" |
| A robot without an active job calls the server at least every 10 seconds. Thus no robot of the world is silent (D42, point 3) | `mindcraft.lua`, `RobotApi.contact` | |
| `b_measurement` checks the key in the environment, builds the provider with the model of the run, and gives it to the harness. It never prints the key | `LiveTests.b_measurement` | It did not run |

### Not covered

- The measurement itself: no paid run occurred. Ticket #27 has the paid runs.
- The sentences of a real model in the record.
- A run with `-Pmeasure` in a started game. Only the evaluation of the build script above. The first paid run shows the line `Enabled Gametest Namespaces: [mindcraft_measure]`.
- A request that reaches the time limit of Uno in the harness. Then the record has zero tokens and the note "the request reached the time limit". No test reaches it.
- The ages grow during a run. After 5 minutes an observation is old and its stock is not given, and a wait writes a "long wait" line in the diary (D30). A slow model can thus get other facts than a fast one. The record gives the facts of each request. The stand-in run is shorter than one second.
- A model that gives a pause for a different request pauses that job. The next requests see the paused job. The record shows it.
- The route of an operator in the measurement: the counter, the limit of the session, the fixed rules after a failure and the log line of Uno. See D42, "As built" under point 7.

### The record of the measurement (ticket #27)

Not done. **No paid run occurred.** Ticket #27 puts here the section of each run, as the harness wrote it. Then come the marks of the agent and the decision of the project owner (D42, point 20).

## Increment B — final runs and evidence (ticket #28)

Ticket #28 of spec #15, on 2026-11-15. It changed only documents: the checklist [PLAY_SESSION_B.md](PLAY_SESSION_B.md), the README, the decision log and this file.
The code of its runs is the integration branch of increment B at `8b0d3e2`, with tickets #16 to #26. No part of the runtime, no Lua module and no test changed after it. No run called the real Claude service.

| Run | Type | Result |
| --- | --- | --- |
| 155, the normal run | In the game, automatic | 106 of 107. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `107 GAME TESTS COMPLETE`, `1 required tests failed :(`. `b_replacement_refused` failed: "the computer is not off yet". See below |
| 156, the normal run | In the game, automatic | **107 of 107**. Log lines: `Enabled Gametest Namespaces: [mindcraft]`, `107 GAME TESTS COMPLETE`, `All 107 required tests passed :)`. Uno used the fixed rules: "Uno interprets requests with the fixed rules, without a language model." |
| `stop` | In the game, automatic | Passed. `All 1 required tests passed :)`. The status at the stop, at the game time 82: `RUNNING`, taken 24, delivered 0, on board 24, "fuel: 999, about 166 trips between the stations" |
| `check` | In the game, automatic | Passed. `All 1 required tests passed :)`. After the restart, at the game time 84: the diary `RUNNING`, and the fuel 999 of the record. At the end: `DONE`, source 40, destination 24, robot 0, and the diary `RUNNING, DONE`. The summary: "Since you last opened this screen:" and "0 s ago: Job 1 on robot #0 (…) is done: 24 delivered." |
| `crash` | In the game, automatic | The test halted the process at the game time 422, as expected. The record said `DONE`, delivered 24, on board 0 |
| `check-crash` | In the game, automatic | Passed. `All 1 required tests passed :)`. After the restart, at the game time 85: `RECOVERY` ("the world is older than the record of this job"), the diary `RUNNING, DONE, RECOVERY WORLD_OLDER`, source 40, destination 0, robot 24. At the end: `DONE`, "1 recovery. The totals can be inexact.", and the diary `RUNNING, DONE, RECOVERY WORLD_OLDER, RUNNING, DONE`. The summary has the `RECOVERY` with its recorded reason, then the delivery |
| 157, the normal run | In the game, automatic | **107 of 107**. The same three log lines as run 156 |

The four phases ran after run 156, one at a time, on the same world. After them the data folder has `mindcraft.dat` and no temporary file.

The failure of run 155:

- `b_replacement_refused`, part 3: the test sends `computercraft shutdown` one time for the robot. Then it waits until the computer of the robot is off. The computer was still on at the time limit of the test: 2400 ticks.
- Runs 156 and 157, on the same code, passed. The cause is not known. Ticket #28 changes no code: the failure is reported, not corrected.
- Two more tests wait for a shutdown in the same way. They passed in the three runs.

The evidence of increment B, by ticket. "Automatic" is a test in the game, automatic, with the fixed rules, providers written in the tests or the local stand-in of the service. Each section above gives the tests.

| Ticket | What it verified | Kind of evidence | Runs | Not verified |
| --- | --- | --- | --- | --- |
| #16, the kinds of a diary entry and the causes of `RECOVERY` | The kinds "change" and "long wait". A record of increment A loads with the same diary and summary. `LOAD_DIFFERS` and `WORLD_OLDER` | Automatic, and the four restart phases | 89 to 91 | A real world of increment A. A long wait across a real restart |
| #17, the adoption | A new job counts the load on board, by the command and by words. A load above the quantity. A load of a different item. The sentences of the proposal | Automatic, and the four restart phases | 85 to 88 | An adoption across a restart. The burn of an item of a different kind. The proposal in a client |
| #18, the replacement | One proposal, and one confirmation in one tick, in each open state. The refusals. A change of the destination by words. The robot of a request that names none | Automatic, and the four restart phases | 93 to 95 | The new system text with the real service. The limit of D26. The screen of a client |
| #19, the facts | The facts and the diary lines in the context of a provider and in the body of a request. No position. A different player gets nothing. The numbers that are not given | Automatic, and the four restart phases | 98 to 102 | A request with the facts to the real service |
| #21, the fixed rules after a model without a valid action | The sentence first, then the answer of the fixed rules. The request counts | Automatic | 104 to 106 | A failure of the real service |
| #20, the voice and its checks | The voice of a report and of "none". The check of the numbers and of the length. The fixed sentences before the voice. No voice for an action. The system text in the body of a request | Automatic | 108 to 112 | A sentence of the real model |
| #23, the record of a request | The record for each result of the checks | Automatic. The log line: a code examination | 113 to 115 | A line with the real service |
| #24, the low fuel notice | The threshold. One notice, also after a save and a load. A second notice after a refuel. The notice at the start of a job. The fuel in the report, the status, the facts and the screen | Automatic, and the four restart phases | 120 to 126 | A server where turtles need no fuel. The notice in a client |
| #22, the setting of the model | The start value. The model of the setting in each request. The fallback only for Opus 5.5 and Sonnet 5.5. The reason of an answer 404 | Automatic | 129 to 131 | The real service with Haiku 5.5. The file on a real installation |
| #25, the conversation part first | The order of the view | Automatic | 140 to 142 | The look and the height in a client |
| #26, the harness of the measurement | The set of 19 requests, its world and its record | Automatic. The build script: a static check | 150 to 153 | The measurement itself |
| #28, the final runs | Each test of the increment on the final code | Automatic, and the four restart phases. `gradlew check`: a static check | 155 to 157 | |

The evidence of increment B, by kind:

| Kind | What it covers |
| --- | --- |
| **In the game, automatic** | The 107 tests of runs 156 and 157. 24 tests are new in increment B: the tests with the prefix `b_`. Increment B also changed older tests: each section above lists them. The four restart phases after run 156 |
| **In the game, automatic, with the real service** | None. No paid run occurred in increment B |
| **Code examination** | The log line of a request. No Lua module changed. The way of a notice calls no provider. A trip from the docks. The read of the fuel at each contact. The order of the widgets in `UnoScreen`. The rows "Code examination" of each section above |
| **Static check** | `gradlew check` before each commit of ticket #28. The evaluation of the build script of ticket #26 |
| **In the game, manual** | None yet. The play session of ticket #29 uses [PLAY_SESSION_B.md](PLAY_SESSION_B.md) |
| **Not executed** | The rows of increment B in "Not executed" below |

What stays not verified for increment B:

- The real model and its sentences. No sentence of the real model passed or failed a check. The facts and the fuel fields did not go to the real service.
- The system text of `ClaudeProvider` with the real service: the `-Plive` run that CLAUDE.md asks for before the merge into `main`. Ticket #27 has it.
- The measurement and the choice of the model (ticket #27).
- The screen of a client: the voice, the fixed sentences before it, the proposals of an adoption and of a replacement, the line about low fuel, the order and the height of the screen, and the confirmation of "Cancel". The notices in the chat of a client.
- The configuration file on a real installation, and the log lines with a key.
- The play session (ticket #29).

## Guardrail — the rules of CLAUDE.md and the CI

Date: 2026-11-14. See D39.

| Check | Type | Result |
| --- | --- | --- |
| `gradlew checkRules` on the files of the repository | Static check | Passed |
| `gradlew checkRules` with probe files made for the check, then deleted: a tracked file in `run-gametest`, a text in the form of an API key, an email address, an import of `dan200.computercraft.shared`, an assignment of `Uno.provider` in a test class, and a local path in three forms (a drive with a backslash, a drive with a slash, a home folder) | Static check | Each rule failed on its probe, as expected |
| The workflow `.github/workflows/check.yml`, first run, on the commit `1e83bf4` | Static check, then in the game, automatic, on a GitHub runner (Ubuntu, Temurin 17) | Passed in about 7 minutes. `check` passed. The normal run: "All 81 required tests passed". The four restart phases passed |

## Manual test in the game

Date: 2026-11-12. Executor: the project owner, with the development client (`gradlew runClient`) and the procedure of the README.
These tests are about S2. The play session of increment A is in the section above.
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
| A stair (a path that also changes y), or more than one corner | The rigs have none. The move step should permit them when each move brings the robot nearer to the target (code examination). The path with one corner is tested: `a_path_with_one_corner` |
| Two robots in one corridor that is one block wide | Outside the rig. From the code, each robot is an obstacle for the other |
| T08: delayed confirmation | Not applicable: the runtime and the turtle are in the same process, without network messages |
| Inventory not loaded (`WAITING` with unknown stock) in a real area that is not loaded | One test reaches the branch with an artificial station record (`t21_report_inventory_not_loaded`). No test unloads a chunk |
| A change of world in a client: two servers, one after the other, in one Java process | It needs a person at the game. The logic is tested with an artificial session change (`t23_state_of_an_earlier_session`). See D24 |
| A request to Uno that is open when a real server stops | It needs a server stop during a test. The branch comes from a code examination. See D24 |
| A source that has the items but does not release them (`SOURCE_KEEPS`) | It needs an inventory that refuses the extraction. The chests of the rig do not |
| A robot session that CC: Tweaked does not stop in time at a real server stop, then a second world | It needs a person at the game and a stop that exceeds the limit of one second. The logic is tested with an artificial server start (`t23_robots_of_an_earlier_session`). See D24 |
| Double chests, inventories with sides (furnaces), hoppers, items with NBT | Outside the S1 rig |
| More turtles on different jobs with shared resources | Outside S1 |
| New world with an old `mindcraft.dat` file | Only the variant in memory (T09) |
| Cost on the server thread | No measurement |
| S2: the screen of the tablet in the client (drawing, mouse, keyboard, network messages) | It needs a person at the game. See D19 |
| S2: each single point of the projector in the client (the three colours, the range without a tablet, the dark) | The project owner confirmed the projector in general, not point by point. See D20 |
| S2: Claude with requests different from the four of the test `t18_claude_live` | Other languages, ambiguous station names, more than one free robot, a simple transport. Each request costs money to the key owner |
| S2: the fallback on refusals (`fallbacks`) with the real service | No request of the real test was declined. The branch is tested only with the false API |
| Increment A: a stop of the process during a write of the record | No test can stop the process at that moment. The protection rests on a code examination (D37) |
| Increment A: a notice to an owner who is not connected, in a client | In a single-player game the server stops when the player leaves. The exit is a code examination (`Notices.CHAT`). The tests replace the exit |
| Increment A: a notice to an owner who is connected in a different dimension | No test and no step of the checklist builds it |
| Increment A: a long wait across a real restart | The restart phases build no wait. `a_long_wait` saves and loads the start of the wait on a copy of the record |
| Increment A: the long waits with the causes `SOURCE_KEEPS`, `DESTINATION_FULL`, `SOURCE_UNLOADED`, `DESTINATION_UNLOADED`, `SOURCE_AT_RESERVE` with a reserve, and `NONE` | No test builds them. Their templates come from a code examination |
| Increment A: a real crash with a job of a player | The restart test has a job of the console. Thus the `RECOVERY` at the load gives no notice to a player, and only the summary of an operator shows it |
| Increment A: a summary with more than 10 events on the screen of a client | `a_summary_shows_the_10_newest` builds it without a client. The checklist of the play session does not |
| Increment A: a player who is not an operator, in a real client | The tests build such actors (`a_model_only_for_operators`, `a_screen_model_lines`). The play session needs commands, thus its player is an operator |
| Increment A: a confirmation and the buttons of a job with a provider that counts its calls | The tests count the calls only for the notices, the summary and the "Report" button. The rest is a code examination |
| The production jar `mindcraft-<version>-all.jar` loaded on an installed Forge | Only built and examined: 25 nested libraries. In the development game the libraries arrive in a different way |
| S2: the `/uno` commands in the game | Tested only through the functions that the commands call |
| Increment B: the `-Plive` run that CLAUDE.md asks for before the merge into `main`, after the change of the system text of `ClaudeProvider` (tickets #18 and #20). Thus no sentence of the real model passed or failed a check | A `-Plive` run costs money and needs the consent of the project owner. Ticket #27 has it, with the paid runs of the measurement. The automatic tests use providers written in the tests. See "Increment B — the voice of Uno and its checks (ticket #20)" |
| Increment B: a failure of the real Claude service after ticket #21: an error, a refusal, the time limit, or an answer that Uno cannot read | No run with the real service tested it. Such a run costs money and needs the consent of the project owner. The tests use providers written in the tests. See "Increment B — the fixed rules read a request when the model gives no valid action (ticket #21)" |
| Increment B: the measurement of the models with the real service (ticket #26 built its harness) | Each run costs money and needs the consent of the project owner. Ticket #27 has the paid runs. See "Increment B — the harness of the measurement (ticket #26)" |
| Increment B: a call of an old Lua run for the new job after a replacement (the limit of D26) | It needs a confirmation in the tick between a reading and a transfer of the module. No test builds that moment. See "Increment B — a job replaces a job (ticket #18)" |
| Increment B: the screen with the conversation part first, in a client: the look, and the height in a small window | It needs a person at the game. The play session of increment B has a step for it. See "Increment B — the conversation part comes first on the screen (ticket #25)" |
| Increment B: the other parts that the player sees in a client: the voice, the fixed sentences before it, the proposals of an adoption and of a replacement, the low fuel notice in the chat, the fuel line of a panel, and the confirmation of "Cancel" | It needs a person at the game. The automatic tests read the answers of Uno, the view that the server builds and the exit of the notices. [PLAY_SESSION_B.md](PLAY_SESSION_B.md) has a step for each |
| Increment B: the configuration file on a real installation, and the log lines of the provider and of a request with a key | The game test server never uses Claude. Steps S2, S3 and C7 of [PLAY_SESSION_B.md](PLAY_SESSION_B.md) read them in the development client. The installed jar is deferred (D27) |
| Increment B: the play session (ticket #29) | It needs the project owner at the game. It comes after the measurement (ticket #27). The checklist is [PLAY_SESSION_B.md](PLAY_SESSION_B.md) |
| T17 and the rest of T15 and T16 | They belong to S3. T15 has one minimum test: `t15_next_job_with_another_module` |

## Known limits

- The guarantees apply to the actions that go through `mindcraft.take` and `mindcraft.give`. A different Lua program can use `turtle.suck` and `turtle.drop`.
- An external actor that changes a chest between two visits is not detected. The state shows the last observation and its age.
- After `RECOVERY` and `resume` the totals of the job can be inexact. The state says so.
- There is no path finding. A path works only when each of its moves brings the robot nearer to the target dock. A straight path and a path with one corner are tested.
- Outside loaded areas the robot does not work and the job stays `RUNNING`. The state shows the robot as not loaded. No state changes, thus no notice comes.
- A robot behind an obstacle that does not go away waits without an end. One notice comes after 5 minutes of game time (D30).
- A chest has no owner. There is no integration with the protections of the server.
- Uno answers only in English, also to a request in another language.
- The `/uno confirm` command starts the newest proposal of the player, also when a different contact point made it. Only the screen names the proposal that its button confirms.
- A lower quantity does not bring items back. The load above the new quantity stays on board until the owner decides.
- The Lua calls of a module carry no job ID. A module of the mod stops when the robot gets a different job, but one tick stays open. See D26.
