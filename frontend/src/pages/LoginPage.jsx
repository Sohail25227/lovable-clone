import { useState } from 'react'
import { useAuth } from '../auth/authContext'
import { Button, ErrorNote, Field } from '../components/ui'

export default function LoginPage() {
  const { login, signup } = useAuth()
  const [isSignup, setIsSignup] = useState(false)
  const [form, setForm] = useState({ username: '', password: '', name: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

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
    <div className="flex min-h-full items-center justify-center p-6">
      <form
        onSubmit={submit}
        className="w-full max-w-sm space-y-4 rounded-2xl border border-[var(--color-edge)] bg-[var(--color-surface)] p-7"
      >
        <div>
          <h1 className="text-xl font-semibold text-slate-100">
            {isSignup ? 'Create an account' : 'Welcome back'}
          </h1>
          <p className="mt-1 text-sm text-slate-500">Describe an app, get a working one.</p>
        </div>

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
    </div>
  )
}
