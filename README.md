# JBlockchain

A demo blockchain built with Java and Spring Boot, designed for learning purposes. It implements the core mechanics of a real blockchain - Proof of Work, SHA-256 hashing, chain validation, and a basic P2P network - with a full REST API and an interactive Swagger UI.

## Features

- **Proof of Work** mining with configurable difficulty
- **SHA-256** block hashing and chain integrity validation
- **Generic message payload**: store text, JSON, or Base64-encoded binary data in blocks
- **P2P networking**: register peer nodes, broadcast mined blocks, and run the Nakamoto consensus algorithm to resolve chain conflicts
- **REST API** with full Swagger UI
- **In-memory chain** (no database needed to run)

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.4.4 |
| API Docs | springdoc-openapi (Swagger UI) |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| Testing | JUnit 5, Spring MockMvc |
| Build | Maven |

## Project Structure

```
src/main/java/com/giovannimenzano/jblockchain/
├── config/         WebConfig.java               (RestTemplate + timeouts)
├── controller/     IBlockchainController        (Swagger-annotated interfaces)
│                   INetworkController
│   impl/           BlockchainController         (implementations)
│                   NetworkController
├── dto/
│   request/        Message.java, NodeInfo.java
│   response/       GenericResponse.java, BlockchainStatus.java, MineResponse.java
├── entities/       Block.java
├── exceptions/     BlockchainException.java, NotFoundException.java
├── interceptor/    GlobalExceptionHandler.java
├── scheduler/      ChainSyncScheduler.java      (startup sync + periodic consensus)
└── services/       IBlockchainService           (interfaces)
│                   INetworkService
│   impl/           BlockchainServiceImpl        (PoW, validation, consensus)
│                   NetworkServiceImpl           (P2P gossip, bootstrap, broadcast)
```

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run a single node

```powershell
mvn spring-boot:run
```

The node starts on port `8091` with context path `/jblockchain`.

Swagger UI: **http://localhost:8091/jblockchain/swagger-ui/index.html**

### Run N nodes (P2P network)

Each node is started in a separate PowerShell terminal. The **first node** is the seed and has no peers. Every subsequent node points to the seed via `blockchain.network.seed-nodes` and is automatically registered, no manual setup needed.

**General pattern:**

```powershell
# First node (seed) - no seed-nodes argument needed
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=<PORT> --blockchain.node.name=<NAME>"

# Every additional node - points to node-1 as seed
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=<PORT> --blockchain.node.name=<NAME> --blockchain.network.seed-nodes=http://localhost:<SEED_PORT>/jblockchain"
```

**Example with 3 nodes** (open three terminals in the project root):

*Terminal 1 — node-1 (seed):*
```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8091 --blockchain.node.name=node-1"
```

*Terminal 2 — node-2:*
```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8092 --blockchain.node.name=node-2 --blockchain.network.seed-nodes=http://localhost:8091/jblockchain"
```

*Terminal 3 — node-3:*
```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8093 --blockchain.node.name=node-3 --blockchain.network.seed-nodes=http://localhost:8091/jblockchain"
```

> Always start **node-1 first** and wait for it to be ready before starting the others.
> Each additional node will self-register with the seed and discover all other peers automatically at startup.

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Server
server.port=8091
server.servlet.context-path=/jblockchain

# Mining difficulty: number of leading zeros required in a valid block hash.
# Recommended: 2 (instant) to 5 (several seconds per block)
blockchain.mining.difficulty=4

# Node identity
blockchain.node.name=node-1

# Public base URL of this node (scheme + host only).
# Override with the public hostname when deploying on a remote server.
# REQUIRED - the application will not start if left blank.
blockchain.node.url=http://localhost

# Comma-separated seed node URLs for auto-registration at startup.
# Leave empty for the first (bootstrap) node in the network.
blockchain.network.seed-nodes=
```

## API Reference

All endpoints are documented and interactive via Swagger UI at `/jblockchain/swagger-ui/index.html`.

### Blockchain Controller - `/api/chain`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/chain` | Returns the full blockchain |
| `GET` | `/api/chain/status` | Chain length, validity, last hash, pending count |
| `GET` | `/api/chain/block/{index}` | Returns a specific block by index |
| `GET` | `/api/chain/validate` | Runs full integrity validation |
| `GET` | `/api/chain/pending` | Lists messages waiting to be mined |
| `POST` | `/api/chain/messages` | Submits a new message to the pending pool |
| `POST` | `/api/chain/mine` | Mines all pending messages into a new block |

**Message body example:**
```json
{
  "type": "TEXT",
  "content": "Hello, blockchain!"
}
```
Supported types: `TEXT`, `JSON`, `BINARY` (Base64-encoded).

### Network Controller - `/api/network`

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/network/nodes` | Lists all registered peer nodes |
| `POST` | `/api/network/nodes` | Registers a new peer node |
| `POST` | `/api/network/resolve` | Triggers the consensus algorithm |
| `POST` | `/api/network/broadcast` | Receives a block broadcast from a peer |

**Node registration body example:**
```json
{
  "url": "http://localhost:8092/jblockchain"
}
```

## Live P2P Test Walkthrough (using Swagger UI)

### Setup

1. **Start 3 nodes** as shown in the [Run N nodes](#run-n-nodes-p2p-network) section above.
2. **Open three browser tabs:**
   - node-1: `http://localhost:8091/jblockchain/swagger-ui/index.html`
   - node-2: `http://localhost:8092/jblockchain/swagger-ui/index.html`
   - node-3: `http://localhost:8093/jblockchain/swagger-ui/index.html`

### Phase 1 — Verify automatic bootstrap

Check the logs of node-2 and node-3: both should show `[Bootstrap] Registered self` and `Bootstrap complete` before the first sync. No manual registration is needed.

On node-1's Swagger, call `GET /api/network/nodes` — it should already list node-2 and node-3.

### Phase 2 — Gossip broadcast

1. On **node-1** call `POST /api/chain/messages` with any payload:
   ```json
   { "type": "TEXT", "content": "Hello from node-1!" }
   ```
2. On **node-1** call `POST /api/chain/mine`.
3. Check the logs: node-2 and node-3 should receive the broadcast and synchronize automatically.
4. Verify with `GET /api/chain` on any node — all three should show 2 blocks.

### Phase 3 — Offline recovery

1. Stop node-3 (`Ctrl+C` in Terminal 3).
2. Mine one or two more blocks on node-1.
3. Restart node-3 with the same command.
4. Check its log: it will bootstrap, discover peers, and pull the missing blocks automatically.
5. Verify with `GET /api/chain` on node-3 — the chain will be up to date.

## How It Works

### Proof of Work

To add a block to the chain, a node must find a `nonce` value such that:

```
SHA-256(index + timestamp + data + previousHash + nonce)
```

starts with N leading zeros, where N is the configured difficulty. This requires brute-force iteration and makes tampering computationally expensive.

### Chain Validation

Validation checks three properties for every block:
1. **Data integrity** - the stored hash matches a freshly recomputed hash
2. **Chain linkage** - `previousHash` matches the actual hash of the preceding block
3. **Proof of Work** - the hash satisfies the difficulty prefix

### Nakamoto Consensus

When nodes disagree, the **longest valid chain wins**. Calling `POST /api/network/resolve` makes a node query all its peers, and if any peer has a longer valid chain, the node replaces its own.

## Running Tests

```bash
mvn test
```

The test suite includes unit tests for `BlockchainService` (mining, validation, tamper detection, consensus) and `MockMvc` integration tests for all controller endpoints.

## License

MIT - see [LICENSE](LICENSE).
