# Play session of increment B — checklist

This is the checklist of the play session of increment B. It is the third part of the exit of the increment (D42, point 14). Ticket #29 of spec #15 is the session.
The session is a test in the game. The project owner plays it in the development client.
The session is not played yet. The project owner fills in the results. The results go to [TEST_RESULTS.md](TEST_RESULTS.md), and the differences go to [DECISIONS.md](DECISIONS.md).

- The setup is the only part with commands.
- After the setup, no step uses a command. The words go to Uno through the screen. The tablet and the projector open the screen.
- Each step gives the expected result and has a field "Result". Write "As expected", or what you saw instead.
- A difference is not a failure of the session. Record it. Decide about it after the session.
- The voice of Uno is the sentence of the model. It is different at each request. Thus a step gives the rules that the voice must follow, not its words. A fixed sentence of the server is given word for word.
- After the session, the results go to [TEST_RESULTS.md](TEST_RESULTS.md), section "Increment B — the play session", as a test in the game.

## Before the session

| No. | Point | Result |
| --- | --- | --- |
| B1 | The measurement of ticket #27 is recorded, and the project owner chose the model (D42, points 7 and 20). Write down the model. Until the choice, the model is `claude-haiku-5-5` | Model: |
| B2 | Ticket #27 ran `gradlew runGameTestServer -Plive` one time, with the consent of the project owner. CLAUDE.md asks for this run after a change of the system text of `ClaudeProvider` (tickets #18 and #20). Write down its run number | Run: |
| B3 | Write down the commit of the code of the session | |
| B4 | The key is in the environment of the process that starts the client, as `ANTHROPIC_API_KEY`. It is in no file, in no visible command and in no message. Do not write it in the chat of the game or in a request to Uno | |
| B5 | Each written request of this session is a paid call. The steps have about 15 requests. **Estimate, not a measurement:** each request has 2400 input tokens and 250 output tokens (D42, point 8). Then the session costs about 0.01 $ with Haiku 5.5, 0.11 $ with Sonnet 5.5 and 0.22 $ with Opus 5.5. The session is part of the budget of 5 $ for all paid runs of increment B. The counter on the screen shows the real tokens | |

## Setup — the only part with commands

The rig. Top view, north is up. Each character is one block at the height of the chests.

```
x:      0           1   2   3   4   5   6
z = 0   [depot]     D   .   .   .   D   [furnace]
z = 1               .
z = 2               .
z = 3               .
z = 4   [smelter]   D
```

- `[depot]`, `[furnace]` and `[smelter]`: three single chests. `D`: the dock of the chest next to it.
- `.` and `D`: the corridor. It has two blocks of air, thus a player can stand in it. The ground around the rig is flat and free.
- The two arms of the corridor have the same length. From the dock of the depot to each other dock is 4 moves.
- Thus a trip of each job of this session is 8: from one dock to the other and back (D42, point 11). The fuel is low below 5 trips: below 40.
- The robot starts with 80 fuel: 10 trips. The steps use 36 moves before step 6: then the level is 44. Thus the low fuel notice comes in step 6, with the level 39.

| No. | Do | Expected | Result |
| --- | --- | --- | --- |
| S1 | From a shell that has the key in its environment, start the development client: `gradlew runClient` | The title screen. No error of MindCraft in the log | |
| S2 | At the title screen, open the file `run/config/mindcraft-common.toml` with a text editor. If its model is not the model of B1, close the client. Write the model of B1 in the file, save it, and do S1 again | The file has three lines of comment and the line `model = "claude-haiku-5-5"`, or the model of B1. Forge wrote the file at the first start of the client | |
| S3 | Create a new world. Game mode Creative. Difficulty Peaceful: no monster walks into the corridor. "Allow Cheats" on. World type Superflat | You are an operator. The log has the line `Uno interprets requests with Claude (claude-haiku-5-5) for the operators, at most 100 requests for each of them in this session. The fixed rules read the requests of the other players.`, with the model of B1 | |
| S4 | Build the rig above. Put 48 coal in the depot chest. The furnace chest and the smelter chest stay empty | | |
| S5 | Place a turtle on the dock of the furnace. Open it with the right mouse button. Put one piece of charcoal in its first slot. Write `refuel` in its terminal | The terminal shows "Fuel level is 80". The inventory of the turtle is empty. Leave no item in it | |
| S6 | Register the three stations. Look at a block and press Tab: the command completes its coordinates.<br>`/mindcraft station add depot <x y z of the depot chest> <x y z of its dock>`<br>`/mindcraft station add furnace <x y z of the furnace chest> <x y z of its dock>`<br>`/mindcraft station add smelter <x y z of the smelter chest> <x y z of its dock>` | "Station 'depot' is registered.", "Station 'furnace' is registered." and "Station 'smelter' is registered." | |
| S7 | Enroll the turtle: `/mindcraft robot enroll <x y z of the turtle>` | "Robot #R is enrolled. It restarts now and works for Uno." Write down R. The terminal of the turtle shows "MindCraft: this turtle works for Uno." | R = |
| S8 | Keep the rig loaded: `/forceload add <x z of the smelter chest> <x z of the furnace chest>`. Then `/forceload query` | The query lists the chunks of the rig | |
| S9 | `/give @s mindcraft:tablet` and `/give @s mindcraft:projector`. Place the projector 3 blocks to the north of the corridor | A hologram above the projector, light blue | |
| S10 | Put in the hotbar: the tablet, 64 coal, and a stack of stone | | |
| S11 | Close the chat. From here on, use no command | | |

## Rules for the steps

- Open the screen with the tablet (right mouse button) or with a click on the projector. Each of the two is an "opening".
- Write in the field "Write to Uno". Press Enter or "Send".
- A proposal adds the button "Confirm #N". Press that button. The text of Uno says "Confirm with /uno confirm": do not use the command.
- Do not hit the robot or a chest: in creative mode one hit breaks the block.
- Some steps wait for game time. During a wait, do not open the menu with Esc. Keep the window of the game in focus. A single-player game pauses in both cases, and the paused time does not count.
- The sentences of the steps are written for Claude. The fixed rules also read each request that starts a job, and "How is the furnace?". They do not read the other questions, and not "Carry it to the smelter instead". You can also write in your own words, or in Italian. Record such a sentence and its answer.
- The fixed report of increment A starts with "Job J, robot #R:". The voice does not. If an answer of a report is the fixed report, the voice failed a check. Record it, with the line of the log (C7).
- Uno does not remember an earlier request. Each request must give what Uno needs, or name it so that the facts of your jobs give it.
- The fuel: the steps use the moves of the rig above. If the low fuel notice comes before step 6, record when. Then go on with the steps: step 6 records the notice that came.

## Steps — no command

### Step 1 — The voice on a report in two states

Do:

1. Use the tablet. Read the lines of the screen before you write.
2. Write: "Keep at least 32 coal in the furnace, taking them from the depot. Leave at least 32 in the depot." Read the proposal. Press "Confirm #N".
3. Wait until the job is "waiting" and the robot stands at the dock of the depot.
4. Write: "Why does the furnace get no more coal?" Compare the answer with the panel of the job.
5. Put 32 coal in the depot chest. Wait until the job is "stock at level".
6. Write: "How is the furnace?" Optionally, write the same question in Italian: "Come va la fornace?"

Expected:

- Before the request: "Uno › Tell me what you need.", "Your model requests in this session: 0 of 100. Tokens: 0 in, 0 out." and "Written requests go to Claude, a model of Anthropic, with the data of your jobs and the newest lines of my diary. No position leaves this server."
- The order of the screen: the sentence of Uno, the field, the buttons "Send" and "Report", the two lines above, then the jobs. Before the first job: "I have no record of a job of yours."
- The proposal: "Proposal: keep at least 32 x minecraft:coal in 'furnace', taken from 'depot'. Reserve to leave in 'depot': 32. Robot #R. I have done nothing yet. Confirm with /uno confirm." The robot holds nothing: the proposal has no sentence about a load.
- After the button: "Job J started on robot #R." The robot observes the furnace, takes 16 from the depot, delivers them, and goes back to the depot.
- At point 3: the depot has 32, the reserve. The furnace has 16. The job is "waiting", and the hologram is amber. No line in the chat.
- At point 4: an answer in the words of Uno, at once. It says why the furnace gets no coal: the depot is at its reserve. It can say how many are still to carry: 16. It does not start with "Job J, robot #R:". No proposal.
- At point 5: the robot takes 16 more and delivers them. The furnace has 32, the depot 48. The job is "stock at level", and the hologram is light blue. No line in the chat.
- At point 6: an answer in the words of Uno that agrees with the new state: the stock is at its level. In Italian too, the answer is in English.
- Each answer of Uno in its own words: at most two sentences and 300 characters. Each number in it is a number of the panel of the job, or of your request. It speaks in the first person. It describes no action that it did now.
- The counter grows by one for each written request.
- The log has one line for each written request, for example: `Uno request: model claude-haiku-5-5, … input tokens, … output tokens, … ms, action report, checks PASSED, request "Why does the furnace get no more coal?"`.

Result:

### Step 2 — A question that is not about a job

Do:

1. Write: "Which stations do I have?"
2. Write: "What did I ask you before?"

Expected:

- Point 1: one answer in the words of Uno. It names the depot, the furnace and the smelter. It does not start with "I cannot propose a job". No proposal. The log line has `action none, checks PASSED`.
- Point 2: Uno says that it does not remember an earlier request or sentence. It does not invent one. The log line has `action none` or `action report`: record which.
- If a check fails for a "none", the answer is "I cannot answer that. I did nothing." The log line then has `checks FAILED` and the sentence that Uno did not use. Record it.

Result:

### Step 3 — A job whose robot is not running

Do:

1. Open the turtle with the right mouse button. Hold Ctrl+S until its terminal goes off. Close the screen of the turtle. Do not use the right mouse button on the turtle again.
2. Use the tablet. Read the panel of the job.
3. Write: "How is the furnace?"
4. Open the turtle with the right mouse button. Close its screen. Wait 10 seconds.

Expected:

- Point 2: the panel has "The robot is not running. These data are not the current state." The job is still "stock at level".
- Point 3: the answer starts with a fixed sentence. The sentence: "Robot #R of job J is not running or not loaded. The last recorded data, not the current state: 0 on board, a fuel level of … (about … trips between the stations), 48 in 'depot' (seen … ago) and 32 in 'furnace' (seen … ago)."
- Then the voice follows. The voice does not give the stocks, the load or the fuel as the current state: the model did not get them.
- If the voice fails a check, the answer is the fixed report of the job. Its fuel sentence starts with "My last recorded fuel level is". It ends with "The robot is not running or not loaded. These are the last recorded data, not the current state." Record it.
- Point 4: the right mouse button turns the turtle on. Its terminal shows "MindCraft: this turtle works for Uno." The line of the panel about the robot goes away. The job stays "stock at level".
- No line in the chat.

Result:

### Step 4 — A replacement on the busy robot

Do: write "Carry 16 coal from the depot to the smelter." Read the proposal. Look at the world: nothing moves yet. Press "Confirm #N".

Expected:

- The robot has an open job. Thus the request is the proposal of a replacement. The proposal: "Proposal: cancel job J on robot #R (keep minecraft:coal in 'furnace', taken from 'depot'), and start in its place: carry 16 x minecraft:coal from 'depot' to 'smelter'. Reserve to leave in 'depot': 0. Robot #R. The robot holds no minecraft:coal now. The load can change until the confirmation: the new job counts the load that the robot holds then. I have done nothing yet. Confirm with /uno confirm."
- You named no robot. You have one robot, and it is busy: Uno uses that robot.
- Above the field: "Waiting for your confirmation: cancel job J on robot #R …", with the same text.
- Before the button: job J stays "stock at level", and the robot stays at the dock of the furnace.
- After the button: "Job J on robot #R is cancelled. Job K started on robot #R. The robot holds no minecraft:coal: the new job starts without a load."
- The panel shows job K. The robot takes 16 from the depot and delivers them to the smelter.
- No line in the chat for the cancellation of job J: it is your request.
- At the end, one line in the chat: "Uno › Job K on robot #R (minecraft:coal from 'depot' to 'smelter') is done: 16 delivered." The smelter has 16, the depot 32. The furnace keeps its 32.

Result:

### Step 5 — Change the destination with a load on board

Do:

1. Place a stone in the corridor at x 4, z 0: the block west of the dock of the furnace. The robot stands at the dock of the smelter.
2. Write "Carry 24 coal from the depot to the furnace." Press "Confirm #N".
3. The robot takes 24 from the depot and stops in front of the stone. Wait until the job is "waiting", with "on board 24".
4. Write: "Carry it to the smelter instead." Read the proposal. Press "Confirm #N".
5. When the robot is at the dock of the smelter, break the stone.

Expected:

- Point 2: "Proposal: carry 24 x minecraft:coal from 'depot' to 'furnace'. Reserve to leave in 'depot': 0. Robot #R. I have done nothing yet. Confirm with /uno confirm." Then "Job L started on robot #R."
- Point 3: about 10 seconds after the stop, the job is "waiting", with "on board 24". The panel shows "Recorded reason: the robot cannot move from x y z (east, in front: minecraft:stone) toward x y z: Movement obstructed". The hologram is amber.
- Point 4: Uno reads "it" from the facts of job L. The proposal: "Proposal: cancel job L on robot #R (carry minecraft:coal from 'depot' to 'furnace'), and start in its place: carry 24 x minecraft:coal from 'depot' to 'smelter'. Reserve to leave in 'depot': 0. Robot #R. The robot holds 24 x minecraft:coal: the job counts them as its first load. The load can change until the confirmation: the new job counts the load that the robot holds then. I have done nothing yet. Confirm with /uno confirm."
- If Claude gives a different quantity or reserve, record it. A quantity below 24 adds the sentence "That is more than the quantity: the job delivers at most …, then it stops with the rest on board for your decision."
- After the button: "Job L on robot #R is cancelled. Job M started on robot #R. It counts the 24 x minecraft:coal on board as its first load."
- The robot leaves the stone, goes back along the corridor and delivers the 24 to the smelter. It takes nothing more from the depot. If job M shows "waiting" for some seconds before the robot moves, record it: that is the limit of D26.
- No line in the chat for the cancellation of job L. At the end, one line: "Uno › Job M on robot #R (minecraft:coal from 'depot' to 'smelter') is done: 24 delivered."
- The smelter has 40, the depot 8, the robot 0.
- If Claude gives no start, or the proposal of a change, record the answer. Then write the whole request: "Carry 24 coal from the depot to the smelter."

Result:

### Step 6 — The low fuel notice, far from the rig

Do:

1. Fly at least 300 blocks away from the rig. F3 shows your position. The forced chunks keep the rig loaded.
2. Use the tablet. Write "Carry 8 coal from the depot to the furnace." Press "Confirm #N". Close the screen.
3. Do not open the screen. Read the chat. Then wait 2 more minutes.

Expected:

- The opening at point 2 can show a summary with the end of job M, if you did not open the screen after it.
- The proposal: "Proposal: carry 8 x minecraft:coal from 'depot' to 'furnace'. Reserve to leave in 'depot': 0. Robot #R. I have done nothing yet. Confirm with /uno confirm." After the button: "Job N started on robot #R."
- In less than a minute, one line in the chat: "Uno › Job N on robot #R (minecraft:coal from 'depot' to 'furnace') has little fuel: 39, about 4 trips between its stations." The level is the first level below 40. With other moves than this checklist, it can be a different number below 40.
- The notice proposes no remedy. It comes one time: the later moves of the robot give no second line.
- The job continues after the notice. Then one more line: "Uno › Job N on robot #R (minecraft:coal from 'depot' to 'furnace') is done: 8 delivered."

Result:

### Step 7 — Come back and read the summary

Do:

1. Fly back to the rig. Do not use the tablet.
2. Click the projector. Read the text of Uno and the panel of job N.
3. Write: "What happened while I was away?"
4. Write: "How much fuel does the robot have left?"

Expected:

- The text of Uno: "Since you last opened this screen:", then one line for each event, the oldest first:
  - "N min ago: Job N on robot #R (minecraft:coal from 'depot' to 'furnace') has little fuel: 39, about 4 trips between its stations."
  - "N min ago: Job N on robot #R (minecraft:coal from 'depot' to 'furnace') is done: 8 delivered."
- The ages agree with the time since the lines in the chat. The summary has the same sentences as the chat.
- Job N is "complete". Its panel has "Little fuel: 36, about 4 trips between the stations." The furnace has 40, the depot 0.
- Point 3: an answer in the words of Uno, from the diary. It can name the low fuel and the delivery of job N. It invents no event.
- Point 4: an answer in the words of Uno with the level 36 and about 4 trips.
- More than 5 minutes of game time can pass after the robot last saw a station. Then the answer of a report starts with a fixed sentence, for example "I last observed 'depot' 6 min ago: it had 0 then." Record it.

Result:

### Step 8 — The screen in a small window

Do:

1. Make the window of the game small: about 850 × 510 pixels, as in increment A (D40). Do not change the GUI scale.
2. Use the tablet. Write "Keep at least 32 coal in the furnace, taking them from the depot." Do not scroll. Read the screen.
3. Scroll down to the panel of job N. Then scroll up again.
4. Press "Confirm #N". Read the chat.

Expected:

- Point 2: without a scroll, you see from the top: the proposal of Uno, "Waiting for your confirmation: keep at least 32 x minecraft:coal in 'furnace', taken from 'depot'. Reserve to leave in 'depot': 0. Robot #R.", the field "Write to Uno", the buttons "Send", "Confirm #N" and "Report", the counter, and the line about Claude.
- The panels of the jobs come below these lines.
- Point 3: when you scroll down to a panel, the field leaves the view. This is the known cost of D42, point 13.
- If a part of the conversation is out of view without a scroll, record which part. Then the fixed conversation part of D42, point 13 opens again.
- Point 4: the answer is "Job P started on robot #R."
- At once the chat has one line: "Uno › Job P on robot #R (minecraft:coal from 'depot' to 'furnace') has little fuel: 36, about 4 trips between its stations." This is the notice at the start of a job with little fuel.
- The furnace has 40: the job is "stock at level" without a trip. Its panel has the line "Little fuel: 36, …". The hologram does not turn amber: a warning is not a state.

Result:

### Step 9 — The button "Cancel" and its confirmation

Do: keep the small window. In the panel of job P, press "Cancel". Read the screen. Look at the job. Then press "Confirm #N".

Expected:

- "Cancel" does not cancel at once (D41, decision 4). The text of Uno: "Proposal: cancel job P on robot #R (keep minecraft:coal in 'furnace', taken from 'depot'). The cargo stays on board the robot. I have done nothing yet. Confirm with /uno confirm."
- Above the field: "Waiting for your confirmation: cancel job P on robot #R (keep minecraft:coal in 'furnace', taken from 'depot'). The cargo stays on board the robot." The button "Confirm #N".
- Before the confirmation, job P stays "stock at level", with its fields and its buttons.
- After "Confirm #N": "Job P on robot #R is cancelled. The cargo stays on board the robot." The job is "cancelled", without fields and buttons.
- No line in the chat. The counter does not grow: a button is not a written request.
- At the end, make the window large again.

Result:

## Checks during the whole session

| No. | Check | Expected | Result |
| --- | --- | --- | --- |
| C1 | Commands after the setup | None. Ctrl+S in the terminal of the turtle is not a command | |
| C2 | Language | Each sentence of Uno and each line of the screen is in English, also after a request in Italian | |
| C3 | The voice | At most two sentences and 300 characters. Each number in digits is a number of the screen, of the record or of your request. First person: competent, curious and slightly ironic (brief 2.4). It says no action that did not occur. It does not use the names of the fields, of the modules or of the states | |
| C4 | Your own pause, resume and cancellations, also the cancellation of a replacement | No line in the chat | |
| C5 | The counter | It grows by one for each written request. It does not grow for a button, a confirmation, a notice or a summary | |
| C6 | The times of Claude | Each answer arrives in less than 20 seconds. Record the slowest | |
| C7 | The log of the game, `run/logs/latest.log` | One line `Uno request: model …` for each written request, with the model of B1, the tokens, the time, the action, the result of the checks and the text of the request. After a failed check, also the sentence that Uno did not use. No key and no coordinate in the line | |
| C8 | Errors | No error of MindCraft in the log | |
| C9 | The label "Confirm #N" | It fits the button, also with a number of two digits | |

## If Claude fails

- An answer that starts with "The model gave no valid answer (…), thus I read your request with the fixed rules: they understand only simple sentences." is a failure of the model (D42, point 17). Record its text, with the step. The rest of the answer comes from the fixed rules: read the proposal before you confirm.
- "(the model service does not know the model '…')": the setting is wrong. Close the client. Correct the file of S2. Start again.
- "(the API key is not valid or has no permission)": the key of B4.
- An error 400 at the first request: the service refuses a part of the request (D29.1). Then close the client. Start it again from a shell without the key. Open the same world. Continue with the fixed rules.
- With the fixed rules the screen has no counter and no line about Claude. The points about the voice, the counter and Claude become "not applicable".
- "I cannot answer that. I did nothing." and the fixed report after a question are not failures of the service: the voice failed a check (C7).

## After the session

These points belong to ticket #29.

- Each step and each check gets its result in [TEST_RESULTS.md](TEST_RESULTS.md), as a test in the game.
- Each sentence of the voice that can contradict the record gets a mark. The project owner decides about it (D42, point 20).
- Each difference from the expected result gets a decision: correct it now, or defer it with a reason.
- Each initial value of spec #15 gets a review: keep it or change it. The model, the length of a sentence, the diary entries in the context, the age of an old observation, the threshold of low fuel and the budget.
- [DECISIONS.md](DECISIONS.md) says whether the exit of increment B is reached (D42, point 14).

## Not part of this session

| Case | Reason |
| --- | --- |
| A player who is not an operator | The setup needs commands, thus the player is an operator. The automatic tests `a_model_only_for_operators`, `a_screen_model_lines` and `b_facts_in_the_context` build such a player |
| A failure of the real service: an error, the time limit or a refusal | No step causes one. The automatic tests `t18_provider_failures` and `b_model_of_the_setting` build them with providers of the tests and the stand-in of the service |
| A second low fuel notice after a refuel | It needs more moves than this session. The automatic test `b_low_fuel` builds it |
| A server where turtles need no fuel | It needs a change of the configuration of CC: Tweaked. No automatic test changes it: `b_low_fuel` gives the need of fuel as a parameter of the check |
| A replacement across a restart of the server | A proposal is not saved (D24) |
| The robot burns an item of another kind when its fuel ends | The fuel of this session does not end. The play session of increment A saw it once (step 7) |
| A summary with more than 10 events | It needs more than 10 events between two openings. The automatic test `a_summary_shows_the_10_newest` builds it without a client |
