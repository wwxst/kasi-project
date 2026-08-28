import { memo } from 'react'
import { Button, Layout, Space } from 'tdesign-react'
import { ViewListIcon } from 'tdesign-icons-react'
import { useLayout } from '../../LayoutProvider'
import Search from './Search'
import HeaderIcon from './HeaderIcon'
import Style from './index.module.less'

export default memo(() => {
  const { state, dispatch } = useLayout()
  if (!state.showHeader) return null
  return (
    <Layout.Header className={Style.panel}>
      <Space align="center">
        <Button
          shape="square"
          size="large"
          variant="text"
          onClick={() => dispatch({ type: 'toggleMenu', value: null })}
          icon={<ViewListIcon />}
        />
        <Search />
      </Space>
      <HeaderIcon />
    </Layout.Header>
  )
})
