import { LayoutProvider } from './layout/LayoutProvider'
import AppRouter from './app/AppRouter'
import { BrowserRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from './app/queryClient'

export default function App() {
  return (
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <LayoutProvider>
          <AppRouter />
        </LayoutProvider>
      </QueryClientProvider>
    </BrowserRouter>
  )
}
