# DeX v3.0 Trust Architecture

This document outlines the target "gold standard" architecture for modern, secure device-to-device trust in future versions of DeX. It replaces the legacy JSON/TCP/Token approach with proven cryptographic protocols and primitives, aligning with industry standards like Matter, Apple HAP, and Bluetooth Secure Simple Pairing.

## Core Philosophy
Move trust enforcement from the **application layer** (custom JSON `isTrusted` flags, HTTP endpoints, email hashes) to the **transport and cryptographic layers** (mTLS, PAKE, Hardware Keystores).

---

## 1. Pairing Layer (SPAKE2+ & SAS)
- **Current Vulnerability**: The system transmits a raw 6-digit PIN over the LAN. Even with rate limiting, it's theoretically vulnerable to brute-force or MITM if the channel is unencrypted.
- **v3 Architecture**: 
  - Implement **SPAKE2+** (or CPace) Password-Authenticated Key Exchange.
  - The PIN (Short Authentication String / SAS) is generated but **never transmitted** over the network.
  - Both devices independently use the PIN to mathematically derive the exact same strong session key.
  - If a Man-in-the-Middle intercepts the handshake, the cryptographic math fails, and the keys will not match.

## 2. Transport Layer (mTLS)
- **Current Vulnerability**: Relying on string-based `Fingerprints` to authenticate connections, which requires manual validation in every HTTP route and WebSocket message.
- **v3 Architecture**: 
  - All local and remote P2P channels (HTTP REST, WebSockets, raw TCP streams) are wrapped in **TLS 1.3**.
  - Implement **mTLS (Mutual TLS)** for device-to-device authentication.
  - During the SPAKE2+ pairing phase, devices exchange self-signed X.509 public certificates.
  - Subsequent connections require the client to present its specific certificate. 
  - Unauthenticated endpoints vanish—if a device doesn't have a valid paired certificate, the TLS handshake fails at the OS/Socket level before the application ever sees the request.

## 3. Trust Storage & Revocation (Hardware Keystore)
- **Current Vulnerability**: Pair tokens are stored in static JSON configuration files in user-space directories, vulnerable to extraction via root access or local privilege escalation.
- **v3 Architecture**:
  - The private keys used for mTLS certificates are generated and securely stored within the **Android Keystore** (TEE/Secure Enclave) and Windows **TPM / DPAPI**.
  - Keys are marked non-exportable.
  - **Trust Revocation**: Revoking trust (Unpairing) simply means deleting the peer's public certificate from the local trust store and sending a signed revocation control message. The untrusted peer instantly loses the ability to establish a TLS connection.

## 4. Identity & Auto-Trust Layer (OIDC)
- **Current Vulnerability**: Using a static SHA-256 hash of the user's email address (`IdentityHash`) to automatically pair devices belonging to the same user.
- **v3 Architecture**:
  - Integrate true **OAuth 2.0 + OpenID Connect (OIDC)** via Google Sign-In.
  - Devices exchange short-lived, cryptographically signed ID Tokens (JWTs).
  - Devices are linked and auto-trusted based on the signed `sub` (Subject) claim directly validated against Google's JWKS (JSON Web Key Set), rather than trusting a plain-text hash broadcasted over UDP.

## 5. Resilient Control Messages
- **Current Vulnerability**: Custom HTTP endpoints like `/local/unpair` that require manual Loopback restrictions or state-tracking to prevent infinite loops.
- **v3 Architecture**:
  - Every control message over the WebSocket includes:
    - `type` (Operation)
    - `sequence_number` (Replay protection)
    - `signature` (Authenticated HMAC via session key)
    - `idempotency_key` (Prevents infinite loops and duplicate processing)
  - Standardized RFC 6455 Ping/Pong frames handle zombie socket detection, rather than custom application-level heartbeat payloads.

---

## Migration Path
Moving to v3 requires a fundamental rewrite of the networking stack on both Android and C# engines:
1. **Dependency Upgrade**: Introduce BouncyCastle (C#) and Tink (Android) for PAKE/SPAKE2+ math.
2. **TLS Infrastructure**: Rework `TcpListener` and `Ktor` to mandate SSL contexts with custom Trust Managers that validate against the known-peers certificate store.
3. **Deprecation**: Remove legacy `/local/` unsecured endpoints, `PendingPairPins` memory maps, and custom rate-limiters, as mTLS handles connection rejection natively.
