#!/bin/bash
set -e

start_all() {
    echo "🚀 Starting databases..."
    docker-compose up -d postgres1 postgres2 clickhouse
    echo "⏳ Waiting for databases..."
    sleep 10

    echo "🚀 Starting Flink cluster..."
    docker-compose up -d jobmanager taskmanager
    echo "⏳ Waiting for Flink to be ready..."
    for i in {1..10}; do
        if curl -s http://localhost:8081/overview > /dev/null; then
            echo "✅ Flink cluster is ready"
            break
        fi
        echo "⏳ Waiting for Flink... ($i/10)"
        sleep 2
    done
}

deploy_job() {
    echo "📦 Building Flink job..."
    cd "flink-job"
    mvn clean package -DskipTests
    cd ..
    mkdir -p target
    cp "flink-job/target/flink-cdc-demo-1.0-SNAPSHOT.jar" target/

    echo "🚀 Submitting Flink job..."
    JOB_ID=$(docker-compose exec -T jobmanager flink run -d \
        -c OrderEnrichmentJob \
        /opt/flink/usrlib/flink-cdc-demo-1.0-SNAPSHOT.jar | grep -o "JobID [a-z0-9]\{32\}" | cut -d' ' -f2)
    
    [ -z "$JOB_ID" ] && { echo "❌ Failed to submit job"; exit 1; }
    echo "✅ Job submitted: $JOB_ID"

    for i in {1..15}; do
        JOB_STATUS=$(curl -s http://localhost:8081/jobs/$JOB_ID | grep -o '"state":"[^"]*"' | cut -d'"' -f4)
        [ "$JOB_STATUS" = "RUNNING" ] && { echo "✅ Job is running"; return 0; }
        [ "$JOB_STATUS" = "FAILED" ] && { echo "❌ Job failed"; exit 1; }
        echo "⏳ Waiting for job... ($i/15)"
        sleep 2
    done
    echo "❌ Timeout waiting for job"
    exit 1
}

sample_records() {
    echo "🌱 Seeding customers..."
    CUSTOMER_IDS=$(docker-compose exec -T postgres2 psql -U user -d customers_db -t -c "
    WITH random_names AS (
        SELECT
            'User' || substr(md5(random()::text), 1, 5) AS name,
            'user' || substr(md5(random()::text), 1, 5) || '@example.com' AS email,
            NOW() - (interval '1 day' * (10 - g)) AS created_at
        FROM generate_series(1, 5) g
    ),
    inserted AS (
        INSERT INTO customers (name, email, created_at)
        SELECT name, email, created_at FROM random_names
        RETURNING customer_id
    )
    SELECT customer_id FROM inserted;
    ")
    
    IDS=($CUSTOMER_IDS)
    CUSTOMER_IDS_CLEAN=$(IFS=,; echo "${IDS[*]}")
    
    echo "🌱 Seeding orders database..."
    docker-compose exec -T postgres1 psql -U user -d orders_db << EOF
    WITH random_orders AS (
        SELECT
            unnest(string_to_array('$CUSTOMER_IDS_CLEAN', ','))::int AS customer_id,
            NOW() - (interval '1 day' * (6 - g)) AS order_date,
            CAST(RANDOM() * 500 + 100 AS DECIMAL(10,2)) AS total_amount,
            (ARRAY['COMPLETED', 'PROCESSING', 'NEW'])[ceil(random() * 3)] AS status
        FROM generate_series(1, 5) g
    ),
    inserted AS (
        INSERT INTO orders (customer_id, order_date, total_amount, status)
        SELECT customer_id, order_date, total_amount, status FROM random_orders
        RETURNING *
    )
    SELECT * FROM inserted;
EOF
    echo "✅ Database seeding completed"
}

case "$1" in
    "up")
        start_all
        deploy_job
        ;;
    "services")
        start_all
        ;;
    "job")
        deploy_job
        ;;
    "sample")
        sample_records
        ;;
    "stop")
        docker-compose down
        ;;
    "status")
        curl -s http://localhost:8081/jobs/overview
        ;;
    *)
        echo "📝 Available commands:"
        echo "./deploy.sh up       - Start everything, deploy job and seed data"
        echo "./deploy.sh services - Start Flink and databases only"
        echo "./deploy.sh job      - Deploy the CDC job"
        echo "./deploy.sh sample   - Seed databases with sample data"
        echo "./deploy.sh stop     - Stop all services"
        echo "./deploy.sh status   - Check jobs status"
        ;;
esac