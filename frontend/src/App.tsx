import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { AuthProvider } from './auth/AuthProvider'
import RequireAuth from './components/RequireAuth'
import ConnectionsPage from './pages/ConnectionsPage'
import LoginPage from './pages/LoginPage'
import MetricsPage from './pages/MetricsPage'
import SqlConsolePage from './pages/SqlConsolePage'
import UsersPage from './pages/UsersPage'
import './App.css'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<RequireAuth />}>
            <Route path="/" element={<ConnectionsPage />} />
            <Route path="/sql" element={<SqlConsolePage />} />
            <Route path="/metrics" element={<MetricsPage />} />
            <Route path="/users" element={<UsersPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}
