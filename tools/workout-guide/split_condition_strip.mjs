/** Slice one native three-phase drawing at fixed cell boundaries, without resizing or recoloring. */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { lstat, readFile, readdir, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';

const sharp = createRequire(new URL('./authoring/package.json', import.meta.url))('sharp');
const [source, destination] = process.argv.slice(2);
assert(source && process.argv.length <= 4, 'Usage: node split_condition_strip.mjs native.png [empty-master-directory]');
if (destination) {
  assert((await lstat(destination)).isDirectory() && (await readdir(destination)).length === 0, 'Choose an empty master directory');
}
if (source !== '-') {
  const file = await lstat(source);
  assert(file.isFile() && file.size <= 16 * 1024 * 1024, 'Expected a bounded regular input file');
}
const bytes = source === '-' ? readFileSync(0) : await readFile(source);
assert(bytes.length <= 16 * 1024 * 1024, 'Oversized input strip');
const image = sharp(bytes, { limitInputPixels: 16777216, failOn: 'warning' });
const { format, width, height, hasAlpha, pages } = await image.metadata();
assert(format === 'png' && hasAlpha && width >= 3 && width <= 8192 && height <= 8192 && (pages ?? 1) === 1,
  'Expected a bounded, single-page transparent PNG strip');
const hashes = [];
for (let cell = 0; cell < 3; cell++) {
  const left = Math.floor(width * cell / 3);
  const right = Math.floor(width * (cell + 1) / 3);
  const png = await image.clone().extract({ left, top: 0, width: right - left, height }).png().toBuffer();
  hashes.push(createHash('sha256').update(png).digest('hex'));
  if (destination) await writeFile(path.join(destination, `frame-${cell + 1}.png`), png, { flag: 'wx' });
}
console.log(JSON.stringify(hashes));
