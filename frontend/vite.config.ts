import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

function vendorChunk(id: string) {
  if (!id.includes('node_modules')) return undefined
  if (id.includes('@ant-design/icons')) return 'vendor-icons'
  if (
    id.includes('@ant-design/colors') ||
    id.includes('@ant-design/fast-color') ||
    id.includes('@rc-component/color-picker') ||
    id.includes('rc-color-picker')
  ) {
    return 'vendor-color'
  }
  if (id.includes('antd/es/table') || id.includes('antd\\es\\table')) return 'vendor-antd-table'
  if (id.includes('antd') || id.includes('@ant-design')) return 'vendor-antd'
  if (id.includes('react-router') || id.includes('@remix-run')) return 'vendor-router'
  if (id.includes('@tanstack')) return 'vendor-query'
  if (id.includes('dayjs')) return 'vendor-dayjs'
  if (id.includes('axios')) return 'vendor-axios'
  if (id.includes('react-dom') || id.includes('scheduler')) return 'vendor-react'
  if (id.includes('/react/') || id.includes('\\react\\')) return 'vendor-react'
  if (id.includes('rc-') || id.includes('rc_') || id.includes('@rc-component')) return 'vendor-rc'
  return 'vendor'
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    assetsDir: 'static',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: vendorChunk,
      },
    },
  },
})
