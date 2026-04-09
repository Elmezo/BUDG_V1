import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// Plugin to ensure CSS is output for both entry points
// Since both wizard and dashboard import the same index.css, we need CSS in both directories
const cssEntryPlugin = () => {
  return {
    name: 'css-entry-plugin',
    generateBundle(options, bundle) {
      // Find the CSS file that was generated
      const cssEntry = Object.entries(bundle).find(([key, asset]) => 
        asset.type === 'asset' && key.endsWith('.css')
      );
      
      if (cssEntry) {
        const [cssKey, cssAsset] = cssEntry;
        
        // Check if dashboard bundle exists
        const hasDashboard = Object.keys(bundle).some(key => 
          key.includes('bulk-dashboard') && key.endsWith('.js')
        );
        
        // Check if wizard bundle exists  
        const hasWizard = Object.keys(bundle).some(key => 
          key.includes('bulk-upload') && key.endsWith('.js')
        );
        
        // If dashboard exists and CSS is not already in bulk-dashboard, copy it
        if (hasDashboard && cssKey !== 'bulk-dashboard/bundle.css') {
          this.emitFile({
            type: 'asset',
            fileName: 'bulk-dashboard/bundle.css',
            source: cssAsset.source
          });
        }
        
        // If wizard exists and CSS is not already in bulk-upload, ensure it's there
        if (hasWizard && cssKey !== 'bulk-upload/bundle.css') {
          // CSS should already be going to bulk-upload via assetFileNames, but ensure it
          if (!Object.keys(bundle).some(key => key === 'bulk-upload/bundle.css')) {
            this.emitFile({
              type: 'asset',
              fileName: 'bulk-upload/bundle.css',
              source: cssAsset.source
            });
          }
        }
      }
    }
  };
};

export default defineConfig({
  plugins: [react(), cssEntryPlugin()],
  build: {
    outDir: '../src/main/webapp/assets/js',
    emptyOutDir: false, // Don't empty entire dir since we have multiple entries
    chunkSizeWarningLimit: 500, // Keep the warning but we're optimizing it
    rollupOptions: {
      input: {
        wizard: './src/main.jsx',
        dashboard: './src/dashboard.jsx'
      },
      output: {
        entryFileNames: (chunkInfo) => {
          // Put wizard in bulk-upload/, dashboard in bulk-dashboard/
          return chunkInfo.name === 'dashboard' 
            ? 'bulk-dashboard/bundle.js' 
            : 'bulk-upload/bundle.js';
        },
        assetFileNames: (assetInfo) => {
          // CSS files: default to bulk-upload, plugin will handle dashboard copy
          if (assetInfo.name && assetInfo.name.endsWith('.css')) {
            return 'bulk-upload/bundle.css';
          }
          return 'assets/[name]-[hash][extname]';
        },
        chunkFileNames: 'bulk-upload/chunk-[name]-[hash].js',
        // Manual chunking to separate large vendor libraries
        manualChunks: (id) => {
          // Separate xlsx library into its own chunk (it's ~600KB)
          if (id.includes('node_modules/xlsx')) {
            return 'vendor-xlsx';
          }
          // Separate React and React-DOM into a vendor chunk
          if (id.includes('node_modules/react') || id.includes('node_modules/react-dom')) {
            return 'vendor-react';
          }
          // Separate axios into its own chunk
          if (id.includes('node_modules/axios')) {
            return 'vendor-axios';
          }
          // Other node_modules go into a general vendor chunk
          if (id.includes('node_modules')) {
            return 'vendor';
          }
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
});

