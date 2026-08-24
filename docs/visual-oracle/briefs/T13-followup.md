# T13 follow-up: settling must survive GTK theme transitions

Your T13 commit is rebased onto the merged T04 capture runtime, and the combination fails:

```
CHECK 13 catalog-discovered-and-wellformed: PASS
CHECK 14 catalog-triple-render-deterministic: FAIL
      java.lang.AssertionError: button.toggle.selected rendered differently on capture 2; it is not deterministic
SELFTEST-FAILED: 1 of 15 checks failed
```

Nothing is wrong with your work in isolation and nothing is wrong with T04's in isolation.
You measured against the old skeleton capture with its fixed 150 ms settle; T04 replaced settling with an event-driven drain that waits for two consecutive quiet cycles with no paint, resize or move activity.
For GTK CSS transitions that condition is not sufficient: the theme animates a state change on the frame clock, so the event queue can go quiet while the pixels are still changing.

Your own T13 handoff already measured this class of problem and put the stable point between 250 ms and 400 ms, and you dropped three specimens because of it (`button.radio.selected`, `button.check.selected`, `button.push.disabled`) and filed SCR-1.
`button.toggle.selected` survived under the old settle and does not survive under the new one, which means the boundary moved rather than the problem being new.

## What to do

Fix the cause, in the capture runtime, not by dropping another specimen.

`impl/CaptureRuntime` is now yours to change for this purpose; T04 is merged and its agent is finished.
The robust approach is to make capture inherently stable rather than to guess a delay: capture, capture again, compare, and repeat until two consecutive captures are byte-identical, with a bounded number of attempts and a clear failure when it never settles.
That is deterministic by construction, needs no magic number, and costs nothing for the overwhelming majority of specimens that are already stable on the first pair.

Whatever you choose, the reason it is sufficient must be stated in the code, as T04 did for its own condition.

## Then restore what was dropped for this reason

SCR-1 records the restoration recipe. With settling fixed, re-add the three specimens dropped for transition animation and prove they now pass the triple-render check:

- `button.radio.selected`
- `button.check.selected`
- `button.push.disabled`

If any of them still cannot be made deterministic, keep it dropped and say precisely why, with the measurement.
Do not re-add a specimen you cannot prove stable.

`clabel.disabled` stays dropped for a different and entirely valid reason (pixel-identical to `clabel.default` because CLabel never consults its enabled state), so leave that decision alone.

## Check your own work

```bash
tools/oracle/build-harness.sh && tools/oracle/oracle selftest
```

All 15 checks must pass, with the restored specimens included in CHECK 14's iteration.
Do not weaken CHECK 14, do not special-case a specimen inside it, and do not raise a tolerance to make a comparison succeed.
The determinism check is the foundation the entire project rests on: a false pass there makes every later comparison meaningless.

## Rules

Scratch in `/tmp/opencode/oracle-t13/` only. Never read PNG files into your context; use ImageMagick from the shell and quote the numbers. Commit early and amend. Do not push.

Amend your existing single T13 commit rather than adding a second one, and update your handoff record in `docs/visual-oracle/TRACKING.md` to reflect the settling fix and the restored specimens, including replacing the SCR-1 paragraph with what actually happened.
