import {
  App as AntApp,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, getErrorMessage } from '../../api/client'
import { formatNumber, formatPeriod, formatVnd, MONEY_CELL } from '../../utils/format'

export interface InvoiceLine {
  id: number
  feeTypeId: number
  feeCode: string
  description: string
  quantity: number
  unitPrice: number
  amount: number
}

export interface InvoiceDetail {
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
  roomPriceNote: string | null
  contractRent: number | null
  preElectReading: number | null
  currentElectReading: number | null
  preWaterReading: number | null
  currentWaterReading: number | null
  bankAccount: string | null
  lines: InvoiceLine[]
}

interface FeeType {
  id: number
  code: string
  name: string
  unit: string
}

interface LineFormValues {
  feeTypeId: number
  quantity: number
  unitPrice: number
  description?: string
}

interface RoomPriceFormValues {
  amount: number
  note?: string
}

interface ReadingsFormValues {
  preElectReading?: number | null
  currentElectReading?: number | null
  preWaterReading?: number | null
  currentWaterReading?: number | null
}

const STATUS_META: Record<InvoiceDetail['status'], { label: string; color?: string }> = {
  DRAFT: { label: 'Nháp', color: 'default' },
  UNPAID: { label: 'Chưa đóng', color: 'red' },
  PARTIAL: { label: 'Đóng một phần', color: 'orange' },
  PAID: { label: 'Đã đóng đủ', color: 'green' },
}

const LINE_META: Record<string, { order: number; label: string }> = {
  PHONG: { order: 0, label: 'Tiền phòng' },
  DIEN: { order: 1, label: 'Tiền điện' },
  NUOC: { order: 2, label: 'Tiền nước' },
  MANG: { order: 3, label: 'Tiền mạng' },
  DICH_VU: { order: 4, label: 'Tiền dịch vụ' },
}

export default function InvoiceDrawer({
  invoiceId,
  open,
  onClose,
  onChanged,
  canManage,
}: {
  invoiceId: number | null
  open: boolean
  onClose: () => void
  onChanged: () => void
  canManage: boolean
}) {
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [paymentOpen, setPaymentOpen] = useState(false)
  const [lineModalOpen, setLineModalOpen] = useState(false)
  const [editingLine, setEditingLine] = useState<InvoiceLine | null>(null)
  const [lineForm] = Form.useForm<LineFormValues>()
  const [paymentForm] = Form.useForm<{ amount: number }>()
  const [roomPriceForm] = Form.useForm<RoomPriceFormValues>()
  const [readingsForm] = Form.useForm<ReadingsFormValues>()
  const [qrBroken, setQrBroken] = useState(false)

  useEffect(() => {
    setQrBroken(false)
  }, [invoiceId])

  const detailQuery = useQuery({
    queryKey: ['invoice', invoiceId],
    enabled: open && invoiceId != null,
    queryFn: async () => (await api.get<InvoiceDetail>(`/billing/invoices/${invoiceId}`)).data,
  })

  const feeTypesQuery = useQuery({
    queryKey: ['fee-types'],
    queryFn: async () => (await api.get<FeeType[]>('/billing/fee-types')).data,
  })

  const afterChange = () => {
    queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
    queryClient.invalidateQueries({ queryKey: ['invoices'] })
    onChanged()
  }

  const publishMutation = useMutation({
    mutationFn: async () => api.post(`/billing/invoices/${invoiceId}/publish`),
    onSuccess: () => {
      message.success('Đã phát hành hóa đơn.')
      afterChange()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const payMutation = useMutation({
    mutationFn: async (amount: number) =>
      api.post(`/billing/invoices/${invoiceId}/payments`, { amount }),
    onSuccess: () => {
      message.success('Đã ghi nhận đóng tiền.')
      setPaymentOpen(false)
      paymentForm.resetFields()
      afterChange()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const saveLineMutation = useMutation({
    mutationFn: async (values: LineFormValues) => {
      if (editingLine) {
        return api.put(`/billing/invoices/${invoiceId}/lines/${editingLine.id}`, {
          quantity: values.quantity,
          unitPrice: values.unitPrice,
        })
      }
      return api.post(`/billing/invoices/${invoiceId}/lines`, values)
    },
    onSuccess: () => {
      message.success(editingLine ? 'Đã cập nhật dòng tiền.' : 'Đã thêm dòng tiền.')
      setLineModalOpen(false)
      setEditingLine(null)
      lineForm.resetFields()
      afterChange()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteLineMutation = useMutation({
    mutationFn: async (lineId: number) =>
      api.delete(`/billing/invoices/${invoiceId}/lines/${lineId}`),
    onSuccess: () => {
      message.success('Đã xoá dòng tiền.')
      afterChange()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const detail = detailQuery.data
  const remaining = detail ? detail.totalAmount - detail.paidAmount : 0
  const roomLineAmount = detail?.lines.find((line) => line.feeCode === 'PHONG')?.amount ?? null
  const canEdit = canManage && detail != null && detail.status !== 'PAID'
  const displayUnit = (unit?: string) => {
    const upper = unit?.toUpperCase()
    if (upper === 'KWH') return 'kWh'
    if (upper === 'M3') return 'm³'
    return unit
  }
  const unitElect = displayUnit(feeTypesQuery.data?.find((type) => type.code === 'DIEN')?.unit)
  const unitWater = displayUnit(feeTypesQuery.data?.find((type) => type.code === 'NUOC')?.unit)
  const sortedLines = [...(detail?.lines ?? [])].sort(
    (a, b) => (LINE_META[a.feeCode]?.order ?? 5) - (LINE_META[b.feeCode]?.order ?? 5),
  )
  const qrMonth = detail ? Number(detail.period.slice(5)) : null
  const qrContent = detail ? `${detail.roomNumber} TIEN PHONG THANG ${qrMonth}` : ''

  const updateRoomPriceMutation = useMutation({
    mutationFn: async (values: RoomPriceFormValues) =>
      api.put(`/billing/invoices/${invoiceId}/room-price`, {
        amount: values.amount,
        note: values.note?.trim() ? values.note.trim() : null,
      }),
    onSuccess: () => {
      message.success('Đã cập nhật giá phòng.')
      afterChange()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const saveReadingsMutation = useMutation({
    mutationFn: async (values: ReadingsFormValues) =>
      api.put(`/billing/invoices/${invoiceId}/readings`, {
        preElectReading: values.preElectReading ?? null,
        currentElectReading: values.currentElectReading ?? null,
        preWaterReading: values.preWaterReading ?? null,
        currentWaterReading: values.currentWaterReading ?? null,
      }),
    onSuccess: () => {
      message.success('Đã cập nhật chỉ số công tơ.')
      afterChange()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  useEffect(() => {
    if (detail) {
      roomPriceForm.setFieldsValue({
        amount: roomLineAmount ?? detail.contractRent ?? 0,
        note: detail.roomPriceNote ?? undefined,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail?.id, detail?.roomPriceNote, detail?.totalAmount])

  useEffect(() => {
    if (detail) {
      readingsForm.setFieldsValue({
        preElectReading: detail.preElectReading,
        currentElectReading: detail.currentElectReading,
        preWaterReading: detail.preWaterReading,
        currentWaterReading: detail.currentWaterReading,
      })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    detail?.id,
    detail?.preElectReading,
    detail?.currentElectReading,
    detail?.preWaterReading,
    detail?.currentWaterReading,
  ])

  const watchedPreElect = Form.useWatch('preElectReading', readingsForm)
  const watchedCurrentElect = Form.useWatch('currentElectReading', readingsForm)
  const watchedPreWater = Form.useWatch('preWaterReading', readingsForm)
  const watchedCurrentWater = Form.useWatch('currentWaterReading', readingsForm)

  const formatReading = (value: number | null | undefined) =>
    value == null ? null : formatNumber(value, 2)

  const consumptionText = (pre: number | null | undefined, current: number | null | undefined) => {
    if (pre == null || current == null) return null
    const qty = current - pre
    return formatNumber(qty, 2)
  }

  const extraText = (pre: number | null | undefined, current: number | null | undefined, unit?: string) => {
    if (pre == null || current == null || current < pre) return undefined
    const qty = formatNumber(current - pre, 2)
    return `Tiêu thụ: ${qty}${unit ? ` ${unit}` : ''}`
  }

  const describeReadings = (pre: number | null, current: number | null, unit?: string) => {
    if (pre == null && current == null) return 'chưa nhập'
    const qty = consumptionText(pre, current)
    const prefix = `${formatReading(pre) ?? 'chưa nhập'} → ${formatReading(current) ?? 'chưa nhập'}`
    return qty != null ? `${prefix} (tiêu thụ ${qty}${unit ? ` ${unit}` : ''})` : prefix
  }

  const preMissing = (pre: number | null | undefined, current: number | null | undefined) =>
    current != null && (pre == null || pre === undefined)

  const lessThanPre = (pre: number | null | undefined, current: number | null | undefined) =>
    pre != null && current != null && current < pre

  const readingError = (
    pre: number | null | undefined,
    current: number | null | undefined,
  ): string | null => {
    if (lessThanPre(pre, current)) return 'Phải lớn hơn hoặc bằng chỉ số tháng trước'
    if (preMissing(pre, current)) return 'Vui lòng nhập chỉ số tháng trước'
    return null
  }

  const electErrorText = readingError(watchedPreElect, watchedCurrentElect)
  const waterErrorText = readingError(watchedPreWater, watchedCurrentWater)

  const fixedLabel = (text: string) => (
    <span style={{ display: 'inline-block', width: 48 }}>{text}</span>
  )

  const lineColumns = [
    {
      title: 'Khoản thu',
      key: 'label',
      ellipsis: true,
      render: (_: unknown, line: InvoiceLine) => LINE_META[line.feeCode]?.label ?? line.description,
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      key: 'quantity',
      width: 100,
      align: 'right' as const,
      render: (value: number) => formatNumber(value),
    },
    {
      title: 'Đơn giá',
      dataIndex: 'unitPrice',
      key: 'unitPrice',
      width: 130,
      align: 'right' as const,
      render: (value: number) => <span style={MONEY_CELL}>{formatVnd(value)}</span>,
    },
    {
      title: 'Thành tiền',
      dataIndex: 'amount',
      key: 'amount',
      width: 140,
      align: 'right' as const,
      render: (value: number) => <span style={MONEY_CELL}>{formatVnd(value)}</span>,
    },
    ...(canManage && detail && detail.status !== 'PAID'
      ? [
          {
            title: 'Thao tác',
            key: 'actions',
            width: 140,
            render: (_: unknown, line: InvoiceLine) => (
              <Space>
                <Button
                  size="small"
                  onClick={() => {
                    setEditingLine(line)
                    lineForm.setFieldsValue({
                      feeTypeId: line.feeTypeId,
                      quantity: line.quantity,
                      unitPrice: line.unitPrice,
                    })
                    setLineModalOpen(true)
                  }}
                >
                  Sửa
                </Button>
                <Popconfirm
                  title="Xoá dòng này?"
                  okText="Xoá"
                  cancelText="Huỷ"
                  onConfirm={() => deleteLineMutation.mutate(line.id)}
                >
                  <Button size="small" danger loading={deleteLineMutation.isPending}>
                    Xoá
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]
      : []),
  ]

  return (
    <Drawer
      title={detail ? `Hóa đơn ${detail.roomNumber} kỳ ${formatPeriod(detail.period)}` : 'Chi tiết hóa đơn'}
      open={open}
      onClose={onClose}
      width="min(720px, calc(100vw - 48px))"
      extra={
        canManage && detail ? (
          <Space>
            {(detail.status === 'DRAFT') && (
              <Popconfirm
                title="Phát hành hóa đơn này?"
                description="Sau khi phát hành, khách có thể theo dõi và đóng tiền."
                okText="Phát hành"
                cancelText="Huỷ"
                onConfirm={() => publishMutation.mutate()}
              >
                <Button
                  color="green"
                  variant="solid"
                  loading={publishMutation.isPending}
                >
                  Phát hành
                </Button>
              </Popconfirm>
            )}
            {(detail.status === 'UNPAID' || detail.status === 'PARTIAL') && (
              <Button
                onClick={() => {
                  paymentForm.setFieldsValue({ amount: remaining > 0 ? remaining : undefined })
                  setPaymentOpen(true)
                }}
              >
                Ghi nhận đóng tiền
              </Button>
            )}
          </Space>
        ) : null
      }
    >
      {detailQuery.isLoading && (
        <div style={{ marginTop: 48 }}>
          <Skeleton active paragraph={{ rows: 4 }} />
        </div>
      )}
      {detailQuery.isError && (
        <Empty
          description={getErrorMessage(detailQuery.error)}
          style={{ marginTop: 48 }}
        >
          <Button onClick={() => detailQuery.refetch()}>Thử lại</Button>
        </Empty>
      )}
      {detail && (
        <>
          <Descriptions size="small" column={{ xs: 1, sm: 2 }} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Nhà">{detail.houseName}</Descriptions.Item>
            <Descriptions.Item label="Phòng">{detail.roomNumber}</Descriptions.Item>
            <Descriptions.Item label="Tổng tiền">
              <span style={MONEY_CELL}>{formatVnd(detail.totalAmount)}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Đã đóng">
              <span style={MONEY_CELL}>{formatVnd(detail.paidAmount)}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Còn lại">
              <span style={MONEY_CELL}>{formatVnd(remaining)}</span>
            </Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <Tag color={STATUS_META[detail.status].color}>
                {STATUS_META[detail.status].label}
              </Tag>
            </Descriptions.Item>
          </Descriptions>

          <div
            style={{
              marginBottom: 16,
              padding: 12,
              border: '1px solid rgba(5, 5, 5, 0.08)',
              borderRadius: 8,
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 8,
              }}
            >
              <Typography.Text strong>Giá phòng</Typography.Text>
              {canEdit && (
                <Button
                  type="primary"
                  loading={updateRoomPriceMutation.isPending}
                  onClick={() => roomPriceForm.submit()}
                >
                  Lưu
                </Button>
              )}
            </div>
            <Space wrap size="large">
              <Typography.Text>
                Giá đang áp dụng: {roomLineAmount != null ? formatVnd(roomLineAmount) : 'không có dòng tiền phòng'}
              </Typography.Text>
              {detail.contractRent != null && roomLineAmount != null && detail.contractRent !== roomLineAmount && (
                <Typography.Text type={roomLineAmount < detail.contractRent ? 'warning' : 'danger'}>
                  Chênh lệch {formatVnd(roomLineAmount - detail.contractRent)} so với giá hợp đồng
                </Typography.Text>
              )}
            </Space>
            {detail.roomPriceNote && (
              <Typography.Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
                Lý do điều chỉnh: {detail.roomPriceNote}
              </Typography.Text>
            )}
            {canEdit && (
              <Form
                form={roomPriceForm}
                layout="inline"
                onFinish={(values) => updateRoomPriceMutation.mutate(values)}
                style={{ marginTop: 8, rowGap: 12, width: '100%' }}
              >
                <Form.Item
                  label="Giá phòng điều chỉnh (đồng)"
                  name="amount"
                  rules={[{ required: true, message: 'Vui lòng nhập giá phòng' }]}
                >
                  <InputNumber min={0} step={100000} style={{ width: 160 }} />
                </Form.Item>
                <Form.Item
                  label="Lý do điều chỉnh"
                  name="note"
                  rules={[{ max: 500, message: 'Lý do tối đa 500 ký tự' }]}
                  style={{ width: '100%', marginRight: 0 }}
                >
                  <Input.TextArea
                    rows={1}
                    style={{ width: '100%', resize: 'vertical' }}
                    placeholder="Người thuê vắng nhà cả tháng"
                  />
                </Form.Item>
              </Form>
            )}
          </div>

          <div
            style={{
              marginBottom: 16,
              padding: 12,
              border: '1px solid rgba(5, 5, 5, 0.08)',
              borderRadius: 8,
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: 8,
              }}
            >
              <Typography.Text strong>Chỉ số công tơ</Typography.Text>
              {canEdit && (
                <Button
                  type="primary"
                  loading={saveReadingsMutation.isPending}
                  onClick={() => readingsForm.submit()}
                >
                  Lưu
                </Button>
              )}
            </div>
            {canEdit ? (
              <Form
                form={readingsForm}
                layout="inline"
                onFinish={(values) => {
                  if (electErrorText || waterErrorText) return
                  saveReadingsMutation.mutate(values)
                }}
                style={{ marginTop: 8, rowGap: 8 }}
              >
                <Form.Item style={{ width: '100%', marginBottom: 0 }}>
                  <Typography.Text strong>Điện (kWh):</Typography.Text>
                </Form.Item>
                <Form.Item label={fixedLabel('Trước')} name="preElectReading">
                  <InputNumber
                    min={0}
                    status={preMissing(watchedPreElect, watchedCurrentElect) ? 'error' : undefined}
                    style={{ width: 150 }}
                    placeholder="Chưa có = 0"
                  />
                </Form.Item>
                <Form.Item
                  label={fixedLabel('Sau')}
                  name="currentElectReading"
                  extra={extraText(watchedPreElect, watchedCurrentElect, unitElect)}
                >
                  <InputNumber
                    min={0}
                    status={lessThanPre(watchedPreElect, watchedCurrentElect) ? 'error' : undefined}
                    style={{ width: 150 }}
                    placeholder="Nhập chỉ số công tơ"
                  />
                </Form.Item>
                {electErrorText && (
                  <Form.Item style={{ width: '100%', marginBottom: 0 }}>
                    <Typography.Text type="danger">{electErrorText}</Typography.Text>
                  </Form.Item>
                )}
                <Form.Item style={{ width: '100%', marginBottom: 0 }}>
                  <Typography.Text strong>Nước (m³):</Typography.Text>
                </Form.Item>
                <Form.Item label={fixedLabel('Trước')} name="preWaterReading">
                  <InputNumber
                    min={0}
                    status={preMissing(watchedPreWater, watchedCurrentWater) ? 'error' : undefined}
                    style={{ width: 150 }}
                    placeholder="Chưa có = 0"
                  />
                </Form.Item>
                <Form.Item
                  label={fixedLabel('Sau')}
                  name="currentWaterReading"
                  extra={extraText(watchedPreWater, watchedCurrentWater, unitWater)}
                >
                  <InputNumber
                    min={0}
                    status={lessThanPre(watchedPreWater, watchedCurrentWater) ? 'error' : undefined}
                    style={{ width: 150 }}
                    placeholder="Nhập chỉ số công tơ"
                  />
                </Form.Item>
                {waterErrorText && (
                  <Form.Item style={{ width: '100%', marginBottom: 0 }}>
                    <Typography.Text type="danger">{waterErrorText}</Typography.Text>
                  </Form.Item>
                )}
                <Form.Item style={{ width: '100%', marginBottom: 0 }}>
                  <Typography.Text type="secondary">
                    Chỉ số tháng trước lấy từ hóa đơn kỳ trước, chưa có thì mặc định 0.
                  </Typography.Text>
                </Form.Item>
              </Form>
            ) : (
              <Space size="large" wrap>
                <Typography.Text>Điện: {describeReadings(detail.preElectReading, detail.currentElectReading, unitElect)}</Typography.Text>
                <Typography.Text>Nước: {describeReadings(detail.preWaterReading, detail.currentWaterReading, unitWater)}</Typography.Text>
              </Space>
            )}
          </div>

          <Table<InvoiceLine>
            rowKey="id"
            size="small"
            columns={lineColumns}
            dataSource={sortedLines}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  description="Chưa có dòng tiền nào, bấm Thêm dòng tiền để bổ sung khoản thu"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                />
              ),
            }}
            footer={() => (
              <Typography.Text strong>
                Tổng cộng: <span style={MONEY_CELL}>{formatVnd(detail.totalAmount)}</span>
              </Typography.Text>
            )}
          />

          {detail && !canManage && detail.bankAccount && remaining > 0 && (
            <div style={{ marginTop: 24, textAlign: 'center' }}>
              <Typography.Title level={5} style={{ marginBottom: 12 }}>
                Mã chuyển tiền
              </Typography.Title>
              {!qrBroken ? (
                <img
                  src={`https://img.vietqr.io/image/VCB-${detail.bankAccount}-qr_only.png?amount=${remaining}&addInfo=${encodeURIComponent(qrContent)}`}
                  alt="Mã QR chuyển tiền"
                  width={220}
                  height={220}
                  style={{ maxWidth: '100%' }}
                  onError={() => setQrBroken(true)}
                />
              ) : (
                <Typography.Text type="warning">
                  Không tải được mã QR, vui lòng chuyển {formatVnd(remaining)} theo thông tin dưới đây
                </Typography.Text>
              )}
              <div style={{ marginTop: 8 }}>
                <Typography.Text>
                  Số tài khoản {detail.bankAccount}, nội dung:{' '}
                </Typography.Text>
                <Typography.Text code>{qrContent}</Typography.Text>
              </div>
            </div>
          )}
        </>
      )}

      <Modal
        title={editingLine ? 'Sửa dòng tiền' : 'Thêm dòng tiền'}
        open={lineModalOpen}
        onCancel={() => {
          setLineModalOpen(false)
          setEditingLine(null)
        }}
        onOk={() => lineForm.submit()}
        confirmLoading={saveLineMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
      >
        <Form
          form={lineForm}
          layout="vertical"
          onFinish={(values) => saveLineMutation.mutate(values)}
        >
          <Form.Item
            label="Khoản thu"
            name="feeTypeId"
            rules={[{ required: true, message: 'Vui lòng chọn khoản thu' }]}
          >
            <Select
              placeholder="Chọn khoản thu"
              disabled={editingLine != null}
              loading={feeTypesQuery.isLoading}
              options={(feeTypesQuery.data ?? []).map((type) => ({
                value: type.id,
                label: `${type.name} (${type.unit})`,
              }))}
            />
          </Form.Item>
          <Form.Item
            label="Mô tả"
            name="description"
            rules={[{ max: 200, message: 'Mô tả tối đa 200 ký tự' }]}
          >
            <Input placeholder="Để trống sẽ lấy tên khoản thu" />
          </Form.Item>
          <Space size="large" style={{ display: 'flex' }}>
            <Form.Item
              label="Số lượng"
              name="quantity"
              rules={[{ required: true, message: 'Vui lòng nhập số lượng' }]}
            >
              <InputNumber min={0} step={0.5} style={{ width: 160 }} />
            </Form.Item>
            <Form.Item
              label="Đơn giá (đồng)"
              name="unitPrice"
              rules={[{ required: true, message: 'Vui lòng nhập đơn giá' }]}
            >
              <InputNumber min={0} step={1000} style={{ width: 180 }} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal
        title="Ghi nhận đóng tiền"
        open={paymentOpen}
        onCancel={() => setPaymentOpen(false)}
        onOk={() => paymentForm.submit()}
        confirmLoading={payMutation.isPending}
        okText="Ghi nhận"
        cancelText="Huỷ"
        destroyOnHidden
      >
        {detail && (
          <Typography.Paragraph type="secondary">
            Còn lại <span style={MONEY_CELL}>{formatVnd(remaining)}</span> cho hóa đơn{' '}
            {detail.roomNumber} kỳ {formatPeriod(detail.period)}
          </Typography.Paragraph>
        )}
        <Form
          form={paymentForm}
          layout="vertical"
          onFinish={(values) => payMutation.mutate(values.amount)}
        >
          <Form.Item
            label="Số tiền đóng (đồng)"
            name="amount"
            rules={[{ required: true, message: 'Vui lòng nhập số tiền' }]}
          >
            <InputNumber min={1} step={100000} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  )
}
