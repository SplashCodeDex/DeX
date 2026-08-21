# Plan 002: Rotate and parameterize hardcoded certificate password

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `advisor-plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 6c8ae4a..HEAD -- core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `6c8ae4a`, 2026-08-21

## Why this matters

The `CertificateGenerator.kt` file currently secures the local SSL KeyStore with a hardcoded, universally known password (`PASSWORD = "dexpassword"`). This means the local certificate's private key is unprotected against local extraction. Generating a secure random string and storing it securely (or at least dynamically in preferences) protects the JKS file properly. We will implement a rotation mechanism: if loading the KeyStore fails, we assume the password changed or the file is old, delete the old KeyStore, and generate a new one with a new password.

## Current state

- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt` — generates and loads the KeyStore.

Excerpt of current state:
```kotlin
// core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt:8
const val PASSWORD = "dexpassword"

fun getOrCreateKeyStore(): KeyStore {
    if (!keyStoreFile.exists()) {
        generateKeyStore()
    }
    
    return keyStoreFile.inputStream().use {
        val ks = KeyStore.getInstance("JKS")
        ks.load(it, PASSWORD.toCharArray())
        ks
    }
}
```

## Commands you will need

| Purpose   | Command                  | Expected on success |
|-----------|--------------------------|---------------------|
| Compile   | `./gradlew :composeApp:desktopClasses` | exit 0              |

## Scope

**In scope**:
- `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt`

**Out of scope**:
- Implementing OS keychain integrations (too complex for this PR; local secure file or preferences is enough for now).

## Git workflow

- Branch: `advisor/002-fix-certificate-hardcoded-password`
- Commit message: `[fix] Remove hardcoded KeyStore password in CertificateGenerator`

## Steps

### Step 1: Replace hardcoded password with dynamically generated token

In `core/network/src/jvmMain/kotlin/com/dexstudios/dex/core/network/security/CertificateGenerator.kt`, remove `const val PASSWORD = "dexpassword"`.

Replace it with logic that reads a password from a local file (e.g., `File(System.getProperty("java.io.tmpdir"), ".dex_cert_pwd")`). If the file doesn't exist, generate a UUID string, save it to the file, and use it.

```kotlin
private val passwordFile = File(System.getProperty("java.io.tmpdir"), ".dex_cert_pwd")

private fun getPassword(): String {
    if (!passwordFile.exists()) {
        passwordFile.writeText(java.util.UUID.randomUUID().toString())
    }
    return passwordFile.readText()
}
```

### Step 2: Implement KeyStore recreation fallback

Update `getOrCreateKeyStore` to catch `java.io.IOException` and `java.security.KeyStoreException` during `ks.load()`. If it fails (e.g. because it was generated with the old "dexpassword"), delete the `keyStoreFile`, recreate the password, and run `generateKeyStore()` again.

Target shape:
```kotlin
fun getOrCreateKeyStore(): KeyStore {
    if (!keyStoreFile.exists()) {
        generateKeyStore(getPassword())
    }
    
    return try {
        keyStoreFile.inputStream().use {
            val ks = KeyStore.getInstance("JKS")
            ks.load(it, getPassword().toCharArray())
            ks
        }
    } catch (e: Exception) {
        // Password mismatch or corrupted keystore
        keyStoreFile.delete()
        passwordFile.delete()
        val newPassword = getPassword()
        generateKeyStore(newPassword)
        keyStoreFile.inputStream().use {
            val ks = KeyStore.getInstance("JKS")
            ks.load(it, newPassword.toCharArray())
            ks
        }
    }
}
```

Ensure `generateKeyStore(password: String)` uses the new password in its `ProcessBuilder` arguments.

**Verify**: `./gradlew :composeApp:desktopClasses` → `BUILD SUCCESSFUL`

## Test plan

- Build the application.
- Launch it; verify the internal server starts properly (this implicitly triggers `getOrCreateKeyStore`).

## Done criteria

- [ ] `./gradlew :composeApp:desktopClasses` exits 0.
- [ ] No files outside the in-scope list are modified (`git status`).
- [ ] `advisor-plans/README.md` status row updated.

## STOP conditions

Stop and report back (do not improvise) if:

- The `java.io.tmpdir` fallback logic proves to be problematic on a specific OS.
