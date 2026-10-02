import {
  App as AntApp,
  Button,
  Card,
  Descriptions,
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
  Typography,
} from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import { formatDateTime, formatNumber } from '../utils/format'

interface HouseRow {
  id: number
  code: string
  name: string
  address: string
  ownerId: number
  ownerName: string
  managerId: number
  managerName: string
  roomCount: number
  active: boolean
  note: string | null
  createdAt: string
  updatedAt: string
}

interface RoomRow {
  id: number
  houseId: number
  houseName: string
  roomNumber: string
  areaM2: number | null
  occupied: boolean
  active: boolean
  note: string | null
  createdAt: string
  updatedAt: string
}

interface PersonOption {
  id: number
  fullName: string
  phone: string | null
}

interface HouseFormValues {
  code: string
  name: string
  address: string
  ownerId: number
  managerId?: number
  note?: string
}

interface RoomFormValues {
  roomNumber: string
  areaM2?: number
  note?: string
}

export default function HousesPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()
  const canWrite = me?.role === 'ADMIN'

  const [houseModalOpen, setHouseModalOpen] = useState(false)
  const [editingHouse, setEditingHouse] = useState<HouseRow | null>(null)
  const [houseForm] = Form.useForm<HouseFormValues>()

  const [selectedHouseId, setSelectedHouseId] = useState<number | null>(null)
  const [roomModalOpen, setRoomModalOpen] = useState(false)
  const [editingRoom, setEditingRoom] = useState<RoomRow | null>(null)
  const [roomForm] = Form.useForm<RoomFormValues>()

  const housesQuery = useQuery({
    queryKey: ['houses'],
    queryFn: async () => (await api.get<HouseRow[]>('/houses')).data,
  })

  const personsQuery = useQuery({
    queryKey: ['persons-options'],
    enabled: canWrite,
    queryFn: async () =>
      (await api.get<{ items: PersonOption[] }>('/persons', { params: { size: 100 } })).data.items,
  })

  const roomsQuery = useQuery({
    queryKey: ['rooms', selectedHouseId],
    enabled: selectedHouseId != null,
    queryFn: async () => (await api.get<RoomRow[]>(`/rooms/by-house/${selectedHouseId}`)).data,
  })

  const saveHouseMutation = useMutation({
    mutationFn: async (values: HouseFormValues) => {
      const payload = {
        code: values.code.trim(),
        name: values.name.trim(),
        address: values.address.trim(),
        ownerId: values.ownerId,
        managerId: values.managerId ?? null,
        note: values.note?.trim() || null,
      }
      if (editingHouse) {
        return api.put<HouseRow>(`/houses/${editingHouse.id}`, payload)
      }
      return api.post<HouseRow>('/houses', payload)
    },
    onSuccess: (response) => {
      message.success(editingHouse ? 'Đã cập nhật nhà.' : 'Đã thêm nhà.')
      setHouseModalOpen(false)
      setEditingHouse(null)
      houseForm.resetFields()
      housesQuery.refetch()
      if (!editingHouse) {
        setSelectedHouseId(response.data.id)
      }
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteHouseMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/houses/${id}`),
    onSuccess: () => {
      message.success('Đã xoá nhà.')
      setSelectedHouseId(null)
      housesQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const saveRoomMutation = useMutation({
    mutationFn: async (values: RoomFormValues) => {
      const payload = {
        houseId: selectedHouseId,
        roomNumber: values.roomNumber.trim(),
        areaM2: values.areaM2 ?? null,
        note: values.note?.trim() || null,
      }
      if (editingRoom) {
        return api.put<RoomRow>(`/rooms/${editingRoom.id}`, payload)
      }
      return api.post<RoomRow>('/rooms', payload)
    },
    onSuccess: () => {
      message.success(editingRoom ? 'Đã cập nhật phòng.' : 'Đã thêm phòng.')
      setRoomModalOpen(false)
      setEditingRoom(null)
      roomForm.resetFields()
      roomsQuery.refetch()
      housesQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteRoomMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/rooms/${id}`),
    onSuccess: () => {
      message.success('Đã xoá phòng.')
      roomsQuery.refetch()
      housesQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  useEffect(() => {
    roomForm.resetFields()
  }, [roomForm, roomModalOpen])

  const openCreateHouse = () => {
    setEditingHouse(null)
    houseForm.resetFields()
    setHouseModalOpen(true)
  }

  const openEditHouse = (row: HouseRow) => {
    setEditingHouse(row)
    houseForm.setFieldsValue({
      code: row.code,
      name: row.name,
      address: row.address,
      ownerId: row.ownerId,
      managerId: row.managerId === row.ownerId ? undefined : row.managerId,
      note: row.note ?? undefined,
    })
    setHouseModalOpen(true)
  }

  const openCreateRoom = () => {
    setEditingRoom(null)
    roomForm.resetFields()
    setRoomModalOpen(true)
  }

  const openEditRoom = (row: RoomRow) => {
    setEditingRoom(row)
    roomForm.setFieldsValue({
      roomNumber: row.roomNumber,
      areaM2: row.areaM2 ?? undefined,
      note: row.note ?? undefined,
    })
    setRoomModalOpen(true)
  }

  const personOptions = (personsQuery.data ?? []).map((person) => ({
    value: person.id,
    label: person.phone ? `${person.fullName} (${person.phone})` : person.fullName,
  }))

  const houseColumns = [
    { title: 'Mã nhà', dataIndex: 'code', key: 'code', width: 110 },
    { title: 'Tên nhà', dataIndex: 'name', key: 'name', width: 180, ellipsis: true },
    { title: 'Địa chỉ', dataIndex: 'address', key: 'address', width: 240, ellipsis: true },
    { title: 'Chủ nhà', dataIndex: 'ownerName', key: 'ownerName', width: 150, ellipsis: true },
    {
      title: 'Số phòng',
      dataIndex: 'roomCount',
      key: 'roomCount',
      width: 90,
      align: 'center' as const,
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      key: 'note',
      width: 300,
      ellipsis: true,
      render: (value: string | null) =>
        value ? value : <Typography.Text type="secondary">Không có</Typography.Text>,
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Ngày cập nhật',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 150,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 230,
      fixed: 'right' as const,
      render: (_: unknown, row: HouseRow) => (
        <Space>
          <Button
            size="small"
            type={selectedHouseId === row.id ? 'primary' : 'default'}
            onClick={() => setSelectedHouseId(row.id)}
          >
            Xem phòng
          </Button>
          {canWrite && (
            <>
              <Button size="small" onClick={() => openEditHouse(row)}>
                Sửa
              </Button>
              <Popconfirm
                title="Xoá nhà này?"
                description="Nhà sẽ bị ẩn khỏi danh sách."
                okText="Xoá"
                cancelText="Huỷ"
                onConfirm={() => deleteHouseMutation.mutate(row.id)}
              >
                <Button size="small" danger loading={deleteHouseMutation.isPending}>
                  Xoá
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ]

  const roomColumns = [
    { title: 'Số phòng', dataIndex: 'roomNumber', key: 'roomNumber', width: 110 },
    {
      title: 'Diện tích (m²)',
      dataIndex: 'areaM2',
      key: 'areaM2',
      width: 130,
      render: (value: number | null) => formatNumber(value),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'occupied',
      key: 'occupied',
      width: 130,
      render: (occupied: boolean) =>
        occupied ? <Tag color="green">Đang thuê</Tag> : <Tag>Trống</Tag>,
    },
    {
      title: 'Ghi chú',
      dataIndex: 'note',
      key: 'note',
      width: 300,
      ellipsis: true,
      render: (value: string | null) =>
        value ? value : <Typography.Text type="secondary">Không có</Typography.Text>,
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Ngày cập nhật',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 150,
      render: (value: string) => formatDateTime(value),
    },
    ...(canWrite
      ? [
          {
            title: 'Thao tác',
            key: 'actions',
            width: 160,
            fixed: 'right' as const,
            render: (_: unknown, row: RoomRow) => (
              <Space>
                <Button size="small" onClick={() => openEditRoom(row)}>
                  Sửa
                </Button>
                <Popconfirm
                  title="Xoá phòng này?"
                  description="Chỉ xoá được khi phòng không có hợp đồng hoạt động."
                  okText="Xoá"
                  cancelText="Huỷ"
                  onConfirm={() => deleteRoomMutation.mutate(row.id)}
                >
                  <Button size="small" danger loading={deleteRoomMutation.isPending}>
                    Xoá
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]
      : []),
  ]

  const selectedHouse = (housesQuery.data ?? []).find((house) => house.id === selectedHouseId)

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={2} style={{ margin: 0 }}>
          Nhà &amp; phòng
        </Typography.Title>
        {canWrite && (
          <Button type="primary" onClick={openCreateHouse}>
            Thêm nhà
          </Button>
        )}
      </Space>

      {housesQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách nhà: ${getErrorMessage(housesQuery.error)}`}
        >
          <Button loading={housesQuery.isFetching} onClick={() => housesQuery.refetch()}>
            Thử lại
          </Button>
        </Empty>
      )}

      {!housesQuery.isError && (
        <Table<HouseRow>
          rowKey="id"
          loading={housesQuery.isLoading}
          columns={houseColumns}
          dataSource={housesQuery.data}
          pagination={false}
          scroll={{ x: 1600 }}
          locale={{
            emptyText: (
              <Empty
                description="Chưa có nhà nào, bấm Thêm nhà để tạo nhà đầu tiên"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ),
          }}
        />
      )}

      {selectedHouse && (
        <Card
          style={{ marginTop: 24 }}
          title={
            <Space>
              <span>
                Phòng của {selectedHouse.name} ({selectedHouse.code})
              </span>
            </Space>
          }
          extra={
            canWrite && (
              <Button type="primary" onClick={openCreateRoom}>
                Thêm phòng
              </Button>
            )
          }
        >
          <Descriptions size="small" column={{ xs: 1, sm: 3 }} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Địa chỉ">{selectedHouse.address}</Descriptions.Item>
            <Descriptions.Item label="Chủ nhà">{selectedHouse.ownerName}</Descriptions.Item>
            <Descriptions.Item label="Quản lý">{selectedHouse.managerName}</Descriptions.Item>
          </Descriptions>

          {roomsQuery.isError ? (
            <Empty
              style={{ margin: '48px 0' }}
              description={`Không tải được danh sách phòng: ${getErrorMessage(roomsQuery.error)}`}
            >
              <Button onClick={() => roomsQuery.refetch()}>Thử lại</Button>
            </Empty>
          ) : (
            <Table<RoomRow>
              rowKey="id"
              loading={roomsQuery.isLoading}
              columns={roomColumns}
              dataSource={roomsQuery.data}
              pagination={false}
              scroll={{ x: 1130 }}
              locale={{
                emptyText: (
                  <Empty
                    description="Nhà này chưa có phòng nào, bấm Thêm phòng để tạo phòng"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  />
                ),
              }}
            />
          )}
        </Card>
      )}

      <Modal
        title={editingHouse ? 'Sửa nhà' : 'Thêm nhà'}
        open={houseModalOpen}
        onCancel={() => {
          setHouseModalOpen(false)
          setEditingHouse(null)
        }}
        onOk={() => houseForm.submit()}
        confirmLoading={saveHouseMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
      >
        <Form
          form={houseForm}
          layout="vertical"
          onFinish={(values) => saveHouseMutation.mutate(values)}
        >
          <Form.Item
            label="Mã nhà"
            name="code"
            rules={[
              { required: true, message: 'Vui lòng nhập mã nhà' },
              { max: 50, message: 'Mã nhà tối đa 50 ký tự' },
            ]}
          >
            <Input placeholder="N-001" />
          </Form.Item>
          <Form.Item
            label="Tên nhà"
            name="name"
            rules={[
              { required: true, message: 'Vui lòng nhập tên nhà' },
              { max: 200, message: 'Tên nhà tối đa 200 ký tự' },
            ]}
          >
            <Input placeholder="Nhà trọ Láng" />
          </Form.Item>
          <Form.Item
            label="Địa chỉ"
            name="address"
            rules={[
              { required: true, message: 'Vui lòng nhập địa chỉ' },
              { max: 500, message: 'Địa chỉ tối đa 500 ký tự' },
            ]}
          >
            <Input placeholder="Số nhà, đường, quận/huyện..." />
          </Form.Item>
          <Form.Item
            label="Chủ nhà"
            name="ownerId"
            rules={[{ required: true, message: 'Vui lòng chọn chủ nhà' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="Chọn người làm chủ nhà"
              loading={personsQuery.isLoading}
              options={personOptions}
              notFoundContent={personsQuery.isLoading ? 'Đang tải...' : 'Chưa có người, hãy thêm ở trang Người'}
            />
          </Form.Item>
          <Form.Item
            label="Quản lý nhà"
            name="managerId"
            extra="Để trống thì quản lý sẽ là chủ nhà"
          >
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              placeholder="Chọn người quản lý"
              loading={personsQuery.isLoading}
              options={personOptions}
            />
          </Form.Item>
          <Form.Item
            label="Ghi chú"
            name="note"
            rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder="Ghi chú thêm về nhà, có thể bỏ trống"
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={editingRoom ? 'Sửa phòng' : 'Thêm phòng'}
        open={roomModalOpen}
        onCancel={() => {
          setRoomModalOpen(false)
          setEditingRoom(null)
        }}
        onOk={() => roomForm.submit()}
        confirmLoading={saveRoomMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
      >
        <Form
          form={roomForm}
          layout="vertical"
          onFinish={(values) => saveRoomMutation.mutate(values)}
        >
          <Form.Item
            label="Số phòng"
            name="roomNumber"
            rules={[
              { required: true, message: 'Vui lòng nhập số phòng' },
              { max: 20, message: 'Số phòng tối đa 20 ký tự' },
            ]}
          >
            <Input placeholder="101" />
          </Form.Item>
          <Form.Item label="Diện tích (m²)" name="areaM2">
            <InputNumber min={0.01} step={0.5} style={{ width: '100%' }} placeholder="18.5" />
          </Form.Item>
          <Form.Item
            label="Ghi chú"
            name="note"
            rules={[{ max: 500, message: 'Ghi chú tối đa 500 ký tự' }]}
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              placeholder="Ghi chú thêm về phòng, có thể bỏ trống"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
