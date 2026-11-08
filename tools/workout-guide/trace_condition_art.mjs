#!/usr/bin/env node
/** Offline PNG-alpha tracing only. Never edits a master or creates exercise artwork. */
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { lstat, readFile, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(new URL('./authoring/package.json', import.meta.url));
const sharp = require('sharp');
const { Potrace } = require('potrace');
// Pass a bounded raw bitmap, never a URL/file/encoded buffer, into Jimp/Potrace.
const Jimp = createRequire(require.resolve('potrace'))('jimp');
const { optimize } = require('svgo');
const options = {
  decode: { limitInputPixels: 16777216, failOn: 'warning', animated: false },
  resize: { fit: 'fill', kernel: 'lanczos3' },
  canvas: { width: 512, height: 512, fit: 'contain' },
  maskResolution: 'source-or-upscale-to512',
  mask: 'alpha', alphaThreshold: 128,
  potrace: { turnPolicy: 'minority', turdSize: 2, alphaMax: 1, optCurve: true, optTolerance: 0.2, threshold: 128, blackOnWhite: true, color: '#fff', background: 'transparent' },
  svgo: { multipass: false, plugins: ['convertPathData'] },
};
const toolVersions = {
  node: process.version,
  sharp: sharp.versions.sharp,
  potrace: require('potrace/package.json').version,
  svgo: require('svgo').VERSION,
  jimp: createRequire(require.resolve('potrace'))('jimp/package.json').version,
  vips: sharp.versions.vips,
};
const digest = data => createHash('sha256').update(data).digest('hex');

export async function tracePng(input) {
  assert(Buffer.isBuffer(input) && input.length > 8 && input.length <= 16 * 1024 * 1024, 'Invalid bounded PNG input');
  assert(input.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])), 'Input must be PNG');
  const metadata = await sharp(input, options.decode).metadata();
  assert(metadata.format === 'png' && metadata.hasAlpha, 'PNG must retain an alpha channel');
  assert(metadata.pages === undefined || metadata.pages === 1, 'Animated PNG is not supported');
  assert(metadata.width > 0 && metadata.height > 0 && metadata.width <= 8192 && metadata.height <= 8192, 'Invalid PNG dimensions');
  // Keep native contour samples; rasterizing thin linework at 512 first loses geometry.
  const upscale = Math.max(1, 512 / Math.max(metadata.width, metadata.height));
  const resize = { ...options.resize, width: Math.round(metadata.width * upscale), height: Math.round(metadata.height * upscale) };
  assert(resize.width * resize.height <= options.decode.limitInputPixels, 'Oversized trace mask');
  const { data, info } = await sharp(input, options.decode).resize(resize).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  assert(info.channels === 4 && info.width === resize.width && info.height === resize.height, 'Unexpected decoded image');
  const pixelCount = info.width * info.height;
  const rgba = Buffer.alloc(pixelCount * 4);
  let foreground = 0;
  for (let pixel = 0; pixel < pixelCount; pixel++) {
    const black = data[pixel * 4 + 3] >= options.alphaThreshold;
    if (black) foreground++;
    const value = black ? 0 : 255;
    rgba[pixel * 4] = rgba[pixel * 4 + 1] = rgba[pixel * 4 + 2] = value;
    rgba[pixel * 4 + 3] = 255;
  }
  assert(foreground > 0 && foreground < pixelCount, 'Empty or fully opaque alpha mask');
  const tracer = new Potrace(options.potrace);
  const bitmap = new Jimp({ data: rgba, width: info.width, height: info.height });
  await new Promise((resolve, reject) => tracer.loadImage(bitmap, error => error ? reject(error) : resolve()));
  const scale = 512 / Math.max(info.width, info.height);
  const offset = { x: (512 - info.width * scale) / 2, y: (512 - info.height * scale) / 2 };
  let pathTag = tracer.getPathTag('#fff', { x: scale, y: scale }).replace(' stroke="none"', '');
  if (offset.x || offset.y) pathTag = pathTag.replace('<path ', `<path transform="translate(${offset.x} ${offset.y})" `);
  assert(!pathTag.includes('d=""'), 'Empty traced path');
  const unoptimized = `<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">${pathTag}</svg>`;
  const svg = optimize(unoptimized, options.svgo).data + '\n';
  assert(/^<svg[^>]*viewBox="0 0 512 512"[^>]*><path\s/.test(svg) && !/<(?:image|rect|text|script|style|g)\b/.test(svg), 'Trace must be white path-only SVG');
  return { svg, record: { schemaVersion: 1, inputSha256: digest(input), outputSha256: digest(svg), toolVersions, options: { ...options, resize, pathScale: { x: scale, y: scale }, pathOffset: offset } } };
}

async function safeOutput(filename) {
  assert(typeof filename === 'string' && filename.length <= 4096 && !filename.split(/[\\/]/).some(part => part === '..') && !filename.includes('\\'), 'Unsafe output path');
  const parent = await lstat(path.dirname(filename));
  assert(parent.isDirectory() && !parent.isSymbolicLink(), 'Output parent must be an existing nonsymlink directory');
  try {
    const existing = await lstat(filename);
    assert(!existing.isSymbolicLink(), 'Output symlink is forbidden');
    assert(false, 'Output exists; choose a fresh derived path');
  }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
}

export async function traceFile(inputPath, outputPath) {
  assert(inputPath.endsWith('.png') && outputPath.endsWith('.svg'), 'Expected PNG input and SVG output paths');
  await safeOutput(outputPath);
  await safeOutput(outputPath + '.trace.json');
  assert((await stat(inputPath)).size <= 16 * 1024 * 1024, 'Oversized PNG file');
  const input = await readFile(inputPath);
  const result = await tracePng(input); // All validation/tracing precedes either output write.
  await writeFile(outputPath, result.svg, { flag: 'wx' });
  await writeFile(outputPath + '.trace.json', JSON.stringify(result.record, null, 2) + '\n', { flag: 'wx' });
  assert((await readFile(inputPath)).equals(input), 'Master bytes changed during tracing');
  return result.record;
}

export async function selfTest() {
  // Deliberately artificial hollow square and tiny alpha noise; never an exercise stand-in.
  const pixels = Buffer.alloc(64 * 64 * 4);
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    const i = (y * 64 + x) * 4;
    pixels[i] = 73; pixels[i + 1] = 29; pixels[i + 2] = 183;
    const ring = x >= 8 && x < 56 && y >= 8 && y < 56 && (x < 12 || x >= 52 || y < 12 || y >= 52);
    pixels[i + 3] = ring ? 255 : x < 4 && y < 4 ? 10 : 0;
  }
  const png = await sharp(pixels, { raw: { width: 64, height: 64, channels: 4 } }).png().toBuffer();
  const original = Buffer.from(png);
  const first = await tracePng(png);
  const second = await tracePng(png);
  assert(png.equals(original), 'Source PNG bytes/alpha changed');
  assert.equal(first.svg, second.svg, 'Tracing is nondeterministic');
  const { data: rendered } = await sharp(Buffer.from(first.svg)).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const expected = await sharp(png).resize({ ...options.resize, width: 512, height: 512 }).ensureAlpha().raw().toBuffer();
  let intersection = 0, union = 0;
  for (let i = 3; i < rendered.length; i += 4) {
    const a = rendered[i] >= 128, b = expected[i] >= 128;
    intersection += a && b; union += a || b;
  }
  assert(intersection / union > 0.98, 'Trace shape/alpha differs from artificial mask');
  assert.equal(rendered[3], 0, 'Low-alpha noise became opaque');
  assert.equal(rendered[(256 * 512 + 256) * 4 + 3], 0, 'Transparent hole was filled');
  const altered = Buffer.from(pixels);
  for (let i = 0; i < altered.length; i += 4) { altered[i] = 255; altered[i + 1] = 255; altered[i + 2] = 255; }
  const white = await sharp(altered, { raw: { width: 64, height: 64, channels: 4 } }).png().toBuffer();
  assert.equal((await tracePng(white)).svg, first.svg, 'RGB changed the alpha-only trace');
  console.log(JSON.stringify({ selfTest: 'passed-artificial-geometry-only', svgSha256: digest(first.svg), intersectionOverUnion: intersection / union, toolVersions, options: first.record.options }));
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  if (args.length === 1 && args[0] === '--self-test') await selfTest();
  else {
    assert(args.length === 2, 'Usage: trace_condition_art.mjs input.png output.svg | --self-test');
    console.log(JSON.stringify(await traceFile(args[0], args[1])));
  }
}
