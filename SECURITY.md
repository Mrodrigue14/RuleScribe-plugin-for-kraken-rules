# Security Policy

## Supported versions

Only the latest released version of RuleScribe receives security fixes.
The plugin is distributed through the JetBrains Marketplace and as GitHub
release artifacts; always run the most recent build.

| Version | Supported |
| ------- | --------- |
| latest  | ✅        |
| older   | ❌        |

## Reporting a vulnerability

Please report suspected vulnerabilities **privately**, not through public
issues:

1. Preferred: open a private advisory via GitHub Security Advisories
   (repository *Security* tab → *Report a vulnerability*).

Please include:

- the affected version,
- a description of the issue and its impact,
- reproduction steps or a proof of concept if available.

You can expect an initial acknowledgement within **7 days**. Once a fix is
released, credit will be given to the reporter unless anonymity is
requested.

## Security posture

RuleScribe is intentionally low-risk by construction:

- **No third-party runtime dependencies are bundled.** The plugin declares
  only a single test-scoped dependency (JUnit); the Kotlin standard library
  and all UI/PSI APIs are provided by the host IntelliJ Platform at runtime.
- **No network access.** The plugin performs no outbound network calls; it
  only reads and analyzes `.rules` files in the open project.
- **No code execution.** It parses and inspects DSL text; it does not
  execute rules or any user-supplied code.

## Automated scanning

- **CodeQL** static analysis (`java-kotlin`) for code-level vulnerabilities, on
  every push and pull request to `main` and on a weekly schedule
  (`.github/workflows/codeql.yml`).
- **Dependabot** flags vulnerable Gradle dependencies and keeps GitHub Actions
  up to date (`.github/dependabot.yml`), continuously and on GitHub's
  infrastructure.
- **OWASP Dependency-Check** against the NVD database, scoped to the plugin's
  **shipped** runtime classpath; the scan fails on any bundled dependency with
  a CVSS score ≥ 7.0. It runs **weekly and on demand**
  (`.github/workflows/dependency-check.yml`), not on every push, since building
  the NVD 2.0 database is slow and the shipped artifact carries no third-party
  dependencies. Build- and test-time dependencies (JUnit and the IntelliJ
  Platform SDK) are **not** distributed in the plugin and are out of scope;
  platform libraries are patched by JetBrains through IDE updates.
