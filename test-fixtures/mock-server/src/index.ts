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
      port = Number(argv[++i]);
    } else if (arg === '--fixtures-dir') {
      fixturesDir = argv[++i];
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
    await handle.close();
    process.exit(0);
  };

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
}

void main();
