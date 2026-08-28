export type LayoutTheme = 'light' | 'dark'

export interface LayoutState {
  collapsed: boolean
  theme: LayoutTheme
  showHeader: boolean
  showBreadcrumbs: boolean
  showFooter: boolean
  settingOpen: boolean
}

export type LayoutAction =
  | { type: 'toggleMenu'; value?: boolean | null }
  | { type: 'switchTheme'; value: LayoutTheme }
  | { type: 'toggleSetting' }
  | { type: 'toggleBreadcrumbs' }
  | { type: 'toggleFooter' }
  | { type: 'toggleHeader' }
