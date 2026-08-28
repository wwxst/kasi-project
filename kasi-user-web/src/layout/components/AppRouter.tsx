import { Suspense } from 'react'
import { Loading } from 'tdesign-react'
import { Outlet, useLocation } from 'react-router-dom'
import { appRoutes } from '../../app/routes'
import Page from './Page'
import Style from './AppRouter.module.less'

export default function AppRouter() {
  const location = useLocation()
  const route = appRoutes.find((item) => item.path === location.pathname)
  return (
    <Suspense
      fallback={
        <div className={Style.loading}>
          <Loading />
        </div>
      }
    >
      <Page breadcrumbs={route ? ['工作台', route.title] : ['工作台']}>
        <Outlet />
      </Page>
    </Suspense>
  )
}
