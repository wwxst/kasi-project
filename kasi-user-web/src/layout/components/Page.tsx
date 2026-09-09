import { Layout } from 'tdesign-react'
import type { PropsWithChildren } from 'react'
import Style from './Page.module.less'

export default function Page({
  children,
  className,
}: PropsWithChildren<{ className?: string }>) {
  return (
    <Layout.Content className={`${Style.panel} ${className ?? ''}`}>
      {children}
    </Layout.Content>
  )
}
