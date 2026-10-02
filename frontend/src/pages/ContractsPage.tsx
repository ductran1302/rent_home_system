import {
  App as AntApp,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  theme,
  Tooltip,
  Typography,
} from 'antd'
import { EyeInvisibleOutlined, EyeOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useRef, useState, type Key, type ReactNode } from 'react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import ContractPhotoFrame from '../components/ContractPhotoFrame'
import { formatVnd } from '../utils/format'

interface ContractRow {
  id: number
  roomId: number
  houseId: number
  houseName: string
  houseCode: string
  roomNumber: string
  holderId: number
  holderName: string
  holderPhone: string | null
  monthlyRent: number
  startDate: string
  endDate: string
  status: 'ACTIVE' | 'EXPIRED' | 'TERMINATED'
  tenants: { id: number; fullName: string; phone: string | null }[]
  feePrices: Record<string, number>
  note: string | null
}

interface HouseOption {
  id: number
  name: string
  code: string
}

interface PersonOption {
  id: number
  fullName: string
  phone: string | null
}

interface RoomOption {
  id: number
  roomNumber: string
  occupied: boolean
}

interface ContractFormValues {
  houseId: number
  roomId: number
  holderId: number
  monthlyRent: number
  startDate: Dayjs
  endDate: Dayjs
  tenantIds?: number[]
  feePrices?: Record<FeeCode, number | undefined>
  note?: string
}

interface EditFormValues {
  monthlyRent: number
  endDate: Dayjs
  tenantIds?: number[]
  feePrices?: Record<FeeCode, number | undefined>
  note?: string
}

type FeeCode = 'DIEN' | 'NUOC' | 'MANG' | 'DICH_VU'

const FEE_FIELDS: { key: FeeCode; label: string; unit: string; step: number }[] = [
  { key: 'DIEN', label: 'Giá điện', unit: 'đồng/kWh', step: 100 },
  { key: 'NUOC', label: 'Giá nước', unit: 'đồng/m3', step: 1000 },
  { key: 'MANG', label: 'Giá mạng', unit: 'đồng/tháng', step: 10000 },
  { key: 'DICH_VU', label: 'Giá dịch vụ chung', unit: 'đồng/tháng', step: 10000 },
]

function compactFeePrices(feePrices?: Record<string, number | undefined>) {
  const result: Record<string, number> = {}
  Object.entries(feePrices ?? {}).forEach(([code, price]) => {
    if (price != null) {
      result[code] = price
    }
  })
  return result
}

function EllipsisCell({
  title,
  secondary,
  children,
}: {
  title: string
  secondary?: boolean
  children?: ReactNode
}) {
  const { token } = theme.useToken()
  const ref = useRef<HTMLSpanElement>(null)
  const [overflow, setOverflow] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el) return
    const update = () => setOverflow(el.scrollWidth > el.clientWidth + 1)
    update()
    const observer = new ResizeObserver(update)
    observer.observe(el)
    return () => observer.disconnect()
  }, [title])

  return (
    <Tooltip title={overflow ? title : undefined}>
      <span
        ref={ref}
        style={{
          display: 'block',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          ...(secondary ? { fontSize: token.fontSizeSM, color: token.colorTextSecondary } : null),
        }}
      >
        {children ?? title}
      </span>
    </Tooltip>
  )
}

function FeePriceFields() {
  return (
    <Space style={{ display: 'flex' }} size="large" wrap>
      {FEE_FIELDS.map((field) => (
        <Form.Item
          key={field.key}
          label={`${field.label} (${field.unit})`}
          name={['feePrices', field.key]}
          rules={[{ required: true, message: `Vui lòng nhập ${field.label.toLowerCase()}` }]}
        >
          <InputNumber min={0} step={field.step} style={{ width: 220 }} placeholder="Nhập giá" />
        </Form.Item>
      ))}
    </Space>
  )
}

const STATUS_META: Record<ContractRow['status'], { label: string; color?: string }> = {
  ACTIVE: { label: 'Đang hiệu lực', color: 'green' },
  EXPIRED: { label: 'Hết hạn', color: 'orange' },
  TERMINATED: { label: 'Đã thu hồi', color: 'default' },
}

export default function ContractsPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()
  const canManage = me?.role === 'ADMIN' || me?.role === 'MANAGER'

  const [houseFilter, setHouseFilter] = useState<number | null>(null)
  const [statusFilter, setStatusFilter] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [createOpen, setCreateOpen] = useState(false)
  const [editing, setEditing] = useState<ContractRow | null>(null)
  const [createForm] = Form.useForm<ContractFormValues>()
  const [editForm] = Form.useForm<EditFormValues>()
  const [formHouseId, setFormHouseId] = useState<number | null>(null)
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([])
  const [expandAll, setExpandAll] = useState(false)

  const contractsQuery = useQuery({
    queryKey: ['contracts', houseFilter, statusFilter, page],
    queryFn: async () =>
      (
        await api.get<{ items: ContractRow[]; total: number }>('/contracts', {
          params: {
            houseId: houseFilter ?? undefined,
            status: statusFilter ?? undefined,
            page,
            size: 20,
          },
        })
      ).data,
  })

  const rowIds = (contractsQuery.data?.items ?? []).map((row) => row.id)
  const allExpanded = rowIds.length > 0 && rowIds.every((id) => expandedKeys.includes(id))

  useEffect(() => {
    if (expandAll) {
      setExpandedKeys((contractsQuery.data?.items ?? []).map((row) => row.id))
    }
  }, [expandAll, contractsQuery.data])

  const toggleExpandAll = () => {
    const next = !allExpanded
    setExpandAll(next)
    setExpandedKeys(next ? rowIds : [])
  }

  const handleExpandedChange = (keys: readonly Key[]) => {
    setExpandedKeys([...keys])
    if (expandAll && keys.length < rowIds.length) {
      setExpandAll(false)
    }
  }

  const housesQuery = useQuery({
    queryKey: ['houses'],
    enabled: canManage,
    queryFn: async () => (await api.get<HouseOption[]>('/houses')).data,
  })

  const personsQuery = useQuery({
    queryKey: ['persons-options'],
    enabled: canManage,
    queryFn: async () =>
      (await api.get<{ items: PersonOption[] }>('/persons', { params: { size: 100 } })).data.items,
  })

  const roomsQuery = useQuery({
    queryKey: ['rooms', formHouseId],
    enabled: formHouseId != null,
    queryFn: async () => (await api.get<RoomOption[]>(`/rooms/by-house/${formHouseId}`)).data,
  })

  const createMutation = useMutation({
    mutationFn: async (values: ContractFormValues) =>
      api.post('/contracts', {
        roomId: values.roomId,
        holderId: values.holderId,
        monthlyRent: values.monthlyRent,
        startDate: values.startDate.format('YYYY-MM-DD'),
        endDate: values.endDate.format('YYYY-MM-DD'),
        tenantIds: values.tenantIds ?? [],
        feePrices: compactFeePrices(values.feePrices),
        note: values.note?.trim() || null,
      }),
    onSuccess: () => {
      message.success('Đã ký hợp đồng')
      setCreateOpen(false)
      createForm.resetFields()
      setFormHouseId(null)
      contractsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const updateMutation = useMutation({
    mutationFn: async ({ id, values }: { id: number; values: EditFormValues }) =>
      api.put(`/contracts/${id}`, {
        monthlyRent: values.monthlyRent,
        endDate: values.endDate.format('YYYY-MM-DD'),
        tenantIds: values.tenantIds ?? [],
        feePrices: compactFeePrices(values.feePrices),
        note: values.note?.trim() || null,
      }),
    onSuccess: () => {
      message.success('Đã cập nhật hợp đồng')
      setCreateOpen(false)
      setEditing(null)
      editForm.resetFields()
      contractsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const terminateMutation = useMutation({
    mutationFn: async (id: number) => api.post(`/contracts/${id}/terminate`),
    onSuccess: () => {
      message.success('Đã thu hồi hợp đồng')
      contractsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const personOptions = (personsQuery.data ?? []).map((person) => ({
    value: person.id,
    label: person.phone ? `${person.fullName} (${person.phone})` : person.fullName,
  }))

  const roomOptions = (roomsQuery.data ?? [])
    .filter((room) => (editing ? true : !room.occupied))
    .map((room) => ({
      value: room.id,
      label: editing && room.id === editing.roomId ? `${room.roomNumber} (phòng hiện tại)` : room.roomNumber,
      disabled: !editing && room.occupied,
    }))

  const openCreate = () => {
    setEditing(null)
    createForm.resetFields()
    createForm.setFieldsValue({
      startDate: dayjs(),
      endDate: dayjs().add(1, 'year'),
    })
    setFormHouseId(null)
    setCreateOpen(true)
  }

  const openEdit = (row: ContractRow) => {
    setEditing(row)
    editForm.setFieldsValue({
      monthlyRent: row.monthlyRent,
      endDate: dayjs(row.endDate),
      tenantIds: row.tenants.map((tenant) => tenant.id),
      feePrices: { ...row.feePrices },
      note: row.note ?? undefined,
    })
    setCreateOpen(true)
  }

  const columns = [
    {
      title: 'Phòng',
      key: 'room',
      width: 180,
      render: (_: unknown, row: ContractRow) => (
        <>
          <EllipsisCell title={row.roomNumber} />
          <EllipsisCell title={row.houseName} secondary />
        </>
      ),
    },
    {
      title: 'Mã nhà',
      dataIndex: 'houseCode',
      key: 'houseCode',
      width: 110,
      render: (value: string) => (
        <EllipsisCell title={value}>
          <Typography.Text code>{value}</Typography.Text>
        </EllipsisCell>
      ),
    },
    {
      title: 'Người thuê chính',
      key: 'holder',
      width: 200,
      render: (_: unknown, row: ContractRow) => (
        <>
          <EllipsisCell title={row.holderName} />
          {row.holderPhone && <EllipsisCell title={row.holderPhone} secondary />}
        </>
      ),
    },
    {
      title: 'Giá phòng / tháng',
      dataIndex: 'monthlyRent',
      key: 'monthlyRent',
      width: 150,
      align: 'right' as const,
      render: (value: number) => <EllipsisCell title={formatVnd(value)} />,
    },
    {
      title: 'Kỳ thuê',
      key: 'period',
      width: 210,
      render: (_: unknown, row: ContractRow) => (
        <EllipsisCell
          title={`${dayjs(row.startDate).format('DD/MM/YYYY')} - ${dayjs(row.endDate).format('DD/MM/YYYY')}`}
        />
      ),
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      key: 'note',
      width: 240,
      render: (value: string | null) =>
        value ? (
          <EllipsisCell title={value} />
        ) : (
          <Typography.Text type="secondary">Không có</Typography.Text>
        ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      fixed: 'right' as const,
      render: (value: ContractRow['status']) => {
        const meta = STATUS_META[value]
        return <Tag color={meta.color}>{meta.label}</Tag>
      },
    },
    ...(canManage
      ? [
          {
            title: 'Thao tác',
            key: 'actions',
            width: 200,
            fixed: 'right' as const,
            render: (_: unknown, row: ContractRow) => (
              <Space>
                {row.status === 'ACTIVE' && (
                  <>
                    <Button size="small" onClick={() => openEdit(row)}>
                      Sửa
                    </Button>
                    <Popconfirm
                      title="Thu hồi hợp đồng này?"
                      description="Phòng sẽ chuyển sang trạng thái trống."
                      okText="Thu hồi"
                      cancelText="Huỷ"
                      onConfirm={() => terminateMutation.mutate(row.id)}
                    >
                      <Button size="small" danger loading={terminateMutation.isPending}>
                        Thu hồi
                      </Button>
                    </Popconfirm>
                  </>
                )}
              </Space>
            ),
          },
        ]
      : []),
  ]

  const isCreateMode = editing == null

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Hợp đồng
        </Typography.Title>
        <Space wrap>
          {canManage && (
            <Select
              allowClear
              placeholder="Lọc theo nhà"
              style={{ width: 200 }}
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
            style={{ width: 160 }}
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
            <Button type="primary" onClick={openCreate}>
              Ký hợp đồng
            </Button>
          )}
        </Space>
      </Space>

      {contractsQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách hợp đồng: ${getErrorMessage(contractsQuery.error)}`}
        >
          <Button onClick={() => contractsQuery.refetch()}>Thử lại</Button>
        </Empty>
      )}

      <Table<ContractRow>
        rowKey="id"
        loading={contractsQuery.isLoading}
        columns={columns}
        dataSource={contractsQuery.data?.items}
        locale={{
          emptyText: <Empty description="Chưa có hợp đồng nào" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
        }}
        expandable={{
          columnWidth: 56,
          fixed: 'left',
          columnTitle: (
            <Tooltip title={allExpanded ? 'Thu gọn tất cả' : 'Mở tất cả khoản phí'}>
              <Button
                type="text"
                size="small"
                aria-label={allExpanded ? 'Thu gọn tất cả khoản phí' : 'Mở tất cả khoản phí'}
                icon={allExpanded ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                onClick={toggleExpandAll}
                disabled={rowIds.length === 0}
              />
            </Tooltip>
          ),
          expandedRowKeys: expandedKeys,
          onExpandedRowsChange: handleExpandedChange,
          expandedRowRender: (row) => {
            const entries = FEE_FIELDS.filter((field) => row.feePrices[field.key] != null)
            if (entries.length === 0) {
              return (
                <Typography.Text type="secondary">
                  Hợp đồng không đặt giá riêng, dùng giá chung theo kỳ hóa đơn.
                </Typography.Text>
              )
            }
            return (
              <Space wrap>
                {entries.map((field) => (
                  <Tag key={field.key}>
                    {field.label}: {formatVnd(row.feePrices[field.key])}/{field.unit.replace('đồng/', '')}
                  </Tag>
                ))}
              </Space>
            )
          },
        }}
        pagination={{
          current: page + 1,
          pageSize: 20,
          total: contractsQuery.data?.total ?? 0,
          showTotal: (total) => `Tổng ${total} hợp đồng`,
          onChange: (nextPage) => setPage(nextPage - 1),
        }}
      />

      <Modal
        title={editing ? 'Sửa hợp đồng' : 'Ký hợp đồng'}
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)
          setEditing(null)
        }}
        onOk={() => (editing ? editForm.submit() : createForm.submit())}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
        width={560}
      >
        {isCreateMode ? (
          <Form
            form={createForm}
            layout="vertical"
            onFinish={(values) => createMutation.mutate(values)}
          >
            <Form.Item
              label="Nhà"
              name="houseId"
              rules={[{ required: true, message: 'Vui lòng chọn nhà' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="Chọn nhà"
                loading={housesQuery.isLoading}
                options={(housesQuery.data ?? []).map((house) => ({
                  value: house.id,
                  label: `${house.name} (${house.code})`,
                }))}
                onChange={(value: number) => {
                  setFormHouseId(value)
                  createForm.setFieldValue('roomId', undefined)
                }}
              />
            </Form.Item>
            <Form.Item
              label="Phòng trống"
              name="roomId"
              rules={[{ required: true, message: 'Vui lòng chọn phòng' }]}
            >
              <Select
                placeholder={formHouseId ? 'Chọn phòng' : 'Hãy chọn nhà trước'}
                disabled={formHouseId == null}
                loading={roomsQuery.isLoading}
                options={roomOptions}
                notFoundContent={roomsQuery.isLoading ? 'Đang tải...' : 'Nhà này không còn phòng trống'}
              />
            </Form.Item>
            <Form.Item
              label="Người thuê chính"
              name="holderId"
              rules={[{ required: true, message: 'Vui lòng chọn người thuê chính' }]}
            >
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="Chọn người"
                loading={personsQuery.isLoading}
                options={personOptions}
                notFoundContent={personsQuery.isLoading ? 'Đang tải...' : 'Chưa có người, hãy thêm ở trang Người'}
              />
            </Form.Item>
            <Form.Item
              label="Giá phòng mỗi tháng (đồng)"
              name="monthlyRent"
              rules={[{ required: true, message: 'Vui lòng nhập giá phòng' }]}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                step={100000}
                placeholder="3500000"
              />
            </Form.Item>
            <Space style={{ display: 'flex' }} size="large">
              <Form.Item
                label="Ngày bắt đầu"
                name="startDate"
                rules={[{ required: true, message: 'Vui lòng chọn ngày bắt đầu' }]}
              >
                <DatePicker format="DD/MM/YYYY" style={{ width: 180 }} />
              </Form.Item>
              <Form.Item
                label="Ngày kết thúc"
                name="endDate"
                rules={[
                  { required: true, message: 'Vui lòng chọn ngày kết thúc' },
                  {
                    validator: (_, value: Dayjs | undefined) => {
                      const start = createForm.getFieldValue('startDate')
                      if (value && start && !value.isAfter(start)) {
                        return Promise.reject(new Error('Ngày kết thúc phải sau ngày bắt đầu'))
                      }
                      return Promise.resolve()
                    },
                  },
                ]}
              >
                <DatePicker format="DD/MM/YYYY" style={{ width: 180 }} />
              </Form.Item>
            </Space>
            <Form.Item label="Người cùng thuê (không bắt buộc)" name="tenantIds">
              <Select
                mode="multiple"
                showSearch
                optionFilterProp="label"
                placeholder="Chọn nhiều người"
                loading={personsQuery.isLoading}
                options={personOptions}
              />
            </Form.Item>
            <Form.Item
              label="Ghi chú (không bắt buộc)"
              name="note"
              rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}
            >
              <Input.TextArea
                rows={3}
                placeholder="VD: Ký ngày 30/9, dọn đến ở ngày 15/10, đã cọc 1.000.000 đ"
              />
            </Form.Item>
            <FeePriceFields />
          </Form>
        ) : (
          <Form
            form={editForm}
            layout="vertical"
            onFinish={(values) => updateMutation.mutate({ id: editing.id, values })}
          >
            <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
              {editing.roomNumber} ({editing.houseName}), người thuê chính: {editing.holderName}
            </Typography.Paragraph>
            <Form.Item
              label="Giá phòng mỗi tháng (đồng)"
              name="monthlyRent"
              rules={[{ required: true, message: 'Vui lòng nhập giá phòng' }]}
            >
              <InputNumber
                style={{ width: '100%' }}
                min={0}
                step={100000}
              />
            </Form.Item>
            <Form.Item
              label="Ngày kết thúc"
              name="endDate"
              rules={[
                { required: true, message: 'Vui lòng chọn ngày kết thúc' },
                {
                  validator: (_, value: Dayjs | undefined) => {
                    if (value && !value.isAfter(dayjs(editing.startDate))) {
                      return Promise.reject(new Error('Ngày kết thúc phải sau ngày bắt đầu'))
                    }
                    return Promise.resolve()
                  },
                },
              ]}
            >
              <DatePicker format="DD/MM/YYYY" style={{ width: 180 }} />
            </Form.Item>
            <Form.Item label="Người cùng thuê" name="tenantIds">
              <Select
                mode="multiple"
                showSearch
                optionFilterProp="label"
                placeholder="Chọn nhiều người"
                loading={personsQuery.isLoading}
                options={personOptions}
              />
            </Form.Item>
            <FeePriceFields />
            <Form.Item
              label="Ghi chú (không bắt buộc)"
              name="note"
              rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}
            >
              <Input.TextArea
                rows={3}
                placeholder="VD: Ký ngày 30/9, dọn đến ở ngày 15/10, đã cọc 1.000.000 đ"
              />
            </Form.Item>
            <Form.Item label="Ảnh hợp đồng">
              <ContractPhotoFrame />
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  )
}
