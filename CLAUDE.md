# MindCraft

The vision and the status of the choices are in [docs/MINDCRAFT_CLAUDE_BRIEF.md](docs/MINDCRAFT_CLAUDE_BRIEF.md). Read it before you work.

- The repository is public. English is its base language: code, comments, documents, in-game text and commit messages. See D15 in [docs/DECISIONS.md](docs/DECISIONS.md).
- Speak with the project owner in Italian.
- Write short sentences. Use one responsibility for each sentence.
- Keep the states "proposed", "decided" and "verified" separate in [docs/DECISIONS.md](docs/DECISIONS.md).
- Always distinguish an automatic test, a test in the game and a code examination in [docs/TEST_RESULTS.md](docs/TEST_RESULTS.md).
- Use only `dan200.computercraft.api` of CC: Tweaked. The other classes are internal.
- The build needs a JDK 17. Set `JAVA_HOME` before you use `gradlew`. A portable JDK can be in `%USERPROFILE%\.jdks`.
- Compile with `gradlew compileJava`. Run the in-game tests with `gradlew runGameTestServer`.
- The normal run does not contain the server restart test. After a change of the runtime or of the Lua modules, also run its four phases. See `RestartTests.java`.
- The test `gradlew runGameTestServer -Plive` calls the real Claude service with the key of the project owner. It costs money. Run it only with the consent of the project owner.
- Never write an API key in a file, in a visible command or in a message.
- Keep the repository tidy: no local paths, no personal data and no generated files in a commit.
