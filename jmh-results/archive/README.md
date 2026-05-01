These files were captured under a misconfigured EpsilonGC JMH profile (1x1s warmup +
2x1s measurement = 3s/param) that did not allow C2 to stabilise. The numbers are
measurement artifacts of an under-warmed JIT, not GC behaviour, and they are not
comparable to the 3x3s + 5x5s G1 baseline in `../phase-A1-g1.{txt,json}`. They are
retained here for the investigation record only; see
`C:\Users\BrianGraham\.claude\plans\epsilon-gc-investigation.md` for the full
analysis. Do not cite these numbers as a comparison baseline.
