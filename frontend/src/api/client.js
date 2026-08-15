const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'
const TOKEN_KEY = 'lovable.accessToken'

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

// AuthContext yahan apna logout register karta hai. Iske bina har call site ko khud
// 401 handle karna padta, aur ek jagah bhoolne se app aadhi logged-in halat mein atak jati
let onUnauthorized = () => {}
export const setUnauthorizedHandler = (handler) => {
  onUnauthorized = handler
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  const token = getToken()
  if (auth && token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  const text = await response.text()

  // Backend hamesha ApiErrorDto bhejta hai, par gateway ya CORS ki failure HTML de sakti
  // hai. Parse ki galti ko asli problem — status — chhupane nahi dena chahiye
  let payload = null
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    payload = null
  }

  if (!response.ok) {
    if (response.status === 401 && auth) {
      clearToken()
      onUnauthorized()
    }
    throw new ApiError(response.status, payload?.message ?? `Request failed (${response.status})`)
  }

  return payload
}

export const api = {
  signup: (credentials) => request('/api/auth/signup', { method: 'POST', body: credentials, auth: false }),
  login: (credentials) => request('/api/auth/login', { method: 'POST', body: credentials, auth: false }),

  listProjects: () => request('/api/projects'),
  createProject: (project) => request('/api/projects', { method: 'POST', body: project }),
  getProject: (id) => request(`/api/projects/${id}`),
  deleteProject: (id) => request(`/api/projects/${id}`, { method: 'DELETE' }),

  generate: (id, prompt) => request(`/api/projects/${id}/generate`, { method: 'POST', body: { prompt } }),
  getFiles: (id) => request(`/api/projects/${id}/files`),
  getMessages: (id) => request(`/api/projects/${id}/messages`),
  createPreviewToken: (id) => request(`/api/projects/${id}/preview-token`, { method: 'POST' }),
}

// Backend relative previewUrl deta hai. iframe ko poora URL chahiye, kyunki woh is app ki
// origin pe nahi, backend ki origin pe khulta hai
export const previewSrc = (previewUrl) => `${BASE_URL}${previewUrl}`
