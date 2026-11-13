# MindCraft

> Build a world together with an intelligence that can live in it.

MindCraft is an addon of [CC: Tweaked](https://tweaked.cc) for Minecraft 1.20.1 with Forge.
The player works together with **Uno**, an intelligence that acts in the world through turtles.

Status: increments S0, S1 and S2 of the [brief](docs/MINDCRAFT_CLAUDE_BRIEF.md). S3 did not start. This is an early version, not a release.
The plan after S2 is D27 in [docs/DECISIONS.md](docs/DECISIONS.md).
The mod is for one player now. Do not open the world to other players: see D37 in [docs/DECISIONS.md](docs/DECISIONS.md).

What this version contains:

- A runtime in the server that owns stations, robots and jobs, and saves them with the world.
- Two Lua modules for the turtle: transport, and keep a stock.
- Uno: it changes a written request into a proposal, starts it after your confirmation, and reports on the work.
- Two contact points that open the screen of Uno: a tablet and a projector.
- Commands for each operation.

Without an API key Uno understands only simple sentences. With an Anthropic API key Uno interprets the request with Claude.

| Document | Content |
| --- | --- |
| [docs/MINDCRAFT_CLAUDE_BRIEF.md](docs/MINDCRAFT_CLAUDE_BRIEF.md) | Vision and status of the choices |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decisions, evidence and review conditions |
| [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md) | Tests that ran, tests that did not run, and limits |

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

The jar to install in a game is `build/libs/mindcraft-<version>-all.jar`. It contains the Anthropic SDK and is about 43 MB.
The load of this jar on an installed Forge is not verified yet.

```bash
./gradlew build
```

## Manual test in the game

No automatic test does this procedure with a client. See [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md).

1. Start the development client with `./gradlew runClient`. Create a world in creative mode with commands enabled.
2. Place two chests. Put the coal in the first chest.
3. Place a turtle. Give it fuel: put a fuel item in its inventory and run `refuel` in its terminal.
   Do not use the coal of the job as fuel.
4. Register the two stations. A station is an inventory and the adjacent position where the turtle stops (the dock).
   The path between the two docks must be free and straight along the axes.

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
Uno accepts one request at a time. A new request replaces the proposal before it: `/uno confirm` starts only the newest proposal.

```
/uno ask Keep at least 64 pieces of coal in the furnace chest, taking them from the depot. Leave at least 32 pieces in the depot.
/uno confirm
/uno report
```

### The tablet

The tablet is an item that you carry. It is in the "Tools & Utilities" tab of the creative inventory, or use `/give @s mindcraft:tablet`.
Use it with the right mouse button: it opens the screen of Uno at any distance.

The screen shows your jobs, with state, observed stocks and cargo on board.
You can change the quantity and the reserve and press "Apply". You can pause, resume or cancel.
At the bottom you write to Uno. Press Enter or "Send". If Uno makes a proposal, the "Confirm" button appears.
Above the button the screen repeats the proposal in words: the button starts that proposal and no other.
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

### Uno with Claude

Uno uses Claude if the server process has the environment variable `ANTHROPIC_API_KEY`. In a single-player game the server is the game itself.
Set the variable outside the project. Do not write the key in a file of the repository.
The server log says which provider is active: look for the line `Uno interprets requests with`.

- Each written request to Uno is a paid call on the account of the key. The report, the confirmation and the jobs do not call the model.
- Anthropic gets the text of the request, the names of your stations and the numbers of your robots. The screen says so with one line.
- The answer of Claude is only a proposal. It goes through the same validation as the commands and starts only after your confirmation.
- The model is Claude Opus 5.5. You can write the request in any language. Uno answers in English.
- If Claude cannot find a necessary value, Uno says what is missing.

Details and limits: [D21](docs/DECISIONS.md).

### What Uno understands without Claude

Without a key Uno knows only these elements:

- The exact names of two of your stations. The source is the one after "from".
- A quantity, and a reserve after "leave" or "reserve".
- An item: the Minecraft ID (`coal`, `minecraft:coal`).
- "Keep" for a stock. Otherwise the job is a transport.
- "robot N" if you have more than one free robot.

It also reads the same request in Italian, the first language of this project.

## States of a job

| State | Meaning | Who unblocks it |
| --- | --- | --- |
| `RUNNING` | The robot works | |
| `WAITING` | The source has no material above the reserve, the destination is full, the robot is full, or the area of an inventory is not loaded. The runtime records which one. The robot tries again each 5 seconds | It unblocks by itself |
| `SATISFIED` | Only for a stock: at the last observation the destination had the requested level. A load can stay on board, if the owner lowered the level after the pickup | It starts again by itself when the stock goes down |
| `BLOCKED` | The path is obstructed, the fuel ended or a station has no inventory. The reason contains the position, the direction and the block in front of the robot. Also: a transport has a load on board above its quantity, because the owner lowered the quantity after the pickup | The owner, with `resume`. For a load above the quantity: raise the quantity first, or cancel |
| `PAUSED` | The owner asked for a pause. The cargo stays on board | The owner, with `resume` |
| `RECOVERY` | The real cargo of the robot is different from the recorded cargo, or the world is older than the record. The runtime does not know the result | The owner, with `resume` (accepts the observed cargo) or `cancel` |
| `DONE` | The requested quantity was delivered and no load of the job is on board | |
| `CANCELLED` | The owner cancelled. The cargo stays on board | |

The state of the link with the robot is separate from the state of the job. The `status` command shows both.

## Conventions of the code

- The mod uses only the public API of CC: Tweaked, `dan200.computercraft.api`.
- A comment that starts with `ponytail:` marks a deliberate simplification. It names the limit and the moment to replace it.
- The documents use short sentences, with one responsibility for each sentence.

## License

[MIT](LICENSE).
