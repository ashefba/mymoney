#!/usr/bin/env node
// Renders the PNG and ICO app icons from desktop/assets/icon.svg and icon-maskable.svg.
// It has no dependencies so it runs in Vercel's build and in GitHub Actions without an install step.
// Existing files are left alone unless --force is passed.
//
// The parser supports exactly what those SVGs use: one linearGradient, one rect (optionally rounded)
// filled with it, and one solid path of M/L/H/V/Z commands with an optional translate/scale transform.
import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { deflateSync } from "node:zlib";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const ASSETS = join(ROOT, "desktop", "assets");
const FORCE = process.argv.includes("--force");

const PNGS = [
  { file: "icon-192.png", svg: "icon.svg", size: 192 },
  { file: "icon-512.png", svg: "icon.svg", size: 512 },
  { file: "icon-maskable-512.png", svg: "icon-maskable.svg", size: 512 },
  { file: "apple-touch-icon.png", svg: "icon-maskable.svg", size: 180 },
];
const ICO = { file: "icon.ico", svg: "icon.svg", sizes: [16, 24, 32, 48, 64, 128, 256] };

function write(file, build) {
  const target = join(ASSETS, file);
  const label = relative(ROOT, target);
  if (!FORCE && existsSync(target)) {
    console.log(`kept ${label}`);
    return;
  }
  writeFileSync(target, build());
  console.log(`wrote ${label}`);
}

// ---- SVG subset -------------------------------------------------------------------------------

function parseIcon(svg) {
  const viewBox = numbers(attr(tag(svg, "svg"), "viewBox"));
  if (viewBox.length !== 4 || viewBox[2] !== viewBox[3]) throw new Error("Expected a square viewBox");

  const gradientTag = tag(svg, "linearGradient");
  const vector = ["x1", "y1", "x2", "y2"].map((name, i) => Number(attr(gradientTag, name) ?? (i === 2 ? 1 : 0)));
  const stops = [...svg.matchAll(/<stop\b([^>]*)>/g)].map(([, attrs]) => ({
    offset: Number(attr(attrs, "offset") ?? 0),
    color: hexColor(attr(attrs, "stop-color")),
  }));
  if (stops.length === 0) throw new Error("The gradient has no stops");

  const rectTag = tag(svg, "rect");
  const rect = {
    x: Number(attr(rectTag, "x") ?? 0),
    y: Number(attr(rectTag, "y") ?? 0),
    width: Number(attr(rectTag, "width")),
    height: Number(attr(rectTag, "height")),
  };
  rect.rx = Number(attr(rectTag, "rx") ?? attr(rectTag, "ry") ?? 0);
  rect.ry = Number(attr(rectTag, "ry") ?? rect.rx);

  const pathTag = tag(svg, "path");
  const transform = parseTransform(attr(pathTag, "transform") ?? "");
  const rings = parsePath(attr(pathTag, "d")).map(ring => ring.map(transform));
  const pathColor = hexColor(attr(pathTag, "fill"));

  const gradientAt = (u, v) => {
    const px = (u - rect.x) / rect.width;
    const py = (v - rect.y) / rect.height;
    const [x1, y1, x2, y2] = vector;
    const dx = x2 - x1;
    const dy = y2 - y1;
    const t = Math.min(1, Math.max(0, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)));
    let i = 0;
    while (i < stops.length - 1 && t > stops[i + 1].offset) i += 1;
    const from = stops[i];
    const to = stops[Math.min(i + 1, stops.length - 1)];
    const span = to.offset - from.offset;
    const k = span > 0 ? (t - from.offset) / span : 0;
    return from.color.map((c, j) => c + (to.color[j] - c) * k);
  };

  return {
    viewBox,
    colorAt(u, v) {
      if (insideRings(rings, u, v)) return pathColor;
      if (insideRoundedRect(rect, u, v)) return gradientAt(u, v);
      return null;
    },
  };
}

function tag(svg, name) {
  const match = svg.match(new RegExp(`<${name}\\b([^>]*)>`));
  if (!match) throw new Error(`Missing <${name}>`);
  return match[1];
}

function attr(attrs, name) {
  const match = attrs.match(new RegExp(`(?:^|\\s)${name}="([^"]*)"`));
  return match ? match[1] : undefined;
}

function numbers(text) {
  return (text.match(/-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g) ?? []).map(Number);
}

function hexColor(value) {
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(value ?? "")?.[1];
  if (!hex) throw new Error(`Unsupported colour "${value}"`);
  const full = hex.length === 3 ? [...hex].map(c => c + c).join("") : hex;
  return [0, 2, 4].map(i => parseInt(full.slice(i, i + 2), 16));
}

function parsePath(d) {
  const tokens = d.match(/[A-Za-z]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?/g) ?? [];
  const rings = [];
  let ring = null;
  let x = 0;
  let y = 0;
  let command = null;
  let i = 0;
  const next = () => Number(tokens[i++]);
  while (i < tokens.length) {
    if (/[A-Za-z]/.test(tokens[i])) command = tokens[i++];
    else if (command === "M") command = "L";
    else if (command === "m") command = "l";
    switch (command) {
      case "M": x = next(); y = next(); rings.push(ring = []); break;
      case "m": x += next(); y += next(); rings.push(ring = []); break;
      case "L": x = next(); y = next(); break;
      case "l": x += next(); y += next(); break;
      case "H": x = next(); break;
      case "h": x += next(); break;
      case "V": y = next(); break;
      case "v": y += next(); break;
      case "Z":
      case "z": continue;
      default: throw new Error(`Unsupported path command "${command}"`);
    }
    ring.push([x, y]);
  }
  return rings;
}

function parseTransform(text) {
  const steps = [...text.matchAll(/(translate|scale)\(([^)]*)\)/g)].map(([, kind, args]) => {
    const [a, b] = numbers(args);
    return kind === "translate"
      ? ([x, y]) => [x + a, y + (b ?? 0)]
      : ([x, y]) => [x * a, y * (b ?? a)];
  });
  if (text.replace(/(translate|scale)\([^)]*\)/g, "").trim()) throw new Error(`Unsupported transform "${text}"`);
  // SVG applies the rightmost transform to the point first.
  return point => steps.reduceRight((p, step) => step(p), point);
}

function insideRings(rings, u, v) {
  let inside = false;
  for (const ring of rings) {
    for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
      const [xi, yi] = ring[i];
      const [xj, yj] = ring[j];
      if ((yi > v) !== (yj > v) && u < ((xj - xi) * (v - yi)) / (yj - yi) + xi) inside = !inside;
    }
  }
  return inside;
}

function insideRoundedRect({ x, y, width, height, rx, ry }, u, v) {
  if (u < x || u > x + width || v < y || v > y + height) return false;
  if (rx <= 0 || ry <= 0) return true;
  const cx = Math.min(Math.max(u, x + rx), x + width - rx);
  const cy = Math.min(Math.max(v, y + ry), y + height - ry);
  return ((u - cx) / rx) ** 2 + ((v - cy) / ry) ** 2 <= 1;
}

// ---- Rasterising ------------------------------------------------------------------------------

function render(icon, size) {
  const samples = size >= 128 ? 4 : 8;
  const [minX, minY, viewSize] = icon.viewBox;
  const unitsPerPixel = viewSize / size;
  const rgba = Buffer.alloc(size * size * 4);
  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      let r = 0;
      let g = 0;
      let b = 0;
      let covered = 0;
      for (let sy = 0; sy < samples; sy++) {
        for (let sx = 0; sx < samples; sx++) {
          const color = icon.colorAt(
            minX + (px + (sx + 0.5) / samples) * unitsPerPixel,
            minY + (py + (sy + 0.5) / samples) * unitsPerPixel,
          );
          if (!color) continue;
          r += color[0];
          g += color[1];
          b += color[2];
          covered += 1;
        }
      }
      const i = (py * size + px) * 4;
      if (covered) {
        rgba[i] = Math.round(r / covered);
        rgba[i + 1] = Math.round(g / covered);
        rgba[i + 2] = Math.round(b / covered);
      }
      rgba[i + 3] = Math.round((255 * covered) / (samples * samples));
    }
  }
  return rgba;
}

// ---- PNG and ICO encoding ---------------------------------------------------------------------

function encodePng(size, rgba) {
  const stride = size * 4;
  const filtered = Buffer.alloc((stride + 1) * size);
  for (let y = 0; y < size; y++) {
    const row = y * (stride + 1);
    filtered[row] = 4; // Paeth, which suits the smooth gradient
    for (let x = 0; x < stride; x++) {
      const left = x >= 4 ? rgba[y * stride + x - 4] : 0;
      const up = y > 0 ? rgba[(y - 1) * stride + x] : 0;
      const upLeft = x >= 4 && y > 0 ? rgba[(y - 1) * stride + x - 4] : 0;
      filtered[row + 1 + x] = (rgba[y * stride + x] - paeth(left, up, upLeft)) & 0xff;
    }
  }
  const header = Buffer.alloc(13);
  header.writeUInt32BE(size, 0);
  header.writeUInt32BE(size, 4);
  header[8] = 8; // bits per channel
  header[9] = 6; // RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(filtered, { level: 9 })),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

function paeth(left, up, upLeft) {
  const estimate = left + up - upLeft;
  const toLeft = Math.abs(estimate - left);
  const toUp = Math.abs(estimate - up);
  const toUpLeft = Math.abs(estimate - upLeft);
  if (toLeft <= toUp && toLeft <= toUpLeft) return left;
  return toUp <= toUpLeft ? up : upLeft;
}

function chunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([length, body, crc]);
}

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(bytes) {
  let c = 0xffffffff;
  for (const byte of bytes) c = CRC_TABLE[(c ^ byte) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

// Windows reads PNG-compressed entries in .ico files, which keeps every size crisp and small.
function encodeIco(images) {
  const header = Buffer.alloc(6);
  header.writeUInt16LE(1, 2); // type: icon
  header.writeUInt16LE(images.length, 4);
  const directory = Buffer.alloc(16 * images.length);
  let offset = header.length + directory.length;
  images.forEach(({ size, png }, i) => {
    const entry = i * 16;
    directory[entry] = size >= 256 ? 0 : size;
    directory[entry + 1] = size >= 256 ? 0 : size;
    directory.writeUInt16LE(1, entry + 4); // colour planes
    directory.writeUInt16LE(32, entry + 6); // bits per pixel
    directory.writeUInt32LE(png.length, entry + 8);
    directory.writeUInt32LE(offset, entry + 12);
    offset += png.length;
  });
  return Buffer.concat([header, directory, ...images.map(image => image.png)]);
}

// ---- Main -------------------------------------------------------------------------------------

const icons = new Map();
const iconFor = name => {
  if (!icons.has(name)) icons.set(name, parseIcon(readFileSync(join(ASSETS, name), "utf8")));
  return icons.get(name);
};

for (const { file, svg, size } of PNGS) {
  write(file, () => encodePng(size, render(iconFor(svg), size)));
}
write(ICO.file, () => encodeIco(ICO.sizes.map(size => ({ size, png: encodePng(size, render(iconFor(ICO.svg), size)) }))));
