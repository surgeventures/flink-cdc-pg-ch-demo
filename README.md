# Flink CDC PostgreSQL to ClickHouse

Real-time data replication from PostgreSQL to ClickHouse using Flink CDC.

## Architecture
- Source: Two PostgreSQL databases (customers and orders)
- Processing: Flink CDC for Change Data Capture
- Target: ClickHouse

## Prerequisites
- Docker and Docker Compose
- jvm
- scala
- sbt
- curl (for deployment scripts)

## Quick Start

1. Start services and deploy job:
```bash
./deploy.sh up
```

2. Add sample data:
```bash
./deploy.sh sample
```

3. Check job status:
```bash
./deploy.sh status
```

## Available Commands
```bash
./deploy.sh up       # Start everything, deploy job and seed data
./deploy.sh services # Start Flink and databases only
./deploy.sh job      # Deploy the CDC job
./deploy.sh sample   # Seed databases with sample data
./deploy.sh stop     # Stop all services
./deploy.sh status   # Check jobs status
```

## Components

### PostgreSQL
- postgres1: Orders database (order_id, customer_id, order_date, total_amount, status)
- postgres2: Customers database (customer_id, name, email, created_at)

### Apache Flink
- JobManager: Flink cluster management
- TaskManager: Flink task execution
- CDC Connectors: PostgreSQL CDC source

### ClickHouse
- Analytical database
- Enriched orders table with customer data

Cmd to check results of real-time job:
```bash
docker-compose exec clickhouse clickhouse-client -q "SELECT * FROM cdc_demo.enriched_orders"
```

## Development

Build the Flink job:
```bash
sbt "project flinkJob" clean assembly
```
Or you can just use sbt in a root as it's multimodule sbt project :) 