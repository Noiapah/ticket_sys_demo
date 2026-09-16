let credential: string | undefined
let ready: Promise<void> | undefined

export function waitForDesktop(): Promise<void> {
  if (import.meta.env.MODE === 'mock' || credential) return Promise.resolve()
  if (!ready) ready = new Promise<void>(resolve => {
    window.acceptDesktopSession = token => {
      if (!/^[A-Za-z0-9_-]{43}$/.test(token)) return
      credential = token
      delete window.acceptDesktopSession
      resolve()
    }
  })
  return ready
}

export async function apiFetch(path: string, init?: RequestInit): Promise<Response> {
  await waitForDesktop()
  const headers = new Headers(init?.headers)
  headers.set('X-Desktop-Token', credential!)
  if (init?.body) headers.set('Content-Type', 'application/json')
  const response = await fetch(`/api${path}`, { ...init, headers, cache: 'no-store', credentials: 'omit', redirect: 'error' })
  if (response.status === 401) window.dispatchEvent(new Event('session-ended'))
  return response
}
