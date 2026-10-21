import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthProvider.tsx'
import { createUserManager } from './auth/config.ts'

const manager = createUserManager()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider manager={manager}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </AuthProvider>
  </StrictMode>,
)
