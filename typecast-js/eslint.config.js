import js from '@eslint/js';
import tseslint from '@typescript-eslint/eslint-plugin';

export default [
  { ignores: ['lib/**', 'node_modules/**', 'coverage/**'] },
  js.configs.recommended,
  ...tseslint.configs['flat/recommended-type-checked'],
  {
    files: ['src/**/*.ts'],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.json', './examples/tsconfig.json'],
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      // Preserve the previous recommended checks during this tooling-only upgrade.
      'preserve-caught-error': 'off',
      'no-useless-assignment': 'off',
      '@typescript-eslint/explicit-function-return-type': 'off',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      camelcase: ['error', { properties: 'never' }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
  },
];
