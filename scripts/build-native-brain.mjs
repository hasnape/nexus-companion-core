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
// One capacity contract for the TypeScript bridge, Android host and text field.
const capacityPath = 'packages/companion-core/src/native-capacity.json';
const capacity = JSON.parse(await readFile(path.join(root, capacityPath), 'utf8'));
if (!Object.entries(capacity).every(([key, value]) => /^[A-Z_]+$/.test(key) && Number.isSafeInteger(value) && value > 0 && value <= 2147483647)) {
  throw new Error('Invalid native capacity contract');
}
const generated = {
  'apps/companion-android/app/src/main/java/fr/nexuscompanion/android/NativeCapacity.kt':
    `package fr.nexuscompanion.android\n\n// Generated from ${capacityPath}; run npm run build:native-brain.\ninternal object NativeCapacity {\n${Object.entries(capacity).map(([key, value]) => `    const val ${key} = ${value}`).join('\n')}\n}\n`,
  'apps/companion-android/app/src/main/res/values/native_capacity.xml':
    `<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from ${capacityPath}. -->\n<resources>\n    <integer name="nexus_message_chars">${capacity.MAX_MESSAGE_CHARS}</integer>\n    <string name="note_invalid">Note vide ou trop longue (${String(capacity.MAX_NOTE_CHARS).replace(/\B(?=(\d{3})+(?!\d))/g, " ")} caractères maximum).</string>\n</resources>\n`
};
for (const [filename, content] of Object.entries(generated)) {
  if (check) {
    if (await readFile(path.join(root, filename), 'utf8') !== content) throw new Error(`Stale capacity contract: ${filename}`);
  } else await writeFile(path.join(root, filename), content);
}
const recoveredRoot = path.join(root, 'packages/companion-core/recovered-native/0.4.25');
if (!process.argv.includes('--from-typescript')) {
  const script = await readFile(path.join(recoveredRoot, 'nexus-brain.js'));
  if (createHash('sha256').update(script).digest('hex') !== '48da6489e14dd595c48706c0d65f9d3e3c72b83d3d062b22636968bf4deaf779') throw new Error('Recovered 0.4.25 source changed');
  const filenames = ['packages/companion-core/recovered-native/0.4.25/nexus-brain.js', 'packages/companion-core/recovered-native/0.4.25/manifest.json', ...Object.keys(generated), 'scripts/build-native-brain.mjs'];
  const sources = Object.fromEntries(await Promise.all(filenames.map(async filename => [filename, createHash('sha256').update(await readFile(path.join(root, filename))).digest('hex')])));
  const metadata = JSON.stringify({protocolVersion: 1, engine: 'nexus-core-rules-v1', recoveredFrom: 'signed-android-0.4.25', sha256: createHash('sha256').update(script).digest('hex'), sources}, null, 2) + '\n';
  if (check) {
    if (!(await readFile(path.join(destination, 'nexus-brain.js'))).equals(script) || await readFile(path.join(destination, 'manifest.json'), 'utf8') !== metadata) throw new Error('Recovered native brain is stale');
    console.log('Android brain exactly preserves the delivered 0.4.25 runtime.');
  } else {
    await mkdir(destination, {recursive: true});
    await writeFile(path.join(destination, 'nexus-brain.js'), script);
    await writeFile(path.join(destination, 'manifest.json'), metadata);
  }
  process.exit(0);
}
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
const sourceFiles = [...Object.keys(result.metafile.inputs), ...Object.keys(generated), 'scripts/build-native-brain.mjs', 'package.json', 'package-lock.json'].sort();
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
