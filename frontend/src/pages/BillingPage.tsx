import {
  App as AntApp,
  Button,
  DatePicker,
  Empty,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import FeeRateModal from '../components/billing/FeeRateModal'
import InvoiceDrawer from '../components/billing/InvoiceDrawer'
import MeterModal from '../components/billing/MeterModal'
import { formatVnd } from '../utils/format'

interface InvoiceRow {
  id: number
  roomId: number
  houseId: number
  houseName: string
  roomNumber: string
  period: string
  totalAmount: number
  paidAmount: number
  status: 'DRAFT' | 'UNPAID' | 'PARTIAL' | 'PAID'
  note: string | null
  lineCount: number
}

interface HouseOption {
  id: number
  name: string
  code: string
}

const STATUS_META: Record<InvoiceRow['status'], { label: string; color?: string }> = {
  DRAFT: { label: 'Nháp', color: 'default' },
  UNPAID: { label: 'Chưa đóng', color: 'red' },
  PARTIAL: { label: 'Đóng một phần', color: 'orange' },
  PAID: { label: 'Đã đóng đủ', color: 'green' },
}

export default function BillingPage() {
  const { me } = useAuth()
  const { message, modal } = AntApp.useApp()
  const canManage = me?.role === 'ADMIN' || me?.role === 'MANAGER'
  const isAdmin = me?.role === 'ADMIN'

  const [period, setPeriod] = useState<string>(dayjs().format('YYYY-MM'))
  const [houseFilter, setHouseFilter] = useState<number | null>(null)
  const [statusFilter, setStatusFilter] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [selectedInvoiceId, setSelectedInvoiceId] = useState<number | null>(null)
  const [meterOpen, setMeterOpen] = useState(false)
  const [feeRateOpen, setFeeRateOpen] = useState(false)

  const invoicesQuery = useQuery({
    queryKey: ['invoices', period, houseFilter, statusFilter, page],
    queryFn: async () =>
      (
        await api.get<{ items: InvoiceRow[]; total: number }>('/billing/invoices', {
          params: {
            period,
            houseId: houseFilter ?? undefined,
            status: statusFilter ?? undefined,
            page,
            size: 20,
          },
        })
      ).data,
  })

  const housesQuery = useQuery({
    queryKey: ['houses'],
    enabled: canManage,
    queryFn: async () => (await api.get<HouseOption[]>('/houses')).data,
  })

  const generateMutation = useMutation({
    mutationFn: async () =>
      (
        await api.post<{ created: number; skipped: { roomNumber: string; reason: string }[] }>(
          `/billing/invoices/generate?period=${period}`,
        )
      ).data,
    onSuccess: (result) => {
      const skippedText = result.skipped
        .slice(0, 5)
        .map((item) => `${item.roomNumber}: ${item.reason}`)
        .join('; ')
      message.success(
        `Đã tạo ${result.created} hóa đơn` +
          (result.skipped.length > 0
            ? `, bỏ qua ${result.skipped.length} phòng${skippedText ? ` (${skippedText})` : ''}`
            : ''),
        6,
      )
      invoicesQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const publishMutation = useMutation({
    mutationFn: async (id: number) => api.post(`/billing/invoices/${id}/publish`),
    onSuccess: () => {
      message.success('Đã phát hành hóa đơn')
      invoicesQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const openGenerate = () => {
    modal.confirm({
      title: `Tạo hóa đơn kỳ ${period}?`,
      content:
        'Mỗi phòng có hợp đồng hoạt động trong kỳ sẽ tạo 1 hóa đơn nháp. Hóa đơn đã tồn tại sẽ được bỏ qua.',
      okText: 'Tạo',
      cancelText: 'Huỷ',
      onOk: () => generateMutation.mutate(),
    })
  }

  const columns = [
    {
      title: 'Phòng',
      key: 'room',
      width: 190,
      render: (_: unknown, row: InvoiceRow) => (
        <span>
          {row.roomNumber}
          <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
            {row.houseName}
          </Typography.Text>
        </span>
      ),
    },
    {
      title: 'Tổng tiền',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 140,
      align: 'right' as const,
      render: (value: number) => formatVnd(value),
    },
    {
      title: 'Đã đóng',
      dataIndex: 'paidAmount',
      key: 'paidAmount',
      width: 140,
      align: 'right' as const,
      render: (value: number) => formatVnd(value),
    },
    {
      title: 'Còn lại',
      key: 'remaining',
      width: 140,
      align: 'right' as const,
      render: (_: unknown, row: InvoiceRow) => formatVnd(row.totalAmount - row.paidAmount),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      render: (value: InvoiceRow['status']) => (
        <Tag color={STATUS_META[value].color}>{STATUS_META[value].label}</Tag>
      ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 240,
      render: (_: unknown, row: InvoiceRow) => (
        <Space>
          <Button size="small" onClick={() => setSelectedInvoiceId(row.id)}>
            Chi tiết
          </Button>
          {canManage && row.status === 'DRAFT' && (
            <Popconfirm
              title="Phát hành hóa đơn này?"
              description="Sau khi phát hành, khách có thể theo dõi và đóng tiền."
              okText="Phát hành"
              cancelText="Huỷ"
              onConfirm={() => publishMutation.mutate(row.id)}
            >
              <Button size="small" type="primary" loading={publishMutation.isPending}>
                Phát hành
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Hóa đơn
        </Typography.Title>
        <Space wrap>
          <DatePicker
            picker="month"
            allowClear={false}
            format="MM/YYYY"
            value={dayjs(period)}
            onChange={(value: Dayjs | null) => {
              if (value) {
                setPeriod(value.format('YYYY-MM'))
                setPage(0)
              }
            }}
          />
          {canManage && (
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
          )}
          <Select
            allowClear
            placeholder="Trạng thái"
            style={{ width: 150 }}
            value={statusFilter}
            onChange={(value) => {
              setStatusFilter(value ?? null)
              setPage(0)
            }}
            options={Object.entries(STATUS_META).map(([key, meta]) => ({
              value: key,
              label: meta.label,
            }))}
          />
          {canManage && (
            <>
              {isAdmin && <Button onClick={() => setFeeRateOpen(true)}>Cấu hình giá</Button>}
              <Button onClick={() => setMeterOpen(true)}>Nhập chỉ số</Button>
              <Tooltip title="Tạo hóa đơn theo tháng đang chọn">
                <Button type="primary" loading={generateMutation.isPending} onClick={openGenerate}>
                  Tạo hóa đơn
                </Button>
              </Tooltip>
            </>
          )}
        </Space>
      </Space>

      {invoicesQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách hóa đơn: ${getErrorMessage(invoicesQuery.error)}`}
        >
          <Button onClick={() => invoicesQuery.refetch()}>Thử lại</Button>
        </Empty>
      )}

      <Table<InvoiceRow>
        rowKey="id"
        loading={invoicesQuery.isLoading}
        columns={columns}
        dataSource={invoicesQuery.data?.items}
        locale={{
          emptyText: (
            <Empty
              description={`Chưa có hóa đơn kỳ ${period.replace('-', '/')}`}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ),
        }}
        pagination={{
          current: page + 1,
          pageSize: 20,
          total: invoicesQuery.data?.total ?? 0,
          showTotal: (total) => `Tổng ${total} hóa đơn`,
          onChange: (nextPage) => setPage(nextPage - 1),
        }}
      />

      <InvoiceDrawer
        invoiceId={selectedInvoiceId}
        open={selectedInvoiceId != null}
        onClose={() => setSelectedInvoiceId(null)}
        onChanged={() => invoicesQuery.refetch()}
        canManage={canManage}
      />
      <MeterModal open={meterOpen} onClose={() => setMeterOpen(false)} period={period} />
      <FeeRateModal open={feeRateOpen} onClose={() => setFeeRateOpen(false)} period={period} />
    </div>
  )
}
