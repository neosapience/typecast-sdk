#!/bin/bash
#
# Typecast C SDK E2E Test Runner
#
# Tests the built libraries on multiple platforms and architectures using Docker.
#
# Usage:
#   ./run_e2e.sh                    # All Linux environments
#   ./run_e2e.sh --linux            # Linux x86_64 only
#   ./run_e2e.sh --macos            # macOS (local)
#   ./run_e2e.sh --centos6          # CentOS 6.9 only
#   ./run_e2e.sh --amazonlinux      # Amazon Linux 2 only
#   ./run_e2e.sh --ubuntu           # Ubuntu 20.04 only
#   ./run_e2e.sh --help             # Show help
#
# Copyright (c) 2025 Typecast
# MIT License

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
BUILD_DIR="$PROJECT_DIR/build"
TEST_SOURCE="$SCRIPT_DIR/test_e2e.c"

# Counters
PASSED=0
FAILED=0

# Print functions
print_header() {
    echo ""
    echo "=================================================="
    echo "  $1"
    echo "=================================================="
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${BLUE}$1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Show help
show_help() {
    cat << EOF
Typecast C SDK E2E Test Runner

Usage:
  $0 [options]

Options:
  --linux, -l       Run on CentOS 6.9 and Amazon Linux 2 (default)
  --centos6         Run on CentOS 6.9 only
  --centos7         Run on CentOS 7 only
  --amazonlinux     Run on Amazon Linux 2 only
  --ubuntu          Run on Ubuntu 20.04 only
  --debian          Run on Debian Bullseye only
  --windows, -w     Run on Windows (via Wine + MinGW-w64)
  --macos, -m       Run on macOS (local execution)
  --all, -a         Run on all environments (Linux + Windows)
  --help, -h        Show this help message

Examples:
  $0                   # Default: CentOS 6.9 + Amazon Linux 2
  $0 --macos           # macOS local test
  $0 --windows         # Windows DLL test (via Wine)
  $0 --all             # All environments including Windows
  $0 --centos6         # CentOS 6.9 only

Tested Environments:
  - CentOS 6.9 (x86_64) - glibc 2.12
  - CentOS 7 (x86_64) - glibc 2.17
  - Amazon Linux 2 (x86_64) - glibc 2.26
  - Ubuntu 20.04 LTS (x86_64) - glibc 2.31
  - Debian Bullseye (x86_64) - glibc 2.31
  - Windows (x64) - via MinGW-w64 + Wine
  - macOS (local, universal binary)
EOF
}

# Run test on CentOS 6.9
run_centos6() {
    print_header "Testing on CentOS 6.9 (x86_64)"
    
    docker run --rm \
        -v "$PROJECT_DIR:/source:ro" \
        --platform linux/amd64 \
        quay.io/centos/centos:6 sh -c "
            # Fix repos for EOL CentOS 6
            sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-Base.repo
            sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-Base.repo
            
            # Install dependencies
            yum install -y gcc libcurl-devel > /dev/null 2>&1
            
            # Build library with gcc directly (cmake 2.8 is too old)
            mkdir -p /lib_check
            cd /source/src
            gcc -std=c99 -shared -fPIC -O2 -o /lib_check/libtypecast.so \
                typecast.c cJSON.c \
                -I/source/include -I. \
                -lcurl -DTYPECAST_BUILDING_DLL 2>&1
            
            # Build and run test
            gcc -std=c99 -o /tmp/test_e2e /source/scripts/e2e/test_e2e.c -ldl
            /tmp/test_e2e /lib_check/libtypecast.so
        "
    
    if [ $? -eq 0 ]; then
        print_success "CentOS 6.9 (x86_64): ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "CentOS 6.9 (x86_64): SOME TESTS FAILED"
        ((FAILED++))
    fi
}

# Run test on CentOS 7
run_centos7() {
    print_header "Testing on CentOS 7 (x86_64)"
    
    docker run --rm \
        -v "$PROJECT_DIR:/source:ro" \
        --platform linux/amd64 \
        quay.io/centos/centos:7 sh -c "
            # Fix repos for EOL CentOS 7 - must use vault
            sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*.repo 2>/dev/null || true
            sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*.repo 2>/dev/null || true
            
            # Install dependencies
            yum install -y gcc make libcurl-devel > /dev/null 2>&1
            
            # Build library directly with gcc (similar to CentOS 6)
            mkdir -p /lib_check
            cd /source/src
            gcc -std=c99 -shared -fPIC -O2 -o /lib_check/libtypecast.so \
                typecast.c cJSON.c \
                -I/source/include -I. \
                -lcurl -DTYPECAST_BUILDING_DLL 2>&1
            
            # Build and run test
            gcc -o /tmp/test_e2e /source/scripts/e2e/test_e2e.c -ldl
            /tmp/test_e2e /lib_check/libtypecast.so
        "
    
    if [ $? -eq 0 ]; then
        print_success "CentOS 7 (x86_64): ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "CentOS 7 (x86_64): SOME TESTS FAILED"
        ((FAILED++))
    fi
}

# Run test on Amazon Linux 2
run_amazonlinux() {
    print_header "Testing on Amazon Linux 2 (x86_64)"
    
    docker run --rm \
        -v "$PROJECT_DIR:/source:ro" \
        --platform linux/amd64 \
        amazonlinux:2 sh -c "
            # Install dependencies
            yum install -y gcc make cmake3 libcurl-devel > /dev/null 2>&1
            
            # Build library
            mkdir -p /tmp/build && cd /tmp/build
            cmake3 /source -DCMAKE_BUILD_TYPE=Release -DTYPECAST_BUILD_EXAMPLES=OFF -DTYPECAST_BUILD_TESTS=OFF > /dev/null 2>&1
            make > /dev/null 2>&1
            
            mkdir -p /lib_check
            cp -L /tmp/build/libtypecast.so /lib_check/
            
            # Build and run test
            gcc -o /tmp/test_e2e /source/scripts/e2e/test_e2e.c -ldl
            /tmp/test_e2e /lib_check/libtypecast.so
        "
    
    if [ $? -eq 0 ]; then
        print_success "Amazon Linux 2 (x86_64): ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "Amazon Linux 2 (x86_64): SOME TESTS FAILED"
        ((FAILED++))
    fi
}

# Run test on Ubuntu 20.04
run_ubuntu() {
    print_header "Testing on Ubuntu 20.04 LTS (x86_64)"
    
    docker run --rm \
        -v "$PROJECT_DIR:/source:ro" \
        --platform linux/amd64 \
        ubuntu:20.04 sh -c "
            # Install dependencies
            apt-get update > /dev/null 2>&1
            apt-get install -y gcc make cmake libcurl4-openssl-dev > /dev/null 2>&1
            
            # Build library
            mkdir -p /tmp/build && cd /tmp/build
            cmake /source -DCMAKE_BUILD_TYPE=Release -DTYPECAST_BUILD_EXAMPLES=OFF -DTYPECAST_BUILD_TESTS=OFF > /dev/null 2>&1
            make > /dev/null 2>&1
            
            mkdir -p /lib_check
            cp -L /tmp/build/libtypecast.so /lib_check/
            
            # Build and run test
            gcc -o /tmp/test_e2e /source/scripts/e2e/test_e2e.c -ldl
            /tmp/test_e2e /lib_check/libtypecast.so
        "
    
    if [ $? -eq 0 ]; then
        print_success "Ubuntu 20.04 LTS (x86_64): ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "Ubuntu 20.04 LTS (x86_64): SOME TESTS FAILED"
        ((FAILED++))
    fi
}

# Run test on Debian Bullseye
run_debian() {
    print_header "Testing on Debian Bullseye (x86_64)"
    
    docker run --rm \
        -v "$PROJECT_DIR:/source:ro" \
        --platform linux/amd64 \
        debian:bullseye-slim sh -c "
            # Install dependencies
            apt-get update > /dev/null 2>&1
            apt-get install -y gcc make cmake libcurl4-openssl-dev > /dev/null 2>&1
            
            # Build library
            mkdir -p /tmp/build && cd /tmp/build
            cmake /source -DCMAKE_BUILD_TYPE=Release -DTYPECAST_BUILD_EXAMPLES=OFF -DTYPECAST_BUILD_TESTS=OFF > /dev/null 2>&1
            make > /dev/null 2>&1
            
            mkdir -p /lib_check
            cp -L /tmp/build/libtypecast.so /lib_check/
            
            # Build and run test
            gcc -o /tmp/test_e2e /source/scripts/e2e/test_e2e.c -ldl
            /tmp/test_e2e /lib_check/libtypecast.so
        "
    
    if [ $? -eq 0 ]; then
        print_success "Debian Bullseye (x86_64): ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "Debian Bullseye (x86_64): SOME TESTS FAILED"
        ((FAILED++))
    fi
}

# Run test on Windows (via MinGW-w64 + Wine)
run_windows() {
    print_header "Testing on Windows x64 (MinGW-w64 + Wine)"
    
    docker run --rm \
        -v "$PROJECT_DIR:/source:ro" \
        --platform linux/amd64 \
        ubuntu:22.04 bash -c "
            set -e
            
            # Install MinGW-w64, Wine, and utilities
            export DEBIAN_FRONTEND=noninteractive
            dpkg --add-architecture i386 > /dev/null 2>&1
            apt-get update > /dev/null 2>&1
            apt-get install -y --no-install-recommends \
                mingw-w64 cmake make \
                wine wine64 \
                wget unzip \
                ca-certificates > /dev/null 2>&1
            
            # Suppress Wine debug messages
            export WINEDEBUG=-all
            export WINEPREFIX=/tmp/wine
            
            # Initialize Wine silently
            wineboot --init > /dev/null 2>&1 || true
            
            echo 'Downloading Windows libcurl...'
            
            # Download pre-built curl for Windows (MinGW64)
            mkdir -p /tmp/curl
            cd /tmp/curl
            wget -q https://curl.se/windows/dl-8.11.1_1/curl-8.11.1_1-win64-mingw.zip
            unzip -q curl-8.11.1_1-win64-mingw.zip
            CURL_DIR=/tmp/curl/curl-8.11.1_1-win64-mingw
            
            echo 'Building Windows DLL with MinGW-w64...'
            
            # Create build directory
            mkdir -p /tmp/build && cd /tmp/build
            
            # Build library directly with MinGW-w64
            x86_64-w64-mingw32-gcc -shared -O2 \
                -o typecast.dll \
                /source/src/typecast.c /source/src/cJSON.c \
                -I/source/include -I/source/src \
                -I\${CURL_DIR}/include \
                -L\${CURL_DIR}/lib \
                -DTYPECAST_BUILDING_DLL \
                -lcurl \
                -lws2_32 \
                -Wl,--out-implib,libtypecast.a 2>&1
            
            # Check if DLL was created
            if [ ! -f 'typecast.dll' ]; then
                echo 'ERROR: DLL not created'
                ls -la /tmp/build/
                exit 1
            fi
            
            echo 'DLL created: typecast.dll'
            
            # Create test directory
            mkdir -p /tmp/test_win
            cp typecast.dll /tmp/test_win/
            
            # Copy required DLLs
            cp \${CURL_DIR}/bin/*.dll /tmp/test_win/ 2>/dev/null || true
            cp /usr/x86_64-w64-mingw32/lib/libwinpthread-1.dll /tmp/test_win/ 2>/dev/null || true
            cp /usr/lib/gcc/x86_64-w64-mingw32/*/libgcc_s_seh-1.dll /tmp/test_win/ 2>/dev/null || true
            
            echo 'Building Windows test executable...'
            
            # Compile test program for Windows
            x86_64-w64-mingw32-gcc -o /tmp/test_win/test_e2e.exe \
                /source/scripts/e2e/test_e2e.c \
                -I/source/include 2>&1
            
            echo 'Running Windows test with Wine...'
            
            # Run test with Wine
            cd /tmp/test_win
            wine64 test_e2e.exe typecast.dll
        "
    
    if [ $? -eq 0 ]; then
        print_success "Windows x64 (MinGW-w64): ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "Windows x64 (MinGW-w64): SOME TESTS FAILED"
        ((FAILED++))
    fi
}

# Run test on macOS (local)
run_macos() {
    print_header "Testing on macOS (local)"
    
    # Check if running on macOS
    if [ "$(uname -s)" != "Darwin" ]; then
        print_error "macOS tests can only run on macOS"
        ((FAILED++))
        return
    fi
    
    # Check if library exists
    if [ ! -f "$BUILD_DIR/libtypecast.dylib" ]; then
        print_warning "libtypecast.dylib not found in $BUILD_DIR"
        print_info "Building library..."
        
        mkdir -p "$BUILD_DIR"
        cd "$BUILD_DIR"
        cmake .. -DCMAKE_BUILD_TYPE=Release > /dev/null 2>&1
        cmake --build . > /dev/null 2>&1
    fi
    
    # Build and run test
    gcc -o /tmp/test_e2e "$TEST_SOURCE"
    DYLD_LIBRARY_PATH="$BUILD_DIR" /tmp/test_e2e "$BUILD_DIR/libtypecast.dylib"
    
    if [ $? -eq 0 ]; then
        print_success "macOS (local, universal binary): ALL TESTS PASSED"
        ((PASSED++))
    else
        print_error "macOS (local): SOME TESTS FAILED"
        ((FAILED++))
    fi
    
    rm -f /tmp/test_e2e
}

# Print final results
print_results() {
    local total=$((PASSED + FAILED))
    
    print_header "Final E2E Test Results"
    echo "Environments tested: $total"
    echo -e "Passed: ${GREEN}$PASSED${NC}"
    echo -e "Failed: ${RED}$FAILED${NC}"
    echo ""
    
    if [ "$FAILED" -eq 0 ]; then
        print_success "All E2E tests passed!"
    else
        print_error "Some E2E tests failed!"
    fi
}

# Main
main() {
    print_header "Typecast C SDK E2E Test"
    echo "Project directory: $PROJECT_DIR"
    
    # Check if test source exists
    if [ ! -f "$TEST_SOURCE" ]; then
        print_error "Test source not found: $TEST_SOURCE"
        exit 1
    fi
    
    # Parse arguments
    case "${1:-}" in
        --help|-h)
            show_help
            exit 0
            ;;
        --macos|-m)
            run_macos
            ;;
        --centos6)
            run_centos6
            ;;
        --centos7)
            run_centos7
            ;;
        --amazonlinux)
            run_amazonlinux
            ;;
        --ubuntu)
            run_ubuntu
            ;;
        --debian)
            run_debian
            ;;
        --windows|-w)
            run_windows
            ;;
        --all|-a)
            run_centos6
            run_centos7
            run_amazonlinux
            run_ubuntu
            run_debian
            run_windows
            ;;
        --linux|-l|"")
            # Default: CentOS 6.9 + Amazon Linux 2
            run_centos6
            run_amazonlinux
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
    
    print_results
    
    if [ "$FAILED" -eq 0 ]; then
        exit 0
    else
        exit 1
    fi
}

main "$@"
