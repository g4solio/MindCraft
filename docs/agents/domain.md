# Domain Docs

How the engineering skills use the domain documents of this repository.

This repository has one context. It does not use `GLOSSARY.md` or `docs/adr/`. Do not create them.

## Before exploring, read these

- **`docs/MINDCRAFT_CLAUDE_BRIEF.md`**: the vision. Section 5 defines the vocabulary.
- **`docs/DECISIONS.md`**: the decision log, with entries `D01`, `D02` and so on. It takes the place of the ADRs. Read the entries that touch your area. D27 is the plan of the increments.
- **`docs/TEST_RESULTS.md`**: what is verified, and how.

## Use the vocabulary of the project

When your output names a domain concept, use the term of the brief and of the decision log. Do not use a synonym.
If a concept has no term, do not invent one silently. Say so to the project owner.

## Record a decision

When a skill says to write an ADR or to update the glossary, add or amend an entry in `docs/DECISIONS.md`.
Keep the states "proposed", "decided" and "verified" separate.

## Flag a conflict with a decision

If your output contradicts an entry, say so explicitly:

> _Contradicts D13 (no chunk loading), but worth reopening because…_
