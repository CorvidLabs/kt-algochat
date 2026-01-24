# kt-algochat

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
