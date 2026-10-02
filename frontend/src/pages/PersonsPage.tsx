import { App as AntApp, Button, Empty, Form, Input, Modal, Popconfirm, Space, Table, Typography } from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api, getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'
import { CODE_CELL, formatDateTime } from '../utils/format'

const PAGE_SIZE = 20

interface PersonRow {
  id: number
  fullName: string
  idNumber: string | null
  phone: string | null
  address: string | null
  active: boolean
  createdAt: string | null
  updatedAt: string | null
  createdBy: string | null
  updatedBy: string | null
}

interface PersonPageData {
  items: PersonRow[]
  total: number
  page: number
  size: number
}

interface PersonFormValues {
  fullName: string
  idNumber?: string
  phone?: string
  address?: string
}

export default function PersonsPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()
  const canManage = me?.role === 'ADMIN' || me?.role === 'MANAGER'

  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<PersonRow | null>(null)
  const [form] = Form.useForm<PersonFormValues>()

  useEffect(() => {
    setPage(0)
  }, [query])

  const listQuery = useQuery({
    queryKey: ['persons', query, page],
    queryFn: async () => {
      const response = await api.get<PersonPageData>('/persons', {
        params: { q: query || undefined, page, size: PAGE_SIZE },
      })
      return response.data
    },
  })

  const saveMutation = useMutation({
    mutationFn: async (values: PersonFormValues) => {
      const payload = {
        fullName: values.fullName.trim(),
        idNumber: values.idNumber?.trim() || null,
        phone: values.phone?.trim() || null,
        address: values.address?.trim() || null,
      }
      if (editing) {
        return api.put<PersonRow>(`/persons/${editing.id}`, payload)
      }
      return api.post<PersonRow>('/persons', payload)
    },
    onSuccess: () => {
      message.success(editing ? 'Đã cập nhật người.' : 'Đã thêm người.')
      setModalOpen(false)
      setEditing(null)
      form.resetFields()
      listQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const deleteMutation = useMutation({
    mutationFn: async (id: number) => api.delete(`/persons/${id}`),
    onSuccess: () => {
      message.success('Đã xoá người.')
      listQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (row: PersonRow) => {
    setEditing(row)
    form.setFieldsValue({
      fullName: row.fullName,
      idNumber: row.idNumber ?? undefined,
      phone: row.phone ?? undefined,
      address: row.address ?? undefined,
    })
    setModalOpen(true)
  }

  const columns = [
    {
      title: 'Họ tên',
      dataIndex: 'fullName',
      key: 'fullName',
      ellipsis: true,
    },
    {
      title: 'CCCD',
      dataIndex: 'idNumber',
      key: 'idNumber',
      width: 140,
      render: (value: string | null) =>
        value ? <span style={CODE_CELL}>{value}</span> : '-',
    },
    {
      title: 'Số điện thoại',
      dataIndex: 'phone',
      key: 'phone',
      width: 140,
      render: (value: string | null) => value || '-',
    },
    {
      title: 'Địa chỉ',
      dataIndex: 'address',
      key: 'address',
      ellipsis: true,
      render: (value: string | null) => value || '-',
    },
    {
      title: 'Ngày tạo / người tạo',
      key: 'createdBy',
      width: 180,
      render: (_: unknown, row: PersonRow) => (
        <span>
          <div>{formatDateTime(row.createdAt) || '-'}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {row.createdBy || '-'}
          </Typography.Text>
        </span>
      ),
    },
    {
      title: 'Ngày cập nhật / người cập nhật',
      key: 'updatedBy',
      width: 190,
      render: (_: unknown, row: PersonRow) =>
        row.updatedAt ? (
          <span>
            <div>{formatDateTime(row.updatedAt)}</div>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {row.updatedBy || '-'}
            </Typography.Text>
          </span>
        ) : (
          <Typography.Text type="secondary">Chưa cập nhật</Typography.Text>
        ),
    },
    ...(canManage
      ? [
          {
            title: 'Thao tác',
            key: 'actions',
            width: 160,
            render: (_: unknown, row: PersonRow) => (
              <Space>
                <Button size="small" onClick={() => openEdit(row)}>
                  Sửa
                </Button>
                <Popconfirm
                  title="Xoá người này?"
                  description="Dữ liệu chỉ bị ẩn, vẫn giữ cho báo cáo cũ."
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
      : []),
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={2} style={{ margin: 0 }}>
          Quản lý người
        </Typography.Title>
        <Space>
          <Input.Search
            allowClear
            placeholder="Tìm theo họ tên, CCCD, số điện thoại"
            style={{ width: 280 }}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            onSearch={(value) => setQuery(value.trim())}
          />
          {canManage && (
            <Button type="primary" onClick={openCreate}>
              Thêm người
            </Button>
          )}
        </Space>
      </Space>

      {listQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách: ${getErrorMessage(listQuery.error)}`}
        >
          <Button loading={listQuery.isFetching} onClick={() => listQuery.refetch()}>
            Thử lại
          </Button>
        </Empty>
      )}

      {!listQuery.isError && (
        <Table<PersonRow>
          rowKey="id"
          loading={listQuery.isLoading}
          columns={columns}
          dataSource={listQuery.data?.items}
          scroll={{ x: 1250 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  query
                    ? 'Không tìm thấy người khớp với tìm kiếm'
                    : 'Chưa có người nào, bấm Thêm người để thêm người đầu tiên'
                }
              >
                {query && (
                  <Button
                    onClick={() => {
                      setSearch('')
                      setQuery('')
                    }}
                  >
                    Xoá tìm kiếm
                  </Button>
                )}
              </Empty>
            ),
          }}
          pagination={{
            current: page + 1,
            pageSize: PAGE_SIZE,
            total: listQuery.data?.total ?? 0,
            showTotal: (total) => `Tổng ${total} người`,
            onChange: (nextPage) => setPage(nextPage - 1),
          }}
        />
      )}

      <Modal
        title={editing ? 'Sửa người' : 'Thêm người'}
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
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Form.Item
            label="Họ tên"
            name="fullName"
            rules={[
              { required: true, message: 'Vui lòng nhập họ tên' },
              { max: 200, message: 'Họ tên tối đa 200 ký tự' },
            ]}
          >
            <Input placeholder="Nguyễn Văn A" autoFocus />
          </Form.Item>
          <Form.Item
            label="Số CCCD"
            name="idNumber"
            rules={[
              { max: 20, message: 'Số CCCD tối đa 20 ký tự' },
              {
                validator: (_, value: string) =>
                  !value || /^\d+$/.test(value)
                    ? Promise.resolve()
                    : Promise.reject(new Error('Số CCCD chỉ gồm chữ số')),
              },
            ]}
          >
            <Input placeholder="12 ký tự số" />
          </Form.Item>
          <Form.Item
            label="Số điện thoại"
            name="phone"
            rules={[
              { max: 20, message: 'Số điện thoại tối đa 20 ký tự' },
              {
                validator: (_, value: string) =>
                  !value || /^\d+$/.test(value)
                    ? Promise.resolve()
                    : Promise.reject(new Error('Số điện thoại chỉ gồm chữ số')),
              },
            ]}
          >
            <Input placeholder="0901234567" />
          </Form.Item>
          <Form.Item
            label="Địa chỉ"
            name="address"
            rules={[{ max: 500, message: 'Địa chỉ tối đa 500 ký tự' }]}
          >
            <Input placeholder="Số nhà, đường, quận/huyện..." />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
