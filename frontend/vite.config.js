import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Backend ke liye koi dev proxy nahi hai, jaan-boojh ke. Alag origin se do faayde hain:
// asli CORS dev mein hi test hota hai, aur preview iframe hamare app ki origin ke bahar
// rehta hai — to model ka likha code hamara DOM ya token chhu nahi sakta
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5173, strictPort: true },
})
