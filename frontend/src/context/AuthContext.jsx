import { createContext, useContext, useState, useCallback } from 'react'

const AuthContext = createContext(null)

function isTokenExpired(token) {
  try {
    const { exp } = JSON.parse(atob(token.split('.')[1]))
    return exp * 1000 < Date.now()
  } catch {
    return true
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem('user')
    const token = localStorage.getItem('token')
    if (!stored || !token || isTokenExpired(token)) {
      localStorage.removeItem('user')
      localStorage.removeItem('token')
      return null
    }
    return JSON.parse(stored)
  })

  const login = useCallback((userData) => {
    localStorage.setItem('user', JSON.stringify(userData))
    localStorage.setItem('token', userData.token)
    setUser(userData)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('user')
    localStorage.removeItem('token')
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
