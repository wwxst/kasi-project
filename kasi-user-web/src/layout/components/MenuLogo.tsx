import { memo } from 'react'
import { useNavigate } from 'react-router-dom'
import logo from '../../assets/image/kasi-brand-logo.png'
import Style from './Menu.module.less'

export default memo(({ collapsed }: { collapsed?: boolean }) => {
  const navigate = useNavigate()
  return (
    <div className={Style.menuLogo} onClick={() => navigate('/workspace')}>
      <img src={logo} alt="卡司短剧推广平台" />
      {!collapsed && <strong>卡司短剧推广平台</strong>}
    </div>
  )
})
