import React from 'react';
import ReactDOM from 'react-dom/client';
import BulkJobsDashboard from './components/BulkJobsDashboard';
import './index.css';

const root = ReactDOM.createRoot(document.getElementById('bulk-jobs-root'));
root.render(
  <React.StrictMode>
    <BulkJobsDashboard />
  </React.StrictMode>
);

