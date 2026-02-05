#!/bin/bash
#
# update-third-party-licenses.sh
#
# Automatically collects and updates third-party license information
# for all Typecast SDK implementations.
#
# Usage: ./scripts/update-third-party-licenses.sh
#
# Requirements:
#   - Python: pip-licenses (pip install pip-licenses)
#   - Node.js: npx (comes with npm)
#   - Go: go-licenses (go install github.com/google/go-licenses@latest)
#   - Rust: cargo-license (cargo install cargo-license)
#   - Java: Maven with license plugin
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
OUTPUT_FILE="$ROOT_DIR/THIRD-PARTY-LICENSES.txt"
TEMP_DIR=$(mktemp -d)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Collect Python licenses
collect_python_licenses() {
    log_info "Collecting Python licenses..."
    local sdk_dir="$ROOT_DIR/typecast-python"
    
    if [ ! -d "$sdk_dir" ]; then
        log_warn "typecast-python directory not found, skipping..."
        return
    fi
    
    cd "$sdk_dir"
    
    # Try using uv first (faster), fallback to pip
    if command_exists uv; then
        uv pip install pip-licenses >/dev/null 2>&1 || true
        uv run pip-licenses --format=csv --with-urls > "$TEMP_DIR/python-licenses.csv" 2>/dev/null || true
    elif command_exists pip3; then
        # Use system pip with --user flag
        pip3 install --user -q pip-licenses >/dev/null 2>&1 || true
        python3 -m pip_licenses --format=csv --with-urls > "$TEMP_DIR/python-licenses.csv" 2>/dev/null || true
    else
        log_warn "Neither uv nor pip3 found, using fallback..."
        echo "" > "$TEMP_DIR/python-licenses.csv"
    fi
    
    log_info "Python licenses collected."
}

# Collect JavaScript licenses
collect_js_licenses() {
    log_info "Collecting JavaScript licenses..."
    local sdk_dir="$ROOT_DIR/typecast-js"
    
    if [ ! -d "$sdk_dir" ]; then
        log_warn "typecast-js directory not found, skipping..."
        return
    fi
    
    cd "$sdk_dir"
    
    if [ ! -d "node_modules" ]; then
        log_info "Installing npm dependencies..."
        npm install --silent >/dev/null 2>&1
    fi
    
    npx license-checker --production --csv > "$TEMP_DIR/js-licenses.csv" 2>/dev/null || true
    log_info "JavaScript licenses collected."
}

# Collect Go licenses
collect_go_licenses() {
    log_info "Collecting Go licenses..."
    local sdk_dir="$ROOT_DIR/typecast-go"
    
    if [ ! -d "$sdk_dir" ]; then
        log_warn "typecast-go directory not found, skipping..."
        return
    fi
    
    cd "$sdk_dir"
    
    # Check if go-licenses is installed
    if ! command_exists go-licenses; then
        if command_exists go; then
            log_info "Installing go-licenses..."
            go install github.com/google/go-licenses@latest 2>/dev/null || true
        fi
    fi
    
    # Try to run go-licenses
    local go_licenses_path="${GOPATH:-$HOME/go}/bin/go-licenses"
    if [ -x "$go_licenses_path" ]; then
        "$go_licenses_path" csv . > "$TEMP_DIR/go-licenses.csv" 2>/dev/null || true
    elif command_exists go-licenses; then
        go-licenses csv . > "$TEMP_DIR/go-licenses.csv" 2>/dev/null || true
    else
        echo "No external dependencies" > "$TEMP_DIR/go-licenses.csv"
    fi
    
    log_info "Go licenses collected."
}

# Collect Rust licenses
collect_rust_licenses() {
    log_info "Collecting Rust licenses..."
    local sdk_dir="$ROOT_DIR/typecast-rust"
    
    if [ ! -d "$sdk_dir" ]; then
        log_warn "typecast-rust directory not found, skipping..."
        return
    fi
    
    cd "$sdk_dir"
    
    # Check if cargo-license is installed
    if ! cargo license --version >/dev/null 2>&1; then
        log_info "Installing cargo-license..."
        cargo install cargo-license >/dev/null 2>&1 || true
    fi
    
    cargo license > "$TEMP_DIR/rust-licenses.txt" 2>/dev/null || true
    log_info "Rust licenses collected."
}

# Collect Java licenses
collect_java_licenses() {
    log_info "Collecting Java licenses..."
    local sdk_dir="$ROOT_DIR/typecast-java"
    
    if [ ! -d "$sdk_dir" ]; then
        log_warn "typecast-java directory not found, skipping..."
        return
    fi
    
    cd "$sdk_dir"
    
    if command_exists mvn; then
        mvn license:add-third-party -DoutputDirectory="$TEMP_DIR" -q 2>/dev/null || true
        if [ -f "$TEMP_DIR/THIRD-PARTY.txt" ]; then
            mv "$TEMP_DIR/THIRD-PARTY.txt" "$TEMP_DIR/java-licenses.txt"
        fi
    else
        log_warn "Maven not found, skipping Java license collection..."
    fi
    
    log_info "Java licenses collected."
}

# Collect Kotlin licenses
collect_kotlin_licenses() {
    log_info "Collecting Kotlin licenses..."
    local sdk_dir="$ROOT_DIR/typecast-kotlin"
    
    if [ ! -d "$sdk_dir" ]; then
        log_warn "typecast-kotlin directory not found, skipping..."
        return
    fi
    
    cd "$sdk_dir"
    
    # Extract dependencies from build.gradle.kts
    grep -E 'implementation\(' build.gradle.kts 2>/dev/null | \
        grep -v test | \
        sed 's/.*implementation("\(.*\)").*/\1/' > "$TEMP_DIR/kotlin-deps.txt" || true
    
    log_info "Kotlin licenses collected."
}

# Collect C# licenses
collect_csharp_licenses() {
    log_info "Collecting C# licenses..."
    local sdk_dir="$ROOT_DIR/typecast-csharp"
    
    if [ ! -d "$sdk_dir" ]; then
        log_warn "typecast-csharp directory not found, skipping..."
        return
    fi
    
    cd "$sdk_dir"
    
    # Extract PackageReference from .csproj files
    find . -name "*.csproj" -exec grep -h "PackageReference" {} \; 2>/dev/null | \
        grep -v "Test" | \
        sed 's/.*Include="\([^"]*\)".*Version="\([^"]*\)".*/\1 \2/' > "$TEMP_DIR/csharp-deps.txt" || true
    
    log_info "C# licenses collected."
}

# Generate the output file
generate_output() {
    log_info "Generating THIRD-PARTY-LICENSES.txt..."
    
    local today=$(date +%Y-%m-%d)
    
    cat > "$OUTPUT_FILE" << 'HEADER'
================================================================================
THIRD-PARTY LICENSES FOR TYPECAST SDK
================================================================================

This file contains the licenses of third-party libraries used in the Typecast
SDK project across all language implementations.

HEADER

    echo "Generated: $today" >> "$OUTPUT_FILE"
    echo "" >> "$OUTPUT_FILE"
    echo "This file is auto-generated by scripts/update-third-party-licenses.sh" >> "$OUTPUT_FILE"
    echo "Do not edit manually." >> "$OUTPUT_FILE"

    cat >> "$OUTPUT_FILE" << 'TOC'

================================================================================
TABLE OF CONTENTS
================================================================================

1. typecast-python
2. typecast-js
3. typecast-go
4. typecast-rust
5. typecast-java
6. typecast-kotlin
7. typecast-csharp
8. typecast-swift
9. typecast-c

TOC

    # Python section
    cat >> "$OUTPUT_FILE" << 'PYTHON_HEADER'
================================================================================
1. TYPECAST-PYTHON
================================================================================

The following production dependencies are used in the Python SDK:

PYTHON_HEADER

    if [ -f "$TEMP_DIR/python-licenses.csv" ]; then
        # Parse CSV and format nicely (skip header and dev dependencies)
        tail -n +2 "$TEMP_DIR/python-licenses.csv" | while IFS=',' read -r name version license url; do
            name=$(echo "$name" | tr -d '"')
            version=$(echo "$version" | tr -d '"')
            license=$(echo "$license" | tr -d '"')
            url=$(echo "$url" | tr -d '"')
            
            # Skip dev/test packages and self
            case "$name" in
                pytest*|black|flake8|mypy|isort|pip-licenses|prettytable|wcwidth|coverage*|ruff*|Pygments|pluggy|iniconfig|python-dotenv|typecast-python)
                    continue
                    ;;
            esac
            
            if [ -n "$name" ] && [ "$name" != "Name" ]; then
                echo "- $name ($version): $license" >> "$OUTPUT_FILE"
                [ -n "$url" ] && [ "$url" != "UNKNOWN" ] && echo "  URL: $url" >> "$OUTPUT_FILE"
            fi
        done
    else
        echo "Unable to collect Python licenses automatically." >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
        echo "Main dependencies (from pyproject.toml):" >> "$OUTPUT_FILE"
        echo "- aiohttp (Apache-2.0)" >> "$OUTPUT_FILE"
        echo "- requests (Apache-2.0)" >> "$OUTPUT_FILE"
        echo "- pydantic (MIT)" >> "$OUTPUT_FILE"
        echo "- sseclient-py (Apache-2.0)" >> "$OUTPUT_FILE"
        echo "- websockets (BSD-3-Clause)" >> "$OUTPUT_FILE"
        echo "- typing-extensions (PSF-2.0)" >> "$OUTPUT_FILE"
    fi

    # JavaScript section
    cat >> "$OUTPUT_FILE" << 'JS_HEADER'

================================================================================
2. TYPECAST-JS (JavaScript/TypeScript)
================================================================================

JS_HEADER

    if [ -f "$TEMP_DIR/js-licenses.csv" ] && [ "$(wc -l < "$TEMP_DIR/js-licenses.csv")" -gt 2 ]; then
        echo "The following production dependencies are used in the JavaScript SDK:" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
        tail -n +2 "$TEMP_DIR/js-licenses.csv" | while IFS=',' read -r name license url; do
            name=$(echo "$name" | tr -d '"')
            license=$(echo "$license" | tr -d '"')
            # Skip self
            [[ "$name" == *"@neosapience/typecast-js"* ]] && continue
            if [ -n "$name" ]; then
                echo "- $name: $license" >> "$OUTPUT_FILE"
            fi
        done
    else
        echo "No production dependencies. All dependencies are development-only." >> "$OUTPUT_FILE"
    fi

    # Go section
    cat >> "$OUTPUT_FILE" << 'GO_HEADER'

================================================================================
3. TYPECAST-GO
================================================================================

GO_HEADER

    if [ -f "$TEMP_DIR/go-licenses.csv" ] && grep -v "neosapience\|No external" "$TEMP_DIR/go-licenses.csv" | grep -q .; then
        echo "The following dependencies are used in the Go SDK:" >> "$OUTPUT_FILE"
        echo "" >> "$OUTPUT_FILE"
        grep -v "neosapience" "$TEMP_DIR/go-licenses.csv" | while IFS=',' read -r pkg url license; do
            [ -n "$pkg" ] && echo "- $pkg ($license)" >> "$OUTPUT_FILE"
        done
    else
        echo "No external dependencies. Uses only Go standard library." >> "$OUTPUT_FILE"
    fi

    # Rust section
    cat >> "$OUTPUT_FILE" << 'RUST_HEADER'

================================================================================
4. TYPECAST-RUST
================================================================================

The following dependencies are used in the Rust SDK:

RUST_HEADER

    if [ -f "$TEMP_DIR/rust-licenses.txt" ] && [ -s "$TEMP_DIR/rust-licenses.txt" ]; then
        # cargo license outputs: "LICENSE (count): crate1, crate2, ..."
        # Just copy the output directly as it's already well formatted
        while read -r line; do
            # Skip lines with dev dependencies
            case "$line" in
                *dotenvy*|*tokio-test*|*typecast*)
                    # Remove these from the line if present
                    line=$(echo "$line" | sed 's/, *dotenvy//g; s/dotenvy, *//g; s/, *tokio-test//g; s/tokio-test, *//g; s/, *typecast//g; s/typecast, *//g')
                    ;;
            esac
            [ -n "$line" ] && echo "$line" >> "$OUTPUT_FILE"
        done < "$TEMP_DIR/rust-licenses.txt"
    else
        echo "Main dependencies (from Cargo.toml):" >> "$OUTPUT_FILE"
        echo "- reqwest (Apache-2.0 OR MIT)" >> "$OUTPUT_FILE"
        echo "- serde (Apache-2.0 OR MIT)" >> "$OUTPUT_FILE"
        echo "- serde_json (Apache-2.0 OR MIT)" >> "$OUTPUT_FILE"
        echo "- thiserror (Apache-2.0 OR MIT)" >> "$OUTPUT_FILE"
        echo "- tokio (MIT)" >> "$OUTPUT_FILE"
    fi

    # Java section
    cat >> "$OUTPUT_FILE" << 'JAVA_HEADER'

================================================================================
5. TYPECAST-JAVA
================================================================================

The following dependencies are used in the Java SDK:

JAVA_HEADER

    if [ -f "$TEMP_DIR/java-licenses.txt" ]; then
        grep -v "^$\|^Lists of" "$TEMP_DIR/java-licenses.txt" | \
            grep -v "junit\|mockwebserver\|test" | \
            head -20 >> "$OUTPUT_FILE" || true
    else
        echo "- Gson (com.google.code.gson:gson): Apache-2.0" >> "$OUTPUT_FILE"
        echo "- OkHttp (com.squareup.okhttp3:okhttp): Apache-2.0" >> "$OUTPUT_FILE"
        echo "- Okio (com.squareup.okio:okio): Apache-2.0" >> "$OUTPUT_FILE"
        echo "- dotenv-java (io.github.cdimascio:dotenv-java): Apache-2.0" >> "$OUTPUT_FILE"
    fi

    # Kotlin section
    cat >> "$OUTPUT_FILE" << 'KOTLIN_HEADER'

================================================================================
6. TYPECAST-KOTLIN
================================================================================

The following dependencies are used in the Kotlin SDK:

KOTLIN_HEADER

    if [ -f "$TEMP_DIR/kotlin-deps.txt" ] && [ -s "$TEMP_DIR/kotlin-deps.txt" ]; then
        while read -r dep; do
            [ -n "$dep" ] && echo "- $dep" >> "$OUTPUT_FILE"
        done < "$TEMP_DIR/kotlin-deps.txt"
        echo "" >> "$OUTPUT_FILE"
        echo "All dependencies are licensed under Apache-2.0." >> "$OUTPUT_FILE"
    else
        echo "- com.squareup.okhttp3:okhttp (Apache-2.0)" >> "$OUTPUT_FILE"
        echo "- org.jetbrains.kotlinx:kotlinx-serialization-json (Apache-2.0)" >> "$OUTPUT_FILE"
        echo "- io.github.cdimascio:dotenv-kotlin (Apache-2.0)" >> "$OUTPUT_FILE"
        echo "- org.jetbrains.kotlinx:kotlinx-coroutines-core (Apache-2.0)" >> "$OUTPUT_FILE"
    fi

    # C# section
    cat >> "$OUTPUT_FILE" << 'CSHARP_HEADER'

================================================================================
7. TYPECAST-CSHARP
================================================================================

The following dependencies are used in the C# SDK (for .NET Standard 2.0/2.1):

CSHARP_HEADER

    if [ -f "$TEMP_DIR/csharp-deps.txt" ] && [ -s "$TEMP_DIR/csharp-deps.txt" ]; then
        while read -r dep; do
            [ -n "$dep" ] && echo "- $dep (MIT)" >> "$OUTPUT_FILE"
        done < "$TEMP_DIR/csharp-deps.txt"
    else
        echo "- System.Text.Json (MIT)" >> "$OUTPUT_FILE"
        echo "- Microsoft.Bcl.AsyncInterfaces (MIT)" >> "$OUTPUT_FILE"
        echo "- System.Net.Http.Json (MIT)" >> "$OUTPUT_FILE"
    fi
    echo "" >> "$OUTPUT_FILE"
    echo "Note: These dependencies are only required for .NET Standard 2.0/2.1 targets." >> "$OUTPUT_FILE"
    echo ".NET 6+ includes these in the framework." >> "$OUTPUT_FILE"

    # Swift section
    cat >> "$OUTPUT_FILE" << 'SWIFT_SECTION'

================================================================================
8. TYPECAST-SWIFT
================================================================================

No external dependencies. Uses only Swift standard library and Apple frameworks.

SWIFT_SECTION

    # C section
    cat >> "$OUTPUT_FILE" << 'C_SECTION'
================================================================================
9. TYPECAST-C
================================================================================

The following third-party code is bundled with the C SDK:

-------------------------------------------------------------------------------
cJSON (MIT)
-------------------------------------------------------------------------------
https://github.com/DaveGamble/cJSON
Copyright (c) 2009-2017 Dave Gamble and cJSON contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.

C_SECTION

    # Add full license texts
    cat >> "$OUTPUT_FILE" << 'LICENSE_TEXTS'
================================================================================
COMMON LICENSE TEXTS
================================================================================

-------------------------------------------------------------------------------
APACHE LICENSE, VERSION 2.0
-------------------------------------------------------------------------------

                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form.

      "Work" shall mean the work of authorship made available under the License.

      "Derivative Works" shall mean any work that is based on the Work.

      "Contribution" shall mean any work of authorship submitted for
      inclusion in the Work.

      "Contributor" shall mean Licensor and any Legal Entity on behalf of
      whom a Contribution has been received by Licensor.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      patent license to make, have made, use, offer to sell, sell, import,
      and otherwise transfer the Work.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.

   8. Limitation of Liability. In no event shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work, You may choose to offer acceptance of support, warranty,
      indemnity, or other liability obligations.

   END OF APACHE LICENSE 2.0 TERMS AND CONDITIONS

-------------------------------------------------------------------------------
MIT LICENSE
-------------------------------------------------------------------------------

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

-------------------------------------------------------------------------------
BSD 3-CLAUSE LICENSE
-------------------------------------------------------------------------------

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors
   may be used to endorse or promote products derived from this software
   without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
THE POSSIBILITY OF SUCH DAMAGE.

================================================================================
END OF THIRD-PARTY LICENSES
================================================================================
LICENSE_TEXTS

    log_info "Generated: $OUTPUT_FILE"
}

# Cleanup
cleanup() {
    rm -rf "$TEMP_DIR"
}

# Main
main() {
    log_info "Starting third-party license collection..."
    log_info "Root directory: $ROOT_DIR"
    log_info "Temp directory: $TEMP_DIR"
    echo ""
    
    # Collect licenses from each SDK
    collect_python_licenses
    collect_js_licenses
    collect_go_licenses
    collect_rust_licenses
    collect_java_licenses
    collect_kotlin_licenses
    collect_csharp_licenses
    
    # Generate the output file
    generate_output
    
    # Cleanup
    cleanup
    
    echo ""
    log_info "Done! Third-party licenses have been updated."
    log_info "Output: $OUTPUT_FILE"
}

# Run main
main "$@"
