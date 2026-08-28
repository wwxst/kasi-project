import { Layout } from 'tdesign-react'
import Menu from './Menu'
import Header from './Header'
import Footer from './Footer'
import AppRouter from './AppRouter'
import Style from './AppLayout.module.less'

export default function AppLayout() {
  return (
    <Layout className={`${Style.sidePanel} narrow-scrollbar`}>
      <Menu showLogo />
      <Layout className={Style.sideContainer}>
        <Header />
        <AppRouter />
        <Footer />
      </Layout>
    </Layout>
  )
}
