#!/bin/bash
# Bulk Upload Wizard Build Script for Linux/Mac
# This script installs dependencies and builds the React application

echo "========================================"
echo "Bulk Upload Wizard - Build Script"
echo "========================================"
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js is not installed!"
    echo "Please install Node.js from https://nodejs.org/"
    echo "Recommended version: 18.x or higher"
    exit 1
fi

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo "ERROR: npm is not installed!"
    echo "Please install Node.js from https://nodejs.org/"
    exit 1
fi

echo "Node.js version:"
node --version
echo ""
echo "npm version:"
npm --version
echo ""

# Check if node_modules exists
if [ ! -d "frontend/node_modules" ]; then
    echo "Step 1: Installing dependencies..."
    echo "This may take a few minutes on first run..."
    echo ""
    cd frontend
    npm install
    if [ $? -ne 0 ]; then
        echo "ERROR: npm install failed!"
        exit 1
    fi
    cd ..
    echo ""
    echo "Dependencies installed successfully!"
    echo ""
else
    echo "Step 1: Dependencies already installed (skipping)"
    echo ""
fi

echo "Step 2: Building React application..."
echo ""
cd frontend
npm run build
if [ $? -ne 0 ]; then
    echo "ERROR: Build failed!"
    exit 1
fi
cd ..

echo ""
echo "========================================"
echo "Build completed successfully! ✓"
echo "========================================"
echo ""
echo "Output location: src/main/webapp/assets/js/bulk-upload/"
echo "- bundle.js  (React application)"
echo "- bundle.css (TailwindCSS styles)"
echo ""
echo "Next steps:"
echo "1. Build Java application: mvn clean package"
echo "2. Deploy WAR to Tomcat"
echo "3. Access wizard at: http://localhost:8080/bulk-upload.html"
echo ""

