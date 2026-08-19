# Line markers leak across CodeInsightContext changes — permanent duplicate gutter icons

> Draft YouTrack issue. Suggested project: **IJPL** (IntelliJ Platform) — the defect is in
> `platform/lang-impl`; observed in CLion Nova. Affected build: **261.26222.59** (CLion 2026.1.4).

## Summary

`LineMarkersUtil.setLineMarkersToEditor` skips highlighters created under a different
`CodeInsightContext` when populating its recycler (shared-source filtering). When the
context of a file *changes* — which routinely happens a few seconds after project open,
while the workspace model is still settling — the next `LineMarkersPass` runs under the
new context, cannot recycle the previous generation of line markers, and adds a fresh
generation next to it. The old generation is never disposed by anyone afterwards: it is a
permanent orphan. The user sees two identical gutter icons on the same line; editing the
declaration removes only the live copy, the orphan stays forever.

## Environment

- CLion 2026.1.4 (build CL-261.26222.59), CLion Nova engine, shared-source support enabled
- Remote Development: Gateway → Linux host (`remote-dev-server`), JetBrains Client on macOS
- Third-party plugin contributing a `RunLineMarkerContributor` for C++ files
  (run markers for the Yandex `Y_UNIT_TEST` framework)
- Large C++ monorepo (ydb), file contexts resolve asynchronously after project open

Note on why this is not noticed in first-party dogfooding: in CLion Nova all bundled C++
test frameworks produce their gutter marks inside the ReSharper engine, so in Nova C++
files a third-party `RunLineMarkerContributor` is effectively the **only** producer on the
JVM `LineMarkersPass` pipeline. The same mechanics should affect any context-blind line
marker producer in any product where shared-source support is enabled (e.g. Kotlin
run/test markers in KMP common sources during a Gradle sync race — worth checking, it is
the same `RunLineMarkerContributor` code path).

## Steps to reproduce

1. CLion Nova + Remote Development, a project where `isSharedSourceSupportEnabled(project)`
   is true and file contexts change shortly after open (large workspace model / compdb).
2. Install any plugin with a `RunLineMarkerContributor` that returns an `Info` for some
   token in a C++ file (one leaf element per declaration).
3. Open the project so that a file with such markers is the active (visible) editor.
4. Watch the gutter: one icon appears immediately (first highlighting pass, initial
   context), a second identical icon appears a few seconds later (pass re-run after the
   context settled).

Files opened *after* the contexts have settled show a single icon — only editors
highlighted inside the context-churn window are affected.

## What we measured (all data from a live session, instrumented via `MarkupModelListener`)

**Both generations are added by the standard batch pipeline** — identical stacks:

```
at com.intellij.openapi.editor.impl.MarkupModelImpl.fireAfterAdded(MarkupModelImpl.java:276)
at com.intellij.openapi.editor.impl.MarkupModelImpl.addRangeHighlighter(MarkupModelImpl.java:160)
at com.intellij.openapi.editor.impl.MarkupModelImpl.addRangeHighlighterAndChangeAttributes(MarkupModelImpl.java:151)
at com.intellij.codeInsight.daemon.impl.LineMarkersUtil.createOrReuseLineMarker(LineMarkersUtil.java:113)
at com.intellij.codeInsight.daemon.impl.LineMarkersUtil.setLineMarkersToEditor(LineMarkersUtil.java:85)
at com.intellij.codeInsight.daemon.impl.LineMarkersPass.doCollectInformation(LineMarkersPass.java:92)
at com.intellij.codeHighlighting.TextEditorHighlightingPass.collectInformation(TextEditorHighlightingPass.java:76)
at com.intellij.codeInsight.daemon.impl.PassExecutorService$ScheduledPass.lambda$doRun$2(PassExecutorService.java:449)
```

**Timeline** (12-test file, active editor at project open):

| time | event |
|---|---|
| 22:19:17.8 | batch pass adds 11 markers (generation 1) |
| 22:19:29.0 | batch pass adds the same 11 markers again (generation 2) — **zero `beforeRemoved` events in between** |
| 22:19:34.1 | one incremental add (`addLineMarkerToEditorIncrementally`, LineMarkersPass.java:126) for a declaration edited meanwhile |

**Producer is not at fault:**

- the contributor's `getInfo` is invoked exactly once per element per pass (12 calls per
  pass for 12 tests, logged), so collection is single;
- every call runs under the **same ClientId** (single guest session) on the same thread
  pool — this is not per-client duplication;
- `DumbService.isDumb == false` throughout — not a dumb-mode artifact.

**Resulting markup state** (from `DaemonCodeAnalyzerImpl.getLineMarkers`): 24 markers for
12 declarations; per line two `RunLineMarkerProvider$RunLineMarkerInfo` anchored to the
same leaf element offset with identical tooltips, but **distinct `LineMarkerInfo`
instances and distinct valid `RangeHighlighter`s**.

**Orphan proof:** breaking the declaration (so `getInfo` returns null) removes exactly one
icon — the live generation. The other icon survives any edits: no pass ever recycles or
disposes it.

## Root cause

`LineMarkersUtil.setLineMarkersToEditor` (branch 261.26222, context filter at ~lines
62–66) excludes highlighters from recycling when their stored `CodeInsightContext`
differs from the highlighting session's context (and is not `anyContext`):

```java
CodeInsightContext highlighterContext = isSharedSourceSupportEnabled(project) ?
  CodeInsightContextHighlightingUtil.getCodeInsightContext(highlighter) : null;
if (highlighterContext != null &&
    highlighterContext != CodeInsightContexts.anyContext() &&
    !highlightingSession.getCodeInsightContext().equals(highlighterContext)) {
  return true;  // skip: neither reused nor disposed
}
```

Keeping foreign-context markers alive is presumably intentional for files opened in
several *live* contexts simultaneously. However when a context is **replaced** (the
startup churn case), the markers tagged with the obsolete context are skipped by every
subsequent pass and are never cleaned up. Since generation 1 demonstrably survived
generation 2's pass, generation 1 was tagged with a concrete context (an `anyContext` tag
would have been recycled by the predicate above).

## Expected behavior

When a file's context set changes, markers belonging to contexts that no longer apply to
the file must be recycled or disposed. `setLineMarkersToEditor` has both the session
context and (via `CodeInsightContextManager`) the file's live contexts at hand, so
"foreign context" could be split into "still-live foreign context" (keep) vs "obsolete
context" (incinerate).

## Actual behavior

Every context change while an editor is open leaks one full generation of that file's
line markers; the leaked generation renders as duplicate gutter icons (and is invisible
memory/work overhead for artifact types that overlap visually).

## Workaround used by our plugin

A `MarkupModelListener` that, when a new run marker is added, removes older highlighters
with the same range and tooltip (post-factum deduplication). It self-neutralizes once the
platform recycles cross-context markers properly.

## Complementary API suggestion

Many third-party markers are inherently context-invariant (ours are computed from the
document text and are identical in every context of the file). The recycling predicate
already special-cases `CodeInsightContexts.anyContext()` — such markers are recycled by
any session. However a producer currently has no way to opt into that: the highlighter is
always stamped with the session's concrete context. Letting `LineMarkerInfo` (or the
contributor) declare "context-agnostic" — so the highlighter is stamped with `anyContext`
— would make context-blind producers correct by construction, independently of the
cleanup fix above.
