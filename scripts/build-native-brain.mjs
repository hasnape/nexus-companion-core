import { build } from 'esbuild';
import { transformAsync } from '@babel/core';
import presetEnv from '@babel/preset-env';
import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const destination = path.join(root, 'apps/companion-android/app/src/main/assets/brain');
const check = process.argv.includes('--check');
const result = await build({
  entryPoints: [path.join(root, 'packages/companion-core/src/native-bridge.ts')],
  absWorkingDir: root, metafile: true,
  bundle: true, write: false, format: 'iife', globalName: 'NexusNativeBrain',
  platform: 'neutral', target: 'es2015', charset: 'utf8', legalComments: 'none',
  minify: false, sourcemap: false
});
// Rhino implements modern built-ins but only part of the ES2015 syntax.
// Emit standard ES5 syntax through a compiler, never custom text rewriting.
const transformed = await transformAsync(result.outputFiles[0].text, {
  presets: [[presetEnv, { targets: { ie: '11' }, modules: false, useBuiltIns: false }]],
  configFile: false, babelrc: false, comments: false, compact: false
});
if (!transformed?.code) throw new Error('Native brain compiler returned no code');
const script = Buffer.from(`${transformed.code}\n`, 'utf8');
const sourceFiles = [...Object.keys(result.metafile.inputs), 'scripts/build-native-brain.mjs', 'package.json', 'package-lock.json'].sort();
const sources = Object.fromEntries(await Promise.all(sourceFiles.map(async (filename) => [
  filename, createHash('sha256').update(await readFile(path.join(root, filename))).digest('hex')
])));
const metadata = `${JSON.stringify({
  protocolVersion: 1, engine: 'nexus-core-rules-v1',
  sha256: createHash('sha256').update(script).digest('hex'), sources
}, null, 2)}\n`;
if (check) {
  const current = await readFile(path.join(destination, 'nexus-brain.js'));
  const manifest = await readFile(path.join(destination, 'manifest.json'), 'utf8');
  if (!current.equals(Buffer.from(script)) || manifest !== metadata) {
    throw new Error('Android brain asset is stale. Run npm run build:native-brain.');
  }
  console.log('Android brain matches the shared TypeScript source.');
} else {
  await mkdir(destination, { recursive: true });
  await writeFile(path.join(destination, 'nexus-brain.js'), script);
  await writeFile(path.join(destination, 'manifest.json'), metadata);
  console.log(`Android brain bundled (${script.length} bytes).`);
}
