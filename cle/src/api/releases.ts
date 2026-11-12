import { request } from './client'
export interface ReleaseText { zh: string; en: string }
export interface ReleaseNotice {
  read: boolean
  release: { version: string; date: string; title: ReleaseText; added: ReleaseText[]; fixed: ReleaseText[]; improved: ReleaseText[]; notes: ReleaseText[] }
}
export const releaseApi = {
  current: (signal?: AbortSignal) => request<ReleaseNotice>('/notifications/release', { signal }),
  read: (version: string) => request<void>(`/notifications/releases/${encodeURIComponent(version)}/read`, { method: 'PUT' }),
}
