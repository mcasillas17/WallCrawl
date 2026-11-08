import assert from 'node:assert/strict';
import { mkdtemp, readFile, writeFile, symlink, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import { tracePng, traceFile, selfTest } from './trace_condition_art.mjs';
const require = createRequire(new URL('./authoring/package.json', import.meta.url));
const sharp = require('sharp');

await selfTest();
await assert.rejects(tracePng(Buffer.from('not a PNG')));
const empty = await sharp({ create: { width: 8, height: 8, channels: 4, background: '#0000' } }).png().toBuffer();
await assert.rejects(tracePng(empty), /empty/i);
const opaque = await sharp({ create: { width: 8, height: 8, channels: 3, background: '#fff' } }).png().toBuffer();
await assert.rejects(tracePng(opaque), /alpha/i);
const largeHeader = Buffer.from(empty);
largeHeader.writeUInt32BE(20000, 16);
await assert.rejects(tracePng(largeHeader));
// Thin artificial curves at retained-master resolution catch premature raster downsampling.
const thinPng = await sharp(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="1254" height="1254"><path fill="none" stroke="white" stroke-width="3" stroke-linecap="round" d="M100 1100C250 100 900 150 1100 1000M150 1100C300 180 950 220 1150 1050M100 200L1100 850M200 150L1050 1150"/></svg>')).png().toBuffer();
const thin = await tracePng(thinPng);
const expectedThin = await sharp(thinPng).resize(320, 320).ensureAlpha().raw().toBuffer();
const actualThin = await sharp(Buffer.from(thin.svg)).resize(320, 320).ensureAlpha().raw().toBuffer();
let sourceAlpha = 0, alphaError = 0;
for (let i = 3; i < expectedThin.length; i += 4) {
  sourceAlpha += expectedThin[i]; alphaError += Math.abs(expectedThin[i] - actualThin[i]);
}
console.log(JSON.stringify({ artificialThinContourAlphaError: alphaError / sourceAlpha }));
assert(alphaError / sourceAlpha < 0.20, 'Thin-contour tracing loses detail before vector scaling');
const tallPng = await sharp(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="200" height="400"><path fill="white" d="M50 150h100v100h-100z"/></svg>')).png().toBuffer();
const tall = await tracePng(tallPng);
const tallPixels = await sharp(Buffer.from(tall.svg)).ensureAlpha().raw().toBuffer();
const coordinates = [];
for (let y = 0; y < 512; y++) for (let x = 0; x < 512; x++) {
  if (tallPixels[(y * 512 + x) * 4 + 3] >= 128) coordinates.push([x, y]);
}
const xs = coordinates.map(point => point[0]), ys = coordinates.map(point => point[1]);
assert(Math.abs((Math.min(...xs) + Math.max(...xs)) / 2 - 255.5) < 1, 'Nonsquare input is not horizontally centered');
assert(Math.abs((Math.min(...ys) + Math.max(...ys)) / 2 - 255.5) < 1, 'Nonsquare input is not vertically centered');
assert(Math.abs(Math.max(...xs) - Math.min(...xs) + 1 - 128) <= 2, 'Nonsquare input was stretched');
const dir = await mkdtemp(path.join(os.tmpdir(), 'wallcrawl-artificial-geometry-'));
const master = path.join(dir, 'artificial.png');
const target = path.join(dir, 'artificial.svg');
try {
  await writeFile(master, empty);
  await assert.rejects(traceFile(master, target));
  await assert.rejects(readFile(target), /ENOENT/);
  await assert.rejects(traceFile(master, path.join(dir, 'bad.png')), /svg/i);
  await assert.rejects(traceFile(master, `${dir}/../escape.svg`), /path/i);
  await symlink(master, target);
  await assert.rejects(traceFile(master, target), /symlink/i);
  await rm(target);
  const shape = await sharp(Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64"><path fill="white" d="M10 10h20v20h-20z"/></svg>')).png().toBuffer();
  await writeFile(master, shape);
  const record = await traceFile(master, target);
  assert((await readFile(master)).equals(shape), 'Tracing changed PNG bytes');
  assert.equal(record.inputSha256.length, 64);
  assert.deepEqual(JSON.parse(await readFile(target + '.trace.json', 'utf8')), record);
  assert.match(await readFile(target, 'utf8'), /viewBox="0 0 512 512"/);
  await assert.rejects(traceFile(master, target), /exists/i);
  console.log('Trace boundary checks passed (artificial geometry only).');
} finally { await rm(dir, { recursive: true, force: true }); }
