# Dependabot Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable Dependabot alerts, security-update pull requests, scheduled updates for Gradle and GitHub Actions, and complete Gradle dependency submission.

**Architecture:** GitHub repository settings are enabled through the dedicated REST endpoints. Repository automation is split between a minimal Dependabot configuration and a least-privilege dependency-submission workflow that runs only on `main` pushes or manual dispatches.

**Tech Stack:** GitHub REST API, Dependabot v2 configuration, GitHub Actions, Gradle 8.11.1, JDK 17

---

### Task 1: Configure Dependabot version updates

**Files:**
- Create: `.github/dependabot.yml`

- [ ] **Step 1: Confirm the detected ecosystems**

Run:

```bash
git ls-files \
  'build.gradle.kts' \
  'app/build.gradle.kts' \
  'gradle/libs.versions.toml' \
  '.github/workflows/*.yml'
```

Expected: Gradle files and GitHub Actions workflows are listed, with no additional
package-manager manifests found by the repository scan in the design.

- [ ] **Step 2: Create the Dependabot configuration**

Create `.github/dependabot.yml` with:

```yaml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

- [ ] **Step 3: Parse and assert the configuration**

Run:

```bash
ruby -e '
require "yaml"
config = YAML.safe_load_file(".github/dependabot.yml")
abort "wrong version" unless config["version"] == 2
updates = config.fetch("updates")
actual = updates.map { |entry| [entry["package-ecosystem"], entry["directory"], entry.dig("schedule", "interval")] }
expected = [["gradle", "/", "weekly"], ["github-actions", "/", "weekly"]]
abort "wrong updates: #{actual.inspect}" unless actual == expected
'
```

Expected: exit status 0 with no output.

- [ ] **Step 4: Commit the Dependabot configuration**

```bash
git add .github/dependabot.yml
git commit -m "chore: configure Dependabot updates" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 2: Submit the resolved Gradle dependency graph

**Files:**
- Create: `.github/workflows/dependency-submission.yml`

- [ ] **Step 1: Create the dependency-submission workflow**

Create `.github/workflows/dependency-submission.yml` with:

```yaml
name: Dependency submission

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: write

concurrency:
  group: dependency-submission-${{ github.ref }}
  cancel-in-progress: true

jobs:
  submit:
    name: Submit Gradle dependency graph
    runs-on: ubuntu-latest
    timeout-minutes: 15

    steps:
      - name: Check out repository
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
        with:
          persist-credentials: false

      - name: Set up JDK 17
        uses: actions/setup-java@b6effb05e454b25005698d916606bdc6ffcbf961 # v5.7.0
        with:
          distribution: temurin
          java-version: "17"

      - name: Generate and submit dependency graph
        uses: gradle/actions/dependency-submission@9c971963bec38e04b3d30dcc455b5382be2fdbfb # v6.3.0
        with:
          cache-provider: basic
          dependency-graph: generate-and-submit
```

- [ ] **Step 2: Parse and assert the workflow**

Run:

```bash
ruby -e '
require "yaml"
workflow = YAML.safe_load_file(".github/workflows/dependency-submission.yml", aliases: true)
triggers = workflow["on"] || workflow[true]
abort "missing push trigger" unless triggers.dig("push", "branches") == ["main"]
abort "wrong permissions" unless workflow["permissions"] == {"contents" => "write"}
steps = workflow.dig("jobs", "submit", "steps")
action = steps.find { |step| step["name"] == "Generate and submit dependency graph" }
abort "missing dependency submission action" unless action
abort "action is not pinned" unless action["uses"].match?(/@[0-9a-f]{40}\z/)
abort "wrong graph mode" unless action.dig("with", "dependency-graph") == "generate-and-submit"
'
```

Expected: exit status 0 with no output.

- [ ] **Step 3: Commit the dependency-submission workflow**

```bash
git add .github/workflows/dependency-submission.yml
git commit -m "ci: submit Gradle dependency graph" \
  -m "Co-authored-by: Copilot App <223556219+Copilot@users.noreply.github.com>"
```

### Task 3: Enable and verify GitHub repository settings

**Files:**
- None

- [ ] **Step 1: Enable Dependabot alerts**

Run:

```bash
gh api --method PUT repos/mcasillas17/WallCrawl/vulnerability-alerts
```

Expected: HTTP 204.

- [ ] **Step 2: Enable Dependabot security updates**

Run:

```bash
gh api --method PUT repos/mcasillas17/WallCrawl/automated-security-fixes
```

Expected: HTTP 204.

- [ ] **Step 3: Verify both settings**

Run:

```bash
gh api --include repos/mcasillas17/WallCrawl/vulnerability-alerts
gh api repos/mcasillas17/WallCrawl \
  --jq '.security_and_analysis.dependabot_security_updates.status'
```

Expected: the first request returns HTTP 204 and the second prints `enabled`.

- [ ] **Step 4: Count open alerts by severity**

Run:

```bash
gh api 'repos/mcasillas17/WallCrawl/dependabot/alerts?state=open&per_page=100' \
  --paginate \
  --slurp |
  jq '[.[][] | .security_advisory.severity] |
    {
      critical: (map(select(. == "critical")) | length),
      high: (map(select(. == "high")) | length),
      medium: (map(select(. == "medium")) | length),
      low: (map(select(. == "low")) | length),
      total: length
    }'
```

Expected: a JSON object with explicit counts for `critical`, `high`, `medium`,
`low`, and `total`.

### Task 4: Validate and deliver repository changes

**Files:**
- Verify: `.github/dependabot.yml`
- Verify: `.github/workflows/dependency-submission.yml`

- [ ] **Step 1: Run repository checks**

Run:

```bash
./gradlew test lint assembleDebug --stacktrace --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Check whitespace and branch state**

Run:

```bash
git diff --check
git status --short --branch
```

Expected: no whitespace errors and a clean feature branch ahead of `main`.

- [ ] **Step 3: Push the feature branch**

Run:

```bash
git push --set-upstream origin mcasillas17-enable-dependabot
```

Expected: the remote branch is created or updated successfully.

- [ ] **Step 4: Open the pull request**

Run:

```bash
gh pr create \
  --base main \
  --head mcasillas17-enable-dependabot \
  --title "chore: enable Dependabot" \
  --body-file "$PR_BODY_FILE"
```

Use a temporary `PR_BODY_FILE` containing:

```markdown
## Summary

- configure weekly Dependabot updates for Gradle and GitHub Actions
- submit the resolved Gradle dependency graph on main-branch pushes
- document the Dependabot enablement design

## Testing

- `./gradlew test lint assembleDebug --stacktrace --no-daemon`
- parsed and asserted both YAML files locally
```

Expected: GitHub returns the URL of a new, unmerged pull request.

- [ ] **Step 5: Verify the pull request state**

Run:

```bash
gh pr view --json number,state,isDraft,mergeStateStatus,url
```

Expected: `state` is `OPEN`. Do not merge the pull request.
