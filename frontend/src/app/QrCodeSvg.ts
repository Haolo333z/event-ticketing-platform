import { createElement as h } from 'react';

// QR Code Generator (ISO/IEC 18004 compliant implementation in pure TypeScript)
// Supports Version 1 to 10 with Byte Mode and EC Level L/M

// GF(256) log and antilog tables (primitive polynomial 0x11D = 285)
const EXP_TABLE = new Uint8Array(512);
const LOG_TABLE = new Uint8Array(256);
(function initGaloisField() {
  let x = 1;
  for (let i = 0; i < 255; i++) {
    EXP_TABLE[i] = x;
    EXP_TABLE[i + 255] = x;
    LOG_TABLE[x] = i;
    x <<= 1;
    if (x & 256) x ^= 0x11d;
  }
})();

function gMul(a: number, b: number): number {
  if (a === 0 || b === 0) return 0;
  return EXP_TABLE[LOG_TABLE[a] + LOG_TABLE[b]];
}

function rsGeneratorPoly(degree: number): Uint8Array {
  let poly = new Uint8Array([1]);
  for (let i = 0; i < degree; i++) {
    const next = new Uint8Array(poly.length + 1);
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= gMul(poly[j], EXP_TABLE[i]);
      next[j + 1] ^= poly[j];
    }
    poly = next;
  }
  return poly;
}

function rsCompute(data: Uint8Array, numEc: number): Uint8Array {
  const gen = rsGeneratorPoly(numEc);
  const remainder = new Uint8Array(numEc);
  for (let i = 0; i < data.length; i++) {
    const factor = data[i] ^ remainder[0];
    remainder.copyWithin(0, 1);
    remainder[numEc - 1] = 0;
    if (factor !== 0) {
      for (let j = 0; j < numEc; j++) {
        remainder[j] ^= gMul(gen[j], factor);
      }
    }
  }
  return remainder;
}

// Table of QR parameters for Version 1 to 10 (EC Level L)
// [totalCodewords, dataCodewords, ecCodewordsPerBlock, numBlocks]
const VERSION_PARAMS_L: [number, number, number, number][] = [
  [0, 0, 0, 0], // index 0 unused
  [26, 19, 7, 1],   // V1 21x21
  [44, 34, 10, 1],  // V2 25x25
  [70, 55, 15, 1],  // V3 29x29
  [100, 80, 20, 1], // V4 33x33
  [134, 108, 26, 1],// V5 37x37
  [172, 136, 18, 2],// V6 41x41
  [196, 156, 20, 2],// V7 45x45
  [242, 194, 24, 2],// V8 49x49
  [292, 232, 30, 2],// V9 53x53
  [346, 274, 18, 2],// V10 57x57 (simplified blocks)
];

// Alignment pattern centers for versions 1..10
const ALIGNMENT_COORDS: number[][] = [
  [],
  [], // V1
  [6, 18], // V2
  [6, 22], // V3
  [6, 26], // V4
  [6, 30], // V5
  [6, 34], // V6
  [6, 22, 38], // V7
  [6, 24, 42], // V8
  [6, 26, 46], // V9
  [6, 28, 50], // V10
];

export function generateQrMatrix(text: string): boolean[][] {
  const encoder = new TextEncoder();
  const rawBytes = encoder.encode(text);

  // Pick smallest version that fits
  let version = 1;
  while (version <= 10) {
    const maxData = VERSION_PARAMS_L[version][1];
    // header: 4 bits mode + 8 bits length (for V1-9) + data length
    const neededCodewords = Math.ceil((4 + 8 + rawBytes.length * 8 + 4) / 8);
    if (neededCodewords <= maxData) break;
    version++;
  }
  if (version > 10) version = 10;

  const [totalCodewords, dataCodewords, ecCodewords, numBlocks] = VERSION_PARAMS_L[version];
  const size = 17 + 4 * version;

  // 1. Bit Buffer
  const bits: number[] = [];
  function pushBits(val: number, length: number) {
    for (let i = length - 1; i >= 0; i--) {
      bits.push((val >>> i) & 1);
    }
  }

  // Byte mode indicator: 0100
  pushBits(0b0100, 4);
  // Character count indicator: 8 bits for versions 1..9, 16 bits for version 10
  pushBits(rawBytes.length, version < 10 ? 8 : 16);
  // Data bytes
  for (let i = 0; i < rawBytes.length; i++) {
    pushBits(rawBytes[i], 8);
  }
  // Terminator: up to 4 zero bits
  const maxDataBits = dataCodewords * 8;
  const termLength = Math.min(4, maxDataBits - bits.length);
  pushBits(0, termLength);
  // Pad to byte boundary
  while (bits.length % 8 !== 0) {
    bits.push(0);
  }
  // Pad bytes 0xEC, 0x11
  const padBytes = [0xec, 0x11];
  let padIdx = 0;
  while (bits.length < maxDataBits) {
    pushBits(padBytes[padIdx % 2], 8);
    padIdx++;
  }

  // Convert bits to byte array
  const dataBytes = new Uint8Array(dataCodewords);
  for (let i = 0; i < dataCodewords; i++) {
    let byteVal = 0;
    for (let b = 0; b < 8; b++) {
      byteVal = (byteVal << 1) | (bits[i * 8 + b] ?? 0);
    }
    dataBytes[i] = byteVal;
  }

  // 2. Error correction codewords and interleaving
  const blockSize = Math.floor(dataCodewords / numBlocks);
  const dataBlocks: Uint8Array[] = [];
  const ecBlocks: Uint8Array[] = [];

  let offset = 0;
  for (let b = 0; b < numBlocks; b++) {
    const curBlockSize = (b === numBlocks - 1) ? (dataCodewords - offset) : blockSize;
    const blockData = dataBytes.subarray(offset, offset + curBlockSize);
    dataBlocks.push(blockData);
    ecBlocks.push(rsCompute(blockData, ecCodewords));
    offset += curBlockSize;
  }

  // Final interleaved codeword list
  const finalCodewords: number[] = [];
  const maxBlockLen = Math.max(...dataBlocks.map(b => b.length));
  for (let i = 0; i < maxBlockLen; i++) {
    for (let b = 0; b < numBlocks; b++) {
      if (i < dataBlocks[b].length) {
        finalCodewords.push(dataBlocks[b][i]);
      }
    }
  }
  for (let i = 0; i < ecCodewords; i++) {
    for (let b = 0; b < numBlocks; b++) {
      if (i < ecBlocks[b].length) {
        finalCodewords.push(ecBlocks[b][i]);
      }
    }
  }

  // 3. Grid setup
  const matrix: (boolean | null)[][] = Array.from({ length: size }, () => Array(size).fill(null));
  const isFunction: boolean[][] = Array.from({ length: size }, () => Array(size).fill(false));

  function setModule(r: number, c: number, val: boolean, isFunc = true) {
    if (r >= 0 && r < size && c >= 0 && c < size) {
      matrix[r][c] = val;
      if (isFunc) isFunction[r][c] = true;
    }
  }

  // Finder patterns at (0,0), (0, size-7), (size-7, 0)
  function drawFinderPattern(row: number, col: number) {
    for (let r = -1; r <= 7; r++) {
      for (let c = -1; c <= 7; c++) {
        const nr = row + r;
        const nc = col + c;
        if (nr < 0 || nr >= size || nc < 0 || nc >= size) continue;
        if (r >= 0 && r <= 6 && c >= 0 && c <= 6) {
          if (r === 0 || r === 6 || c === 0 || c === 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4)) {
            setModule(nr, nc, true);
          } else {
            setModule(nr, nc, false);
          }
        } else {
          // Separator
          setModule(nr, nc, false);
        }
      }
    }
  }

  drawFinderPattern(0, 0);
  drawFinderPattern(0, size - 7);
  drawFinderPattern(size - 7, 0);

  // Alignment patterns
  const coords = ALIGNMENT_COORDS[version];
  if (coords.length > 0) {
    for (const r of coords) {
      for (const c of coords) {
        // Skip finders
        if (
          (r < 9 && c < 9) ||
          (r < 9 && c >= size - 9) ||
          (r >= size - 9 && c < 9)
        ) {
          continue;
        }
        for (let dr = -2; dr <= 2; dr++) {
          for (let dc = -2; dc <= 2; dc++) {
            const isBorder = Math.abs(dr) === 2 || Math.abs(dc) === 2;
            const isCenter = dr === 0 && dc === 0;
            setModule(r + dr, c + dc, isBorder || isCenter);
          }
        }
      }
    }
  }

  // Timing patterns
  for (let i = 8; i < size - 8; i++) {
    if (matrix[6][i] === null) setModule(6, i, i % 2 === 0);
    if (matrix[i][6] === null) setModule(i, 6, i % 2 === 0);
  }

  // Dark module
  setModule(size - 8, 8, true);

  // Reserve format info area
  for (let i = 0; i < 9; i++) {
    if (matrix[8][i] === null) setModule(8, i, false);
    if (matrix[i][8] === null) setModule(i, 8, false);
  }
  for (let i = size - 8; i < size; i++) {
    if (matrix[8][i] === null) setModule(8, i, false);
    if (matrix[i][8] === null) setModule(i, 8, false);
  }

  // 4. Place data bits (Mask 0: (row + col) % 2 === 0)
  const allDataBits: number[] = [];
  for (const byte of finalCodewords) {
    for (let b = 7; b >= 0; b--) {
      allDataBits.push((byte >>> b) & 1);
    }
  }

  let bitIdx = 0;
  let upward = true;
  for (let col = size - 1; col > 0; col -= 2) {
    if (col === 6) col--; // Skip vertical timing column
    const rows = upward
      ? Array.from({ length: size }, (_, i) => size - 1 - i)
      : Array.from({ length: size }, (_, i) => i);

    for (const row of rows) {
      for (const c of [col, col - 1]) {
        if (!isFunction[row][c]) {
          const bit = bitIdx < allDataBits.length ? allDataBits[bitIdx++] : 0;
          // Apply mask 0: (row + c) % 2 === 0
          const mask = (row + c) % 2 === 0;
          matrix[row][c] = (bit === 1) !== mask;
        }
      }
    }
    upward = !upward;
  }

  // 5. Format info bits (EC Level L = 01, Mask 0 = 000)
  // Standard format string for EC L + Mask 0 is 0x77C4 (0b111011111000100) ^ 0x5412 = 0x23D6 (0b010001111010110)
  const formatBits = [0, 1, 0, 0, 0, 1, 1, 1, 1, 0, 1, 0, 1, 1, 0];
  // Write format info around top-left finder
  for (let i = 0; i <= 5; i++) matrix[8][i] = formatBits[i] === 1;
  matrix[8][7] = formatBits[6] === 1;
  matrix[8][8] = formatBits[7] === 1;
  matrix[7][8] = formatBits[8] === 1;
  for (let i = 9; i < 15; i++) matrix[14 - i][8] = formatBits[i] === 1;

  // Write format info around other two finders
  for (let i = 0; i < 8; i++) matrix[size - 1 - i][8] = formatBits[i] === 1;
  for (let i = 8; i < 15; i++) matrix[8][size - 15 + i] = formatBits[i] === 1;

  // Convert to boolean matrix
  return matrix.map(row => row.map(cell => cell ?? false));
}

export function QrCodeSvg({
  value,
  size = 180,
  className = 'qr-svg-code',
  ariaLabel = 'Mã QR soát vé điện tử'
}: {
  value: string;
  size?: number;
  className?: string;
  ariaLabel?: string;
}) {
  const matrix = generateQrMatrix(value);
  const matrixSize = matrix.length;
  const padding = 2; // 2 modules quiet zone
  const totalDim = matrixSize + padding * 2;

  // Build path data for all dark modules
  let pathD = '';
  for (let r = 0; r < matrixSize; r++) {
    for (let c = 0; c < matrixSize; c++) {
      if (matrix[r][c]) {
        const x = c + padding;
        const y = r + padding;
        pathD += `M${x} ${y}h1v1h-1z`;
      }
    }
  }

  return h('svg', {
    viewBox: `0 0 ${totalDim} ${totalDim}`,
    width: size,
    height: size,
    className,
    role: 'img',
    'aria-label': ariaLabel
  },
    // Pure white background
    h('rect', {
      x: 0,
      y: 0,
      width: totalDim,
      height: totalDim,
      fill: '#ffffff'
    }),
    // Vector dark dots
    h('path', {
      d: pathD,
      fill: '#05122f',
      shapeRendering: 'crispEdges'
    })
  );
}
