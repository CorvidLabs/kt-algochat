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

## Cross-Implementation Compatibility

This implementation is fully compatible with:
- [swift-algochat](https://github.com/CorvidLabs/swift-algochat) (Swift)
- [ts-algochat](https://github.com/CorvidLabs/ts-algochat) (TypeScript)
- [py-algochat](https://github.com/CorvidLabs/py-algochat) (Python)
- [rs-algochat](https://github.com/CorvidLabs/rs-algochat) (Rust)

## License

MIT
