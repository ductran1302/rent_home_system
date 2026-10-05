import { ConfigProvider, theme } from 'antd'
import type { ThemeConfig } from 'antd'
import viVN from 'antd/locale/vi_VN'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import ThemeContext, { type ThemeMode } from './theme-context'

const STORAGE_KEY = 'theme-mode'

const LIGHT_THEME: ThemeConfig = {
  token: {
    colorPrimary: '#C25000',
    colorLink: '#C25000',
  },
}

const DARK_THEME: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#1677FF',
    colorLink: '#1677FF',
  },
}

function getInitialMode(): ThemeMode {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark') {
      return stored
    }
  } catch {
    // localStorage có thể bị chặn, dùng cài đặt hệ thống
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export default function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(getInitialMode)

  useEffect(() => {
    document.documentElement.dataset.theme = mode
    try {
      window.localStorage.setItem(STORAGE_KEY, mode)
    } catch {
      // bỏ qua khi không ghi được
    }
  }, [mode])

  const toggleMode = useCallback(() => {
    setMode((current) => (current === 'light' ? 'dark' : 'light'))
  }, [])

  const contextValue = useMemo(() => ({ mode, toggleMode }), [mode, toggleMode])

  return (
    <ThemeContext.Provider value={contextValue}>
      <ConfigProvider locale={viVN} theme={mode === 'dark' ? DARK_THEME : LIGHT_THEME}>
        {children}
      </ConfigProvider>
    </ThemeContext.Provider>
  )
}
