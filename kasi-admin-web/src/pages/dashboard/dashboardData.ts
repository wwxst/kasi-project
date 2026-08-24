export interface ChartDatum {
  x: string
  y: number
}

export interface SearchDatum {
  index: number
  keyword: string
  count: number
  range: number
  status: 'up' | 'down'
}

export interface CategoryDatum {
  type: string
  value: number
}

export interface StoreDatum {
  name: string
  conversion: number
}

export interface StoreTrendDatum {
  time: string
  type: string
  value: number
}

export const visitData: ChartDatum[] = [7, 5, 4, 2, 4, 7, 5, 6, 5, 9, 6, 3].map(
  (value, index) => ({ x: `${index + 1}日`, y: value }),
)

export const salesData: ChartDatum[] = [
  520, 990, 360, 510, 1080, 985, 1090, 1110, 730, 1190, 1135, 420,
].map((value, index) => ({ x: `${index + 1}月`, y: value }))

export const rankingData = Array.from({ length: 7 }, (_, index) => ({
  title: `工专路 ${index} 号店`,
  total: 323234,
}))

export const searchData: SearchDatum[] = [
  { index: 1, keyword: '搜索关键词-0', count: 577, range: 11, status: 'up' },
  { index: 2, keyword: '搜索关键词-1', count: 894, range: 89, status: 'up' },
  { index: 3, keyword: '搜索关键词-2', count: 73, range: 47, status: 'up' },
  { index: 4, keyword: '搜索关键词-3', count: 846, range: 9, status: 'up' },
  { index: 5, keyword: '搜索关键词-4', count: 965, range: 53, status: 'up' },
  { index: 6, keyword: '搜索关键词-5', count: 303, range: 23, status: 'down' },
  { index: 7, keyword: '搜索关键词-6', count: 728, range: 36, status: 'up' },
  { index: 8, keyword: '搜索关键词-7', count: 412, range: 18, status: 'down' },
  { index: 9, keyword: '搜索关键词-8', count: 689, range: 42, status: 'up' },
  { index: 10, keyword: '搜索关键词-9', count: 251, range: 12, status: 'down' },
]

export const salesCategories: Record<
  'all' | 'online' | 'stores',
  CategoryDatum[]
> = {
  all: [
    { type: '家用电器', value: 4544 },
    { type: '食用酒水', value: 3321 },
    { type: '个护健康', value: 3113 },
    { type: '服饰箱包', value: 2341 },
    { type: '母婴产品', value: 1231 },
    { type: '其他', value: 1231 },
  ],
  online: [
    { type: '家用电器', value: 244 },
    { type: '食用酒水', value: 321 },
    { type: '个护健康', value: 311 },
    { type: '服饰箱包', value: 141 },
    { type: '母婴产品', value: 121 },
    { type: '其他', value: 111 },
  ],
  stores: [
    { type: '家用电器', value: 399 },
    { type: '食用酒水', value: 288 },
    { type: '个护健康', value: 344 },
    { type: '服饰箱包', value: 255 },
    { type: '母婴产品', value: 198 },
    { type: '其他', value: 165 },
  ],
}

export const stores: StoreDatum[] = [60, 20, 40, 10, 90, 20, 20, 80].map(
  (conversion, index) => ({ name: `Stores ${index}`, conversion }),
)

const trafficValues = [38, 52, 45, 71, 62, 86, 73, 92, 68, 81, 74, 96]
const paymentValues = [18, 27, 24, 39, 34, 48, 41, 55, 37, 46, 43, 59]

export const storeTrendData: StoreTrendDatum[] = trafficValues.flatMap(
  (value, index) => [
    { time: `${String(index + 9).padStart(2, '0')}:00`, type: '客流量', value },
    {
      time: `${String(index + 9).padStart(2, '0')}:00`,
      type: '支付笔数',
      value: paymentValues[index] ?? 0,
    },
  ],
)
