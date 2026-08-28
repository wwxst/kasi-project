import { Layout, Row } from 'tdesign-react'
import { useLayout } from '../LayoutProvider'

export default function Footer() {
  const { state } = useLayout()
  if (!state.showFooter) return null
  return (
    <Layout.Footer>
      <Row justify="center">
        Copyright © 2021-{new Date().getFullYear()} Kasi. All Rights Reserved
      </Row>
    </Layout.Footer>
  )
}
