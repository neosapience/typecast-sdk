#!/usr/bin/env -S tsx
import { createServer } from './server.ts';

interface CliArgs {
  port: number;
  fixturesDir: string;
}

function parseArgs(argv: string[]): CliArgs {
  let port = 8765;
  let fixturesDir = new URL('../fixtures', import.meta.url).pathname;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--port') {
      const raw = argv[++i];
      if (raw === undefined) {
        console.error('--port requires a value');
        process.exit(2);
      }
      const parsed = Number(raw);
      if (!Number.isInteger(parsed) || parsed < 1 || parsed > 65535) {
        console.error(`invalid --port: ${raw} (must be an integer in 1..65535)`);
        process.exit(2);
      }
      port = parsed;
    } else if (arg === '--fixtures-dir') {
      const dir = argv[++i];
      if (dir === undefined || dir.length === 0) {
        console.error('--fixtures-dir requires a value');
        process.exit(2);
      }
      fixturesDir = dir;
    } else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    } else {
      console.error(`unknown argument: ${arg}`);
      printHelp();
      process.exit(2);
    }
  }

  return { port, fixturesDir };
}

function printHelp(): void {
  console.log('Usage: tsx src/index.ts [--port PORT] [--fixtures-dir DIR]');
  console.log('  --port           Port to listen on (default 8765)');
  console.log('  --fixtures-dir   Directory containing fixtures (default ../fixtures)');
}

async function main(): Promise<void> {
  const args = parseArgs(process.argv.slice(2));
  const handle = await createServer(args);
  console.log(`mock server listening on ${handle.url}`);
  console.log(`fixtures dir: ${args.fixturesDir}`);

  const shutdown = async (signal: string): Promise<void> => {
    console.log(`received ${signal}, shutting down...`);
    try {
      await handle.close();
      process.exit(0);
    } catch (err) {
      console.error('shutdown failed:', err);
      process.exit(1);
    }
  };

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
}

void main().catch((err) => {
  console.error('mock server failed to start:', err);
  process.exit(1);
});
