import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../auth/context'

export default function RoleRoute({ roles, children }: { roles: string[]; children: ReactNode }) {
  const { me } = useAuth()
  if (!me || !roles.includes(me.role)) {
    return <Navigate to="/" replace />
  }
  return <>{children}</>
}
