import { Client } from '@stomp/stompjs'
import { getToken } from './api.js'

// One shared STOMP connection per tab. Subscriptions survive reconnects (they are re-applied on connect).
let client = null
const subs = new Map() // id -> { destination, handler, stompSub }
let nextId = 1
const statusListeners = new Set()
let connected = false

function setConnected(v) {
  connected = v
  statusListeners.forEach((fn) => fn(v))
}

export function connectRealtime() {
  if (client) return
  const proto = location.protocol === 'https:' ? 'wss' : 'ws'
  client = new Client({
    brokerURL: `${proto}://${location.host}/ws`,
    connectHeaders: { Authorization: `Bearer ${getToken()}` },
    reconnectDelay: 3000,
    heartbeatIncoming: 20000,
    heartbeatOutgoing: 20000,
    beforeConnect: () => { client.connectHeaders = { Authorization: `Bearer ${getToken()}` } },
    onConnect: () => {
      setConnected(true)
      for (const s of subs.values()) s.stompSub = client.subscribe(s.destination, (m) => s.handler(JSON.parse(m.body)))
    },
    onWebSocketClose: () => setConnected(false),
    onStompError: (f) => console.warn('STOMP error', f.headers.message)
  })
  client.activate()
}

export function disconnectRealtime() {
  if (client) client.deactivate()
  client = null
  subs.clear()
  setConnected(false)
}

/** Returns an unsubscribe function. */
export function subscribe(destination, handler) {
  const id = nextId++
  const entry = { destination, handler, stompSub: null }
  subs.set(id, entry)
  if (client?.connected) entry.stompSub = client.subscribe(destination, (m) => handler(JSON.parse(m.body)))
  return () => {
    entry.stompSub?.unsubscribe()
    subs.delete(id)
  }
}

export function onConnectionChange(fn) {
  statusListeners.add(fn)
  fn(connected)
  return () => statusListeners.delete(fn)
}

/** Unique per browser tab; lets a tab ignore the echo of its own edits. */
export const CLIENT_ID = Math.random().toString(36).slice(2) + Date.now().toString(36)
