import {
  App as AntApp,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import { formatDateTime } from '../utils/format'

interface NoticeRow {
  id: number
  title: string
  content: string
  houseId: number | null
  houseName: string | null
  startsAt: string | null
  endsAt: string | null
  active: boolean
  createdAt: string
}

interface HouseOption {
  id: number
  name: string
}

interface NoticeFormValues {
  title: string
  content: string
  houseId?: number
  period?: [Dayjs | null, Dayjs | null]
}

const ALL_HOUSES = 0
const { RangePicker } = DatePicker

function statusOf(row: NoticeRow): { color: string; label: string } {
  if (!row.active) {
    return { color: 'default', label: 'Đã tắt' }
  }
  const now = Date.now()
  if (row.startsAt && new Date(row.startsAt).getTime() > now) {
    return { color: 'warning', label: 'Chưa tới' }
  }
  if (row.endsAt && new Date(row.endsAt).getTime() < now) {
    return { color: 'default', label: 'Đã hết hạn' }
  }
  return { color: 'success', label: 'Đang hiển thị' }
}

function formatPeriod(row: NoticeRow): string {
  const start = row.startsAt ? formatDateTime(row.startsAt) : 'không giới hạn'
  const end = row.endsAt ? formatDateTime(row.endsAt) : 'không giới hạn'
  if (!row.startsAt && !row.endsAt) {
    return 'Luôn hiển thị'
  }
  return `${start} - ${end}`
}

export default function NoticesPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()
  const isManager = me?.role === 'MANAGER'

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<NoticeRow | null>(null)
  const [form] = Form.useForm<NoticeFormValues>()

  const listQuery = useQuery({
    queryKey: ['notices'],
    queryFn: async () => (await api.get<NoticeRow[]>('/notices')).data,
  })

  const housesQuery = useQuery({
    queryKey: ['houses-options'],
    queryFn: async () => (await api.get<HouseOption[]>('/houses')).data,
    enabled: me != null,
  })

  const saveMutation = useMutation({
    mutationFn: async (values: NoticeFormValues) => {
      const houseId = values.houseId === ALL_HOUSES ? null : (values.houseId ?? null)
      const payload = {
        title: values.title.trim(),
        content: values.content.trim(),
        houseId,
        startsAt: values.period?.[0]
          ? values.period[0].format('YYYY-MM-DDTHH:mm:ss')
          : null,
        endsAt: values.period?.[1] ? values.period[1].format('YYYY-MM-DDTHH:mm:ss') : null,
      }
      if (editing) {
        return api.put<NoticeRow>(`/notices/${editing.id}`, payload)
      }
      return api.post<NoticeRow>('/notices', payload)
    },
    onSuccess: () => {
      message.success(editing ? 'Đã cập nhật thông báo.' : 'Đã tạo thông báo.')
      setModalOpen(false)
      setEditing(null)
      form.resetFields()
      listQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/notices/${id}`),
    onSuccess: () => {
      message.success('Đã xoá thông báo.')
      listQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    if (isManager) {
      form.setFieldsValue({ houseId: undefined })
    } else {
      form.setFieldsValue({ houseId: ALL_HOUSES })
    }
    setModalOpen(true)
  }

  const openEdit = (row: NoticeRow) => {
    setEditing(row)
    form.resetFields()
    form.setFieldsValue({
      title: row.title,
      content: row.content,
      houseId: row.houseId ?? (isManager ? undefined : ALL_HOUSES),
      period: [
        row.startsAt ? dayjs(row.startsAt) : null,
        row.endsAt ? dayjs(row.endsAt) : null,
      ],
    })
    setModalOpen(true)
  }

  const houseOptions = [
    ...(!isManager ? [{ value: ALL_HOUSES, label: 'Tất cả nhà' }] : []),
    ...(housesQuery.data ?? []).map((house) => ({ value: house.id, label: house.name })),
  ]

  const columns = [
    { title: 'Tiêu đề', dataIndex: 'title', key: 'title', width: 200 },
    {
      title: 'Nội dung',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
    },
    {
      title: 'Nhà',
      key: 'house',
      width: 170,
      ellipsis: true,
      render: (_: unknown, row: NoticeRow) =>
        row.houseName ?? <Typography.Text type="secondary">Tất cả nhà</Typography.Text>,
    },
    {
      title: 'Thời gian',
      key: 'period',
      width: 230,
      render: (_: unknown, row: NoticeRow) => formatPeriod(row),
    },
    {
      title: 'Trạng thái',
      key: 'status',
      width: 130,
      render: (_: unknown, row: NoticeRow) => {
        const status = statusOf(row)
        return <Tag color={status.color}>{status.label}</Tag>
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 150,
      fixed: 'right' as const,
      render: (_: unknown, row: NoticeRow) => (
        <Space>
          <Button size="small" onClick={() => openEdit(row)}>
            Sửa
          </Button>
          <Popconfirm
            title="Xoá thông báo này?"
            description="Thông báo sẽ không hiển thị cho người dùng nữa."
            okText="Xoá"
            cancelText="Huỷ"
            onConfirm={() => deleteMutation.mutate(row.id)}
          >
            <Button size="small" danger loading={deleteMutation.isPending}>
              Xoá
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={2} style={{ margin: 0 }}>
          Thông báo quan trọng
        </Typography.Title>
        <Button type="primary" onClick={openCreate}>
          Thêm thông báo
        </Button>
      </Space>

      {listQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách thông báo: ${getErrorMessage(listQuery.error)}`}
        >
          <Button loading={listQuery.isFetching} onClick={() => listQuery.refetch()}>
            Thử lại
          </Button>
        </Empty>
      )}

      {!listQuery.isError && (
        <Table<NoticeRow>
          rowKey="id"
          loading={listQuery.isLoading}
          columns={columns}
          dataSource={listQuery.data}
          pagination={false}
          scroll={{ x: 1000 }}
          locale={{
            emptyText: (
              <Empty
                description="Chưa có thông báo nào, bấm Thêm thông báo để tạo"
                image={Empty.PRESENTED_IMAGE_SIMPLE}
              />
            ),
          }}
        />
      )}

      <Modal
        title={editing ? 'Sửa thông báo' : 'Thêm thông báo'}
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false)
          setEditing(null)
        }}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item
            label="Tiêu đề"
            name="title"
            rules={[
              { required: true, message: 'Vui lòng nhập tiêu đề' },
              { max: 200, message: 'Tiêu đề tối đa 200 ký tự' },
            ]}
          >
            <Input placeholder="Ví dụ: Bảo trì điện nước" autoFocus />
          </Form.Item>
          <Form.Item
            label="Nội dung"
            name="content"
            rules={[
              { required: true, message: 'Vui lòng nhập nội dung' },
              { max: 1000, message: 'Nội dung tối đa 1000 ký tự' },
            ]}
          >
            <Input.TextArea rows={3} placeholder="Nội dung hiển thị trên thanh thông báo" />
          </Form.Item>
          <Form.Item
            label="Nhà"
            name="houseId"
            extra={isManager ? 'Quản lý phải chọn nhà cụ thể' : 'Chọn Tất cả nhà để hiển thị mọi nơi'}
            rules={[{ required: true, message: 'Vui lòng chọn nhà' }]}
          >
            <Select
              placeholder="Chọn nhà"
              loading={housesQuery.isLoading}
              options={houseOptions}
              optionFilterProp="label"
              notFoundContent={
                housesQuery.isLoading ? 'Đang tải...' : 'Chưa có nhà, hãy thêm ở trang Nhà & phòng'
              }
            />
          </Form.Item>
          <Form.Item
            label="Thời gian hiển thị"
            name="period"
            extra="Để trống hai mốc nếu muốn hiển thị không giới hạn"
            dependencies={[['period']]}
            rules={[
              ({ getFieldValue }) => ({
                validator(_, value) {
                  const period = getFieldValue('period') as
                    | [Dayjs | null, Dayjs | null]
                    | undefined
                  const start = period?.[0]
                  const end = value?.[1] ?? period?.[1]
                  if (start && end && !end.isAfter(start)) {
                    return Promise.reject(
                      new Error('Thời gian kết thúc phải sau thời gian bắt đầu'),
                    )
                  }
                  return Promise.resolve()
                },
              }),
            ]}
          >
            <RangePicker
              showTime
              allowEmpty={[true, true]}
              format="DD/MM/YYYY HH:mm"
              style={{ width: '100%' }}
              placeholder={['Bắt đầu', 'Kết thúc']}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
