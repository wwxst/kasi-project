import { memo } from 'react'
import { useNavigate } from 'react-router-dom'
import fullLogo from '../../assets/svg/assets-logo-full.svg'
import miniLogo from '../../assets/svg/assets-t-logo.svg'
import Style from './Menu.module.less'

export default memo(({ collapsed }: { collapsed?: boolean }) => {
  const navigate = useNavigate()
  return (
    <div className={Style.menuLogo} onClick={() => navigate('/workspace')}>
      <img src={collapsed ? miniLogo : fullLogo} alt="Kasi" />
    </div>
  )
})
