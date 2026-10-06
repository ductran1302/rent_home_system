import { lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/AppLayout'
import ProtectedRoute from './components/ProtectedRoute'
import RoleRoute from './components/RoleRoute'
import { AuthProvider } from './auth/AuthProvider'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'

const HomePage = lazy(() => import('./pages/HomePage'))
const HousesPage = lazy(() => import('./pages/HousesPage'))
const PersonsPage = lazy(() => import('./pages/PersonsPage'))
const ContractsPage = lazy(() => import('./pages/ContractsPage'))
const BillingPage = lazy(() => import('./pages/BillingPage'))
const DebtsPage = lazy(() => import('./pages/DebtsPage'))
const AssetsPage = lazy(() => import('./pages/AssetsPage'))
const AccountsPage = lazy(() => import('./pages/AccountsPage'))
const NoticesPage = lazy(() => import('./pages/NoticesPage'))

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<HomePage />} />
          <Route
            path="houses"
            element={
              <RoleRoute roles={['ADMIN', 'MANAGER']}>
                <HousesPage />
              </RoleRoute>
            }
          />
          <Route
            path="persons"
            element={
              <RoleRoute roles={['ADMIN', 'MANAGER']}>
                <PersonsPage />
              </RoleRoute>
            }
          />
          <Route path="contracts" element={<ContractsPage />} />
          <Route path="billing" element={<BillingPage />} />
          <Route
            path="debts"
            element={
              <RoleRoute roles={['ADMIN', 'MANAGER']}>
                <DebtsPage />
              </RoleRoute>
            }
          />
          <Route
            path="assets"
            element={
              <RoleRoute roles={['ADMIN', 'MANAGER']}>
                <AssetsPage />
              </RoleRoute>
            }
          />
          <Route
            path="accounts"
            element={
              <RoleRoute roles={['ADMIN']}>
                <AccountsPage />
              </RoleRoute>
            }
          />
          <Route
            path="notices"
            element={
              <RoleRoute roles={['ADMIN', 'MANAGER']}>
                <NoticesPage />
              </RoleRoute>
            }
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
