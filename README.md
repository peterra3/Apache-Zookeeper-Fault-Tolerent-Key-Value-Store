
# Fault-Tolerant Key-Value Store with Apache ZooKeeper

## Overview
This project involves implementing a fault-tolerant key-value store using Apache ZooKeeper for coordination. The goal is to enhance a partially implemented key-value service by adding primary-backup replication, leveraging ZooKeeper to determine the primary replica and detect crashes.

## Objectives
1. Gain hands-on experience with Apache ZooKeeper and Curator.
2. Learn about fault tolerance and replication in distributed systems.

## Software Environment
- **Java**: 1.11
- **ZooKeeper**: 3.4.13
- **Curator**: 4.3.0
- **Thrift**: 0.13.0
- **Guava**: Included in the starter code

## Setup and Build

### Prerequisites
- Java 1.11
- ZooKeeper 3.4.13
- Curator 4.3.0
- Thrift 0.13.0

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/fault-tolerant-kv-store.git
   cd fault-tolerant-kv-store
   ```

2. Compile the Thrift IDL:
   ```bash
   thrift --gen java kvstore.thrift
   ```

3. Build the project:
   ```bash
   ./gradlew build
   ```

### Step-by-Step Instructions

#### Step 1: Start ZooKeeper
Start the ZooKeeper server:
```bash
zkServer.sh start
```

#### Step 2: Create a Parent ZNode
You need to create a ZooKeeper node manually before running the starter code. Use the provided script to create the znode:
```bash
./createznode.sh
```
The znode name defaults to your Nexus ID stored in the `$USER` environment variable.


### Running the System
1. Start ZooKeeper:
   ```bash
   zkServer.sh start
   ```

2. Start the key-value store service:
   ```bash
   java -jar build/libs/kvstore-service.jar
   ```

3. Run the client:
   ```bash
   java -jar build/libs/kvstore-client.jar
   ```

## License
This project is licensed under the MIT License.

## Contact
For questions or issues, please contact me via GitHub.
