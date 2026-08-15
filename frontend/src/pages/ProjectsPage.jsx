import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/authContext'
import { Button, ErrorNote, Field, Spinner, StatusBadge } from '../components/ui'

export default function ProjectsPage() {
  const { logout } = useAuth()
  const [projects, setProjects] = useState(null)
  const [name, setName] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setProjects(await api.listProjects())
    } catch (failure) {
      setError(failure)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  async function create(event) {
    event.preventDefault()
    setError(null)
    setBusy(true)

    try {
      const project = await api.createProject({ name, description: '' })
      setName('')
      setProjects((current) => [...(current ?? []), project])
    } catch (failure) {
      setError(failure)
    } finally {
      setBusy(false)
    }
  }

  async function remove(id) {
    setError(null)

    try {
      await api.deleteProject(id)
      setProjects((current) => current.filter((project) => project.id !== id))
    } catch (failure) {
      setError(failure)
    }
  }

  return (
    <div className="mx-auto max-w-3xl p-6">
      <header className="mb-8 flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-100">Your projects</h1>
        <Button variant="ghost" onClick={logout}>
          Log out
        </Button>
      </header>

      <form onSubmit={create} className="mb-8 flex items-end gap-3">
        <div className="flex-1">
          <Field
            label="New project"
            placeholder="Expense tracker"
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
            maxLength={100}
          />
        </div>
        <Button type="submit" disabled={busy || !name.trim()}>
          Create
        </Button>
      </form>

      <div className="mb-4">
        <ErrorNote error={error} />
      </div>

      {projects === null ? (
        <Spinner label="Loading projects…" />
      ) : projects.length === 0 ? (
        <p className="text-sm text-slate-500">No projects yet. Create one above to start building.</p>
      ) : (
        <ul className="space-y-2">
          {projects.map((project) => (
            <li
              key={project.id}
              className="flex items-center gap-4 rounded-xl border border-[var(--color-edge)] bg-[var(--color-surface)] px-4 py-3"
            >
              <Link to={`/projects/${project.id}`} className="flex-1 truncate">
                <span className="font-medium text-slate-100 hover:text-indigo-300">{project.name}</span>
              </Link>
              <StatusBadge status={project.status} />
              <Button variant="danger" onClick={() => remove(project.id)}>
                Delete
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
