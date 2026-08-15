import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, clearToken, getToken, setToken, setUnauthorizedHandler } from '../api/client'
import { AuthContext } from './authContext'

/**
 * Token localStorage mein rehta hai, ek soch-samajh kar liya gaya samjhauta.
 *
 * httpOnly cookie XSS se behtar bachati hai, par uske liye CORS credentials aur CSRF
 * ka intezaam chahiye — jo stateless JWT ke saath jaan-boojh ke nahi rakha gaya.
 * Yahan XSS ka daayra chhota hai kyunki model ka likha code alag origin (backend) pe
 * chalta hai, is app ki origin pe nahi.
 */
export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(getToken)

  const logout = useCallback(() => {
    clearToken()
    setTokenState(null)
  }, [])

  // 401 kabhi bhi aa sakta hai — token expire hone ke baad kisi bhi call pe. Client use
  // yahan bhejta hai taaki UI ek hi jagah logged-out ho
  useEffect(() => {
    setUnauthorizedHandler(logout)
  }, [logout])

  const authenticate = useCallback(async (call) => {
    const { accessToken } = await call()
    setToken(accessToken)
    setTokenState(accessToken)
  }, [])

  const value = useMemo(
    () => ({
      isAuthenticated: Boolean(token),
      login: (credentials) => authenticate(() => api.login(credentials)),
      signup: (credentials) => authenticate(() => api.signup(credentials)),
      logout,
    }),
    [token, authenticate, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
