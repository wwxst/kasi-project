import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './shared/ui/tdesignReact19Adapter'
import 'tdesign-react/es/style/index.css'
import App from './App'
import './styles/global.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
