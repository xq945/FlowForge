import client from './client'
import type { NotificationView, PageResult } from '@/types'

export function listNotifications(params: {
  unreadOnly?: boolean
  page?: number
  size?: number
}): Promise<PageResult<NotificationView>> {
  return client.get('/notifications', { params })
}

export function unreadCount(): Promise<{ count: number }> {
  return client.get('/notifications/unread-count')
}

export function markRead(id: number): Promise<void> {
  return client.post(`/notifications/${id}/read`)
}
