import { createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App.ts';
import {createApi} from './api/client.ts';
import './styles.css';

const root = document.getElementById('root');
if (!root) throw new Error('Missing root element');
createRoot(root).render(createElement(App,{api:createApi(import.meta.env.VITE_API_BASE_URL??'http://127.0.0.1:8080'),store:sessionStorage}));
