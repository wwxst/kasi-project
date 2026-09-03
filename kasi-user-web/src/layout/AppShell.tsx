import { Layout } from 'tdesign-react'
import { useLayout } from './LayoutProvider'
import Menu from './components/Menu'
import Header from './components/Header'
import Footer from './components/Footer'
import AppRouter from './components/AppRouter'
import Style from './AppShell.module.less'

export default function AppShell() {
  const { state } = useLayout()
  return (
    <>
      <Layout
        className={`${Style.sidePanel} ${state.collapsed ? Style.collapsed : ''}`}
      >
        <Menu showLogo />
        <Layout className={Style.sideContainer}>
          <Header />
          <AppRouter />
          <Footer />
        </Layout>
      </Layout>
    </>
  )
}
