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
import { formatVnd } from '../utils/format'

interface Stats {
  houseCount: number
  roomCount: number
  vacantRoomCount: number
  activeContractCount: number
  unpaidInvoiceCount: number
  outstandingDebt: number
}

export default function HomePage() {
  const { me } = useAuth()
  const { token } = theme.useToken()
  const isUser = me?.role === 'USER'

  const statsQuery = useQuery({
    queryKey: ['stats'],
    queryFn: async () => (await api.get<Stats>('/stats')).data,
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

  return (
    <div>
      <Typography.Title level={2} style={{ marginTop: 0 }}>
        Tổng quan
      </Typography.Title>
      <Row gutter={[16, 16]}>
        {!isUser && (
          <>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic title="Nhà" value={stats.houseCount} />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic title="Phòng" value={stats.roomCount} />
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card>
                <Statistic title="Phòng trống" value={stats.vacantRoomCount} />
              </Card>
            </Col>
          </>
        )}
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="Hợp đồng đang hiệu lực" value={stats.activeContractCount} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Hóa đơn chưa đóng đủ (tháng này)"
              value={stats.unpaidInvoiceCount}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="Còn phải thu (tháng này)"
              value={formatVnd(stats.outstandingDebt)}
              valueStyle={{ color: stats.outstandingDebt > 0 ? token.colorError : undefined }}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
