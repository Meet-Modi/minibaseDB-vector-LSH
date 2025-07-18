# MiniBaseDB-Vector-LSH

> 📌 *Extending MiniBase from a relational DBMS to a vector-aware DBMS supporting efficient approximate nearest neighbor (ANN) queries in high-dimensional vector spaces using Locality-Sensitive Hashing (LSH).*

## 📖 Overview

Traditional database systems, like MiniBase, are optimized for structured relational data but struggle with modern applications involving high-dimensional vector data such as image embeddings, NLP vectors, and recommendation systems. This project integrates a **disk-based Locality Sensitive Hash Forest (LSHF) Index** into MiniBase to support **approximate nearest-neighbor** and **range queries** on 100D vectors using **Euclidean distance**.

By designing a **clustered hash-based LSH index** that integrates tightly with MiniBase's heapfiles and buffer manager, we significantly reduce the query space and enhance performance for high-dimensional similarity search while maintaining compatibility with MiniBase's relational query engine.

## 🧠 Key Features

- **💾 100D Vector Data Type Support** - Custom vector data type for high-dimensional data
- **🌲 Disk-Based LSH Forest Index** - Efficient approximate similarity search
- **🔍 k-NN and Range Queries** - Support for both nearest neighbor and range queries using Euclidean distance
- **⚙️ Efficient RID-Based Indexing** - Reduced I/O operations through smart indexing
- **✅ MiniBase Integration** - Fully compatible with existing MiniBase operators
- **🧪 Comprehensive Testing** - Unit tests and performance benchmarks on large datasets
- **☕ Java 21+ Support** - Modern Java development environment
- **💽 HeapFile-Based Storage** - Leverages MiniBase's storage engine
- **🌳 LSH Forest Implementation** - Optimized for Euclidean similarity
- **🔧 Bash Automation** - Scripts for batch operations and queries

## ⚙️ System Requirements

- **Java**: OpenJDK 21 or higher
- **Memory**: At least 156MB of RAM
- **Storage**: Sufficient disk space for database files
- **OS**: Linux/Mac (bash shell recommended)

## 📦 Installation & Setup

### 1. Clone the Repository

```bash
git clone https://github.com/Meet-Modi/minibaseDB-vector-LSH.git
cd minibaseDB-vector-LSH
```

### 2. Compile & Prepare Environment

Ensure the `.jar`, `batchinsert.sh`, and `query.sh` files are in the same directory. Make the shell scripts executable:

```bash
chmod +x batchinsert.sh query.sh
```

## 🚀 Usage

### Batch Insert Operations

Insert vector data into the database using the batch insert script:

```bash
./batchinsert.sh <hashesPerLayer> <numLayers> <dataSpecFile> <dbName>
```

**Example:**
```bash
./batchinsert.sh 6 2 sample_data_1.txt demoRun
```

**Parameters:**
- `hashesPerLayer`: Number of hash functions per LSH layer
- `numLayers`: Number of LSH layers
- `dataSpecFile`: Input data specification file
- `dbName`: Name of the database to create

### Query Operations

Execute queries on the vector database:

```bash
./query.sh <dbName> <querySpecFile> <Y|N:useIndex> <numBuffers>
```

**Example:**
```bash
./query.sh demoRun nquery1.txt Y 1000
```

**Parameters:**
- `dbName`: Name of the existing database
- `querySpecFile`: Query specification file
- `useIndex`: Y to use LSH index, N for brute force
- `numBuffers`: Number of buffer pages to allocate

> ⚠️ **Important**: Avoid using `~` for file paths in `.txt` query spec files. Use relative or absolute paths instead.

## 📝 Query Format Specification

### Nearest Neighbor (k-NN) Query

```
NN(<vectorCol>, <targetVecFile>, <topK>, <outputColNums...>)
```

**Parameters:**
- `vectorCol`: Column containing vector data
- `targetVecFile`: File containing the target vector (100 space-separated integers)
- `topK`: Number of nearest neighbors to return
- `outputColNums`: Columns to include in the output

### Range Query

```
Range(<vectorCol>, <targetVecFile>, <distance>, <outputColNums...>)
```

**Parameters:**
- `vectorCol`: Column containing vector data
- `targetVecFile`: File containing the target vector (100 space-separated integers)
- `distance`: Maximum Euclidean distance threshold
- `outputColNums`: Columns to include in the output

**Target Vector File Format:**
The target vector file should contain a single line with 100 space-separated integers representing the query vector.

## 🏗️ Project Architecture

| Component | Description |
|-----------|-------------|
| **LSHFIndex.java** | Core implementation of LSH Forest indexing algorithm |
| **BatchInsert.java** | Command-line interface for data ingestion and index creation |
| **Query.java** | Command-line interface for executing NN and Range queries |
| **NNIndexScan.java** | Iterator implementation for nearest neighbor queries |
| **RSIndexScan.java** | Iterator implementation for range queries |
| **Vector100Dtype.java** | Custom 100-dimensional vector data type |
| **Sort.java** | Enhanced sorting with support for RID and vector-based sorting |
| **tests/** | Comprehensive unit and integration test suite |

## 🧪 Testing

The project includes extensive testing to ensure correctness and performance:

### Test Coverage
- **Index Persistence**: Verification of LSH index serialization and deserialization
- **Hash Function Correctness**: Validation of LSH hashing and binning algorithms
- **Sorting Algorithms**: Testing vector distance-based sorting operations
- **Query Accuracy**: Correctness verification for NN and Range queries (with and without index)
- **Performance Benchmarks**: Evaluation under various configurations and data sizes

### Running Tests
```bash
# Run all tests
make test

# Run specific test categories
make test-unit
make test-integration
```

## 📊 Performance & Optimization

### Performance Highlights
- **Search Space Reduction**: Achieves 89–98% reduction in search space using LSHFIndex
- **Optimal Configuration**: 6 hashes per layer × 2 layers provides best performance/accuracy trade-off
- **Scalable Architecture**: Linear trade-offs observed between layers/hashes and I/O operations
- **Memory Efficient**: Performs efficiently under memory constraints with ≤1500 buffers

### Configuration Recommendations
| Use Case | Hashes/Layer | Layers | Expected Performance |
|----------|--------------|--------|---------------------|
| **High Accuracy** | 8-10 | 3-4 | 95%+ accuracy, higher I/O |
| **Balanced** | 6 | 2 | 89-95% accuracy, optimal I/O |
| **Fast Search** | 4-5 | 1-2 | 80-90% accuracy, minimal I/O |

## 🔮 Future Enhancements

### Planned Optimizations
- **Union Copy Optimization**: Eliminate the current bottleneck in union operations
- **Adaptive Bin Sizing**: Dynamic bin sizing based on data distribution (standard deviation)
- **RID-Only Sorting**: Remove intermediate tuple copying for better performance
- **Cosine Similarity Support**: Extend LSH implementation for cosine similarity queries
- **Multi-Threading**: Parallel query execution for improved throughput
- **Compression**: Vector compression techniques to reduce storage overhead

