import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['packages/**/*.test.ts', 'apps/**/*.test.ts', 'apps/**/*.test.tsx'],
    exclude: ['**/node_modules/**', '**/build/**', '**/dist/**'],
    environment: 'node',
    setupFiles: ['apps/face-web/src/test/setup.ts']
  }
});
