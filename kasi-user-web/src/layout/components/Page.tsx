import { Layout, Breadcrumb } from 'tdesign-react'
import type { PropsWithChildren } from 'react'
import Style from './Page.module.less'

export default function Page({
  children,
  breadcrumbs = [],
}: PropsWithChildren<{ breadcrumbs?: string[] }>) {
  return (
    <Layout.Content className={Style.panel}>
      <Breadcrumb className={Style.breadcrumb}>
        {breadcrumbs.map((item) => (
          <Breadcrumb.BreadcrumbItem key={item}>
            {item}
          </Breadcrumb.BreadcrumbItem>
        ))}
      </Breadcrumb>
      {children}
    </Layout.Content>
  )
}
