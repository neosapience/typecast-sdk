#!/bin/bash
set -e

echo "🔨 Building package..."
npm run build

echo "📦 Creating tarball..."
npm pack

echo "📂 Creating test directory..."
TEST_DIR=$(mktemp -d)
cd $TEST_DIR

echo "📥 Installing package from tarball..."
TARBALL=$(ls $OLDPWD/neosapience-typecast-js-*.tgz | head -1)
npm init -y
npm install "$TARBALL"

echo "✅ Testing CommonJS..."
cat > test-cjs.js << 'EOF'
const { TypecastClient } = require('@neosapience/typecast-js');
console.log('✓ CommonJS import works');
console.log('✓ TypecastClient:', typeof TypecastClient);
if (typeof TypecastClient !== 'function') {
  console.error('✗ TypecastClient is not a constructor');
  process.exit(1);
}
EOF
node test-cjs.js

echo "✅ Testing ESM..."
cat > test-esm.mjs << 'EOF'
import { TypecastClient } from '@neosapience/typecast-js';
console.log('✓ ESM import works');
console.log('✓ TypecastClient:', typeof TypecastClient);
if (typeof TypecastClient !== 'function') {
  console.error('✗ TypecastClient is not a constructor');
  process.exit(1);
}
EOF
node test-esm.mjs

echo "✅ Testing TypeScript..."
npm install -D typescript @types/node
cat > test-ts.ts << 'EOF'
import { TypecastClient, TTSRequest } from '@neosapience/typecast-js';
const client = new TypecastClient({ apiKey: 'test' });
console.log('✓ TypeScript import works');
console.log('✓ TypeScript types available');
EOF
npx tsc --noEmit test-ts.ts

echo "🎉 All package tests passed!"
echo "📍 Test directory: $TEST_DIR"
echo "🧹 Cleaning up tarball..."
rm -f "$TARBALL"

echo ""
echo "To clean up test directory, run: rm -rf $TEST_DIR"

