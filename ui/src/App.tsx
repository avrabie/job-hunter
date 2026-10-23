import { Route, Routes } from 'react-router'
import { AdminPage } from './admin/AdminPage.tsx'
import { CallbackPage } from './auth/CallbackPage.tsx'
import { MatchesPage } from './matches/MatchesPage.tsx'
import { Home } from './pages/Home.tsx'
import { PostingPage } from './postings/PostingPage.tsx'
import { PostingsPage } from './postings/PostingsPage.tsx'
import { ProfilePage } from './profile/ProfilePage.tsx'
import { RegisterPage } from './register/RegisterPage.tsx'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/postings" element={<PostingsPage />} />
      <Route path="/postings/:id" element={<PostingPage />} />
      <Route path="/matches" element={<MatchesPage />} />
      <Route path="/admin" element={<AdminPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/callback" element={<CallbackPage />} />
      <Route path="*" element={<Home />} />
    </Routes>
  )
}
