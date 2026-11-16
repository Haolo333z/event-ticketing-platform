import { describe, it } from 'node:test';
import assert from 'node:assert';
import { generateQrMatrix, QrCodeSvg } from './QrCodeSvg.ts';

describe('QrCodeSvg Generator', () => {
  it('generates valid matrix for short text', () => {
    const matrix = generateQrMatrix('EF-TEST-123');
    assert.ok(Array.isArray(matrix));
    assert.ok(matrix.length >= 21);
    assert.strictEqual(matrix.length, matrix[0].length);

    // Top-left finder pattern check: (0,0) must be true (corner of 7x7)
    assert.strictEqual(matrix[0][0], true);
    assert.strictEqual(matrix[0][6], true);
    assert.strictEqual(matrix[6][0], true);
    assert.strictEqual(matrix[6][6], true);
  });

  it('generates valid matrix for long signed QR token', () => {
    const longToken = '3fa85f64-5717-4562-b3fc-2c963f66afa6.EF-2026-VIP-001.c3b4e4a9-8d65-4bb6-9ef7-d1a2f3a4b5c6.1763300000.dBj_9f8-u12ZAbcDefGhIjKlMnOpQrStUvWxYz01234';
    const matrix = generateQrMatrix(longToken);
    assert.ok(Array.isArray(matrix));
    assert.ok(matrix.length >= 45, 'Matrix dimension should match capacity');
    assert.strictEqual(matrix.length, matrix[0].length);
  });

  it('renders SVG react element with crisp path and aria-label', () => {
    const element = QrCodeSvg({
      value: 'EF-TEST-TOKEN',
      size: 200,
      ariaLabel: 'Test QR'
    });
    assert.strictEqual(element.type, 'svg');
    assert.strictEqual(element.props.width, 200);
    assert.strictEqual(element.props.height, 200);
    assert.strictEqual(element.props['aria-label'], 'Test QR');
    assert.ok(element.props.children);
  });
});
