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
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { useEffect, useState } from 'react'
import { api, getErrorMessage } from '../api/client'
import PhotoUpload, { PhotoThumb } from '../components/PhotoUpload'
import { formatDate, formatVnd } from '../utils/format'
import {
  CATEGORY_LABELS,
  CONDITION_META,
  REPAIR_STATUS_META,
  type AssetCategory,
  type AssetCondition,
  type AssetPhoto,
  type AssetPhotoStage,
  type RepairStatus,
} from '../utils/asset'

const PAGE_SIZE = 20

interface AssetRow {
  id: number
  roomId: number
  houseId: number
  houseName: string
  roomNumber: string
  code: string
  name: string
  category: AssetCategory
  price: number
  purchaseDate: string | null
  condition: AssetCondition
  note: string | null
  active: boolean
  repairCount: number
  repairCost: number
  photoUrl: string | null
  photoCount: number
  createdAt: string | null
  updatedAt: string | null
}

interface RepairRow {
  id: number
  assetId: number
  assetCode: string
  assetName: string
  roomId: number
  houseId: number
  houseName: string
  roomNumber: string
  reportedAt: string
  description: string
  cost: number
  status: RepairStatus
  doneAt: string | null
  note: string | null
  photoBeforeUrl: string | null
  photoAfterUrl: string | null
  createdAt: string | null
  updatedAt: string | null
}

interface HouseOption {
  id: number
  name: string
  code: string
}

interface RoomOption {
  id: number
  roomNumber: string
}

interface ListResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

interface AssetFormValues {
  houseId: number
  roomId?: number
  code: string
  name: string
  category: AssetCategory
  price: number
  purchaseDate?: Dayjs
  condition: AssetCondition
  note?: string
}

interface RepairFormValues {
  assetId?: number
  reportedAt: Dayjs
  description: string
  cost: number
  status: RepairStatus
  doneAt?: Dayjs
  note?: string
}

const MONEY_CELL = { fontVariantNumeric: 'tabular-nums' } as const
const CODE_CELL = { fontFamily: 'monospace' } as const

export default function AssetsPage() {
  const { message } = AntApp.useApp()

  const [tab, setTab] = useState('assets')
  const [houseFilter, setHouseFilter] = useState<number | null>(null)
  const [roomFilter, setRoomFilter] = useState<number | null>(null)
  const [conditionFilter, setConditionFilter] = useState<AssetCondition | null>(null)
  const [categoryFilter, setCategoryFilter] = useState<AssetCategory | null>(null)
  const [statusFilter, setStatusFilter] = useState<RepairStatus | null>(null)
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)

  const [assetModalOpen, setAssetModalOpen] = useState(false)
  const [editingAsset, setEditingAsset] = useState<AssetRow | null>(null)
  const [assetForm] = Form.useForm<AssetFormValues>()

  const [repairModalOpen, setRepairModalOpen] = useState(false)
  const [editingRepair, setEditingRepair] = useState<RepairRow | null>(null)
  const [repairForm] = Form.useForm<RepairFormValues>()

  const formHouseId = Form.useWatch('houseId', assetForm)
  const repairStatus = Form.useWatch('status', repairForm)

  useEffect(() => {
    setPage(0)
  }, [tab, houseFilter, roomFilter, conditionFilter, categoryFilter, statusFilter, query])

  const housesQuery = useQuery({
    queryKey: ['houses'],
    queryFn: async () => (await api.get<HouseOption[]>('/houses')).data,
  })

  const roomsQuery = useQuery({
    enabled: houseFilter != null,
    queryKey: ['rooms', houseFilter],
    queryFn: async () => (await api.get<RoomOption[]>(`/rooms/by-house/${houseFilter}`)).data,
  })

  const formRoomsQuery = useQuery({
    enabled: assetModalOpen && formHouseId != null,
    queryKey: ['rooms', formHouseId],
    queryFn: async () => (await api.get<RoomOption[]>(`/rooms/by-house/${formHouseId}`)).data,
  })

  const assetsQuery = useQuery({
    queryKey: ['assets', houseFilter, roomFilter, conditionFilter, categoryFilter, query, page],
    queryFn: async () =>
      (
        await api.get<ListResponse<AssetRow>>('/assets', {
          params: {
            houseId: houseFilter ?? undefined,
            roomId: roomFilter ?? undefined,
            condition: conditionFilter ?? undefined,
            category: categoryFilter ?? undefined,
            q: query || undefined,
            page,
            size: PAGE_SIZE,
          },
        })
      ).data,
  })

  const repairsQuery = useQuery({
    queryKey: ['repairs', houseFilter, roomFilter, statusFilter, query, page],
    queryFn: async () =>
      (
        await api.get<ListResponse<RepairRow>>('/assets/repairs', {
          params: {
            houseId: houseFilter ?? undefined,
            roomId: roomFilter ?? undefined,
            status: statusFilter ?? undefined,
            q: query || undefined,
            page,
            size: PAGE_SIZE,
          },
        })
      ).data,
  })

  const repairAssetOptionsQuery = useQuery({
    enabled: repairModalOpen,
    queryKey: ['assets-options', houseFilter, roomFilter],
    queryFn: async () =>
      (
        await api.get<ListResponse<AssetRow>>('/assets', {
          params: {
            houseId: houseFilter ?? undefined,
            roomId: roomFilter ?? undefined,
            page: 0,
            size: 100,
          },
        })
      ).data.items,
  })

  const assetPhotosQuery = useQuery({
    enabled: assetModalOpen && editingAsset != null,
    queryKey: ['asset-photos', editingAsset?.id],
    queryFn: async () => (await api.get<AssetPhoto[]>(`/assets/${editingAsset?.id}/photos`)).data,
  })

  const repairPhotosQuery = useQuery({
    enabled: repairModalOpen && editingRepair != null,
    queryKey: ['repair-photos', editingRepair?.assetId, editingRepair?.id],
    queryFn: async () =>
      (
        await api.get<AssetPhoto[]>(
          `/assets/${editingRepair?.assetId}/repairs/${editingRepair?.id}/photos`
        )
      ).data,
  })

  const saveAssetMutation = useMutation({
    mutationFn: async (values: AssetFormValues) => {
      const payload = {
        roomId: values.roomId,
        code: values.code.trim(),
        name: values.name.trim(),
        category: values.category,
        price: values.price,
        purchaseDate: values.purchaseDate ? values.purchaseDate.format('YYYY-MM-DD') : null,
        condition: values.condition,
        note: values.note?.trim() || null,
      }
      if (editingAsset) {
        return api.put<AssetRow>(`/assets/${editingAsset.id}`, payload)
      }
      return api.post<AssetRow>('/assets', payload)
    },
    onSuccess: () => {
      message.success(editingAsset ? 'Đã cập nhật tài sản' : 'Đã thêm tài sản')
      setAssetModalOpen(false)
      setEditingAsset(null)
      assetForm.resetFields()
      assetsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteAssetMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/assets/${id}`),
    onSuccess: () => {
      message.success('Đã xoá tài sản')
      assetsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const saveRepairMutation = useMutation({
    mutationFn: async (values: RepairFormValues) => {
      const payload = {
        reportedAt: values.reportedAt.format('YYYY-MM-DD'),
        description: values.description.trim(),
        cost: values.cost,
        status: values.status,
        doneAt:
          values.status === 'DONE' && values.doneAt
            ? values.doneAt.format('YYYY-MM-DD')
            : undefined,
        note: values.note?.trim() || null,
      }
      if (editingRepair) {
        return api.put<RepairRow>(`/assets/${editingRepair.assetId}/repairs/${editingRepair.id}`, payload)
      }
      return api.post<RepairRow>(`/assets/${values.assetId}/repairs`, payload)
    },
    onSuccess: () => {
      message.success(editingRepair ? 'Đã cập nhật lần sửa' : 'Đã thêm lần sửa')
      setRepairModalOpen(false)
      setEditingRepair(null)
      repairForm.resetFields()
      repairsQuery.refetch()
      assetsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteRepairMutation = useMutation({
    mutationFn: async (row: RepairRow) =>
      api.delete(`/assets/${row.assetId}/repairs/${row.id}`),
    onSuccess: () => {
      message.success('Đã xoá lần sửa')
      repairsQuery.refetch()
      assetsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const uploadAssetPhotoMutation = useMutation({
    mutationFn: async ({ assetId, file }: { assetId: number; file: File }) => {
      const body = new FormData()
      body.append('file', file)
      return api.post(`/assets/${assetId}/photos`, body)
    },
    onSuccess: () => {
      message.success('Đã thêm ảnh tài sản')
      assetPhotosQuery.refetch()
      assetsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteAssetPhotoMutation = useMutation({
    mutationFn: async ({ assetId, photoId }: { assetId: number; photoId: number }) =>
      api.delete(`/assets/${assetId}/photos/${photoId}`),
    onSuccess: () => {
      message.success('Đã xoá ảnh')
      assetPhotosQuery.refetch()
      assetsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const uploadRepairPhotoMutation = useMutation({
    mutationFn: async ({
      assetId,
      repairId,
      stage,
      file,
    }: {
      assetId: number
      repairId: number
      stage: AssetPhotoStage
      file: File
    }) => {
      const body = new FormData()
      body.append('file', file)
      return api.post(`/assets/${assetId}/repairs/${repairId}/photos`, body, {
        params: { stage },
      })
    },
    onSuccess: () => {
      message.success('Đã thêm ảnh lần sửa')
      repairPhotosQuery.refetch()
      repairsQuery.refetch()
      assetsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteRepairPhotoMutation = useMutation({
    mutationFn: async ({
      assetId,
      repairId,
      photoId,
    }: {
      assetId: number
      repairId: number
      photoId: number
    }) => api.delete(`/assets/${assetId}/repairs/${repairId}/photos/${photoId}`),
    onSuccess: () => {
      message.success('Đã xoá ảnh')
      repairPhotosQuery.refetch()
      repairsQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const openCreateAsset = () => {
    setEditingAsset(null)
    assetForm.resetFields()
    assetForm.setFieldsValue({ category: 'KHAC', condition: 'GOOD' })
    setAssetModalOpen(true)
  }

  const openEditAsset = (row: AssetRow) => {
    setEditingAsset(row)
    assetForm.setFieldsValue({
      houseId: row.houseId,
      roomId: row.roomId,
      code: row.code,
      name: row.name,
      category: row.category,
      price: row.price,
      purchaseDate: row.purchaseDate ? dayjs(row.purchaseDate) : undefined,
      condition: row.condition,
      note: row.note ?? undefined,
    })
    setAssetModalOpen(true)
  }

  const openCreateRepair = (assetId?: number) => {
    setEditingRepair(null)
    repairForm.resetFields()
    repairForm.setFieldsValue({
      assetId,
      reportedAt: dayjs(),
      status: 'PENDING',
    })
    setRepairModalOpen(true)
  }

  const openEditRepair = (row: RepairRow) => {
    setEditingRepair(row)
    repairForm.setFieldsValue({
      assetId: row.assetId,
      reportedAt: dayjs(row.reportedAt),
      description: row.description,
      cost: row.cost,
      status: row.status,
      doneAt: row.doneAt ? dayjs(row.doneAt) : undefined,
      note: row.note ?? undefined,
    })
    setRepairModalOpen(true)
  }

  const repairPhotos = repairPhotosQuery.data ?? []
  const repairPhotosBefore = repairPhotos.filter((photo) => photo.stage === 'TRUOC')
  const repairPhotosAfter = repairPhotos.filter((photo) => photo.stage === 'SAU')

  const assetColumns = [
    {
      title: 'Mã',
      dataIndex: 'code',
      key: 'code',
      width: 130,
      render: (value: string) => <span style={CODE_CELL}>{value}</span>,
    },
    {
      title: 'Tên tài sản',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: 'Nhà',
      dataIndex: 'houseName',
      key: 'houseName',
      width: 160,
      ellipsis: true,
    },
    {
      title: 'Phòng',
      dataIndex: 'roomNumber',
      key: 'roomNumber',
      width: 90,
      render: (value: string) => <span style={CODE_CELL}>{value}</span>,
    },
    {
      title: 'Nhóm',
      dataIndex: 'category',
      key: 'category',
      width: 140,
      render: (value: AssetCategory) => CATEGORY_LABELS[value],
    },
    {
      title: 'Tình trạng',
      dataIndex: 'condition',
      key: 'condition',
      width: 110,
      render: (value: AssetCondition) => (
        <Tag color={CONDITION_META[value].color}>{CONDITION_META[value].label}</Tag>
      ),
    },
    {
      title: 'Giá mua',
      dataIndex: 'price',
      key: 'price',
      width: 130,
      align: 'right' as const,
      render: (value: number) => <span style={MONEY_CELL}>{formatVnd(value)}</span>,
    },
    {
      title: 'Sửa chữa',
      key: 'repairs',
      width: 170,
      align: 'right' as const,
      render: (_: unknown, row: AssetRow) =>
        row.repairCount > 0 ? (
          <span style={MONEY_CELL}>
            {row.repairCount} lần, {formatVnd(row.repairCost)}
          </span>
        ) : (
          <Typography.Text type="secondary">Chưa sửa</Typography.Text>
        ),
    },
    {
      title: 'Ảnh',
      key: 'photos',
      width: 100,
      render: (_: unknown, row: AssetRow) =>
        row.photoUrl ? (
          <PhotoThumb url={row.photoUrl} alt={`Ảnh tài sản ${row.code}`} size={44} />
        ) : (
          <Typography.Text type="secondary">Chưa có ảnh</Typography.Text>
        ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 210,
      render: (_: unknown, row: AssetRow) => (
        <Space>
          <Button size="small" onClick={() => openEditAsset(row)}>
            Sửa
          </Button>
          <Button size="small" onClick={() => openCreateRepair(row.id)}>
            Sửa chữa
          </Button>
          <Popconfirm
            title="Xoá tài sản này?"
            description="Dữ liệu chỉ bị ẩn, vẫn giữ cho hợp đồng và báo cáo cũ."
            okText="Xoá"
            cancelText="Huỷ"
            onConfirm={() => deleteAssetMutation.mutate(row.id)}
          >
            <Button size="small" danger loading={deleteAssetMutation.isPending}>
              Xoá
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const repairColumns = [
    {
      title: 'Ngày báo',
      dataIndex: 'reportedAt',
      key: 'reportedAt',
      width: 110,
      render: (value: string) => formatDate(value),
    },
    {
      title: 'Mã tài sản',
      dataIndex: 'assetCode',
      key: 'assetCode',
      width: 130,
      render: (value: string) => <span style={CODE_CELL}>{value}</span>,
    },
    {
      title: 'Tên tài sản',
      dataIndex: 'assetName',
      key: 'assetName',
      width: 180,
      ellipsis: true,
    },
    {
      title: 'Phòng',
      dataIndex: 'roomNumber',
      key: 'roomNumber',
      width: 90,
      render: (value: string) => <span style={CODE_CELL}>{value}</span>,
    },
    {
      title: 'Mô tả',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: 'Chi phí',
      dataIndex: 'cost',
      key: 'cost',
      width: 130,
      align: 'right' as const,
      render: (value: number) => <span style={MONEY_CELL}>{formatVnd(value)}</span>,
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (value: RepairStatus) => (
        <Tag color={REPAIR_STATUS_META[value].color}>{REPAIR_STATUS_META[value].label}</Tag>
      ),
    },
    {
      title: 'Hoàn thành',
      dataIndex: 'doneAt',
      key: 'doneAt',
      width: 115,
      render: (value: string | null) => formatDate(value) || '-',
    },
    {
      title: 'Ảnh trước và sau',
      key: 'photos',
      width: 130,
      render: (_: unknown, row: RepairRow) =>
        row.photoBeforeUrl || row.photoAfterUrl ? (
          <Space size={4}>
            {row.photoBeforeUrl && (
              <PhotoThumb
                url={row.photoBeforeUrl}
                alt={`Ảnh trước khi sửa của ${row.assetCode}`}
                size={44}
              />
            )}
            {row.photoAfterUrl && (
              <PhotoThumb
                url={row.photoAfterUrl}
                alt={`Ảnh sau khi sửa của ${row.assetCode}`}
                size={44}
              />
            )}
          </Space>
        ) : (
          <Typography.Text type="secondary">Chưa có ảnh</Typography.Text>
        ),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 150,
      render: (_: unknown, row: RepairRow) => (
        <Space>
          <Button size="small" onClick={() => openEditRepair(row)}>
            Sửa
          </Button>
          <Popconfirm
            title="Xoá lần sửa này?"
            description="Chi phí này sẽ không còn trong báo cáo."
            okText="Xoá"
            cancelText="Huỷ"
            onConfirm={() => deleteRepairMutation.mutate(row)}
          >
            <Button size="small" danger loading={deleteRepairMutation.isPending}>
              Xoá
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const assetTable = assetsQuery.isError ? (
    <Empty
      style={{ margin: '48px 0' }}
      description={`Không tải được danh sách tài sản: ${getErrorMessage(assetsQuery.error)}`}
    >
      <Button onClick={() => assetsQuery.refetch()}>Thử lại</Button>
    </Empty>
  ) : (
    <Table<AssetRow>
      rowKey="id"
      loading={assetsQuery.isLoading}
      columns={assetColumns}
      dataSource={assetsQuery.data?.items}
      scroll={{ x: 1400 }}
      locale={{
        emptyText: (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Chưa có tài sản nào, bấm Thêm tài sản để nhập tài sản đầu tiên"
          />
        ),
      }}
      pagination={{
        current: page + 1,
        pageSize: PAGE_SIZE,
        total: assetsQuery.data?.total ?? 0,
        showTotal: (total) => `Tổng ${total} tài sản`,
        onChange: (nextPage) => setPage(nextPage - 1),
      }}
    />
  )

  const repairTable = repairsQuery.isError ? (
    <Empty
      style={{ margin: '48px 0' }}
      description={`Không tải được lịch sử sửa chữa: ${getErrorMessage(repairsQuery.error)}`}
    >
      <Button onClick={() => repairsQuery.refetch()}>Thử lại</Button>
    </Empty>
  ) : (
    <Table<RepairRow>
      rowKey="id"
      loading={repairsQuery.isLoading}
      columns={repairColumns}
      dataSource={repairsQuery.data?.items}
      scroll={{ x: 1450 }}
      locale={{
        emptyText: (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Chưa có lần sửa nào, chọn Thêm lần sửa để ghi nhận"
          />
        ),
      }}
      pagination={{
        current: page + 1,
        pageSize: PAGE_SIZE,
        total: repairsQuery.data?.total ?? 0,
        showTotal: (total) => `Tổng ${total} lần sửa`,
        onChange: (nextPage) => setPage(nextPage - 1),
      }}
    />
  )

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Tài sản
        </Typography.Title>
        <Space wrap>
          <Select
            allowClear
            placeholder="Lọc theo nhà"
            style={{ width: 200 }}
            value={houseFilter}
            onChange={(value) => {
              setHouseFilter(value ?? null)
              setRoomFilter(null)
            }}
            options={(housesQuery.data ?? []).map((house) => ({
              value: house.id,
              label: `${house.name} (${house.code})`,
            }))}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder={houseFilter ? 'Lọc theo phòng' : 'Chọn nhà trước'}
            style={{ width: 140 }}
            value={roomFilter}
            disabled={houseFilter == null}
            onChange={(value) => setRoomFilter(value ?? null)}
            options={(roomsQuery.data ?? []).map((room) => ({
              value: room.id,
              label: room.roomNumber,
            }))}
          />
          {tab === 'assets' ? (
            <>
              <Select
                allowClear
                placeholder="Tình trạng"
                style={{ width: 140 }}
                value={conditionFilter}
                onChange={(value) => setConditionFilter(value ?? null)}
                options={Object.entries(CONDITION_META).map(([key, meta]) => ({
                  value: key,
                  label: meta.label,
                }))}
              />
              <Select
                allowClear
                placeholder="Nhóm tài sản"
                style={{ width: 160 }}
                value={categoryFilter}
                onChange={(value) => setCategoryFilter(value ?? null)}
                options={Object.entries(CATEGORY_LABELS).map(([key, label]) => ({
                  value: key,
                  label,
                }))}
              />
            </>
          ) : (
            <Select
              allowClear
              placeholder="Trạng thái"
              style={{ width: 150 }}
              value={statusFilter}
              onChange={(value) => setStatusFilter(value ?? null)}
              options={Object.entries(REPAIR_STATUS_META).map(([key, meta]) => ({
                value: key,
                label: meta.label,
              }))}
            />
          )}
          <Input.Search
            allowClear
            placeholder={tab === 'assets' ? 'Tìm theo mã, tên tài sản' : 'Tìm theo mã, tên, mô tả'}
            style={{ width: 260 }}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            onSearch={(value) => setQuery(value.trim())}
          />
          <Button type="primary" onClick={() => (tab === 'assets' ? openCreateAsset() : openCreateRepair())}>
            {tab === 'assets' ? 'Thêm tài sản' : 'Thêm lần sửa'}
          </Button>
        </Space>
      </Space>

      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key)}
        items={[
          { key: 'assets', label: 'Danh mục', children: assetTable },
          { key: 'repairs', label: 'Lịch sử sửa chữa', children: repairTable },
        ]}
      />

      <Modal
        title={editingAsset ? 'Sửa tài sản' : 'Thêm tài sản'}
        open={assetModalOpen}
        onCancel={() => {
          setAssetModalOpen(false)
          setEditingAsset(null)
        }}
        onOk={() => assetForm.submit()}
        confirmLoading={saveAssetMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
        styles={{ body: { maxHeight: '68vh', overflowY: 'auto' } }}
      >
        <Form
          form={assetForm}
          layout="vertical"
          onFinish={(values) => saveAssetMutation.mutate(values)}
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
              options={(housesQuery.data ?? []).map((house) => ({
                value: house.id,
                label: `${house.name} (${house.code})`,
              }))}
              onChange={() => assetForm.setFieldsValue({ roomId: undefined })}
            />
          </Form.Item>
          <Form.Item
            label="Phòng"
            name="roomId"
            rules={[{ required: true, message: 'Vui lòng chọn phòng' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder={formHouseId ? 'Chọn phòng' : 'Hãy chọn nhà trước'}
              disabled={formHouseId == null}
              options={(formRoomsQuery.data ?? []).map((room) => ({
                value: room.id,
                label: room.roomNumber,
              }))}
            />
          </Form.Item>
          <Form.Item
            label="Mã tài sản"
            name="code"
            rules={[
              { required: true, message: 'Vui lòng nhập mã tài sản' },
              { max: 50, message: 'Mã tài sản tối đa 50 ký tự' },
            ]}
          >
            <Input placeholder="TS-A101-01" autoFocus />
          </Form.Item>
          <Form.Item
            label="Tên tài sản"
            name="name"
            rules={[
              { required: true, message: 'Vui lòng nhập tên tài sản' },
              { max: 200, message: 'Tên tài sản tối đa 200 ký tự' },
            ]}
          >
            <Input placeholder="Giường đôi 1m6" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              label="Nhóm tài sản"
              name="category"
              rules={[{ required: true, message: 'Vui lòng chọn nhóm tài sản' }]}
              style={{ width: 200 }}
            >
              <Select
                placeholder="Chọn nhóm"
                options={Object.entries(CATEGORY_LABELS).map(([key, label]) => ({
                  value: key,
                  label,
                }))}
              />
            </Form.Item>
            <Form.Item
              label="Tình trạng"
              name="condition"
              rules={[{ required: true, message: 'Vui lòng chọn tình trạng' }]}
              style={{ width: 180 }}
            >
              <Select
                placeholder="Chọn tình trạng"
                options={Object.entries(CONDITION_META).map(([key, meta]) => ({
                  value: key,
                  label: meta.label,
                }))}
              />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              label="Giá mua (đồng)"
              name="price"
              rules={[
                { required: true, message: 'Vui lòng nhập giá mua' },
                { type: 'number', min: 0, message: 'Giá mua tối thiểu 0' },
              ]}
              style={{ width: 220 }}
            >
              <InputNumber min={0} step={100000} style={{ width: '100%' }} placeholder="4500000" />
            </Form.Item>
            <Form.Item label="Ngày mua" name="purchaseDate" style={{ width: 200 }}>
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" placeholder="Chọn ngày" />
            </Form.Item>
          </Space>
          <Form.Item
            label="Ghi chú"
            name="note"
            rules={[{ max: 1000, message: 'Ghi chú tối đa 1000 ký tự' }]}
          >
            <Input.TextArea rows={3} placeholder="Vị trí đặt, phụ kiện kèm theo..." />
          </Form.Item>
        </Form>
        {editingAsset ? (
          <PhotoUpload
            label="Ảnh tài sản"
            photos={assetPhotosQuery.data ?? []}
            loading={assetPhotosQuery.isLoading}
            uploading={uploadAssetPhotoMutation.isPending}
            onUpload={(file) =>
              uploadAssetPhotoMutation.mutate({ assetId: editingAsset.id, file })
            }
            onDelete={(photo) =>
              deleteAssetPhotoMutation.mutate({ assetId: editingAsset.id, photoId: photo.id })
            }
          />
        ) : (
          <Typography.Text type="secondary">
            Lưu tài sản trước khi thêm ảnh đối chiếu.
          </Typography.Text>
        )}
      </Modal>

      <Modal
        title={editingRepair ? 'Sửa lần sửa' : 'Thêm lần sửa'}
        open={repairModalOpen}
        onCancel={() => {
          setRepairModalOpen(false)
          setEditingRepair(null)
        }}
        onOk={() => repairForm.submit()}
        confirmLoading={saveRepairMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
        styles={{ body: { maxHeight: '68vh', overflowY: 'auto' } }}
      >
        <Form
          form={repairForm}
          layout="vertical"
          onFinish={(values) => saveRepairMutation.mutate(values)}
        >
          <Form.Item
            label="Tài sản"
            name="assetId"
            rules={[{ required: true, message: 'Vui lòng chọn tài sản' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Chọn tài sản"
              options={(repairAssetOptionsQuery.data ?? []).map((asset) => ({
                value: asset.id,
                label: `${asset.code} - ${asset.name}`,
              }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              label="Ngày báo"
              name="reportedAt"
              rules={[{ required: true, message: 'Vui lòng chọn ngày báo' }]}
              style={{ width: 180 }}
            >
              <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" placeholder="Chọn ngày" />
            </Form.Item>
            <Form.Item
              label="Trạng thái"
              name="status"
              rules={[{ required: true, message: 'Vui lòng chọn trạng thái' }]}
              style={{ width: 180 }}
            >
              <Select
                placeholder="Chọn trạng thái"
                options={Object.entries(REPAIR_STATUS_META).map(([key, meta]) => ({
                  value: key,
                  label: meta.label,
                }))}
              />
            </Form.Item>
          </Space>
          <Form.Item
            label="Mô tả sự cố"
            name="description"
            rules={[
              { required: true, message: 'Vui lòng nhập mô tả sự cố' },
              { max: 500, message: 'Mô tả tối đa 500 ký tự' },
            ]}
          >
            <Input.TextArea rows={3} placeholder="Máy lạnh không lạnh, block kêu to..." />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              label="Chi phí (đồng)"
              name="cost"
              rules={[
                { required: true, message: 'Vui lòng nhập chi phí' },
                { type: 'number', min: 0, message: 'Chi phí tối thiểu 0' },
              ]}
              style={{ width: 200 }}
            >
              <InputNumber min={0} step={50000} style={{ width: '100%' }} placeholder="300000" />
            </Form.Item>
            {repairStatus === 'DONE' && (
              <Form.Item label="Ngày hoàn thành" name="doneAt" style={{ width: 180 }}>
                <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" placeholder="Chọn ngày" />
              </Form.Item>
            )}
          </Space>
          <Form.Item
            label="Ghi chú"
            name="note"
            rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}
          >
            <Input.TextArea rows={2} placeholder="Phiếu bảo hành, thời gian bảo hành..." />
          </Form.Item>
        </Form>
        {editingRepair ? (
          <>
            <PhotoUpload
              label="Ảnh trước khi sửa"
              photos={repairPhotosBefore}
              loading={repairPhotosQuery.isLoading}
              uploading={uploadRepairPhotoMutation.isPending}
              onUpload={(file) =>
                uploadRepairPhotoMutation.mutate({
                  assetId: editingRepair.assetId,
                  repairId: editingRepair.id,
                  stage: 'TRUOC',
                  file,
                })
              }
              onDelete={(photo) =>
                deleteRepairPhotoMutation.mutate({
                  assetId: editingRepair.assetId,
                  repairId: editingRepair.id,
                  photoId: photo.id,
                })
              }
            />
            <PhotoUpload
              label="Ảnh sau khi sửa"
              photos={repairPhotosAfter}
              loading={repairPhotosQuery.isLoading}
              uploading={uploadRepairPhotoMutation.isPending}
              onUpload={(file) =>
                uploadRepairPhotoMutation.mutate({
                  assetId: editingRepair.assetId,
                  repairId: editingRepair.id,
                  stage: 'SAU',
                  file,
                })
              }
              onDelete={(photo) =>
                deleteRepairPhotoMutation.mutate({
                  assetId: editingRepair.assetId,
                  repairId: editingRepair.id,
                  photoId: photo.id,
                })
              }
            />
          </>
        ) : (
          <Typography.Text type="secondary">
            Lưu lần sửa trước khi thêm ảnh trước và sau.
          </Typography.Text>
        )}
      </Modal>
    </div>
  )
}
