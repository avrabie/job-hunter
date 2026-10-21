import { Route, Routes } from 'react-router'
import { CallbackPage } from './auth/CallbackPage.tsx'
import { Home } from './pages/Home.tsx'
import { ProfilePage } from './profile/ProfilePage.tsx'
import { RegisterPage } from './register/RegisterPage.tsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/callback" element={<CallbackPage />} />
      <Route path="*" element={<Home />} />
    </Routes>
  )
}
