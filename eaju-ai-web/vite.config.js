import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
export default defineConfig({
    plugins: [vue()],
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    server: {
        host: '0.0.0.0', // 允许通过局域网 IP 访问
        port: 5173,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/swagger-ui': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            '/v3/api-docs': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
        },
        // 允许外部访问时保持代理功能
        hmr: {
            protocol: 'ws',
            host: 'localhost',
        },
    },
});
