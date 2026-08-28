import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useReducer,
} from 'react'
import type { PropsWithChildren, Dispatch } from 'react'
import type { LayoutAction, LayoutState } from './layoutTypes'

const initialState: LayoutState = {
  collapsed: typeof window !== 'undefined' ? window.innerWidth < 1000 : false,
  theme: 'light',
  showHeader: true,
  showBreadcrumbs: true,
  showFooter: true,
  settingOpen: false,
}

export function layoutReducer(
  state: LayoutState,
  action: LayoutAction,
): LayoutState {
  switch (action.type) {
    case 'toggleMenu':
      return {
        ...state,
        collapsed:
          action.value === null || action.value === undefined
            ? !state.collapsed
            : action.value,
      }
    case 'switchTheme':
      return { ...state, theme: action.value }
    case 'toggleSetting':
      return { ...state, settingOpen: !state.settingOpen }
    case 'toggleBreadcrumbs':
      return { ...state, showBreadcrumbs: !state.showBreadcrumbs }
    case 'toggleFooter':
      return { ...state, showFooter: !state.showFooter }
    case 'toggleHeader':
      return { ...state, showHeader: !state.showHeader }
    default:
      return state
  }
}

const LayoutContext = createContext<{
  state: LayoutState
  dispatch: Dispatch<LayoutAction>
} | null>(null)

export function LayoutProvider({ children }: PropsWithChildren) {
  const [state, dispatch] = useReducer(layoutReducer, initialState)

  useEffect(() => {
    document.documentElement.setAttribute('theme-mode', state.theme)
  }, [state.theme])

  useEffect(() => {
    const onResize = () => {
      if (window.innerWidth < 900) dispatch({ type: 'toggleMenu', value: true })
      else if (window.innerWidth > 1000)
        dispatch({ type: 'toggleMenu', value: false })
    }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const value = useMemo(() => ({ state, dispatch }), [state])
  return (
    <LayoutContext.Provider value={value}>{children}</LayoutContext.Provider>
  )
}

export function useLayout() {
  const value = useContext(LayoutContext)
  if (!value) throw new Error('useLayout must be used inside LayoutProvider')
  return value
}
