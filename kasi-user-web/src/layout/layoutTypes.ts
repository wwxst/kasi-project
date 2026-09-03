export interface LayoutState {
  collapsed: boolean
}

export type LayoutAction = { type: 'toggleMenu'; value?: boolean | null }
