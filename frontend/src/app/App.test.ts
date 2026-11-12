import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { App } from './App.ts';

test('scaffold renders a page without an API connection or booking controls', () => {
  const html = renderToStaticMarkup(createElement(App));
  assert.match(html, /<main>/);
  assert.match(html, /<h1>Event Ticketing<\/h1>/);
  assert.doesNotMatch(html, /<form|<button/);
});
