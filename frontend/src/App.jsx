import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/authContext'
import BuilderPage from './pages/BuilderPage'
import LoginPage from './pages/LoginPage'
import ProjectsPage from './pages/ProjectsPage'

export default function App() {
  const { isAuthenticated } = useAuth()

  // Yeh guard sirf UI ka hai, security ka nahi: har endpoint apni ownership khud check
  // karta hai. Iska kaam bas logged-out user ko khaali screens dikhane se bachana hai
  if (!isAuthenticated) {
    return <LoginPage />
  }

  return (
    <Routes>
      <Route path="/projects" element={<ProjectsPage />} />
      <Route path="/projects/:projectId" element={<BuilderPage />} />
      <Route path="*" element={<Navigate to="/projects" replace />} />
    </Routes>
  )
}
