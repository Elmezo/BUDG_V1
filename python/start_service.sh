#!/bin/bash

# Load environment variables from .env file if it exists
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

# Start the FastAPI service
echo "Starting Regulator Bulk Validation Service..."
echo "Service URL: http://localhost:8000"
echo "Health check: http://localhost:8000/"
echo "API docs: http://localhost:8000/docs"
echo ""

python bulk_validation_service.py


