---
spec: protocol.spec.md
---

## User Stories

- As an implementer, I can produce and consume the same authenticated AlgoChat v1 envelope as every supported language.
- As an application developer, I can use structured replies while continuing to read legacy plaintext payloads.

## Acceptance Criteria

1. **REQ-protocol-001**: Envelope encoding and decoding preserve every field and reject short or unsupported input; `EnvelopeTest` covers round trips, recognition, equality, and invalid headers.
2. **REQ-protocol-002**: Seed-derived and ephemeral X25519 keys produce shared secrets suitable for v1 encryption; `AlgoChatTest` and `CrossImplTest` exercise key interoperability.
3. **REQ-protocol-003**: Authenticated encryption round-trips for sender and recipient and rejects the wrong identity or tampering; `AlgoChatTest` and `CrossImplTest` provide evidence.
4. **REQ-protocol-004**: Structured text/reply payloads round-trip while legacy UTF-8 remains readable; `MessagePayloadTest` covers JSON, reply, legacy, malformed, and key-publish cases.
5. **REQ-protocol-005**: Encryption-key signatures verify only for the correct key and signer, and fingerprints are deterministic; `SignatureTest` covers valid and invalid signatures and fingerprint formatting.

## Constraints

- Wire constants and field order are cross-language compatibility contracts.
- Cryptographic authentication failures must never return partial plaintext.

## Out of Scope

- Blockchain transaction transport and persistence, owned by `client`.
- Pre-shared-key ratcheting and envelope id 2, owned by `psk`.

### REQ-protocol-001

The v1 envelope codec SHALL preserve every fixed-width field and reject short or unsupported headers.

Acceptance Criteria
- Existing round-trip, recognition, equality, short-input, and header-version cases pass in `EnvelopeTest`.

### REQ-protocol-002

Key operations SHALL derive deterministic seed identities, fresh ephemeral identities, matching X25519 secrets, and reversible public-key bytes.

Acceptance Criteria
- Existing key and interoperability cases pass in `AlgoChatTest` and `CrossImplTest`.

### REQ-protocol-003

Authenticated encryption SHALL decrypt for the intended sender and recipient and reject unrelated identities or modified ciphertext.

Acceptance Criteria
- Existing sender, recipient, wrong-key, and tampering cases pass in `AlgoChatTest` and `CrossImplTest`.

### REQ-protocol-004

Message payload decoding SHALL round-trip structured replies and retain backward-compatible UTF-8 plaintext decoding.

Acceptance Criteria
- Existing JSON, reply, legacy, malformed-JSON, and key-publish cases pass in `MessagePayloadTest`.

### REQ-protocol-005

Encryption-key signatures SHALL verify only for the correct key and Ed25519 signer, and fingerprints SHALL be deterministic SHA-256 hexadecimal.

Acceptance Criteria
- Existing valid, invalid, wrong-key, and fingerprint cases pass in `SignatureTest`.

