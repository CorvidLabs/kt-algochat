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
    implementation("com.corvidlabs:algochat:0.2.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.corvidlabs:algochat:0.2.0'
}
```

## Usage

### Client Initialization

```kotlin
import com.corvidlabs.algochat.*

// Create a client from an Algorand account seed (32-byte Ed25519 private key)
val client = AlgoChatClient.fromSeed(
    seed = accountSeed,          // 32-byte seed
    address = "ALGO_ADDRESS...", // Algorand address
    config = AlgoChatConfig.testnet(),
    algod = algodClient,
    indexer = indexerClient
)

// Network presets: localnet(), testnet(), mainnet()
// Custom config:
val config = AlgoChatConfig(
    network = AlgorandConfig(
        algodUrl = "https://your-node.example.com",
        algodToken = "your-token",
        indexerUrl = "https://your-indexer.example.com",
        indexerToken = "your-token"
    ),
    autoDiscoverKeys = true,
    cachePublicKeys = true,
    cacheMessages = true
)
```

### Key Discovery

```kotlin
// Discover a recipient's encryption public key from on-chain announcements
val discovered = client.discoverKey("RECIPIENT_ADDRESS...")
if (discovered != null) {
    println("Key verified: ${discovered.isVerified}")
    // discovered.publicKey -> 32-byte X25519 public key
}
```

### Encrypt and Decrypt

```kotlin
// Encrypt a message for a recipient
val recipientKey = client.discoverKey("RECIPIENT_ADDRESS...")!!
val encrypted = client.encrypt("Hello, World!", recipientKey.publicKey)

// Decrypt a received message
val plaintext = client.decrypt(encrypted, senderPublicKey)

// Decrypt with full reply context
val content = client.decryptFull(encrypted)
println(content.text)
println(content.replyToId)      // null if not a reply
println(content.replyToPreview) // null if not a reply
```

### Replies

```kotlin
// Reply to a message using Crypto.encryptReply
val replyEnvelope = Crypto.encryptReply(
    text = "Thanks for the message!",
    replyToTxid = originalMessage.id,
    replyToPreview = "Hello, World!",
    senderPrivateKey = senderKeys.privateKey,
    senderPublicKey = senderKeys.publicKey,
    recipientPublicKey = recipientKeys.publicKey
)

// The recipient decrypts and sees the reply context
val decrypted = Crypto.decryptMessage(replyEnvelope, recipientKeys.privateKey, recipientKeys.publicKey)
decrypted?.let {
    println(it.text)           // "Thanks for the message!"
    println(it.replyToId)      // original transaction ID
    println(it.replyToPreview) // "Hello, World!"
}
```

### Conversations and Sync

```kotlin
// Fetch new messages from the blockchain
val messages = client.sync()

// Access conversations
val conv = client.conversation("RECIPIENT_ADDRESS...")
println(conv.messages)       // all messages
println(conv.lastMessage)    // most recent
println(conv.lastReceived)   // most recent received
println(conv.messageCount)   // total count
```

### Low-Level Crypto API

```kotlin
// Direct key derivation and encryption (without the client)
val senderKeys = Keys.deriveKeysFromSeed(seed)
val recipientKeys = Keys.deriveKeysFromSeed(recipientSeed)

val envelope = Crypto.encryptMessage(
    "Hello, World!",
    senderKeys.privateKey,
    senderKeys.publicKey,
    recipientKeys.publicKey
)

val encoded = envelope.encode()
val decoded = ChatEnvelope.decode(encoded)

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
- [go-algochat](https://github.com/CorvidLabs/go-algochat) (Go)

## License

MIT
