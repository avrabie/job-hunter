import { Route, Routes } from 'react-router'
import { AdminPage } from './admin/AdminPage.tsx'
import { ApplicationPage } from './applications/ApplicationPage.tsx'
import { ApplicationsPage } from './applications/ApplicationsPage.tsx'
import { CallbackPage } from './auth/CallbackPage.tsx'
import { ExportPage } from './export/ExportPage.tsx'
import { Layout } from './layout/Layout.tsx'
import { MatchesPage } from './matches/MatchesPage.tsx'
import { Home } from './pages/Home.tsx'
import { PostingPage } from './postings/PostingPage.tsx'
import { PostingsPage } from './postings/PostingsPage.tsx'
import { ProfilePage } from './profile/ProfilePage.tsx'
import { PublicCvPage } from './publiccv/PublicCvPage.tsx'
import { RegisterPage } from './register/RegisterPage.tsx'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/profile" element={<ProfilePage />} />
        <Route path="/postings" element={<PostingsPage />} />
        <Route path="/postings/:id" element={<PostingPage />} />
        <Route path="/applications" element={<ApplicationsPage />} />
        <Route path="/applications/:id" element={<ApplicationPage />} />
        <Route path="/matches" element={<MatchesPage />} />
        <Route path="/export" element={<ExportPage />} />
        <Route path="/publish" element={<PublicCvPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/callback" element={<CallbackPage />} />
        <Route path="*" element={<Home />} />
      </Route>
    </Routes>
  )
}
