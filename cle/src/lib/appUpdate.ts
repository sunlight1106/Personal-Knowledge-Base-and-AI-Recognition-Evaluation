// Public build metadata only. Never reload a page or discard unfinished work.
export function watchAppUpdate(currentEntry: string, onUpdate: () => void, intervalMs = 300000) {
  if (!/^\/assets\/[\w.-]+\.js$/.test(currentEntry)) return () => {}
  let stopped = false, checking = false, lastCheck = -Infinity
  let controller: AbortController | undefined
  const check = async () => {
    if (stopped || checking || document.visibilityState === 'hidden' || Date.now() - lastCheck < 60000) return
    checking = true; lastCheck = Date.now(); controller = new AbortController()
    const timeout = setTimeout(() => controller?.abort(), 5000)
    try {
      const response = await fetch('/build-info.json', { cache: 'no-store', credentials: 'omit', signal: controller.signal })
      if (!response.ok) return
      const info: unknown = await response.json()
      if (!stopped && info && typeof info === 'object' && 'entry' in info && typeof info.entry === 'string'
          && /^\/assets\/[\w.-]+\.js$/.test(info.entry) && info.entry !== currentEntry) {
        onUpdate(); stop()
      }
    } catch { /* Offline or older deployments do not interrupt the current page. */ }
    finally { clearTimeout(timeout); checking = false }
  }
  const timer = setInterval(() => void check(), intervalMs)
  const focus = () => void check()
  function stop() {
    stopped = true; clearInterval(timer); controller?.abort()
    window.removeEventListener('focus', focus); document.removeEventListener('visibilitychange', focus)
  }
  window.addEventListener('focus', focus); document.addEventListener('visibilitychange', focus)
  void check()
  return stop
}
