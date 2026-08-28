import type { ComponentType } from 'react'
import {
  BookIcon,
  HomeIcon,
  LinkIcon,
  MoneyIcon,
  OrderListIcon,
  UsergroupIcon,
} from 'tdesign-icons-react'
import MediaAccountsPage from '../pages/mediaAccounts/MediaAccountsPage'
import DramaPage from '../pages/drama/DramaPage'
import WorkspacePage from '../pages/WorkspacePage'
import PromotionLinksPage from '../pages/promotionLinks/PromotionLinksPage'

export interface RouteConfig {
  path: string
  title: string
  icon: ComponentType
  element: ComponentType<{ title: string }>
}

export const appRoutes: RouteConfig[] = [
  { path: '/workspace', title: '首页', icon: HomeIcon, element: WorkspacePage },
  {
    path: '/workspace/media-accounts',
    title: '账号报白',
    icon: UsergroupIcon,
    element: MediaAccountsPage,
  },
  {
    path: '/workspace/drama',
    title: '短剧推广',
    icon: BookIcon,
    element: DramaPage,
  },
  {
    path: '/workspace/promotion-links',
    title: '推广任务',
    icon: LinkIcon,
    element: PromotionLinksPage,
  },
  {
    path: '/workspace/orders',
    title: '订单',
    icon: OrderListIcon,
    element: WorkspacePage,
  },
  {
    path: '/workspace/commission',
    title: '佣金',
    icon: MoneyIcon,
    element: WorkspacePage,
  },
]
