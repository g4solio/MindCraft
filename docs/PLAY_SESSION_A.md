# Play session of increment A — checklist

This is the checklist of the play session of increment A. It is the second part of the exit of the increment (D27). Ticket #13 of spec #1 is the session.
The session is a test in the game. The project owner plays it in the development client.
The project owner played it on 2026-11-14. The results are in [TEST_RESULTS.md](TEST_RESULTS.md), and the differences are in D40 of [DECISIONS.md](DECISIONS.md).

- The setup is the only part with commands.
- After the setup, no step uses a command. The words go to Uno through the screen. The tablet and the projector open the screen.
- Each step gives the expected result and has a field "Result". Write "As expected", or what you saw instead.
- A difference is not a failure of the session. Record it. Decide about it after the session.
- After the session, the results go to [TEST_RESULTS.md](TEST_RESULTS.md), section "Increment A — the play session", as a test in the game.

## Before the session

| No. | Point | Result |
| --- | --- | --- |
| B1 | Decide whether to run the test with the real service one time: `gradlew runGameTestServer -Plive`. It costs four requests on the account of the key. Its requests test the actions `start` and `none` and the counter, not the other actions. Without this run, the first requests of the schema of increment A to the real service are the requests of this session (D29.1) | Done on 2026-11-14, with the consent of the project owner. Runs 76 and 78 found a defect of the schema; run 81 passed after the correction. See D29.1 |
| B2 | Write down the commit of the code of the session | |
| B3 | The key is in the environment of the process that starts the client, as `ANTHROPIC_API_KEY`. It is in no file, in no visible command and in no message. Do not write it in the chat of the game or in a request to Uno | |
| B4 | Each written request of this session is a paid call. The steps have about 15 requests. With the estimate of D33 this is about 0.25 $. The counter on the screen shows the real tokens | |

## Setup — the only part with commands

The rig. Top view, north is up. Each character is one block at the height of the chests.

```
x:      0         1   2   3   4   5   6
z = 0   [depot]   D   .   .   .   C
z = 1             #   #   #   #   .
z = 2             #   #   #   #   .
z = 3             #   #   #   #   .
z = 4             #   #   #   #   D   [furnace]
```

- `[depot]` and `[furnace]`: two single chests. `D`: the dock of the chest next to it. `C`: the corner.
- `.`, `C` and `D`: the corridor. It has two blocks of air, thus a player can stand in it.
- `#`: a wall, two blocks high. It fills the inside of the L. Without it the robot cuts the corner (README, step 4 of the manual test).
- The robot goes along the corridor: 4 moves along x and 4 along z. One trip from a dock to the other is 8 moves. A round trip is 16 moves.

| No. | Do | Expected | Result |
| --- | --- | --- | --- |
| S1 | From a shell that has the key in its environment, start the development client: `gradlew runClient` | The title screen. No error of MindCraft in the log | |
| S2 | Create a world. Game mode Creative. "Allow Cheats" on. Default world type | You are an operator. The log has the line `Uno interprets requests with Claude (claude-opus-5-5) for the operators, at most 100 requests for each of them in this session. The fixed rules read the requests of the other players.` | |
| S3 | Build the rig above. Put 256 coal (four stacks) in the depot chest. The furnace chest stays empty | | |
| S4 | Place a turtle on the dock of the furnace. Open it with the right mouse button. Put one piece of charcoal in its first slot. Write `refuel` in its terminal | The terminal shows the fuel level 80. The inventory of the turtle is empty. Leave no item in it: the robot burns each item that is not coal when its fuel ends | |
| S5 | Register the two stations. Look at a block and press Tab: the command completes its coordinates.<br>`/mindcraft station add depot <x y z of the depot chest> <x y z of its dock>`<br>`/mindcraft station add furnace <x y z of the furnace chest> <x y z of its dock>` | "Station 'depot' is registered." and "Station 'furnace' is registered." | |
| S6 | Enroll the turtle: `/mindcraft robot enroll <x y z of the turtle>` | "Robot #R is enrolled. It restarts now and works for Uno." Write down R. The terminal of the turtle shows "MindCraft: this turtle works for Uno." | R = |
| S7 | Keep the rig loaded: `/forceload add <x z of the depot chest> <x z of the furnace chest>`. Then `/forceload query` | The query lists the chunks of the rig | |
| S8 | `/give @s mindcraft:tablet` and `/give @s mindcraft:projector`. Place the projector 3 blocks to the north of the corridor | A hologram above the projector, light blue | |
| S9 | Put in the hotbar: the tablet, 8 pieces of charcoal, and a stack of stone | | |
| S10 | Close the chat. From here on, use no command | | |

## Rules for the steps

- Open the screen with the tablet (right mouse button) or with a click on the projector. Each of the two is an "opening".
- Write in the field "Write to Uno". Press Enter or "Send".
- A proposal adds the button "Confirm #N". Press that button. The text of Uno says "Confirm with /uno confirm": do not use the command.
- Do not hit the robot or a chest: in creative mode one hit breaks the block.
- Some steps wait for game time. During a wait, do not open the menu with Esc. Keep the window of the game in focus. A single-player game pauses in both cases, and the paused time does not count.
- The sentences of the steps are written so that Claude and the fixed rules both read them. With Claude you can also write in your own words, or in Italian. Record such a sentence and its answer.
- If the robot stops for fuel during a step before step 7, record when. Do step 7 at that moment. Then go back to the step.

## Steps — no command

### Step 1 — Start a stock job by words

Do:

1. Use the tablet. Read the lines of the screen before you write.
2. Write: "Keep at least 64 pieces of coal in the furnace chest, taking them from the depot. Leave at least 32 pieces in the depot."
3. Read the proposal. Look at the world: nothing moves yet. Press "Confirm #N".

Expected:

- Before the request: "Uno › Tell me what you need.", "Your model requests in this session: 0 of 100. Tokens: 0 in, 0 out." and "Written requests go to Claude, a model of Anthropic."
- After "Send": "Uno › Let me think." In less than 20 seconds, the proposal: "Proposal: keep at least 64 x minecraft:coal in 'furnace', taken from 'depot'. Reserve to leave in 'depot': 32. Robot #R. I have done nothing yet. Confirm with /uno confirm."
- Above the buttons: "Waiting for your confirmation: …", with the same proposal. The button "Confirm #N".
- The counter: 1 of 100, with input and output tokens above 0.
- After the button: "Job J started on robot #R." The robot goes along the corridor to the depot, takes 64 and comes back. The hologram is green while the robot works.
- At the end: "Job J · robot #R · stock at level". The bar of 'furnace' shows 64/64. The depot has 192. The hologram is light blue.
- No line in the chat.

Result:

### Step 2 — Ask about the job

Do: write "How is the furnace?". Optionally, write the same question in Italian: "Come va la fornace?"

Expected:

- The report of this job only, at once: "Job J, robot #R: keep at least 64 items in 'furnace', reserve 32 in 'depot'. I delivered 64 items. At the last observation the stock was at the requested level. Last observations: …"
- The numbers agree with the screen and with the chests.
- No proposal and no "Confirm" button. The counter grows by one for each request.

Result:

### Step 3 — Change the reserve by words

Do: write "Leave only 16 in the depot". Read the proposal and the field "Reserve in the source". Then press "Confirm #N".

Expected:

- The proposal: "Proposal: change job J on robot #R (keep minecraft:coal in 'furnace', taken from 'depot'). Level to keep in 'furnace': 64, unchanged. Reserve to leave in 'depot': from 32 to 16. I have done nothing yet. Confirm with /uno confirm."
- Before the confirmation the field "Reserve in the source" shows 32.
- The label "Confirm #N" is readable and fits the button.
- After the button: "Job J on robot #R changed. Level to keep in 'furnace': 64. Reserve to leave in 'depot': 16. The robot uses the new values at its next step." The field shows 16. The "Confirm" button is gone.
- No line in the chat.

Result:

### Step 4 — Pause and resume by words

Do: write "Pause the furnace job". Then write "Resume the furnace job" and press "Confirm #N".

Expected:

- The pause acts at once, without a proposal: "Job J on robot #R is paused. The cargo stays on board." The job is "paused" and has the button "Resume".
- The resume is a proposal: "Proposal: resume job J on robot #R (keep minecraft:coal in 'furnace', taken from 'depot'). I have done nothing yet. Confirm with /uno confirm." The job stays "paused" until the button.
- After the button: "I resume job J on robot #R." Then the job is "stock at level" again.
- No line in the chat for the pause and for the resume.

Result:

### Step 5 — An obstacle in the corridor

Do:

1. Take 16 coal out of the furnace chest. In about 5 seconds the robot sees the lower stock and leaves for the depot.
2. When the robot has passed the corner toward the depot, place a stone in the corridor between the corner and the dock of the furnace.
3. The robot comes back with the load and stops in front of the stone. 15 seconds later, open the screen. Write "How is the furnace?"
4. Leave the stone. Wait 5 minutes of game time from the stop of the robot.
5. Wait one more minute.
6. Break the stone.

Expected:

- About 10 seconds after the stop, the job is "waiting", with "on board 16". If the robot met the stone before the pickup, it has "on board 0": record it.
- The screen shows "Recorded reason: the robot cannot move from x y z (direction, in front: minecraft:stone) toward x y z: Movement obstructed".
- The hologram is amber.
- The report says: "My path is obstructed. I try to move again every few seconds, and I continue by myself when the path is free."
- No line in the chat during the first 5 minutes. Then one line: "Uno › Job J on robot #R (minecraft:coal from 'depot' to 'furnace') has waited for 5 minutes: the path of the robot is obstructed."
- No second line in the next minute.
- The stone is intact: the robot does not dig.
- After the stone is gone, the robot moves in less than 5 seconds, without a resume. The job is "in progress" and the hologram is green. Then the robot delivers: the furnace has 64, and the job is "stock at level". No line in the chat.

Result:

### Step 6 — Stand on the path

Do: take 16 coal out of the furnace chest. Stand in the corridor, on the path of the robot. Stay there until the robot reaches you, and 20 seconds more. Then leave the corridor. Record where you stood.

Expected, one of two results. Record which one:

- The robot pushes you along the corridor and continues. The server configuration permits it (`can_push = true`).
- The robot cannot push you. About 10 seconds later the job is "waiting", as in step 5. After you leave, the robot continues by itself.

In both cases: you take no damage, and no item is lost. At the end the furnace has 64. No line in the chat: the wait is shorter than 5 minutes.

Result:

### Step 7 — The fuel ends

Do:

1. Each time the robot is back at the dock of the furnace and the hologram is light blue, take 16 coal out of the furnace chest. Do not open the screen. Repeat until the robot stops for fuel. With the setup, the robot makes two more trips, and the third trip does not start.
2. Read the chat and the hologram. Then open the screen. Write "How is the furnace?"
3. Open the robot with the right mouse button. Put 8 pieces of charcoal in its inventory. Close it. Wait 15 seconds.
4. Write "Resume the furnace job". Press "Confirm #N".

Expected:

- One line in the chat: "Uno › Job J on robot #R (minecraft:coal from 'depot' to 'furnace') is blocked: the robot has no fuel."
- The hologram is amber.
- The opening shows a summary: "Since you last opened this screen:", then the events since your last opening, each with its age. The block is the last event. The long wait of step 5 is in it too, if you did not open the screen after step 5.
- The job is "blocked", with "Recorded reason: the robot cannot move from … : Out of fuel".
- The report says: "I am stopped: I have no fuel. Give me fuel, then resume the job."
- With the charcoal on board, the job stays "blocked" and the robot does not move. Only the owner unblocks a block.
- The resume is a proposal. After the button: "I resume job J on robot #R." The robot burns the charcoal, not the coal. It fetches 16 and delivers. No line in the chat for the resume.

Result:

### Step 8 — Cancel the stock job by words

This step is earlier than in the list of the spec. A transport needs a robot without an open job, and the stock job has no end. Step 12 cancels a second time, with a load on board.

Do: wait until the job is "stock at level" with "on board 0". Write "Cancel the furnace job". Read the proposal. Press "Confirm #N".

Expected:

- The proposal: "Proposal: cancel job J on robot #R (keep minecraft:coal in 'furnace', taken from 'depot'). The cargo stays on board the robot. I have done nothing yet. Confirm with /uno confirm."
- The job stays open until the button.
- After the button: "Job J on robot #R is cancelled. The cargo stays on board the robot." The job is "cancelled" and has no buttons.
- No line in the chat.

Result:

### Step 9 — A transport reaches DONE while you are far

Do:

1. Fly at least 300 blocks away from the rig. F3 shows your position. The forced chunks keep the rig loaded.
2. Use the tablet. Write "Carry 16 coal from the depot to the furnace". Read the proposal. Press "Confirm #N". Close the screen.
3. Do not open the screen. Wait for the line in the chat. Then wait 2 more minutes.

Expected:

- The proposal: "Proposal: carry 16 x minecraft:coal from 'depot' to 'furnace'. Reserve to leave in 'depot': 0. Robot #R. …" Claude can give a different reserve: record it.
- After the button: "Job K started on robot #R." The runtime accepts the job: the robot runs, also with you far.
- In less than a minute, one line in the chat: "Uno › Job K on robot #R (minecraft:coal from 'depot' to 'furnace') is done: 16 delivered."

Result:

### Step 10 — Come back and read the summary

Do:

1. Fly back to the rig. Do not use the tablet.
2. Click the projector. Read the text of Uno.
3. Look at the furnace chest.
4. Close the screen and click the projector again at once.

Expected:

- The text of Uno: "Since you last opened this screen:" and, on a line of its own, "2 min ago: Job K on robot #R (minecraft:coal from 'depot' to 'furnace') is done: 16 delivered." The age agrees with the time since the line in the chat.
- The text fits the screen. Each event is on a line of its own.
- Job K is "complete". The furnace chest has 16 more coal than before step 9: the robot worked while you were far.
- The second click: "Tell me what you need." The summary does not come back.

Result:

### Step 11 — Close the world and open it again

Do:

1. Click the projector. Read the counter.
2. Write "Carry 8 coal from the depot to the furnace". Press "Confirm #N". Close the screen.
3. Do not open the screen. Wait for the line in the chat. Then wait 1 more minute.
4. Press Esc and "Save and Quit to Title". Wait 1 minute. Open the same world again.
5. Click the projector. Read the text of Uno and the counter.

Expected:

- In point 1 the counter shows the number of your written requests since the world opened, and their tokens.
- One line in the chat: "Uno › Job L on robot #R (minecraft:coal from 'depot' to 'furnace') is done: 8 delivered."
- After the opening of the world: the log has a new line "Uno interprets requests with Claude …". The turtle shows "MindCraft: this turtle works for Uno." again.
- The text of Uno: "Since you last opened this screen:" and "1 min ago: Job L on robot #R (minecraft:coal from 'depot' to 'furnace') is done: 8 delivered."
- The age does not count the minute while the world was closed. The first line is not "From the start of my memory:": the last opening survived the restart.
- The counter: "Your model requests in this session: 0 of 100. Tokens: 0 in, 0 out." The opening of the world started a new server session.

Result:

### Step 12 — Cancel by words, with a load on board

Do:

1. Write "Carry 32 coal from the depot to the furnace". Press "Confirm #N".
2. When the robot has passed the corner toward the depot, place a stone between the corner and the dock of the furnace.
3. When the job is "waiting" with "on board 32", write "Cancel the job of robot R", with the number of the robot. Read the proposal. Press "Confirm #N".
4. Break the stone. Wait 15 seconds.

Expected:

- The proposal of the cancellation says: "The cargo stays on board the robot."
- After the button: "Job M on robot #R is cancelled. The cargo stays on board the robot." The job is "cancelled", with "on board 32".
- After the stone is gone, the robot does not move.
- No line in the chat.

Result:

## Checks during the whole session

| No. | Check | Expected | Result |
| --- | --- | --- | --- |
| C1 | Commands after the setup | None | |
| C2 | Language | Each sentence of Uno and each line of the screen is in English | |
| C3 | The field "Write to Uno" | It keeps your text while you write. The view of the screen changes each second | |
| C4 | Your own pause, resume and cancellation | No line in the chat | |
| C5 | The label "Confirm #N" | It fits the button, also with a number of two digits | |
| C6 | The counter | It grows by one for each written request. It does not grow for a button, a confirmation, a notice or a summary | |
| C7 | The times of Claude | Each answer arrives in less than 20 seconds. Record the slowest | |
| C8 | The log of the game | No error of MindCraft | |
| C9 | Each opening of the screen | After a line in the chat, the next opening gives a summary with the same sentence. Without a new event, Uno keeps its sentence | |

## If Claude fails

- The answer "I cannot interpret the request now (…)" is a failure of the provider. Record its text, with the step.
- If the first request gives such an answer with an error 400, the schema of increment A probably does not work with the real service (D29.1).
- Then close the client. Start it again from a shell without the key. Open the same world. Continue with the fixed rules.
- With the fixed rules the screen has no counter and no line about Claude. The points about the counter and Claude become "not applicable".

## After the session

These points belong to ticket #13.

- Each step and each check gets its result in [TEST_RESULTS.md](TEST_RESULTS.md), as a test in the game.
- Each difference from the expected result gets a decision: correct it now, or defer it with a reason.
- Each initial value of D38 gets a review: keep it or change it.
- [DECISIONS.md](DECISIONS.md) says whether the exit of increment A is reached (D27).

## Not part of this session

| Case | Reason |
| --- | --- |
| A summary with more than 10 events on the screen of a client | It needs more than 10 events between two openings. The automatic test `a_summary_shows_the_10_newest` builds it without a client |
| A notice to an owner who is not connected | In a single-player game the server stops when the player leaves. Step 11 shows the nearest case: the summary after a restart has an event from before the restart |
| A player who is not an operator | The setup needs commands, thus the player is an operator. The automatic tests `a_model_only_for_operators` and `a_screen_model_lines` build such a player |
| A long wait across a restart, and a real crash with a job of a player | They are not in the list of the spec. See "Not executed" in [TEST_RESULTS.md](TEST_RESULTS.md) |
