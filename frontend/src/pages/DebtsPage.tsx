import { App as AntApp, Button, DatePicker, Empty, Form, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import { CODE_CELL, formatDate, formatPeriod, formatVnd, MONEY_CELL, NUM_CELL } from '../utils/format'

interface DebtRow {
  invoiceId: number
  roomId: number
  houseId: number
  houseName: string
  roomNumber: string
  period: string
  tenantName: string | null
  totalAmount: number
  paidAmount: number
  remainingAmount: number
  status: 'UNPAID' | 'PARTIAL'
  dueDate: string
  overdueDays: number
  level: 'NOT_DUE' | 'OVERDUE' | 'LATE' | 'DEBT'
}

interface HouseOption {
  id: number
  name: string
  code: string
}

const LEVEL_META: Record<DebtRow['level'], { label: string; color: string }> = {
  NOT_DUE: { label: 'Chưa đến hạn', color: 'blue' },
  OVERDUE: { label: 'Quá hạn', color: 'gold' },
  LATE: { label: 'Chậm', color: 'orange' },
  DEBT: { label: 'Nợ', color: 'red' },
}

export default function DebtsPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()
  const isAdmin = me?.role === 'ADMIN'

  const [levelFilter, setLevelFilter] = useState<string | null>(null)
  const [houseFilter, setHouseFilter] = useState<number | null>(null)
  const [page, setPage] = useState(0)
  const [editing, setEditing] = useState<DebtRow | null>(null)
  const [editValue, setEditValue] = useState<Dayjs | null>(null)

  const debtsQuery = useQuery({
    queryKey: ['debts', levelFilter, houseFilter, page],
    queryFn: async () =>
      (
        await api.get<{ items: DebtRow[]; total: number }>('/billing/invoices/debts', {
          params: {
            level: levelFilter ?? undefined,
            houseId: houseFilter ?? undefined,
            page,
            size: 20,
          },
        })
      ).data,
  })

  const housesQuery = useQuery({
    queryKey: ['houses'],
    queryFn: async () => (await api.get<HouseOption[]>('/houses')).data,
  })

  const updateMutation = useMutation({
    mutationFn: async () => {
      if (!editing || !editValue) {
        throw new Error('Thiếu ngày đến hạn')
      }
      return (
        await api.put(`/billing/invoices/${editing.invoiceId}/due-date`, {
          dueDate: editValue.format('YYYY-MM-DD'),
        })
      ).data
    },
    onSuccess: () => {
      message.success('Đã lưu ngày đến hạn.')
      setEditing(null)
      debtsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const hasFilter = levelFilter != null || houseFilter != null

  const columns = [
    {
      title: 'Kỳ',
      dataIndex: 'period',
      key: 'period',
      width: 90,
      render: (value: string) => formatPeriod(value),
    },
    {
      title: 'Phòng',
      key: 'room',
      width: 180,
      render: (_: unknown, row: DebtRow) => (
        <span>
          <span style={CODE_CELL}>{row.roomNumber}</span>
          <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
            {row.houseName}
          </Typography.Text>
        </span>
      ),
    },
    {
      title: 'Khách thuê',
      dataIndex: 'tenantName',
      key: 'tenantName',
      width: 150,
      render: (value: string | null) => value ?? '-',
    },
    {
      title: 'Còn lại',
      dataIndex: 'remainingAmount',
      key: 'remainingAmount',
      width: 130,
      align: 'right' as const,
      render: (value: number) => <span style={MONEY_CELL}>{formatVnd(value)}</span>,
    },
    {
      title: 'Đến hạn',
      dataIndex: 'dueDate',
      key: 'dueDate',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    {
      title: 'Mức',
      dataIndex: 'level',
      key: 'level',
      width: 130,
      render: (value: DebtRow['level']) => <Tag color={LEVEL_META[value].color}>{LEVEL_META[value].label}</Tag>,
    },
    {
      title: 'Quá hạn (ngày)',
      dataIndex: 'overdueDays',
      key: 'overdueDays',
      width: 130,
      align: 'right' as const,
      render: (value: number) => <span style={NUM_CELL}>{value > 0 ? value : '-'}</span>,
    },
    ...(isAdmin
      ? [
          {
            title: 'Thao tác',
            key: 'actions',
            width: 90,
            render: (_: unknown, row: DebtRow) => (
              <Button
                size="small"
                onClick={() => {
                  setEditing(row)
                  setEditValue(dayjs(row.dueDate))
                }}
              >
                Sửa
              </Button>
            ),
          },
        ]
      : []),
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={2} style={{ margin: 0 }}>
          Công nợ
        </Typography.Title>
        <Space wrap>
          <Select
            allowClear
            placeholder="Mức công nợ"
            style={{ width: 170 }}
            value={levelFilter}
            onChange={(value) => {
              setLevelFilter(value ?? null)
              setPage(0)
            }}
            options={Object.entries(LEVEL_META).map(([key, meta]) => ({
              value: key,
              label: meta.label,
            }))}
          />
          <Select
            allowClear
            placeholder="Nhà"
            style={{ width: 190 }}
            value={houseFilter}
            onChange={(value) => {
              setHouseFilter(value ?? null)
              setPage(0)
            }}
            options={(housesQuery.data ?? []).map((house) => ({
              value: house.id,
              label: `${house.name} (${house.code})`,
            }))}
          />
        </Space>
      </Space>

      {debtsQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách công nợ: ${getErrorMessage(debtsQuery.error)}`}
        >
          <Button loading={debtsQuery.isFetching} onClick={() => debtsQuery.refetch()}>
            Thử lại
          </Button>
        </Empty>
      )}

      {!debtsQuery.isError && (
        <Table<DebtRow>
          rowKey="invoiceId"
          loading={debtsQuery.isLoading}
          columns={columns}
          dataSource={debtsQuery.data?.items}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  hasFilter
                    ? 'Không có công nợ khớp bộ lọc'
                    : 'Chưa có công nợ nào. Hóa đơn chưa đóng đủ sẽ hiển thị tại đây.'
                }
              >
                {hasFilter && (
                  <Button
                    onClick={() => {
                      setLevelFilter(null)
                      setHouseFilter(null)
                      setPage(0)
                    }}
                  >
                    Xoá bộ lọc
                  </Button>
                )}
              </Empty>
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total: debtsQuery.data?.total ?? 0,
            showTotal: (total) => `Tổng ${total} công nợ`,
            onChange: (nextPage) => setPage(nextPage - 1),
          }}
        />
      )}

      <Modal
        open={editing != null}
        title="Sửa ngày đến hạn"
        okText="Lưu"
        cancelText="Huỷ"
        confirmLoading={updateMutation.isPending}
        onOk={() => updateMutation.mutate()}
        onCancel={() => setEditing(null)}
      >
        {editing && (
          <>
            <Typography.Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
              Hóa đơn kỳ {formatPeriod(editing.period)}, phòng {editing.roomNumber}
            </Typography.Text>
            <Form layout="vertical">
              <Form.Item label="Ngày đến hạn" required>
                <DatePicker
                  style={{ width: '100%' }}
                  allowClear={false}
                  format="DD/MM/YYYY"
                  placeholder="Chọn ngày"
                  value={editValue}
                  onChange={(value: Dayjs | null) => setEditValue(value)}
                />
              </Form.Item>
            </Form>
          </>
        )}
      </Modal>
    </div>
  )
}
