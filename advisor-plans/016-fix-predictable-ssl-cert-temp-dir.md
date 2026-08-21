# Plan 016: Fix predictable SSL certificate temp directory

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 7f8c1de..HEAD -- core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `7f8c1de`, 2026-08-21

## Why this matters

The system currently stores the generated SSL KeyStore and its password in `java.io.tmpdir`. This directory is shared among all users on the host machine. A malicious actor with access to the machine could read the private key and password, enabling them to impersonate the local server or decrypt local traffic. Moving these sensitive files to a user-private application data directory secures them against unauthorized local access.

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt` — generates and stores the SSL certificate.

Excerpt from `CertificateGenerator.kt:6-8`:
```kotlin
object CertificateGenerator {
    private val keyStoreFile = File(System.getProperty("java.io.tmpdir"), "dex_cert.jks")
    private val passwordFile = File(System.getProperty("java.io.tmpdir"), ".dex_cert_pwd")
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Build   | `./gradlew :composeApp:desktopJar`           | exit 0              |
| Tests     | `./gradlew :composeApp:desktopTest`  | all pass            |

## Scope

**In scope**:
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt`

**Out of scope**:
- Modifications to how the certificate is actually generated or the keytool commands.

## Git workflow

- Branch: `advisor/016-fix-predictable-ssl-cert-temp-dir`
- Commit message: `[fix] Move SSL certs to user-private app data directory`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Update certificate storage location

Modify `CertificateGenerator.kt` to store the certificates in a user-private directory like `~/.dex/security` instead of the public temp directory. Ensure the directory is created if it does not exist.

Replace lines 6-8 in `CertificateGenerator.kt`:
```kotlin
object CertificateGenerator {
    private val appDataDir = File(System.getProperty("user.home"), ".dex/security").apply { mkdirs() }
    private val keyStoreFile = File(appDataDir, "dex_cert.jks")
    private val passwordFile = File(appDataDir, ".dex_cert_pwd")
```

**Verify**: `./gradlew :composeApp:desktopJar` → exit 0

## Test plan

- Verification: `./gradlew :composeApp:desktopTest` → all pass.

## Done criteria

- [ ] `./gradlew :composeApp:desktopJar` exits 0
- [ ] `./gradlew :composeApp:desktopTest` exits 0
- [ ] No files outside the in-scope list are modified (`git status`)
- [ ] `advisor-plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:
- The code at the locations in "Current state" doesn't match the excerpts.
- A step's verification fails twice after a reasonable fix attempt.

## Maintenance notes

- If uninstallation scripts are ever added, they should clean up the `~/.dex/security` directory to remove lingering keys.
