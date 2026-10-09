# Required merge checks

The `CI` workflow runs for pull requests targeting `main` and for pushes to
`main`. Its `Merge gate` job succeeds only after backend tests, infrastructure
validation, the container image build, and pipeline verification all pass.

Backend verification runs the `quality` Maven profile and then validates its
reports. It requires at least 90% line coverage and 60% branch coverage, no
failed or skipped tests, no high-priority SpotBugs findings, and complete
analyzer reports without processing errors. Medium/low analyzer findings,
PMD warnings, and duplication are retained for review. Reports and a summary
are available on the workflow run; artifacts are retained for 14 days.

Pipeline verification checks workflow syntax with actionlint and exercises
quality-gate failure scenarios. Actions are pinned to verified commits;
Dependabot proposes updates weekly. The workflow also handles `merge_group`
events if a merge queue is enabled later.

To prevent unverified changes from merging, configure a GitHub ruleset for the
default branch (`main`) with these settings:

1. Require a pull request before merging.
2. Require status checks to pass before merging.
3. Select **Merge gate** from the `CI` workflow as the required status check.
4. Require branches to be up to date before merging (recommended).
5. Block force pushes and branch deletion.

GitHub branch rules are repository settings and cannot be enabled by a workflow
file alone. After the first run of this workflow, **Merge gate** will be
available in the required status check selector.
