# Examples

Examples for using the Typecast TypeScript SDK.

## Setup

1. Build the SDK from the root directory:
```bash
cd ..
npm run build
```

2. Install dependencies:
```bash
npm install
```

3. Set up API key in the **root directory** `.env` file:
```bash
# In the root directory (not in examples/)
echo "TYPECAST_API_KEY=your_api_key_here" > ../.env
```

4. Run the example:
```bash
npm test
```

The example will automatically load the `.env` file from the parent directory.
