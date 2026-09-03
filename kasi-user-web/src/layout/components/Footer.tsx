import { Layout, Row } from 'tdesign-react'

export default function Footer() {
  return (
    <Layout.Footer>
      <Row justify="center">
        Copyright © 2021-{new Date().getFullYear()} Kasi. All Rights Reserved
      </Row>
    </Layout.Footer>
  )
}
