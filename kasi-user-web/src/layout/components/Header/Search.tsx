import { Input } from 'tdesign-react'
import { SearchIcon } from 'tdesign-icons-react'
import Style from './Search.module.less'

export default function Search() {
  return (
    <Input
      className={Style.panel}
      prefixIcon={<SearchIcon />}
      placeholder="请输入搜索内容"
    />
  )
}
