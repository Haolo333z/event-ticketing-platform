import { createElement } from 'react';

export function App() {
  return createElement('main', null,
    createElement('h1', null, 'Event Ticketing'),
    createElement('p', null, 'Ứng dụng đang được xây dựng.'),
  );
}
