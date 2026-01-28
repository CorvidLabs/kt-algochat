# kt-algochat

[![CI](https://img.shields.io/github/actions/workflow/status/CorvidLabs/kt-algochat/ci.yml?label=CI&branch=main)](https://github.com/CorvidLabs/kt-algochat/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/CorvidLabs/kt-algochat)](https://github.com/CorvidLabs/kt-algochat/blob/main/LICENSE)
[![Version](https://img.shields.io/github/v/release/CorvidLabs/kt-algochat?display_name=tag)](https://github.com/CorvidLabs/kt-algochat/releases)

> **Pre-1.0 Notice**: This library is under active development. The API may change between minor versions until 1.0.

Kotlin implementation of the AlgoChat protocol for encrypted messaging on Algorand.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.corvidlabs:algochat:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.corvidlabs:algochat:0.1.0'
}
```

## Usage

```kotlin
import com.corvidlabs.algochat.*

// Derive keys from a 32-byte seed (e.g., from Algorand account)
val senderKeys = Keys.deriveKeysFromSeed(seed)
val recipientKeys = Keys.deriveKeysFromSeed(recipientSeed)

// Encrypt a message
val envelope = Crypto.encryptMessage(
    "Hello, World!",
    senderKeys.privateKey,
    senderKeys.publicKey,
    recipientKeys.publicKey
)

// Encode for transmission
val encoded = envelope.encode()

// Decode received message
val decoded = ChatEnvelope.decode(encoded)

// Decrypt as recipient
val result = Crypto.decryptMessage(decoded, recipientKeys.privateKey, recipientKeys.publicKey)
result?.let { println(it.text) }
```

## Protocol

AlgoChat uses:
- **X25519** for key agreement
- **ChaCha20-Poly1305** for authenticated encryption
- **HKDF-SHA256** for key derivation

The protocol supports bidirectional decryption, allowing senders to decrypt their own messages.

### PSK v1.1

The PSK (Pre-Shared Key) protocol extends AlgoChat with an additional symmetric key layer:

- **Two-level ratchet** - Session and position keys derived from an initial PSK via HKDF
- **Hybrid encryption** - Combines X25519 ECDH with PSK for dual-layer security
- **Forward secrecy** - Each message uses a unique derived key from the ratchet counter
- **Replay protection** - Sliding counter window prevents message replay attacks

```kotlin
import com.corvidlabs.algochat.*

// Create a shared PSK (exchanged out-of-band)
val psk = ByteArray(32) // 32 random bytes shared between peers

// Derive the ratcheted PSK for a specific counter
val counter = 0u
val currentPSK = PSKRatchet.derivePSKAtCounter(psk, counter)

// Encrypt with PSK
val envelope = PSKCrypto.encryptMessage(
    "Hello with PSK!",
    senderKeys.privateKey,
    senderKeys.publicKey,
    recipientKeys.publicKey,
    currentPSK,
    counter
)

// Encode for transmission
val encoded = PSKEnvelopeCodec.encode(envelope)

// Decode and decrypt
val decoded = PSKEnvelopeCodec.decode(encoded)
val result = PSKCrypto.decryptMessage(decoded, recipientKeys.privateKey, recipientKeys.publicKey, currentPSK)
result?.let { println(it.text) }

// Exchange PSKs via URI
val uri = PSKExchangeURI(address = "ALGO_ADDRESS", psk = psk, label = "My Chat")
val uriString = uri.encode() // algochat-psk://v1?addr=...&psk=...&label=...
val parsed = PSKExchangeURI.decode(uriString)
```

PSK envelope wire format (130-byte header):
```
[0]       version (0x01)
[1]       protocolId (0x02)
[2..5]    ratchetCounter (4 bytes, big-endian)
[6..37]   senderPublicKey (32 bytes)
[38..69]  ephemeralPublicKey (32 bytes)
[70..81]  nonce (12 bytes)
[82..129] encryptedSenderKey (48 bytes)
[130..]   ciphertext + 16-byte tag
```

## Cross-Implementation Compatibility

This implementation is fully compatible with:
- [swift-algochat](https://github.com/CorvidLabs/swift-algochat) (Swift)
- [ts-algochat](https://github.com/CorvidLabs/ts-algochat) (TypeScript)
- [py-algochat](https://github.com/CorvidLabs/py-algochat) (Python)
- [rs-algochat](https://github.com/CorvidLabs/rs-algochat) (Rust)

## License

MIT
