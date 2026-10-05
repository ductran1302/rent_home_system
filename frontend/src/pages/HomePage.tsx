import {
  AppstoreOutlined,
  BorderOutlined,
  DollarOutlined,
  FileTextOutlined,
  HomeOutlined,
  WalletOutlined,
} from '@ant-design/icons'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  Button,
  Card,
  Col,
  Empty,
  Row,
  Skeleton,
  Statistic,
  theme,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import { formatNumber, formatVnd } from '../utils/format'

interface Stats {
  houseCount: number
  roomCount: number
  vacantRoomCount: number
  activeContractCount: number
  unpaidInvoiceCount: number
  outstandingDebt: number
}

interface RevenueMonth {
  period: string
  collected: number
  outstanding: number
}

interface RevenueResponse {
  year: number
  months: RevenueMonth[]
}

const MONTH_LABELS = [
  'T1',
  'T2',
  'T3',
  'T4',
  'T5',
  'T6',
  'T7',
  'T8',
  'T9',
  'T10',
  'T11',
  'T12',
]

const CARD_COLOR = {
  house: '#1677FF',
  room: '#13C2C2',
  vacant: '#FA8C16',
  contract: '#52C41A',
  invoice: '#FAAD14',
  debt: '#FF4D4F',
}

const COLLECTED_COLOR = '#52C41A'
const OUTSTANDING_COLOR = '#FF4D4F'

export default function HomePage() {
  const { me } = useAuth()
  const { token } = theme.useToken()
  const isUser = me?.role === 'USER'

  const statsQuery = useQuery({
    queryKey: ['stats'],
    queryFn: async () => (await api.get<Stats>('/stats')).data,
  })

  const revenueQuery = useQuery({
    queryKey: ['stats', 'revenue'],
    queryFn: async () => (await api.get<RevenueResponse>('/stats/revenue')).data,
  })

  if (statsQuery.isLoading) {
    return (
      <Row gutter={[16, 16]}>
        {[1, 2, 3, 4].map((item) => (
          <Col key={item} xs={24} sm={12} lg={6}>
            <Card>
              <Skeleton active paragraph={false} />
            </Card>
          </Col>
        ))}
      </Row>
    )
  }

  if (statsQuery.isError) {
    return (
      <Empty
        style={{ margin: '48px 0' }}
        description={`Không tải được số liệu: ${getErrorMessage(statsQuery.error)}`}
      >
        <Button onClick={() => statsQuery.refetch()}>Thử lại</Button>
      </Empty>
    )
  }

  const stats = statsQuery.data
  if (!stats) {
    return <Empty description="Không có số liệu" />
  }

  const chartData = (revenueQuery.data?.months ?? []).map((month, index) => ({
    label: MONTH_LABELS[index] ?? month.period,
    collected: month.collected,
    outstanding: month.outstanding,
  }))

  return (
    <div>
      <Typography.Title level={2} style={{ marginTop: 0 }}>
        Tổng quan
      </Typography.Title>
      <Row gutter={[16, 16]}>
        {!isUser && (
          <>
            <Col xs={24} sm={12} lg={6}>
              <Card style={{ background: `${CARD_COLOR.house}14` }}>
                <Statistic
                  title="Nhà"
                  value={stats.houseCount}
                  prefix={<HomeOutlined style={{ color: CARD_COLOR.house }} />}
                  valueStyle={{ color: CARD_COLOR.house }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card style={{ background: `${CARD_COLOR.room}14` }}>
                <Statistic
                  title="Phòng"
                  value={stats.roomCount}
                  prefix={<AppstoreOutlined style={{ color: CARD_COLOR.room }} />}
                  valueStyle={{ color: CARD_COLOR.room }}
                />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card style={{ background: `${CARD_COLOR.vacant}14` }}>
                <Statistic
                  title="Phòng trống"
                  value={stats.vacantRoomCount}
                  prefix={<BorderOutlined style={{ color: CARD_COLOR.vacant }} />}
                  valueStyle={{ color: CARD_COLOR.vacant }}
                />
              </Card>
            </Col>
          </>
        )}
        <Col xs={24} sm={12} lg={6}>
          <Card style={{ background: `${CARD_COLOR.contract}14` }}>
            <Statistic
              title={isUser ? 'Hợp đồng của bạn' : 'Hợp đồng đang hiệu lực'}
              value={stats.activeContractCount}
              prefix={<FileTextOutlined style={{ color: CARD_COLOR.contract }} />}
              valueStyle={{ color: CARD_COLOR.contract }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card style={{ background: `${CARD_COLOR.invoice}14` }}>
            <Statistic
              title="Hóa đơn chưa đóng đủ (tháng này)"
              value={stats.unpaidInvoiceCount}
              prefix={<DollarOutlined style={{ color: CARD_COLOR.invoice }} />}
              valueStyle={{ color: CARD_COLOR.invoice }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card style={{ background: `${CARD_COLOR.debt}14` }}>
            <Statistic
              title={isUser ? 'Còn phải đóng (tháng này)' : 'Còn phải thu (tháng này)'}
              value={formatVnd(stats.outstandingDebt)}
              prefix={<WalletOutlined style={{ color: CARD_COLOR.debt }} />}
              valueStyle={{ color: stats.outstandingDebt > 0 ? token.colorError : undefined }}
            />
          </Card>
        </Col>
      </Row>
      <Card
        title={
          revenueQuery.data ? `Hóa đơn theo năm ${revenueQuery.data.year}` : 'Hóa đơn theo năm'
        }
        style={{ marginTop: 16 }}
      >
        {revenueQuery.isLoading ? (
          <Skeleton active paragraph={{ rows: 4 }} />
        ) : revenueQuery.isError ? (
          <Empty
            style={{ margin: '24px 0' }}
            description={`Không tải được dữ liệu hóa đơn: ${getErrorMessage(revenueQuery.error)}`}
          >
            <Button onClick={() => revenueQuery.refetch()}>Thử lại</Button>
          </Empty>
        ) : (
          <div style={{ width: '100%', height: 360 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} />
                <XAxis
                  dataKey="label"
                  tick={{ fill: token.colorTextSecondary }}
                />
                <YAxis
                  tickFormatter={(value: number) => formatNumber(value, 0)}
                  width={100}
                  tick={{ fill: token.colorTextSecondary }}
                />
                <Tooltip
                  formatter={(value) => formatVnd(Number(value))}
                  contentStyle={{
                    backgroundColor: token.colorBgElevated,
                    borderColor: token.colorBorder,
                    color: token.colorText,
                  }}
                  labelStyle={{ color: token.colorTextSecondary }}
                  itemStyle={{ color: token.colorText }}
                />
                <Legend wrapperStyle={{ color: token.colorTextSecondary }} />
                <Bar
                  dataKey="collected"
                  name={isUser ? 'Đã đóng' : 'Đã thu'}
                  stackId="total"
                  fill={COLLECTED_COLOR}
                  maxBarSize={40}
                />
                <Bar
                  dataKey="outstanding"
                  name={isUser ? 'Còn phải đóng' : 'Còn phải thu'}
                  stackId="total"
                  fill={OUTSTANDING_COLOR}
                  maxBarSize={40}
                />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>
    </div>
  )
}
