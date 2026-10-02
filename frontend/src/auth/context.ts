import { createContext, useContext } from 'react'

export interface Me {
  username: string
  role: string
  personId: number | null
  fullName: string | null
  root: boolean
}

export interface AuthContextValue {
  me: Me | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth phải được dùng trong AuthProvider')
  }
  return context
}
