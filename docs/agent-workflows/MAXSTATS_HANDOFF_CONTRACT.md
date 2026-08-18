# MAXSTATS R4 — Cross-Agent Handoff Contract

The three optimization lanes are intentionally independent. This contract prevents duplicate edits, hidden dependencies, and merge-by-last-writer.

## Ownership is authoritative

A lane owns only the domain assigned in `MAXSTATS_R4_ORCHESTRATION.md`.

If an agent discovers that the correct root-cause fix belongs to another lane, the discovering agent must:

1. preserve the evidence;
2. write a HANDOFF;
3. avoid editing the foreign-domain production file;
4. continue work that remains valid inside its own domain.

A workaround that crosses ownership merely to avoid a handoff is considered a regression risk.

## Required handoff block

Include this in every lane PR body and fill every field. Use `none` where genuinely not applicable.

```text
HANDOFF
Agent:
Lane:
Branch:
Pinned base SHA:
Current head SHA:
Owned production files changed:
Owned tests changed/added:
Foreign-domain dependency discovered:
Public/internal APIs changed:
Behavioral changes:
Five-pass pre-code findings:
Five-pass post-code findings:
Tests executed:
CI/build evidence:
Performance evidence:
Security/privacy implications:
Known risks:
Rebase/merge notes:
Deferred items:
Physical-device validation still required:
```

## Cross-lane dependency block

When another lane must act, add:

```text
CROSS-LANE HANDOFF
From lane:
To lane:
Root cause:
Evidence/source location:
Required behavior, not implementation guess:
Why it belongs to receiving lane:
Interface/contract needed:
What must remain unchanged:
Verification that will prove the handoff complete:
```

Specify behavior and evidence. Do not prescribe a brittle implementation unless the interface itself is the requirement.

## Shared API changes

If a lane introduces an API another lane may consume:

- keep the API minimal;
- document thread/lifecycle semantics;
- document null/error/failure behavior;
- add tests at the owner boundary;
- do not require consumers to know storage/UI/runtime implementation details;
- identify whether the API is stable enough for convergence or intentionally temporary.

## Conflict resolution

When two branches touch the same production file unexpectedly:

1. identify which lane owns the root behavior;
2. reject mechanical merge as the first solution;
3. preserve the owner lane's implementation;
4. re-express the other lane's requirement through a narrow owned interface if needed;
5. rerun both lanes' relevant tests and the convergence suite.

## Verification language

Use precise state labels:

- **observed in source** — source inspection only;
- **covered by test** — a relevant automated test exists and passes;
- **built in CI** — specified build completed successfully;
- **measured in emulator** — runtime evidence collected in emulator;
- **measured on physical device** — runtime evidence collected on named hardware;
- **not measured** — no valid measurement exists yet.

Do not convert "should improve" or "less work is executed" into a numerical performance claim.

## Convergence handoff

The final integrator records:

```text
CONVERGENCE
Accepted Pro/data head:
Accepted Syn/runtime head:
Accepted Flash/UI head:
Integration head:
Conflicts encountered:
Resolution by ownership:
Full CI result:
Emulator QA result:
Physical-device result:
Remaining known limitations:
MAXSTATS R4 disposition: ACCEPTED / ACCEPTED WITH DEFERRED PHYSICAL EVIDENCE / REJECTED
```
