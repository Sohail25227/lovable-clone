import { useState } from 'react'
import { useAuth } from '../auth/authContext'
import {
  Button,
  COLD_START_NOTICE,
  ErrorNote,
  Field,
  Wordmark,
  useSlowRequest,
} from '../components/ui'

export default function LoginPage() {
  const { login, signup } = useAuth()
  const [isSignup, setIsSignup] = useState(false)
  const [form, setForm] = useState({ username: '', password: '', name: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  // Poore app mein yahi woh ek request hai jo soye hue server se takrati hai, kyunki
  // pehla click aksar yahi hota hai
  const waking = useSlowRequest(busy)

  const update = (field) => (event) => setForm({ ...form, [field]: event.target.value })

  async function submit(event) {
    event.preventDefault()
    setError(null)
    setBusy(true)

    try {
      await (isSignup ? signup(form) : login({ username: form.username, password: form.password }))
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }

  return (
    // Do div ki zarurat hai. #root ki height 100% par tay hai (BuilderPage ka andar-scroll
    // wala layout usi par tika hai), aur usse lamba content chup chaap kat jata hai — bina
    // scroll ke. Bahar wala scroll deta hai, andar wala min-h-full se tab bhi beech mein
    // rakhta hai jab jagah bachi ho. Ek hi div mein dono nahi ho sakte: chhoti screen par
    // justify-center content ko kinare se bahar dhakel deta hai aur footer pahunch se nikal
    // jata hai — theek wahi hua tha jab header aur footer jode gaye
    <div className="h-full overflow-y-auto">
      <div className="flex min-h-full flex-col items-center justify-center gap-7 p-6">
        <header className="text-center">
          <h1>
            <Wordmark className="text-4xl sm:text-5xl" />
          </h1>
          <p className="mt-2 text-sm text-slate-500">Describe an app. Get a working one.</p>
        </header>

        <form
          onSubmit={submit}
          className="w-full max-w-sm space-y-4 rounded-2xl border border-[var(--color-edge)] bg-[var(--color-surface)] p-7"
        >
          <h2 className="text-xl font-semibold text-slate-100">
            {isSignup ? 'Create an account' : 'Welcome back'}
          </h2>

          {isSignup && (
            <Field label="Name" value={form.name} onChange={update('name')} required />
          )}
          <Field label="Username" value={form.username} onChange={update('username')} required />
          <Field
            label="Password"
            type="password"
            value={form.password}
            onChange={update('password')}
            required
            // Backend 6 chars maangta hai. Yahi rule yahan rakhne se user ko round trip
            // ke bajaye turant pata chalta hai
            minLength={6}
          />

          <ErrorNote error={error} />

          <Button type="submit" disabled={busy} className="w-full">
            {busy ? 'Please wait…' : isSignup ? 'Sign up' : 'Log in'}
          </Button>

          {waking && (
            <p className="rounded-lg border border-amber-500/25 bg-amber-500/10 px-3 py-2 text-sm text-amber-200">
              {COLD_START_NOTICE}
            </p>
          )}

          <button
            type="button"
            onClick={() => {
              setIsSignup(!isSignup)
              setError(null)
            }}
            className="w-full text-sm text-slate-500 hover:text-slate-300"
          >
            {isSignup ? 'Already have an account? Log in' : 'Need an account? Sign up'}
          </button>
        </form>

        <footer className="text-xs text-slate-600">
          Developed by <span className="text-slate-400">Sohail Ahmad</span>
        </footer>
      </div>
    </div>
  )
}
