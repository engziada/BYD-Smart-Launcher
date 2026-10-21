# GitHub Actions policy — reduce intermediate failures

**Effective after the CI workflow change merges to `main`.** This is a workflow-noise policy, **not** a way to conceal red builds or reduce the acceptance checks for releases.

## Why the old workflow generated repeated failure notifications

Previously `.github/workflows/flutter-test.yml` used both unscoped `push:` and `pull_request:`. Each change committed on a development branch started a complete Flutter + Kotlin + Android APK test, **even before a PR existed**. When the PR was open the same commit often caused **both** a push run and a PR run. During incremental implementation some half-finished commits failed temporarily; GitHub correctly notified the owner each time. E.g. [#185](https://github.com/engziada/BYD-Smart-Launcher/actions/runs/39600231920) failed at `Run native Android unit tests` on an intermediate commit, while [final main #204](https://github.com/engziada/BYD-Smart-Launcher/actions/runs/39602290756) passed.

## New trigger and release policy

| Trigger | Result |
| --- | --- |
| Any development-branch `push` without a PR | **No CI workflow** (avoid premature notifications). |
| Create a **draft** PR or push further intermediate changes to a draft | CI workflow may appear **skipped**, but the expensive Flutter/Android job does not run. |
| Mark the PR **Ready for review** after code changes are coherent and reviewed | **Full** Flutter analyze/tests + Kotlin Android unit tests + debug build + integrity/signature checks. |
| Push more changes to a **ready** PR | Full CI on latest revision; superseded in-progress PR checks are **cancelled** rather than left to finish as obsolete failures. |
| Commit/merge to `main` | Full required CI, and if the commit is `release(test-apk): ...`, publish a GitHub debug **prerelease** only after success. Main/release builds are **never cancelled** by PR concurrency. |
| GitHub `workflow_dispatch` | Authorized user can explicitly run the full suite when desired. |

## Implementation rules for future AI-agent coding work

1. Work on a named development branch, prefer grouped, coherent commits. Avoid publishing a separate commit for every small partial file change; write full slices and tests together.
2. If an unfinished slice needs a PR, create it as **draft**. Keep the PR in draft while implementation and local/offline review are incomplete.
3. Mark **Ready for review** only when the code is coherent and the static source audit is complete. Wait for full Flutter/Android CI and resolve all failures. A failed CI after Ready is a **real blocker** and must remain visible.
4. Merge into `main` **only after the latest PR commit succeeds**, and do not mark features accepted on vehicle until actual parked-car evidence exists.
5. Use the existing versioned debug APK and SHA256 verification; never claim that a skipped, cancelled or earlier successful run validates an untested later commit.

### Limits

- GitHub may still show **skipped** or **cancelled** runs in Actions history. These are not failing code builds.
- A genuine failure on a ready PR or on `main` still generates notifications; that is intentional.
- Personal email/mobile push preferences are controlled in GitHub **Settings → Notifications**, not by this repository workflow. Changing this project does not rewrite past failure notifications.
