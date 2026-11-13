# MindCraft — Exploration and startup brief for Claude Code

Date: 11 November 2026. Status: product proposal and initial plan, with assumptions to verify.

> This is the English translation of the brief. The project owner wrote the original in Italian. Decisions made after that date are in [DECISIONS.md](DECISIONS.md).
> The plan after S2 changed on 2026-11-13: see D27 in [DECISIONS.md](DECISIONS.md).

This document contains the context that is necessary to continue a design conversation and to start the development. It does not state that a repository, a prototype or passed tests exist. The name of the project is **MindCraft**. The identity of the central intelligence is **Uno**. The project owner confirmed these names and the experience reference in section 2.4.

## 1. Operating start for Claude Code

Act as a technical and product collaborator. The goal is to explore the points that are still uncertain and to reach a first playable version, with verifiable results.

1. Read this document and the applicable instructions of the repository. Inspect the structure, the Git state, the existing changes and the available tools. Use the existing code as the authoritative source on the implementation.
2. Summarize the direction in a few lines. Distinguish the requirements of the project owner, the proposals and the technical assumptions. Avoid another long phase of general brainstorming.
3. Verify the Minecraft/Forge/CC: Tweaked combination and the necessary interfaces through the documentation and the primary sources of the chosen version. Record the actual versions and the evidence.
4. Do the targeted exploration S0 of section 9. For each doubt look for a counterexample, a short test and a design consequence.
5. When S0 permits a concrete solution, continue with S1. The goal of the first iteration is a verifiable physical transfer with one turtle, visibility of the state and an explicit management of interruptions.
6. Present what works, how it was verified, which assumptions stay open and the next increment. Always distinguish an automatic test, a test in the game and an examination of the code.

Ask for clarification when a product choice changes the result substantially or a necessary fact is missing. For reversible choices use a declared assumption and continue. Keep the existing work. Do not replace the implementation with scaffolding only or with a list of activities.

Speak with the project owner in Italian. Use consistent code names in English. Write documentation with short sentences, precise terms and one responsibility for each sentence; the project owner prefers the clarity principles of ASD-STE100. Do not claim a certification of conformity to the standard.

This is a project brief. If permanent instructions are necessary in CLAUDE.md, keep there a short reference to this file and only the operating conventions that are really necessary.

## 2. Vision and status of the choices

The player interacts with Uno, the central intelligence of MindCraft. Turtles and, in the future, drones are its eyes and its limbs in the Minecraft world. The player can talk with Uno, program directly or develop behaviours together with it.

The Screeps inspiration is about a shared environment of programming, observation and coordination. It does not mean that its language, ticks, economy, PvP or offline operation must be copied.

The central fantasy is to see an assistant understand a goal, use physical robots, explain what it is doing and reuse abilities developed together with the player.

### 2.1 Requirements and intentions of the project owner

- Minecraft of the 1.20 family, Forge and ComputerCraft. The proposed concrete base is Minecraft Java 1.20.1 with CC: Tweaked.
- MindCraft is the name of the project. Uno is the central interlocutor; the fleet is the means by which it acts.
- Both the player and Uno can write and change programs.
- Visual programming is a candidate for the main level, with a more granular access to Lua.
- The mod should include modules that are ready to use and readable as examples.
- The platform must be able to support advanced applications, such as the design of a structure that one or more fleets then build.
- The ideas must be examined from more than one perspective, with a search for edge cases and attempts to break the assumptions.
- The start of the development must limit the risk of a large investment in wrong foundations.

### 2.2 Working proposals to validate

- An addon of CC: Tweaked, preferably through its public interfaces.
- Minecraft 1.20.1 as the target patch; Forge and CC: Tweaked with exact versions fixed after a compatibility test.
- Modules with a common interface and a visual or a Lua implementation.
- A proactive Uno, autonomous inside the assigned jobs and able to manage permanent responsibilities.
- Research and experimentation started by an explicit job; an always-active laboratory stays a future option.
- Official applications built on the platform and distributed with it. Blueprint is the main case that was discussed.
- First playable path: one turtle and two chests, with a restocking module.

### 2.3 Choices still open

- Shape of the visual level: structured blocks, activity graph, declarative rules or a limited combination.
- Editor in the game, web editor or both; extent of the programmable interfaces.
- Where Uno lives and which model/provider it uses; text before voice is a proposed default.
- Shape of the bridge with CC: Tweaked, place of execution of the modules and guarantees that are possible for custom Lua.
- Observation of the world, localization, navigation, connectivity and rules of chunk loading.
- Sharing permissions in multiplayer, shared resources and integrations with other mods.
- Drones: an implementation of this project or a future integration. Do not consider them already available through CC: Tweaked.

Keep the states "proposed", "decided" and "verified" separate. The exploration must be able to correct the proposals.

### 2.4 Identity of Uno and experience reference

**Confirmed direction:** the name Uno recalls the artificial intelligence of PK / Paperinik New Adventures. The reference is about the presence of an intelligence that lives in a place, collaborates with the protagonist and acts through physical tools.

The guiding sentence of the product is:

> Build a world together with an intelligence that can live in it.

For MindCraft, this direction becomes the following design criteria:

- **One identity.** Terminals, turtles and future drones are contact points and limbs of the same Uno. A change of device does not create another assistant. In multiplayer, a consistent identity and the access to data stay separate questions: an interlocutor does not automatically get information or control of the jobs of other players.
- **Presence in the base.** Uno knows the registered places and uses the robots to observe and act. To say "I am coming to check" must correspond to a traceable action, or to a declared impediment.
- **Character.** Competent, curious and slightly ironic. It can object and propose alternatives with concrete reasons. The frequency of its interventions must respect the attention of the player; a comment on each movement is not necessary.
- **Verifiable memory.** Uno remembers goals and decisions that were really saved. If a preference was not recorded or a context is not available, it does not invent previous conversations or habits. The first useful memory can be the persistent state of the jobs.
- **Words tied to the world.** Intention, sent command, observed effect and uncertain result are different states. The personal tone does not permit sentences such as "I delivered" without evidence of the delivery.
- **Collaboration on programs.** The player can understand and change the behaviours that Uno uses. The abilities developed together become reusable modules, with visible versions and limits.
- **Initiative inside the job.** Uno can suggest an improvement to the base. A joke or a proposal does not permit new demolitions, constructions or use of resources outside the assigned limits.

Example of tone, valid only when the data support it:

> "I delivered 24 items. 32 remain in the depot: that is the reserve you asked me to keep. To reach 64 in the furnace chest I need more materials."

The criteria are a guide for the behaviour and for the tests. They do not require voice, avatar, autonomous learning or general memory in the first iteration. S0-S1 stay focused on the physical transport. In S2, Uno must already connect a request, a job and a reliable report.

## 3. Method of exploration and critique

For each relevant decision record: problem, realistic alternatives, provisional choice, evidence, counterexample, cost of change and the condition that would discard the choice.

Use these perspectives. Separate persons or agents are not necessary: concrete objections are.

| Perspective | Question to test |
| --- | --- |
| Beginner player | Can the player get a result and understand a block without reading Lua? |
| Expert programmer | Can the programmer extend a behaviour without rewriting the whole system or losing diagnosis and control? |
| Game designer | Does Uno make delegation and collaboration pleasant? Which interesting decisions stay with the player? |
| Runtime developer | Are partial results, waits, cancellation and restart defined? |
| Server administrator | Can a program monopolize resources, load areas without limits or control the robots of other players? |
| Application author | Do Blueprint and logistics really use reusable tools, or do they continuously need exceptions in the core? |
| Maintainer | Does a change of the visual level, of the AI provider or of CC: Tweaked stay local? |

A proposal passes the review when it has observable behaviour, declared limits and at least one credible adversarial case. A sentence such as "we add a coordinator" does not by itself solve concurrency or recovery.

## 4. Product model and autonomy

### 4.1 Ways to interact

- **Use:** configure and start an included module.
- **Compose:** connect modules in a visual behaviour.
- **Program:** write or change a Lua module.
- **Collaborate:** ask Uno to configure, compose or change the same artifacts.

The four ways must converge on the same jobs, limits and results. Automations that already started must be able to do their steps without an AI call for each movement.

### 4.2 Proposed autonomy

Uno can propose new projects. Inside a job it can choose tools and organize the work within the assigned limits. A job can have an end or can keep a condition, such as a minimum stock.

Distinguish the freedom to choose the goals from the freedom to choose the means. To write a new program for a restocking does not automatically permit Uno to open a mine or demolish a building.

The limits belong to the job and to the execution system. The text in the prompt of Uno serves to interpret the intention, but it is not a sufficient mechanism to enforce those limits.

For the first AI integration, Uno can change a request into a structured configuration of an existing module. Arbitrary generation of Lua and self-improvement are later increments, to enable with a verified execution environment.

## 5. Initial concepts and responsibilities

These concepts are working vocabulary, not a prescription of classes, tables or microservices.

| Concept | Meaning |
| --- | --- |
| Robot | An identified limb, with abilities, known position, inventory and availability |
| Fleet | An organizational group of robots; it is not the same as one thread or a separate runtime |
| Observation | A detected fact, with origin and freshness; it can become unknown or obsolete |
| Module | A reusable ability with a defined interface and a versioned implementation |
| Job | A concrete goal, its parameters, the owner, the limits and the persistent state |
| Operation | A step with effects, identifiable to correlate request, execution and result |
| Application | A composition of interface, data and modules for a specific use |

Initial division of the responsibilities:

- **Editor/interface:** show and change programs and configurations, show results and errors.
- **Authoritative runtime:** validate jobs, assign robots, keep state and versions, coordinate the execution.
- **CC: Tweaked integration:** change the operations into turtle actions and acquire observations.
- **Uno:** interpret requests and prepare proposals/configurations through explicit interfaces.

These responsibilities can be in the same project. Introduce separate processes, external databases or services only when a verified need justifies them. The .NET stack that the project owner uses in other projects is not a requirement of this project.

### 5.1 Interface of the modules

For the first module define only the data that are necessary to answer these questions:

- Which parameters does it accept and how are they validated?
- Which robots, inventories and areas can it use?
- Which observations and materials does it need?
- What is the condition of success?
- Which progress does it show and how does it return a partial result?
- How does it signal a wait, an impediment, an error and an uncertain result?
- How does it react to pause, cancellation, loss of connection and restart?
- Which state must it keep? Which version does it run?

For a transport, "success=true" is not sufficient. The requested quantity, the quantity really taken/delivered and the material still on board are necessary, when these data can be known.

An offline robot does not prove that the job failed or that the last operation did not occur. Keep the state of the connection separate from the state of the work.

### 5.2 Visual level and Lua

The visual module keeps a structured definition. The Lua module keeps its source. A visual module can call a Lua module through the same interface as the other modules.

Do not assume that arbitrary Lua can be converted automatically into an equivalent graph. If an export of generated Lua is introduced, define explicitly which representation stays authoritative after a manual change.

Each execution uses an identifiable version. An update creates a new version; the behaviour of the jobs in progress must be defined. Simultaneous changes by the player and by Uno must detect conflicts.

A panel with parameters is sufficient for the first test. It does not yet prove that the complete visual language is usable: that test has a separate milestone.

### 5.3 Managed Lua and control of the actions

Verify with a technical test which operations can really be limited and interrupted. A convenience wrapper does not prove isolation: if the program can access the native functions or change the executor, it can go around it.

For each granted ability, find where it is checked and which paths can bypass the check. Consider infinite loops, memory, events, the filesystem of the computer, network access and direct commands to the turtle.

Any guarantees for managed programs must be declared within their perimeter. The ordinary programmability of CC: Tweaked does not automatically become a sandbox of this platform.

## 6. Continuity, physical world and limits of the guarantees

### 6.1 Restart after physical effects

Do not assume that the effect in the world and the save of the state of the addon are atomic. A turtle can have unloaded items when the runtime did not yet record the confirmation.

Operation identifiers and deduplication help to recognize repeated requests, but they do not by themselves prove an exactly-once execution. After an interruption, compare the available evidence and reconcile the state. If you cannot establish the result, show a recovery/uncertainty state and stop the ambiguous operations.

With other players, hoppers or mods that change the inventories, the current count alone may not permit the reconstruction of a delivery. Define which guarantees apply in the controlled case and how they degrade when other actors intervene.

Design also the case of world saves and addon data that are not aligned, or the restore of a backup. The restore of a version of the program does not cancel the physical effects that already occurred.

### 6.2 Observation and coordinates

- Distinguish a zero value, a value that was not observed and a device that is not reachable.
- Keep references to dimension, position and identity when necessary. A block at the same position can have been replaced.
- Determine how the position and the orientation of the turtle are known. To count the movements in memory is not sufficient after any type of stop or repositioning.
- For S1, registered stations and paths are acceptable. Describe how they are registered, verified and invalidated.
- The readings of inventories must come from mechanisms available in the chosen setup. Verify if modems, peripherals, docking or functions of the addon are necessary.
- Keep the perception of Uno consistent: a piece of information available to the server must not automatically become unlimited knowledge of the world for the player.

### 6.3 Concurrency and limits

A robot must have only one valid operating authority at a time. After a disconnection, an old assignment must not be able to continue in conflict with a new one. The expiry of a timer does not prove that the previous executor stopped.

The reserves of the runtime coordinate its jobs. They do not automatically block external withdrawals. Check the conditions again before the effects and describe honestly the limits of the minimum reserve.

Cancellation, pause and return have different meanings. Define what happens to the cargo already taken and which operations can complete during the stop.

### 6.4 Chunks, multiplayer and load

Verify unload/reload, clean restart, sudden stop and disconnection of the player as different cases. Offline work needs the server and the necessary areas to stay simulated; do not promise continuity in any condition.

The first release can operate only in areas that are already active, with an explicit suspension elsewhere. Any chunk loading needs limits of area, duration and use of resources.

The server validates the owner and the permissions of the jobs. Associate the turtle with its owner and verify the path of the messages: the number of the computer or a field in the payload are not by themselves an authorization.

Keep AI requests and network operations outside the main tick. Measure the cost of polling, persistence and planning on the real setup. Define timeouts and limits before a test with many units.

## 7. First playable experience

Initial scenario: one turtle, two identified inventories, a known path and a defined fuel source.

Reference request:

> Keep at least 64 pieces of coal in the furnace chest, taking them from the depot. Leave at least 32 pieces in the depot.

First implement a finite transfer. Then compose the keeping of the stock on the same behaviour. Define what counts as availability: stock in the chest, material already assigned and material in transit are not equivalent.

To simplify the first verification, the fuel can be supplied separately. If it uses the same coal as the job, account for that consumption too. The simplification must be declared in the test setup.

The player must see the assigned robot, the state, the transferred quantity, the remaining cargo and the reason of a block. The player must be able to interrupt the job and change parameters through a defined behaviour.

The completion criterion is not "the command was sent": it is a verified physical effect, or an impediment/uncertain result that is represented correctly.

## 8. Initial adversarial matrix

Change this matrix into repeatable tests as the abilities become available. Record setup, action, observations, expected result and actual result.

| ID | Perturbation | Expected behaviour |
| --- | --- | --- |
| T01 | No coal available | A readable wait/block state; no uncontrolled loop of attempts |
| T02 | Inventory data not available | Unknown state; no silent replacement with zero |
| T03 | Destination full or able to accept only a part | Real quantity and remainder shown; no invented complete delivery |
| T04 | Destination removed before the unload | Cargo kept or impediment detected; a specific test of the window between check and action |
| T05 | Obstacle, robot or player on the path | A defined wait or failure; no implicit destruction to clear the way |
| T06 | Insufficient fuel | Impediment and state of the cargo visible; documented recovery behaviour |
| T07 | Two jobs need the same turtle | Only one valid operating assignment |
| T08 | Duplicated command message or delayed confirmation | Correlation of the operation; no blind repetition |
| T09 | Stop after a withdrawal, before the save of the confirmation | Reconciliation or explicit uncertainty |
| T10 | Stop after a delivery, before the save of the confirmation | As T09; a separate test with inventories changed by an external actor |
| T11 | Chunk unload/reload or restart | State consistent with availability and evidence; no false progress |
| T12 | Turtle removed/repositioned or old executor reconnected | Identity, localization and assignment validated again |
| T13 | External withdrawal while the quantities are planned | Recalculation or block; declared limits of the reserve |
| T14 | Pause/cancellation during the transport | Cargo and operations in progress managed by a visible contract |
| T15 | Module updated during a job | Current version identifiable; no accidental hybrid execution |
| T16 | Concurrent change by the player and by Uno | Conflict detected; no silent overwrite |
| T17 | Wrong Lua, infinite loop or bypass attempt | Observable error or limit; verification of the real managed perimeter |
| T18 | AI provider slow, offline or with a malformed answer | Existing automations independent; invalid configuration refused |
| T19 | Request of another player or reference to an inventory without authorization | Authoritative validation; no control granted on trust in the client alone |
| T20 | UI closed and opened again, or client disconnected | State rebuilt from the runtime; to close the view does not implicitly change the job |
| T21 | Last observation obsolete or robot offline while Uno describes the work | Uno distinguishes the last known data from the current state; it does not invent perception or completion |
| T22 | Uno proposes an improvement beyond the limits of the job | The proposal stays separate from the execution; no change of the world outside the job |
| T23 | Interface opened again or access through another contact point | Same jobs and persistent decisions; no invented memory and permissions validated again |

Do not present a simulation as proof of the real behaviour of Minecraft. Use logic tests for transitions and invariants, integration tests for CC: Tweaked and scenarios in the game for the physical effects. Uncertain results are acceptable if they are declared and managed; invented guarantees are not.

## 9. Development by increments and required evidence

### S0 — Feasibility and reversible choices

Verify the toolchain, the dependencies, the start of client/server and the access to the necessary operations. Test the minimal observation-command-result path on one turtle. Examine the Lua execution model, the interruption and the possibilities to go around the control.

Compare only the realistic alternatives for the bridge/runtime. Prefer the one that permits a small test through supported interfaces. If a dependency on internal implementations of CC: Tweaked is necessary, document the reason and an alternative way.

**Exit:** precise versions, reproducible instructions, evidence of the minimal path, technical risks still open and a justified provisional choice. A search that does not resolve a fact must leave it marked as unknown.

### S1 — Real transport and diagnosis

Implement the identification/association of the robot, the references to the two inventories, a Lua transport module, start/state/stop and the minimum persistence that recovery needs. A debug command is acceptable as the first interface.

Verify a correct transfer and partial results. Run the applicable cases T01-T14 and T20; include the minimum ownership checks also with only one turtle. Define the restart on the basis of the available evidence.

**Exit:** a reproducible test in the game, appropriate automatic checks, results of the scenarios and documented limits. A world that works on the ideal path does not, by itself, conclude S1.

### S2 — Restocking, interface and minimal Uno

Compose "keep stock" with the transport. Add a simple screen for configuration and state. Connect a text request to a structured configuration of the same module.

The AI output goes through the ordinary validation. A deterministic provider permits repeatable tests; a test with the real provider verifies the integration. If credentials are missing, complete the independent parts and say precisely what was not verified.

**Exit:** text request, editable configuration, physical execution, change of the reserve and observability of the impediments. Uno describes the result with verified quantities and states, and keeps the assigned limit when the view is opened again. Run T18, T21-T22 and the applicable part of T23, and the relevant cases of concurrency and versioning. More contact points and a larger memory stay later tests.

### S3 — Visual composition and Lua customization

Compare a minimal visual representation on two behaviours: restocking and a second simple flow, for example a conditional return. Reuse the runtime and the modules.

Make it possible to create a Lua variant, version it and call it from the composition. Show the active node and the cause of an impediment. Test updates, conflicts and code that does not cooperate.

**Exit:** two behaviours through the same model, one Lua customization and tests T15-T17. Document the cases that the visual level does not represent yet.

### Later developments, guided by the results

- More robots with assignments and recovery from disconnection.
- A logistics application and a small reusable UI toolkit.
- Blueprint with simple shapes and a building site with one turtle, then coordinated teams.
- Drones, exploration, autonomous research, voice and distribution of applications.

The later developments are a direction, not a request to implement all of them in the first session.

## 10. Blueprint as a future test of the platform

The project owner wants to design a structure and give its construction to one or more fleets. The proposal is an official application built with the tools of the platform, distributable together with the mod.

Distinguish:

- **Blueprint:** geometry, materials, block states and spaces to leave unchanged or to clear.
- **Building site:** size, origin, orientation, permitted area, depots and initial conditions.
- **Job:** assignments, executable activities, dependencies, progress and impediments.

The geometry is data; thousands of blocks do not need thousands of visual nodes. The parametric generators can be Lua or visual modules. The design can evolve from capture in the world to a 3D editor and assistance from Uno.

More fleets share a consistent plan of the building site. A division into geometric zones only does not solve access, placement dependencies, traffic and supply. Consider robots trapped by a wall, duplicated assignments, replacement of robots and changes by the player.

Define a compatibility matrix of the placement. Start with verified full blocks; orientations, composite blocks, redstone, fluids, gravity and blocks of other mods need dedicated tests. A copy of the block type does not guarantee the copy of the state or of the behaviour.

The Blueprint application also needs programmable interfaces. The core can offer panels, forms, selection, scene views and saving; the application defines the logic of the project. How much of this is possible inside the runtime and how much needs native extensions is still to be shown.

The reuse test is to build also a logistics panel with the same tools. If each new application needs new specific exceptions in the core, review the interface before the promise of a general platform.

## 11. Questions to explore at the right moment

| When | Question | Proposed initial default |
| --- | --- | --- |
| Before S1 | Type of turtle, peripherals, fuel and registration of the stations | A reproducible and documented configuration |
| Before S1 | Operating authority and recovery state | Authoritative server; explicit stop when the result cannot be determined |
| During S0/S1 | Guarantees of managed Lua | Test them before the execution of arbitrary sources that Uno generates |
| Before S2 | Interface and AI provider | Text, structured configuration, replaceable provider |
| Before S3 | Visual language and persistent representation | Compare two real flows before the complete model is fixed |
| Before multiple fleets | Priorities, assignments, access and resources in transit | A common coordination, with explicit policies |
| Before programmable apps | UI toolkit and client/server execution | Small interfaces that come from Blueprint and logistics |
| Before distribution | Installation, updates, custom modules and saves | Identified versions and preserved customizations |

Other product subjects to keep: survival progression, cost of the robots and of the computation, limits of the perception, behaviour when the player is offline, frequency of the proposals of Uno and the pleasure of programming also when the AI can help.

## 12. Evidence, documentation and progress criterion

Keep a few artifacts of the repository up to date, with clear responsibilities:

- This brief or an agreed evolution of it: vision and status of the choices.
- Decision log: the reason of the important choices and the review conditions.
- Start/demo instructions: actual setup and reproducible procedure.
- Results of the adversarial tests: tests that ran, failed or did not run, and limits.

For each increment report:

1. The behaviour that was obtained and the relevant changes.
2. The commands/checks that really ran and their result.
3. The evidence in the game and the adversarial cases that were tested.
4. The assumptions that were disproved or are still open.
5. The next step, justified by the results.

The risk of a rewrite cannot be removed. Keep the experiments small and separate the result of the research from the promise of stability of the code. Review the abstractions when the second concrete behaviour contradicts them.

## 13. Primary sources to start from

These references were used during the exploration. Verify content and version before the implementation; updated documentation does not automatically prove compatibility with an earlier release. The historical issues serve to formulate tests, not to declare current bugs.

- [Forge 1.20.1: Getting Started](https://docs.minecraftforge.net/en/1.20.1/gettingstarted/) — toolchain and initial project.
- [CC: Tweaked repository](https://github.com/cc-tweaked/CC-Tweaked) — dependencies and indications on the public interfaces.
- [CC: Tweaked releases](https://github.com/cc-tweaked/CC-Tweaked/releases) — releases and changes to check.
- [CC: Tweaked 1.20.x: Turtle](https://tweaked.cc/mc-1.20.x/module/turtle.html) — actions, results, inventory and inspection.
- [CC: Tweaked 1.20.x: ComputerCraftAPI](https://tweaked.cc/mc-1.20.x/javadoc/dan200/computercraft/api/ComputerCraftAPI.html) — extension points.
- [CC: Tweaked 1.20.x: IPeripheral](https://tweaked.cc/mc-1.20.x/javadoc/dan200/computercraft/api/peripheral/IPeripheral.html) — integration and execution threads.
- [CC: Tweaked: Rednet](https://tweaked.cc/module/rednet.html) — communication and limits of the authenticity of the messages.
- [CC: Tweaked issue 951](https://github.com/cc-tweaked/CC-Tweaked/issues/951) — historical discussion on restart and persistence of the computers.
- [CC: Tweaked issue 1827](https://github.com/cc-tweaked/CC-Tweaked/issues/1827) — a case of interaction with chunk loading.
- [CC: Tweaked issue 204](https://github.com/cc-tweaked/CC-Tweaked/issues/204) — discussion on directional placement.
- [Blockly: What is Blockly?](https://docs.blockly.com/guides/get-started/what-is-blockly/) — the web nature of the editor.
- [Blockly: Code generation](https://docs.blockly.com/guides/create-custom-blocks/code-generation/overview/) — generators, Lua included; to keep separate from the runtime of this project.
- [Screeps: Scripting Basics](https://docs.screeps.com/scripting-basics.html) — inspiration for the coordination model.

## 14. Short prompt to start

> Read MINDCRAFT_CLAUDE_BRIEF.md and the instructions of the repository. The project is named MindCraft and the central intelligence is named Uno: keep the experience direction described in section 2.4. I want to explore the assumptions that are still open and start the development with the S0-S1 path. Distinguish requirements, proposals and evidence. Look for counterexamples from more than one point of view, verify the real interfaces of Minecraft 1.20.1/Forge/CC: Tweaked and continue on the reversible choices. Start from the test with one turtle and two chests. Ask me only for the clarifications that change the result substantially. Report the tests that ran, the limits and the decisions to review.
