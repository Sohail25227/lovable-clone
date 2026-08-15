const STATUS_STYLES = {
  DRAFT: 'bg-slate-700/60 text-slate-300',
  GENERATING: 'bg-amber-500/15 text-amber-300',
  READY: 'bg-emerald-500/15 text-emerald-300',
  FAILED: 'bg-rose-500/15 text-rose-300',
  LAST_ATTEMPT_FAILED: 'bg-amber-500/15 text-amber-300',
}

/**
 * FAILED ka matlab hai "pichhli koshish nahi chali", na ki "app tooti hui hai" — generation
 * fail hone par purani files zinda rehti hain aur preview chalti rehti hai.
 *
 * Isliye jahan files maujood hain wahan red FAILED galat hai: screen ek taraf app dikhata
 * hai aur doosri taraf kehta hai ki woh fail ho gayi. Jinke paas files hi nahi, unke liye
 * FAILED sach hai aur waisa hi rehta hai
 */
export function StatusBadge({ status, hasFiles = false }) {
  const attemptFailed = status === 'FAILED' && hasFiles
  const label = attemptFailed ? 'LAST ATTEMPT FAILED' : status
  const style = attemptFailed ? STATUS_STYLES.LAST_ATTEMPT_FAILED : STATUS_STYLES[status]

  return (
    <span
      className={`rounded-full px-2.5 py-1 text-xs font-medium tracking-wide ${
        style ?? STATUS_STYLES.DRAFT
      }`}
    >
      {label}
    </span>
  )
}

export function Button({ variant = 'primary', className = '', ...props }) {
  const variants = {
    primary: 'bg-indigo-500 hover:bg-indigo-400 text-white disabled:bg-indigo-500/40',
    ghost: 'bg-transparent hover:bg-white/5 text-slate-300 border border-[var(--color-edge)]',
    danger: 'bg-transparent hover:bg-rose-500/10 text-rose-300 border border-rose-500/30',
  }

  return (
    <button
      className={`rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${className}`}
      {...props}
    />
  )
}

export function Field({ label, ...props }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm text-slate-400">{label}</span>
      <input
        className="w-full rounded-lg border border-[var(--color-edge)] bg-[var(--color-canvas)] px-3 py-2 text-sm text-slate-100 outline-none placeholder:text-slate-600 focus:border-indigo-500"
        {...props}
      />
    </label>
  )
}

// Server ka message hi dikhaya jata hai. Rate limit ("try again in about 54 minutes") aur
// validation ki wajah dono usme aati hain, aur unhe apne shabdon se badalna nuksaan hai
export function ErrorNote({ error }) {
  if (!error) return null

  return (
    <p className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-200">
      {error.message}
    </p>
  )
}

export function Spinner({ label }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-slate-400">
      <span className="size-3.5 animate-spin rounded-full border-2 border-slate-600 border-t-indigo-400" />
      {label}
    </span>
  )
}
