# MindCraft

> Build a world together with an intelligence that can live in it.

MindCraft is an addon of [CC: Tweaked](https://tweaked.cc) for Minecraft 1.20.1 with Forge.
The player works together with **Uno**, an intelligence that acts in the world through turtles.

Status: increments S0, S1 and S2 of the [brief](docs/MINDCRAFT_CLAUDE_BRIEF.md), and increment A of the plan after S2. S3 did not start. This is an early version, not a release.
The plan after S2 is D27 in [docs/DECISIONS.md](docs/DECISIONS.md). The automatic tests of increment A pass. The project owner played its session on 2026-11-14 with [docs/PLAY_SESSION_A.md](docs/PLAY_SESSION_A.md). The exit of increment A is reached: see D27 and D40.
The mod is for one player now. Do not open the world to other players: see D37 in [docs/DECISIONS.md](docs/DECISIONS.md).

What this version contains:

- A runtime in the server that owns stations, robots and jobs, and saves them with the world. It writes its record through a temporary file.
- Two Lua modules for the turtle: transport, and keep a stock. A robot that cannot move waits, and continues by itself when the path is free.
- Uno: it changes a written request into one action. It starts, changes, resumes and cancels a job after your confirmation. It pauses a job and reports on it at once.
- A diary of each job. Uno warns you in the chat when a job needs you, and gives a summary when you open its screen.
- Two contact points that open the screen of Uno: a tablet and a projector.
- Commands for each operation.

Without an API key Uno understands only simple sentences. With an Anthropic API key Uno interprets the requests of an operator with Claude, with a limit for each server session.

| Document | Content |
| --- | --- |
| [docs/MINDCRAFT_CLAUDE_BRIEF.md](docs/MINDCRAFT_CLAUDE_BRIEF.md) | Vision and status of the choices |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decisions, evidence and review conditions |
| [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md) | Tests that ran, tests that did not run, and limits |
| [docs/PLAY_SESSION_A.md](docs/PLAY_SESSION_A.md) | The checklist of the play session of increment A |

## Versions

| Component | Version |
| --- | --- |
| Minecraft Java | 1.20.1 |
| Forge | 47.4.28 |
| CC: Tweaked | 1.121.0 |
| Anthropic Java SDK | 3.3.0 |
| Java | 17 |
| Gradle (wrapper) | 8.8 |
| ForgeGradle | 6.0.x |

## Build and tests

A JDK 17 is necessary. Set `JAVA_HOME` to the JDK before the commands.

```bash
./gradlew compileJava
```

```bash
./gradlew runGameTestServer
```

`runGameTestServer` starts a headless Forge server without players.
The server creates real turtles and chests in a flat world, runs the tests and stops.
The exit code is zero only if all tests pass.
The tests are in `src/main/java/io/github/g4solio/mindcraft/MindCraftTests.java`.

The server restart test uses two starts on the same world. Run the phases in this order:

```bash
./gradlew runGameTestServer -PrestartPhase=stop
```

```bash
./gradlew runGameTestServer -PrestartPhase=check
```

```bash
./gradlew runGameTestServer -PrestartPhase=crash
```

```bash
./gradlew runGameTestServer -PrestartPhase=check-crash
```

The test of the Claude provider with the real service is separate. It needs `ANTHROPIC_API_KEY` in the environment and costs four requests to the model:

```bash
./gradlew runGameTestServer -Plive
```

`check` compiles the code and checks the rules of `CLAUDE.md` that a program can check. Its task `checkRules` refuses a generated file, an API key, a local path or an email address in a file that a commit can hold. It also refuses a class of CC: Tweaked outside `dan200.computercraft.api`, and a test that sets the provider of the server outside `withServerProvider`:

```bash
./gradlew check
```

The workflow `.github/workflows/check.yml` runs `check`, the normal in-game run and the four restart phases at each push and pull request. It never runs the test with the real service.

The jar to install in a game is `build/libs/mindcraft-<version>-all.jar`. It contains the Anthropic SDK and is about 43 MB.
The load of this jar on an installed Forge is not verified yet.

```bash
./gradlew build
```

## Manual test in the game

No automatic test does this procedure with a client. See [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md).
This procedure uses the commands. The play session of increment A uses commands only at the setup: see [docs/PLAY_SESSION_A.md](docs/PLAY_SESSION_A.md).

1. Start the development client with `./gradlew runClient`. Create a world in creative mode with commands enabled.
2. Place two chests. Put the coal in the first chest.
3. Place a turtle. Give it fuel: put a fuel item in its inventory and run `refuel` in its terminal.
   Do not use the coal of the job as fuel.
4. Register the two stations. A station is an inventory and the adjacent position where the turtle stops (the dock).
   The robot does not search a path. At each move it tries the x axis, then z, then y, and it makes the first free move that brings it nearer to the dock.
   Thus a path works when each of its moves brings the robot nearer to the dock, in the two directions.
   A straight path and a path with one corner are tested. A stair and more corners are not tested.
   A path that needs a move away from the dock does not work, for example a way around a wall.
   In an open space the robot goes along x first. It follows a corridor only where blocks close the other moves.

   ```
   /mindcraft station add depot <x y z of the chest> <x y z of the dock>
   /mindcraft station add furnace <x y z of the chest> <x y z of the dock>
   ```

5. Enroll the turtle. The turtle must be on. The command restarts it.

   ```
   /mindcraft robot enroll <x y z of the turtle>
   ```

6. Start the transport. The robot number is the computer ID, which the previous command shows.
   The last number is the minimum reserve to leave in the source.

   ```
   /mindcraft job transport <robot> depot furnace minecraft:coal 24 32
   ```

7. Check and control the job. Only the owner of the robot, an operator and the console can read the status.

   ```
   /mindcraft job status <robot>
   /mindcraft job pause <robot>
   /mindcraft job resume <robot>
   /mindcraft job cancel <robot>
   ```

8. Keep a stock. This job does not end: the robot stays at the dock of the destination and checks it each 5 seconds.
   The first number is the level to keep in the destination. The second is the reserve to leave in the source.
   The `set` command changes the two numbers of an open job, also of a transport.
   A lower quantity does not bring items back. From the change on, the robot delivers only up to the new quantity.
   A load above it stays on board, and the job says so.

   ```
   /mindcraft job keep <robot> depot furnace minecraft:coal 64 32
   /mindcraft job set <robot> 64 16
   ```

## Talk with Uno

Uno proposes, you confirm. A proposal does not change the world and is not saved.
Uno accepts one request at a time. A new request replaces the proposal before it: `/uno confirm` confirms only the newest proposal.

```
/uno ask Keep at least 64 pieces of coal in the furnace chest, taking them from the depot. Leave at least 32 pieces in the depot.
/uno confirm
/uno report
```

A report and a pause act immediately, without a proposal:

```
/uno ask How is the furnace?
/uno ask Pause robot 3
```

- A request about one job gives the report of that job only. A report request that names no job gives the report on all your jobs, as `/uno report` does.
- A pause stops the job at once, with the same request as the "Pause" button. The cargo stays on board. If the runtime refuses the pause, for example because the job is not running, Uno gives the reason.
- Uno looks for the job only among your jobs. This holds for each action that names a job:
  - from the robot number, if the request gives one;
  - from a station name, if exactly one of your open jobs uses that station as source or as destination;
  - with no job named, if you have exactly one open job.
- In each other case Uno says how many jobs fit, asks for the robot number and does nothing.

A change, a resume and a cancellation are proposals, as a new job is. Nothing changes before you confirm:

```
/uno ask Leave only 16 in the depot
/uno confirm
```

- A change gives a new quantity, a new reserve or both. The proposal shows the old value and the new value. A value that the request does not give stays as it is, and the proposal says so.
- After the confirmation the robot uses the new values at its next step.
- The proposal names the robot and the job. The proposal of a change also holds the revision of the job. If a button or a command changes the job before the confirmation, Uno refuses the confirmation and asks you to read the job again. If the robot has a different job, Uno refuses too.
- The proposal to resume a job that needs a check (`RECOVERY`) says that the runtime accepts the load that it observes in the robot, and that the totals can be inexact.
- The proposal to resume a job also holds the state of the job. If the state is different at the confirmation, for example `RECOVERY` after `PAUSED`, Uno refuses the confirmation and asks you to read the job again.
- The proposal to cancel a job says that the cargo stays on board.
- Uno checks the request before the proposal. For example, a resume of a running job gives the reason and no proposal.
- A new job for a robot with an open job stays refused, with the number of that job. Cancel that job first.

### The tablet

The tablet is an item that you carry. It is in the "Tools & Utilities" tab of the creative inventory, or use `/give @s mindcraft:tablet`.
Use it with the right mouse button: it opens the screen of Uno at any distance.

The screen shows your jobs, with state, observed stocks and cargo on board.
You can change the quantity and the reserve and press "Apply". You can pause, resume or cancel.
At the bottom you write to Uno. Press Enter or "Send". If Uno makes a proposal, the button "Confirm #N" appears. N is the number of the proposal.
Above the button the screen repeats the proposal in words: the button confirms that proposal and no other.
The number fields keep the values of the job until you confirm a change.
The game does not pause while the screen is open.

### The projector

The projector is a block that you place in your base. It is in the same tab as the tablet, or use `/give @s mindcraft:projector`.
To place it registers a place of the base in your name. A click with the right mouse button opens the screen of Uno.
Within 8 blocks of a projector the screen also works without a tablet.

Above the block there is a hologram. Its colour shows a general state of your jobs to each player who sees it:

| Colour | Meaning |
| --- | --- |
| Light blue | No job in progress |
| Green | At least one job in progress |
| Amber | At least one job that waits, is blocked or needs a check |

### Notices

Uno writes one line in your chat when one of your jobs goes to `BLOCKED`, `RECOVERY` or `DONE`. For example:

```
Uno › Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') is blocked: the robot has no fuel.
Uno › Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') needs a check: I cannot say how it went. Recorded reason: the robot holds 0, the record says 24.
Uno › Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') is done: 24 delivered.
```

- The line comes from a fixed template for the state and the cause. It names the job, the robot and the cause.
- Uno only warns. It proposes no remedy and starts no action.
- A notice calls no model. It works without a key, and when the model service is down.
- `RUNNING`, `WAITING` and `SATISFIED` give no notice. Thus the ordinary cycle of a stock job is silent.
- A long wait gives one notice: the job stays `WAITING` for 5 minutes of game time without an interruption. For example:

```
Uno › Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') has waited for 5 minutes: the path of the robot is obstructed.
```

- The notice names the cause at that moment. A change of the cause during the wait does not start the 5 minutes again.
- A wait gives one notice only, also after a restart of the server. A job that works again and then waits again can give a new notice, after 5 new minutes.
- Each other state ends the wait: `RUNNING`, `SATISFIED`, a pause, a block. A restart of the server does not end it. Game time does not pass while the server is stopped.
- Your own requests give no notice: a pause, a resume and a cancellation. This holds also for a resume that completes the job.
- A request of an operator for your job is not your request. If the resume of an operator completes your job, you get the notice of `DONE`.
- A notice goes only to the owner of the job, and only while the owner is connected. A job of the console gives no notice.
- If you are not connected, there is no chat line. The summary at your next opening has the event.

### The summary

When you open the screen of Uno, Uno says what needed your attention since you last opened it.
An opening is the use of the tablet or a click on a projector. The new view that the screen gets each second, and the view after a button, are not an opening.

```
Uno › Since you last opened this screen:
12 min ago: Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') is blocked: the robot has no fuel.
2 min ago: Job 3 on robot #7 (minecraft:coal from 'depot' to 'furnace') is done: 24 delivered.
```

- The events are the events of the notices, with the same sentences, also a long wait. The oldest comes first. Each line has its age.
- The source is the diary. Thus the summary also has the events of your absence and the events before a server restart.
- The summary is the sentence of Uno on the screen. The next answer of Uno replaces it.
- If nothing needed your attention, Uno keeps its sentence. If that sentence is the summary of an earlier opening, Uno says "Tell me what you need." instead: the ages of that summary are old.
- The first opening in a world covers your whole diary. Its first line is "From the start of my memory:".
- The summary shows the 10 newest events at most. It says how many older ones it does not show.
- If the runtime removed entries of yours after your last opening, the summary says where the memory of Uno starts: "My memory starts 3 h ago. I do not remember older events."
- The age is game time. It does not count the time while the server is stopped. The ages are those of the moment of the opening: they do not change while the screen is open.
- After a crash the game time can go back. An event of the diary can then be later than the world: its age is "At an unknown time". The summary still shows each event one time.
- The summary has only your events. An operator also gets the events of the jobs of the other players and of the console, as in the list of jobs.
- The summary calls no model.
- Minecraft saves your last opening with the world: the place in the diary where it occurred.

### The diary

The runtime writes a diary: each change of the state of a job, and each change of the cause of a wait or of a block.
A repeated report of the same state and cause writes nothing. A long wait writes one more entry, with the cause at that moment.
An entry has a number in the order of the diary, the game time, the job, the robot, the two stations, the item, the state, the cause, the recorded reason and the quantities at that moment.
Minecraft saves the diary with the world. The entries of a job stay after a new job replaced it on the robot.
The limit is 200 entries for each owner. Above it, the runtime removes the oldest entries of that owner and counts them.
Only the owner and an operator read the entries of a job. The summary shows the events of the diary that give a notice. No screen and no command shows the whole diary.

### Uno with Claude

Uno uses Claude if the server process has the environment variable `ANTHROPIC_API_KEY`. In a single-player game the server is the game itself.
Set the variable outside the project. Do not write the key in a file of the repository.
The server log says which provider is active: look for the line `Uno interprets requests with`.

- Only an operator uses Claude. In a single-player game you are an operator if the world allows commands. Uno reads the requests of each other player with the fixed rules (see below).
- Each written request of an operator is a paid call on the account of the key. The buttons, the confirmation, `/uno report`, the notices, the summary and the jobs do not call the model.
- Each operator has a limit of 100 requests to Claude in one server session. The console is one more actor with its own limit. In a single-player game a session ends when you close the world.
- At the limit Uno says so one time. Then it reads your requests with the fixed rules until the server restarts. The buttons and the commands work as before.
- The screen of an operator shows a counter: the requests to the model in this session, and the input and output tokens of the answers. A request counts when Uno sends it. A request that fails or reaches the time limit counts with zero tokens.
- Anthropic gets the text of the request, the names of the stations and the numbers of the robots. An operator owns each station and each robot of the server: thus these are the names and numbers of all players.
- The screen says "Written requests go to Claude" only when your requests go to Claude. Otherwise one line says that Uno uses the fixed rules for you, and why: you are not an operator, or you reached the limit.
- A new job, a change, a resume and a cancellation from Claude are only proposals. They go through the same validation as the commands and act only after your confirmation.
- The model is Claude Opus 5.5. You can write the request in any language. Uno answers in English.
- If Claude cannot find a necessary value, Uno says what is missing.
- Claude changes the request into one action: start, change, pause, resume, cancel, report or none. The server writes the text of each proposal, never the model.
- A pause and a report from Claude run immediately. The server finds the job, as described above. A written report request is a paid call. The "Report" button and `/uno report` are not.

Details and limits: D21 and D33.1 in [docs/DECISIONS.md](docs/DECISIONS.md).

### What Uno understands without Claude

Without a key Uno knows only these elements.

A request that names two of your stations is a new job:

- The exact names of two of your stations. The source is the one after "from".
- A quantity, and a reserve after "leave" or "reserve".
- An item: the Minecraft ID (`coal`, `minecraft:coal`).
- "Keep" for a stock. Otherwise the job is a transport.
- "robot N" if you have more than one free robot.

It also reads the same request in Italian, the first language of this project.

With one station or none, a word of the action decides. If a request has the words of two actions, the higher row of the table decides.

| Action | English words | Italian words | Examples |
| --- | --- | --- | --- |
| Resume | `resume`, `continue` | `riprendi`, `continua` | "Resume the furnace job", "Riprendi il lavoro in pausa" |
| Cancel | `cancel` | `annulla`, `cancella` | "Cancel robot 3", "Annulla il lavoro del deposito" |
| Pause | `pause` | `pausa`, `sospendi` | "Pause the furnace job", "Metti in pausa il robot 3" |
| Change | A number after `quantity` or `level` (the new quantity), and after `leave` or `reserve` (the new reserve) | A number after `quantità` or `livello`, and after `lascia` or `riserva` | "Leave only 16 in the depot", "Set the quantity of robot 3 to 30", "Lascia solo 16 nel deposito" |
| Report | `report`, `status`, `how is`, `how's`, `how are` | `rapporto`, `stato`, `come va`, `come vanno`, `come sta`, `come stanno` | "How is the furnace?", "Come va il robot 3?", "Report" |

The job of these actions: "robot N", or the exact name of one of your stations, or nothing.
The number after "robot" names the job. It is never a new value of a change.
A change takes the first number after its word. "Change the quantity and leave 16" gives 16 for both values: read the proposal before you confirm.
A request without the words of an action and without two stations gets the reason why Uno cannot propose a job, as before.

## States of a job

| State | Meaning | Who unblocks it |
| --- | --- | --- |
| `RUNNING` | The robot works | |
| `WAITING` | The source has no material above the reserve, the destination is full, the robot is full, the area of an inventory is not loaded, or the path is obstructed. The runtime records which one: the cause. The robot tries again each 5 seconds. A robot that cannot move first tries 10 times at 1 second. For an obstacle the reason contains the position, the direction and the block in front of the robot | It unblocks by itself. After an obstacle the robot continues at the first move that succeeds |
| `SATISFIED` | Only for a stock: at the last observation the destination had the requested level. A load can stay on board, if the owner lowered the level after the pickup | It starts again by itself when the stock goes down |
| `BLOCKED` | The fuel ended, a station has no inventory, or a transport has a load on board above its quantity, because the owner lowered the quantity after the pickup. The runtime records the cause. A module error, or an impediment of a kind that the runtime does not know, is a block without a cause | The owner, with `resume`. Without fuel: give fuel first. For a load above the quantity: raise the quantity first, or cancel |
| `PAUSED` | The owner asked for a pause. The cargo stays on board | The owner, with `resume` |
| `RECOVERY` | The real cargo of the robot is different from the recorded cargo, or the world is older than the record. The runtime does not know the result | The owner, with `resume` (accepts the observed cargo) or `cancel` |
| `DONE` | The requested quantity was delivered and no load of the job is on board | |
| `CANCELLED` | The owner cancelled. The cargo stays on board | |

The rule: a condition that can end by itself is a wait. A condition that needs your hands is a block.
A change to `BLOCKED`, `RECOVERY` or `DONE` gives a notice in the chat of the owner. A `WAITING` of 5 minutes without an interruption gives one notice too. See "Notices".
Uno takes the sentence of its report from the cause. The screen and the `status` command also show the recorded reason with the position.

The state of the link with the robot is separate from the state of the job. The `status` command shows both.

## Conventions of the code

- The mod uses only the public API of CC: Tweaked, `dan200.computercraft.api`.
- A comment that starts with `ponytail:` marks a deliberate simplification. It names the limit and the moment to replace it.
- The documents use short sentences, with one responsibility for each sentence.

## License

[MIT](LICENSE).
