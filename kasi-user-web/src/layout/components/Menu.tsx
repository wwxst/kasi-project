import { memo } from 'react'
import { Menu as TMenu } from 'tdesign-react'
import { useLocation, useNavigate } from 'react-router-dom'
import { appRoutes } from '../../app/routes'
import { useLayout } from '../LayoutProvider'
import MenuLogo from './MenuLogo'
import Style from './Menu.module.less'

const { MenuItem } = TMenu

export default memo(({ showLogo = true }: { showLogo?: boolean }) => {
  const { state } = useLayout()
  const location = useLocation()
  const navigate = useNavigate()
  return (
    <TMenu
      width="232px"
      style={{ flexShrink: 0, height: '100%' }}
      className={Style.menuPanel2}
      value={location.pathname}
      theme={state.theme}
      collapsed={state.collapsed}
      logo={showLogo ? <MenuLogo collapsed={state.collapsed} /> : undefined}
    >
      {appRoutes.map(({ path, title, icon: Icon }) => (
        <MenuItem
          key={path}
          value={path}
          icon={<Icon />}
          onClick={() => navigate(path)}
        >
          {title}
        </MenuItem>
      ))}
    </TMenu>
  )
})
