import { createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { DemoShell } from './app/DemoShell.ts';
import {createApi} from './api/client.ts';
import {createAdminApi} from './api/admin.ts';
import './styles.css';

const root = document.getElementById('root');
if (!root) throw new Error('Missing root element');
const base=import.meta.env.VITE_API_BASE_URL??'http://127.0.0.1:8080';
createRoot(root).render(createElement(DemoShell,{customer:createApi(base),admin:createAdminApi(base),store:sessionStorage}));
