import { Layout, Breadcrumb } from 'tdesign-react'
import type { PropsWithChildren } from 'react'
import Style from './Page.module.less'

export default function Page({
  children,
  breadcrumbs = [],
  className,
}: PropsWithChildren<{ breadcrumbs?: string[]; className?: string }>) {
  return (
    <Layout.Content className={`${Style.panel} ${className ?? ''}`}>
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
