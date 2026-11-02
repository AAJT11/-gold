#!/bin/bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$project_root/build/tests"
swiftc -swift-version 5 -parse-as-library "$project_root/Sources/IncomeModel.swift" "$project_root/Tests/IncomeTests.swift" -o "$project_root/build/tests/IncomeTests"
"$project_root/build/tests/IncomeTests"
