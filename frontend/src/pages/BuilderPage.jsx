import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, previewSrc } from '../api/client'
import { Button, ErrorNote, Spinner, StatusBadge } from '../components/ui'

export default function BuilderPage() {
  const { projectId } = useParams()

  const [project, setProject] = useState(null)
  const [messages, setMessages] = useState([])
  const [files, setFiles] = useState([])
  const [preview, setPreview] = useState(null)
  const [tab, setTab] = useState('preview')

  const [prompt, setPrompt] = useState('')
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState(null)

  // Preview token 30 min mein expire hota hai, isliye woh maanga jata hai — sambhala nahi
  // jata. Har generate ke baad naya, kyunki purana URL purani files nahi, cached response
  // dikha sakta hai
  const refreshPreview = useCallback(async () => {
    try {
      const { previewUrl } = await api.createPreviewToken(projectId)
      setPreview(previewSrc(previewUrl))
    } catch {
      // Preview ki apni failure page ko todni nahi chahiye. Jab tak files nahi bani,
      // yeh 404 hi degi
      setPreview(null)
    }
  }, [projectId])

  const load = useCallback(async () => {
    try {
      const [loadedProject, loadedMessages, loadedFiles] = await Promise.all([
        api.getProject(projectId),
        api.getMessages(projectId),
        api.getFiles(projectId),
      ])

      setProject(loadedProject)
      setMessages(loadedMessages)
      setFiles(loadedFiles)

      if (loadedFiles.length > 0) {
        refreshPreview()
      }
    } catch (failure) {
      setError(failure)
    }
  }, [projectId, refreshPreview])

  useEffect(() => {
    load()
  }, [load])

  /**
   * Page mount pe ek baar padhta hai, to badge utna hi purana ho jata hai jitni der tab
   * khuli rehti hai. Yahan sirf project dobara mangaya jata hai, files ya preview nahi:
   * naya preview token iframe ko dobara mount karwa deta, aur generated app ka apna
   * state har tab switch pe udd jata.
   */
  useEffect(() => {
    const refreshStatus = async () => {
      if (document.visibilityState !== 'visible') return

      try {
        setProject(await api.getProject(projectId))
      } catch {
        // Background refresh chup chaap fail hoti hai. User ne kuch maanga nahi tha,
        // aur uske saamne error rakhna sirf shor hai — agli asli action pe dikh jayega
      }
    }

    window.addEventListener('focus', refreshStatus)
    document.addEventListener('visibilitychange', refreshStatus)

    return () => {
      window.removeEventListener('focus', refreshStatus)
      document.removeEventListener('visibilitychange', refreshStatus)
    }
  }, [projectId])

  async function generate(event) {
    event.preventDefault()
    setError(null)
    setGenerating(true)

    try {
      await api.generate(projectId, prompt)
      setPrompt('')
      await load()
    } catch (failure) {
      setError(failure)
      // Fail hone pe bhi state badal chuki hoti hai: prompt history mein likha ja chuka
      // hai aur project FAILED ho gaya hai. Reload na karne se UI jhoot bolta rehta
      await load()
    } finally {
      setGenerating(false)
    }
  }

  if (!project) {
    return (
      <div className="p-6">
        <ErrorNote error={error} />
        {!error && <Spinner label="Loading project…" />}
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-4 border-b border-[var(--color-edge)] px-5 py-3">
        <Link to="/projects" className="text-sm text-slate-500 hover:text-slate-300">
          ← Projects
        </Link>
        <h1 className="flex-1 truncate font-medium text-slate-100">{project.name}</h1>
        <StatusBadge status={project.status} hasFiles={files.length > 0} />
      </header>

      <div className="flex min-h-0 flex-1">
        <aside className="flex w-96 flex-col border-r border-[var(--color-edge)]">
          <div className="min-h-0 flex-1 space-y-3 overflow-y-auto p-4">
            {messages.length === 0 && (
              <p className="text-sm text-slate-500">
                Describe the app you want. Ask for changes afterwards and it keeps the rest intact.
              </p>
            )}

            {messages.map((message) => (
              <div
                key={message.id}
                className={`rounded-xl px-3 py-2 text-sm ${
                  message.role === 'USER'
                    ? 'ml-6 bg-indigo-500/15 text-indigo-100'
                    : 'mr-6 bg-[var(--color-surface)] text-slate-300'
                }`}
              >
                {message.content}
              </div>
            ))}

            {generating && <Spinner label="Building — this takes a few seconds…" />}
          </div>

          <form onSubmit={generate} className="space-y-3 border-t border-[var(--color-edge)] p-4">
            <ErrorNote error={error} />

            <textarea
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder={
                files.length > 0 ? 'Make the header blue' : 'A todo list with a filter for done items'
              }
              rows={3}
              maxLength={2000}
              className="w-full resize-none rounded-lg border border-[var(--color-edge)] bg-[var(--color-canvas)] px-3 py-2 text-sm text-slate-100 outline-none placeholder:text-slate-600 focus:border-indigo-500"
            />

            <Button type="submit" disabled={generating || !prompt.trim()} className="w-full">
              {generating ? 'Building…' : files.length > 0 ? 'Apply change' : 'Build it'}
            </Button>
          </form>
        </aside>

        <main className="flex min-w-0 flex-1 flex-col">
          <div className="flex items-center gap-1 border-b border-[var(--color-edge)] px-4 py-2">
            {['preview', 'code'].map((name) => (
              <button
                key={name}
                onClick={() => setTab(name)}
                className={`rounded-md px-3 py-1.5 text-sm capitalize ${
                  tab === name ? 'bg-white/10 text-slate-100' : 'text-slate-500 hover:text-slate-300'
                }`}
              >
                {name}
              </button>
            ))}
            {tab === 'preview' && preview && (
              <button
                onClick={refreshPreview}
                className="ml-auto text-sm text-slate-500 hover:text-slate-300"
              >
                Reload
              </button>
            )}
          </div>

          {tab === 'preview' ? (
            preview ? (
              // Backend ki origin pe khulta hai, isliye yeh app ka DOM ya token chhu nahi
              // sakta. sandbox uske upar top-level navigation rokta hai; allow-same-origin
              // rakhna zaroori hai warna generated app ka localStorage phenk deta hai
              <iframe
                key={preview}
                src={preview}
                title="Preview"
                className="min-h-0 flex-1 bg-white"
                sandbox="allow-scripts allow-same-origin allow-forms"
              />
            ) : (
              <p className="p-6 text-sm text-slate-500">
                Nothing to preview yet. Describe an app on the left.
              </p>
            )
          ) : (
            <div className="min-h-0 flex-1 overflow-y-auto p-4">
              {files.length === 0 ? (
                <p className="text-sm text-slate-500">No files yet.</p>
              ) : (
                files.map((file) => (
                  <section key={file.id} className="mb-4">
                    <h2 className="mb-1.5 font-mono text-xs text-slate-500">{file.path}</h2>
                    <pre className="overflow-x-auto rounded-lg border border-[var(--color-edge)] bg-[var(--color-surface)] p-3 text-xs leading-relaxed text-slate-300">
                      {file.content}
                    </pre>
                  </section>
                ))
              )}
            </div>
          )}
        </main>
      </div>
    </div>
  )
}
