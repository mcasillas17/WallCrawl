# Dependabot Enablement Design

## Goal

Enable GitHub Dependabot alerts and security-update pull requests, configure
version updates for every package ecosystem in the repository, and ensure
GitHub receives the complete resolved Gradle dependency graph.

## Detected ecosystems

- Gradle manifests and a version catalog at the repository root.
- GitHub Actions workflows under `/.github/workflows`.

No other supported package-manager manifests are tracked.

## Repository settings

Enable vulnerability alerts and automated security fixes through GitHub's
dedicated repository REST endpoints. Verify both settings after mutation:

- The vulnerability-alerts endpoint must return an enabled status.
- `security_and_analysis.dependabot_security_updates.status` must be `enabled`.

After alerts are enabled, query all open alerts and report counts grouped by
severity. Do not dismiss alerts or merge any pull request.

## Dependabot version updates

Create `.github/dependabot.yml` with version 2 syntax and one weekly update
entry for each detected ecosystem:

- `gradle` in `/`
- `github-actions` in `/`

Weekly checks balance timely updates with manageable pull-request volume. The
configuration will avoid broad ignore rules so security updates remain
eligible.

## Dependency graph coverage

The repository SBOM endpoint currently returns `404`, so GitHub cannot expose a
complete dependency graph. Add a dedicated workflow that runs on pushes to
`main` and can also be dispatched manually. It will:

1. Check out the repository without persisting credentials.
2. Configure JDK 17, matching the existing build.
3. Run Gradle's dependency-submission action to resolve and submit the graph.

The job receives only `contents: write`, which GitHub's dependency submission
API requires. Actions are pinned to immutable commits, following existing
workflow conventions.

## Validation and delivery

Validate the Dependabot YAML and workflow YAML, run the repository's existing
Gradle verification task, verify the GitHub settings and alert counts through
the API, then commit and push repository-file changes. Open a pull request
without merging it.
