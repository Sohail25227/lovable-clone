import { createContext, useContext } from 'react'

// Context aur hook provider se alag file mein hain, warna ek hi module component aur
// non-component dono export karta hai aur Vite ka fast refresh us file pe kaam chhod deta
export const AuthContext = createContext(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
