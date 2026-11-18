import test from 'node:test';
import assert from 'node:assert/strict';
import { createElement as h } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { AiConciergeWidget } from './AiConciergeWidget.ts';
import type { Api } from '../api/client.ts';

const mockApi = {} as Api;

test('AiConciergeWidget renders floating AI Concierge button cleanly', () => {
  const html = renderToStaticMarkup(h(AiConciergeWidget, { api: mockApi, user: null }));
  assert.match(html, /Trợ lý AI/);
  assert.match(html, /ai-fab-btn/);
});
