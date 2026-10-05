import { BellOutlined } from '@ant-design/icons'
import { App as AntApp, Badge, Button, Empty, Popover, Spin, Typography, theme } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, getErrorMessage } from '../api/client'
import { formatDateTime } from '../utils/format'

const { Text } = Typography

interface NotificationRow {
  id: number
  type: string
  title: string
  body: string | null
  link: string | null
  read: boolean
  createdAt: string
}

interface NotificationPage {
  items: NotificationRow[]
  total: number
  page: number
  size: number
}

export default function NotificationBell() {
  const { message } = AntApp.useApp()
  const { token } = theme.useToken()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)

  const countQuery = useQuery({
    queryKey: ['notifications', 'count'],
    queryFn: async () => {
      const response = await api.get<{ count: number }>('/notifications/unread-count')
      return response.data.count
    },
    refetchInterval: 60_000,
  })

  const listQuery = useQuery({
    queryKey: ['notifications', 'list'],
    queryFn: async () => {
      const response = await api.get<NotificationPage>('/notifications', {
        params: { size: 12 },
      })
      return response.data
    },
    enabled: open,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notifications'] })

  const readMutation = useMutation({
    mutationFn: async (id: number) => api.post(`/notifications/${id}/read`),
    onSuccess: invalidate,
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const readAllMutation = useMutation({
    mutationFn: async () => api.post('/notifications/read-all'),
    onSuccess: invalidate,
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const count = countQuery.data ?? 0

  const openItem = (item: NotificationRow) => {
    if (!item.read) {
      readMutation.mutate(item.id)
    }
    setOpen(false)
    if (item.link) {
      navigate(item.link)
    }
  }

  const panel = (
    <div style={{ width: 340 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 8,
        }}
      >
        <Text strong>Thông báo</Text>
        {count > 0 && (
          <Button
            size="small"
            type="link"
            onClick={() => readAllMutation.mutate()}
            loading={readAllMutation.isPending}
          >
            Đọc tất cả
          </Button>
        )}
      </div>
      {listQuery.isLoading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin size="small" />
        </div>
      ) : listQuery.isError ? (
        <div style={{ textAlign: 'center', padding: 16 }}>
          <Text type="danger">Không tải được thông báo.</Text>
          <div style={{ marginTop: 8 }}>
            <Button size="small" onClick={() => listQuery.refetch()}>
              Thử lại
            </Button>
          </div>
        </div>
      ) : listQuery.data?.items.length ? (
        <div style={{ maxHeight: 360, overflowY: 'auto' }}>
          {listQuery.data.items.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => openItem(item)}
              style={{
                display: 'flex',
                gap: 8,
                width: '100%',
                textAlign: 'left',
                border: 'none',
                background: 'transparent',
                padding: '8px 4px',
                cursor: 'pointer',
                borderBottom: `1px solid ${token.colorBorderSecondary}`,
              }}
            >
              <span style={{ flex: 1, minWidth: 0 }}>
                <Text strong={!item.read} style={{ display: 'block' }}>
                  {item.title}
                </Text>
                {item.body && (
                  <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                    {item.body}
                  </Text>
                )}
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {formatDateTime(item.createdAt)}
                </Text>
              </span>
              {!item.read && (
                <Badge
                  color={token.colorPrimary}
                  style={{ marginTop: 6 }}
                  status="default"
                  dot
                />
              )}
            </button>
          ))}
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Không có thông báo."
          style={{ margin: '16px 0' }}
        />
      )}
    </div>
  )

  return (
    <Popover
      content={panel}
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
    >
      <button
        type="button"
        aria-label={count > 0 ? `Thông báo, ${count} chưa đọc` : 'Thông báo'}
        style={{
          border: 'none',
          background: 'transparent',
          padding: 6,
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
        }}
      >
        <Badge count={count} size="small" offset={[-2, 4]}>
          <BellOutlined style={{ fontSize: 18 }} />
        </Badge>
      </button>
    </Popover>
  )
}
