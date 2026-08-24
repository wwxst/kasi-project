import '@testing-library/jest-dom/vitest'
import React from 'react'
import { vi } from 'vitest'

const ChartMock = () => React.createElement('div', { 'data-testid': 'chart' })

vi.mock('@ant-design/plots/es/components/area', () => ({ default: ChartMock }))
vi.mock('@ant-design/plots/es/components/column', () => ({
  default: ChartMock,
}))
vi.mock('@ant-design/plots/es/components/line', () => ({ default: ChartMock }))
vi.mock('@ant-design/plots/es/components/pie', () => ({ default: ChartMock }))

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

class ResizeObserverMock implements ResizeObserver {
  observe = vi.fn()
  unobserve = vi.fn()
  disconnect = vi.fn()
}

globalThis.ResizeObserver = ResizeObserverMock

const getComputedStyle = window.getComputedStyle
window.getComputedStyle = (element: Element) => getComputedStyle(element)
