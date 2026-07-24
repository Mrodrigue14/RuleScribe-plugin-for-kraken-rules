# RuleScribe — Vendor Security Overview

*A one-page summary for security and procurement reviewers. Every claim below is
verifiable from this public repository, except the one row explicitly marked as
a maintainer attestation; [SECURITY.md](SECURITY.md) gives the detail and the
exact commands.*

| | |
| --- | --- |
| **Product** | RuleScribe — IntelliJ IDEA plugin providing editor language support for the Kraken Rules DSL (`.rules` / KEL files) |
| **Type** | Editor-only IDE extension. Static parsing, highlighting, navigation and inspection of DSL text. |
| **Distribution** | JetBrains Marketplace (plugin ID 32804) and signed GitHub Releases |
| **License** | PolyForm Shield 1.0.0 |
| **Data collected** | **None.** No telemetry, no accounts, no network calls. |

## Data handling & privacy

- **No outbound network access, enforced by test.** The plugin makes no network
  calls of any kind — no telemetry, no phone-home, no downloads. A build-failing
  test (`KrakenNoNetworkEgressTest`) scans every shipped class and rejects any
  reference to a networking API. "No egress" is a checked property of each
  build, not a promise.
- **No data leaves the machine.** The plugin reads only the `.rules` files
  already open in the developer's project. It stores no data and transmits
  nothing.
- **No code execution.** It never executes rules, KEL expressions, or any
  user-supplied code — it only parses and inspects text statically.

## Attack surface

- **Zero third-party runtime dependencies.** The shipped artifact bundles no
  third-party libraries; all platform APIs are provided by the host IDE. A
  scoped OWASP scan reports **0 dependencies, 0 vulnerabilities**, and each
  release ships a **CycloneDX SBOM** so this is checkable by your own tooling.
- No bundled binaries, native code, or downloaded components.

## Control mapping

| Domain | Control | Evidence |
| --- | --- | --- |
| **Secure SDLC** | Protected `main`; every change via reviewed PR with required checks; **no maintainer bypass**; force-push and deletion blocked | Repository ruleset (empty bypass list) |
| **Publisher account security** | Two-factor authentication on the GitHub account — the single sign-in root for the JetBrains Marketplace publisher account and the store of all CI secrets, so one hardened account gates the whole chain | Maintainer attestation |
| **SAST** | CodeQL `security-and-quality` suite on push/PR/weekly | GitHub code scanning |
| **Software composition (SCA)** | OWASP Dependency-Check (build fails at CVSS ≥ 7.0); Dependabot alerts + updates; Dependency Review gates every PR | Actions logs, Security tab |
| **CI/CD pipeline security** | poutine (BoostSecurity) scans for pipeline misconfig; least-privilege tokens; actions pinned by commit SHA; no untrusted code execution in privileged contexts | `.github/workflows`, poutine results |
| **Build integrity (SLSA)** | Signed build-provenance attestation binds each artifact to its commit, workflow and runner | `gh attestation verify` |
| **Artifact authenticity** | Cryptographic plugin signature, re-verified against the publisher certificate before publishing; certificate published with each release | `marketplace-zip-signer verify` |
| **Transparency (SBOM)** | Attested CycloneDX SBOM per release, scoped to the shipped artifact | Release assets |
| **Binary compatibility** | IntelliJ Plugin Verifier across IDE versions | Verified 2024.1 → 2026.2 |
| **Vulnerability disclosure** | Private reporting via GitHub Security Advisories; acknowledgement within 7 days | [SECURITY.md](SECURITY.md) |

*Framework alignment: the above maps to NIST SSDF practices (PW.4, PW.7, PW.8,
PS.1–PS.3, RV.1) and SLSA build-provenance level, using GitHub Advanced Security
as the native toolchain.*

## Known limitations (stated plainly)

- The plugin is maintained on a **personal** publisher account; an out-of-band
  upload is not technically impossible, but it is made **detectable** — a daily
  release-integrity workflow flags any Marketplace version without a
  corresponding attested pipeline release.
- The signing certificate is **self-signed**: it proves integrity and
  publisher continuity, not an identity vouched for by a certificate authority.
- "0 vulnerabilities" reflects the scan date; the SBOM lets you re-scan any
  release against your own feed at any time.

## Contact

Security reports: GitHub Security Advisories (repository *Security* tab →
*Report a vulnerability*).
