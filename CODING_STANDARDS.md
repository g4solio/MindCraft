# Coding standards

The reviewer reads this file. It holds the judgement calls that a program cannot check.
The mechanical rules are in the task `checkRules` of `build.gradle` (D39). The rules of the work are in [CLAUDE.md](CLAUDE.md).

## In-game tests

- **Wait for an event, not for ticks.** The test server runs ticks without pauses. The computers of CC: Tweaked run in real time. Thus a test waits with `thenWaitUntil` for a fact of the robot that it can observe. Examples: a state, a cause, a count such as `ops`, or a contact after a change (`job.lastContact` after the game time of the change). Runs 155 and 165 failed one time each without this rule.
- **`thenIdle` comes only before a negative check.** A number of ticks is correct before a check that something did not occur: the robot did not move, the job stays `BLOCKED`, a late answer did not arrive. Put an event wait before it, so that the robot saw the change first.
- **Build the moment.** A test that passed 12 times can hide a defect of one tick (run 21). A test builds the moment that it tests on purpose.
- **No race between threads.** A test does not depend on which thread is faster (run 28). A fast provider can answer inside the call that sends the request.
- **Turn a computer off through `MindCraftTests.shutDown`. Restart it through `MindCraft.reboot`.** CC: Tweaked ignores a "shutdown" while a computer starts. Both repeat the command. `checkRules` refuses a `computercraft shutdown` command in a different place.
- **A value of the whole server goes back.** A test that changes the provider, the session or a setting runs in a batch of its own. It puts the value back, also after a failed check (`withServerProvider`, `withModel`).
- **The time is a parameter.** A test that needs a later moment gives the game time to the account: an old observation, a silent robot. It does not wait 5 minutes.

## The evidence of a run

- **Read the log, not the exit code.** A pass has three lines: `Enabled Gametest Namespaces`, `GAME TESTS COMPLETE` and `All N required tests passed :)` (runs 2 and 14). The namespace says which tests ran.
- **A test of a different kind is not a test in the game.** `docs/TEST_RESULTS.md` names the kind of each piece of evidence (CLAUDE.md).
