import { afterEach, expect, it, vi } from 'vitest'
import { initTheme, setTheme, THEME_KEY } from './theme.js'

afterEach(() => vi.unstubAllGlobals())

it('keeps a saved white theme on a dark system and resumes system updates when selected', () => {
  const toggle = vi.fn()
  const style = {}
  const storage = { getItem: () => 'light', setItem: vi.fn() }
  const media = { matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }
  vi.stubGlobal('localStorage', storage)
  vi.stubGlobal('document', { documentElement: { classList: { toggle }, style } })
  vi.stubGlobal('window', { matchMedia: () => media })
  const dispose = initTheme()
  expect(toggle).toHaveBeenLastCalledWith('dark', false)
  expect(style.colorScheme).toBe('light')
  const onChange = media.addEventListener.mock.calls[0][1]
  onChange()
  expect(toggle).toHaveBeenLastCalledWith('dark', false)
  setTheme('dark')
  expect(toggle).toHaveBeenLastCalledWith('dark', true)
  setTheme('light')
  expect(storage.setItem).toHaveBeenLastCalledWith(THEME_KEY, 'light')
  setTheme('system')
  expect(toggle).toHaveBeenLastCalledWith('dark', true)
  media.matches = false
  onChange()
  expect(toggle).toHaveBeenLastCalledWith('dark', false)
  dispose()
  expect(media.removeEventListener).toHaveBeenCalledWith('change', onChange)
})
