import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { api, clearToken, getToken, setToken } from '../api/client'
import type { Me } from './context'
import { AuthContext } from './context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState<boolean>(getToken() != null)

  useEffect(() => {
    if (!getToken()) {
      return
    }
    api
      .get<Me>('/auth/me')
      .then((response) => setMe(response.data))
      .catch(() => {
        clearToken()
        setMe(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const response = await api.post<{ token: string }>('/auth/login', { username, password })
    setToken(response.data.token)
    const meResponse = await api.get<Me>('/auth/me')
    setMe(meResponse.data)
  }, [])

  const logout = useCallback(() => {
    clearToken()
    setMe(null)
    window.location.href = '/login'
  }, [])

  const value = useMemo(() => ({ me, loading, login, logout }), [me, loading, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
