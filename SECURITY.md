# Security Policy

## Supported versions

Only the latest released version of RuleScribe receives security fixes. The
plugin is distributed through the JetBrains Marketplace and as GitHub release
artifacts; always run the most recent build.

| Version | Supported |
| ------- | --------- |
| latest  | ✅        |
| older   | ❌        |

## Reporting a vulnerability

Please report suspected vulnerabilities **privately**, not through public
issues:

- Open a private advisory via GitHub Security Advisories (repository
  *Security* tab → *Report a vulnerability*).

Please include the affected version, a description of the issue and its
impact, and reproduction steps or a proof of concept if available. You can
expect an initial acknowledgement within **7 days**; once a fix is released,
credit is given to the reporter unless anonymity is requested.

## Why RuleScribe is low-risk by construction

RuleScribe is an editor-only language plugin. Its attack surface is
deliberately minimal:

- **Zero third-party runtime dependencies.** The shipped artifact bundles no
  third-party libraries. The only declared dependency (JUnit) is test-scoped
  and never distributed; the Kotlin standard library and all PSI/UI APIs are
  provided by the host IntelliJ Platform at runtime. This is verified
  continuously: the OWASP scan runs against the plugin's **shipped** runtime
  classpath and reports **0 dependencies, 0 vulnerabilities**. Your users are
  therefore never exposed to a transitive dependency vulnerability through
  RuleScribe.
- **No network access.** The plugin makes no outbound network calls of its
  own — no telemetry, no downloads, no phone-home. It only reads the `.rules`
  files already open in the project.
- **No code execution.** It statically parses and inspects DSL text. It never
  executes rules, KEL expressions, or any user-supplied code.
- **No access to credentials or secrets.** It reads only the `.rules` source
  files in the editor.

## Automated security & quality assurance

Every change is checked automatically in GitHub Actions:

| Tool | What it checks | When |
| ---- | -------------- | ---- |
| **CodeQL** | Static analysis (SAST) of the Kotlin/Java code, `security-and-quality` suite | push, PR, weekly |
| **poutine** (BoostSecurity) | CI/CD pipeline misconfigurations — injection, unsafe triggers, supply-chain | push, PR, weekly |
| **Dependabot** | Vulnerable dependencies (alerts) + dependency/action updates | continuous / weekly |
| **Dependency Review** | **Blocks a PR** that introduces a dependency with a high-severity CVE or a denied (copyleft) license | every PR |
| **OWASP Dependency-Check** | Known CVEs in shipped dependencies; **fails the build on CVSS ≥ 7.0** | weekly, on demand |
| **IntelliJ Plugin Verifier** | Binary compatibility across IntelliJ versions; opens an issue on breakage | weekly, on demand |

Findings from CodeQL and poutine are published to GitHub **code scanning**
(Security tab). The OWASP scan is scoped to the shipped runtime classpath;
build- and test-time dependencies (JUnit, the IntelliJ Platform SDK) are not
distributed and are out of scope — platform libraries are patched by JetBrains
through IDE updates.

These features are part of **GitHub Advanced Security** (free for public
repositories): code scanning (CodeQL), dependency review, Dependabot alerts,
and secret scanning with push protection. Together they cover software
composition analysis (vulnerabilities **and** license policy) and secret
leakage — the GitHub-native equivalent of a dedicated SCA product's core
checks. Deep artifact/registry scanning and runtime analysis are out of scope
for an editor plugin that ships no third-party binaries.

### OWASP Top 10 coverage

CodeQL runs the **`security-and-quality`** suite
(`.github/codeql/codeql-config.yml`), which is a **superset** of CodeQL's
security queries — the built-in suites are nested:

```
code-scanning (default)  ⊂  security-extended  ⊂  security-and-quality
```

An "OWASP Top 10" query suite is a *filter* selecting queries carrying the
corresponding CWE tags, so it is by construction a **subset** of what already
runs here. Adding it would duplicate findings rather than detect anything new.

Coverage is verifiable rather than asserted: every CodeQL alert carries its
`external/cwe/cwe-NNN` tag, so results can be filtered and reported by CWE —
and therefore mapped to OWASP Top 10 categories — directly in the
**Security → Code scanning** view.

## Supply-chain & CI/CD hardening

The build and release pipeline follows current supply-chain best practices:

- **Least-privilege workflows.** Each workflow declares the minimum
  `permissions` it needs; the repository's default token is read-only.
- **No untrusted code execution in privileged contexts.** Automation runs on
  `pull_request` (never `pull_request_target`), never checks out or runs a
  pull request's code with secrets available, and never interpolates untrusted
  input into shell commands — neutralising the "pwn request" class of attacks
  (independently scanned for by poutine).
- **Protected default branch, with no exemption for the maintainer.** Every
  change to `main` must go through a pull request whose required status checks
  (build & tests, CodeQL) pass. This is enforced by a repository ruleset whose
  **bypass list is empty** — direct pushes to `main` are rejected for everyone,
  including the repository owner. Force pushes and branch deletion are blocked,
  so history cannot be rewritten after the fact.
- **Hardened Dependabot auto-merge.** Only patch/minor updates auto-merge, and
  only after the required CI checks pass; the author is verified via the
  non-forgeable `pull_request.user.login` field.
- **Isolated publishing.** The JetBrains Marketplace token is exposed only to
  the tag-triggered publish job — never to pull-request workflows — so it
  cannot be exfiltrated by a malicious pull request. Publishing is gated: tests
  and plugin verification must pass before anything is released.

## Provenance & distribution

- Releases are built and published **only** by CI, from an explicit version
  **tag** — never from arbitrary commits.
- The published artifact is **cryptographically signed** (JetBrains plugin
  signing), and the pipeline **re-verifies the signature against the publisher
  certificate before publishing** — a malformed or mismatched key fails the
  release instead of shipping a worthless signature. This complements the build
  provenance attestation: the attestation proves *the pipeline built it*, the
  signature proves *it genuinely comes from this publisher*.
- Every upload is **re-verified server-side by JetBrains** on the Marketplace.
  Recent releases are verified **Compatible with IntelliJ IDEA 2024.1 through
  2026.2** (Plugin Verifier plus a real IDE run, no issues).
- Source, build configuration, and every CI/CD workflow are fully public and
  auditable in this repository.

## Release integrity — verify it yourself

Every release built by the pipeline is accompanied by a **signed SLSA build
provenance attestation** that cryptographically binds the distributed `.zip`
to the exact commit, workflow, and runner that produced it. You do not have to
trust that a release went through the security gates — you can **verify** it:

```
gh attestation verify rulescribe-<version>.zip \
  --repo Mrodrigue14/RuleScribe-plugin-for-kraken-rules
```

This works on the artifact downloaded from the **JetBrains Marketplace** as
well as from GitHub Releases. A build produced by the pipeline (which passes
tests, CodeQL, and OWASP before publishing) verifies successfully; an artifact
uploaded out-of-band — e.g. hand-uploaded, bypassing the checks — has no valid
attestation and **fails** verification.

As a candid disclaimer: because the plugin is maintained on a personal
Marketplace account, an out-of-band upload is not technically *impossible*.
It is instead made **detectable and unverifiable**:

- Each pipeline release leaves a public, auditable chain — git tag → CI run
  (tests + CodeQL + OWASP) → provenance attestation → OWASP report with its
  SHA-256 → JetBrains server-side verification.
- A scheduled **release-integrity** workflow
  (`.github/workflows/release-integrity.yml`) compares the latest Marketplace
  version against the pipeline's releases every day and opens an issue if a
  version appears without a corresponding attested release.

In short: don't take our word for it — **any published version is verifiable,
and any deviation is surfaced automatically.**
