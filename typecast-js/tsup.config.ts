import { defineConfig } from 'tsup';

export default defineConfig({
  entry: ['src/index.ts'],
  format: ['esm', 'cjs'],
  dts: true,
  splitting: false,
  sourcemap: true,
  clean: true,
  outDir: 'lib',
  minify: false, // Keep readable for debugging, enable for production if needed
  treeshake: true, // Remove unused code
  target: 'es2020',
}); 