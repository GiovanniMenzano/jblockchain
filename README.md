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
├── config/         WebConfig.java          (CORS + RestTemplate)
├── controllers/    BlockchainController    (chain endpoints)
│                   NetworkController       (P2P network endpoints)
├── dto/            Message, MessageType, MineResponse, BlockchainStatus, NodeInfo
├── entities/       Block.java
├── exceptions/     BlockchainException, GlobalExceptionHandler
└── services/       BlockchainService       (core chain logic)
                    NetworkService          (P2P communication)
```

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+

### Run a single node

```bash
mvn spring-boot:run
```

The node starts on port `8091` with context path `/jblockchain`.

Swagger UI: **http://localhost:8091/jblockchain/swagger-ui/index.html**

### Run two nodes (P2P demo)

Open two separate terminals in the project root.

**Terminal 1 - Node 1 (port 8091):**
```bash
mvn spring-boot:run
```

**Terminal 2 - Node 2 (port 8092):**

*Linux/Mac (Bash):*
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8092 --blockchain.node.name=Node-2"
```

*Windows (PowerShell):*
```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8092 --blockchain.node.name=Node-2"
```

### Configuration

Edit `src/main/resources/application.properties`:

```properties
server.port=8091
blockchain.mining.difficulty=4   # number of leading zeros required in a valid hash
blockchain.node.name=Node-1
```

Higher difficulty = more hashing iterations = slower mining. Recommended values: `2` (instant) to `5` (several seconds).

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

1. **Start both nodes** (see above).

2. **Open two browser tabs:**
   - Node 1: `http://localhost:8091/jblockchain/swagger-ui/index.html`
   - Node 2: `http://localhost:8092/jblockchain/swagger-ui/index.html`

3. **Register Node 2 as a peer of Node 1** - on Node 1's Swagger, call `POST /api/network/nodes` with:
   ```json
   { "url": "http://localhost:8092/jblockchain" }
   ```

4. **Submit a message to Node 1** - call `POST /api/chain/messages` with any payload.

5. **Mine the block on Node 1** - call `POST /api/chain/mine`.
   Watch Terminal 2: Node 2 will receive the broadcast and synchronize automatically.

6. **Verify Node 2 has the new block** - call `GET /api/chain` on Node 2's Swagger.

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
