import { Drawer, Switch, Space } from 'tdesign-react'
import { useLayout } from './LayoutProvider'

export default function SettingsDrawer() {
  const { state, dispatch } = useLayout()
  return (
    <Drawer
      destroyOnClose
      visible={state.settingOpen}
      size="458px"
      footer={false}
      header="页面配置"
      onClose={() => dispatch({ type: 'toggleSetting' })}
    >
      <Space direction="vertical" size="large">
        <label>
          主题模式{' '}
          <Switch
            value={state.theme === 'dark'}
            onChange={(checked) =>
              dispatch({
                type: 'switchTheme',
                value: checked ? 'dark' : 'light',
              })
            }
          />
        </label>
        <label>
          显示 Breadcrumb{' '}
          <Switch
            value={state.showBreadcrumbs}
            onChange={() => dispatch({ type: 'toggleBreadcrumbs' })}
          />
        </label>
        <label>
          显示 Footer{' '}
          <Switch
            value={state.showFooter}
            onChange={() => dispatch({ type: 'toggleFooter' })}
          />
        </label>
        <label>
          显示 Header{' '}
          <Switch
            value={state.showHeader}
            onChange={() => dispatch({ type: 'toggleHeader' })}
          />
        </label>
      </Space>
    </Drawer>
  )
}
