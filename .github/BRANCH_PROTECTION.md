# Required merge checks

The `CI` workflow runs for pull requests targeting `main` and for pushes to
`main`. Its `Merge gate` job succeeds only after backend tests, infrastructure
validation, and the container image build all pass.

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
