import client from './client'
import type { LoginResponse, UserProfile } from '@/types'

export interface RegisterPayload {
  username: string
  password: string
  displayName: string
  email?: string
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return client.post('/auth/login', { username, password })
}

export function register(payload: RegisterPayload): Promise<UserProfile> {
  return client.post('/auth/register', payload)
}

export function fetchMe(): Promise<UserProfile> {
  return client.get('/users/me')
}
