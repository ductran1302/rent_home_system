import {
  App as AntApp,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
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
import { formatDate } from '../utils/format'
import { USERNAME_PATTERN } from '../utils/validation'

interface UserRow {
  id: number
  username: string
  role: string
  personId: number | null
  fullName: string | null
  enabled: boolean
  managerStartDate: string | null
  managerEndDate: string | null
  createdAt: string
}

interface PersonOption {
  id: number
  fullName: string
  phone: string | null
}

interface UserFormValues {
  username: string
  password?: string
  role: string
  personId?: number
  managerStartDate?: Dayjs
  managerEndDate?: Dayjs
  enabled: boolean
}

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý',
  USER: 'Người dùng',
}

const ROLE_COLORS: Record<string, string> = {
  ADMIN: 'blue',
  MANAGER: 'green',
  USER: 'default',
}

export default function AccountsPage() {
  const { me } = useAuth()
  const { message } = AntApp.useApp()

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<UserRow | null>(null)
  const [form] = Form.useForm<UserFormValues>()

  const watchRole = Form.useWatch('role', form)

  const listQuery = useQuery({
    queryKey: ['users'],
    queryFn: async () => (await api.get<UserRow[]>('/users')).data,
  })

  const personsQuery = useQuery({
    queryKey: ['persons-options'],
    queryFn: async () =>
      (await api.get<{ items: PersonOption[] }>('/persons', { params: { size: 100 } })).data.items,
  })

  const saveMutation = useMutation({
    mutationFn: async (values: UserFormValues) => {
      const payload = {
        ...(editing ? {} : { username: values.username.trim() }),
        ...(values.password ? { password: values.password } : {}),
        role: values.role,
        personId: values.personId ?? null,
        managerStartDate: values.managerStartDate
          ? values.managerStartDate.format('YYYY-MM-DD')
          : null,
        managerEndDate: values.managerEndDate ? values.managerEndDate.format('YYYY-MM-DD') : null,
        enabled: values.enabled,
      }
      if (editing) {
        return api.put<UserRow>(`/users/${editing.id}`, payload)
      }
      return api.post<UserRow>('/users', payload)
    },
    onSuccess: () => {
      message.success(editing ? 'Đã cập nhật tài khoản' : 'Đã tạo tài khoản')
      setModalOpen(false)
      setEditing(null)
      form.resetFields()
      listQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const toggleMutation = useMutation({
    mutationFn: async ({ row, enabled }: { row: UserRow; enabled: boolean }) =>
      api.put(`/users/${row.id}`, {
        role: row.role,
        personId: row.personId,
        managerStartDate: row.managerStartDate,
        managerEndDate: row.managerEndDate,
        enabled,
      }),
    onSuccess: () => {
      message.success('Đã cập nhật trạng thái tài khoản')
      listQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const openCreate = () => {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({ role: 'USER', enabled: true })
    setModalOpen(true)
  }

  const openEdit = (row: UserRow) => {
    setEditing(row)
    form.resetFields()
    form.setFieldsValue({
      username: row.username,
      role: row.role,
      personId: row.personId ?? undefined,
      managerStartDate: row.managerStartDate ? dayjs(row.managerStartDate) : undefined,
      managerEndDate: row.managerEndDate ? dayjs(row.managerEndDate) : undefined,
      enabled: row.enabled,
    })
    setModalOpen(true)
  }

  const isSelfEdit = editing != null && editing.username === me?.username
  const roleLocked = isSelfEdit || editing?.role === 'ADMIN'
  const needPerson = watchRole === 'MANAGER'
  const isManager = watchRole === 'MANAGER'

  const personOptions = (personsQuery.data ?? []).map((person) => ({
    value: person.id,
    label: person.phone ? `${person.fullName} (${person.phone})` : person.fullName,
  }))

  const columns = [
    { title: 'Tên đăng nhập', dataIndex: 'username', key: 'username', width: 160 },
    {
      title: 'Vai trò',
      dataIndex: 'role',
      key: 'role',
      width: 140,
      render: (role: string) => (
        <Tag color={ROLE_COLORS[role] ?? 'default'}>{ROLE_LABELS[role] ?? role}</Tag>
      ),
    },
    {
      title: 'Hồ sơ liên kết',
      key: 'person',
      width: 200,
      ellipsis: true,
      render: (_: unknown, row: UserRow) =>
        row.fullName || <Typography.Text type="secondary">Chưa liên kết</Typography.Text>,
    },
    {
      title: 'Thời hạn quản lý',
      key: 'period',
      width: 210,
      render: (_: unknown, row: UserRow) => {
        if (row.role !== 'MANAGER') {
          return <Typography.Text type="secondary">Không áp dụng</Typography.Text>
        }
        const start = row.managerStartDate ? formatDate(row.managerStartDate) : 'chưa đặt'
        const end = row.managerEndDate ? formatDate(row.managerEndDate) : 'không giới hạn'
        return `${start} - ${end}`
      },
    },
    {
      title: 'Trạng thái',
      key: 'enabled',
      width: 120,
      render: (_: unknown, row: UserRow) => {
        const isSelf = row.username === me?.username
        return (
          <Switch
            size="small"
            checked={row.enabled}
            checkedChildren="Bật"
            unCheckedChildren="Tắt"
            disabled={isSelf || toggleMutation.isPending}
            aria-label={`${row.enabled ? 'Tắt' : 'Bật'} tài khoản ${row.username}`}
            onChange={(checked) => toggleMutation.mutate({ row, enabled: checked })}
          />
        )
      },
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 140,
      render: (value: string) => formatDate(value),
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 100,
      fixed: 'right' as const,
      render: (_: unknown, row: UserRow) => (
        <Button size="small" onClick={() => openEdit(row)}>
          Sửa
        </Button>
      ),
    },
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }} wrap>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Tài khoản
        </Typography.Title>
        <Button type="primary" onClick={openCreate}>
          Thêm tài khoản
        </Button>
      </Space>

      {listQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách tài khoản: ${getErrorMessage(listQuery.error)}`}
        >
          <Button onClick={() => listQuery.refetch()}>Thử lại</Button>
        </Empty>
      )}

      <Table<UserRow>
        rowKey="id"
        loading={listQuery.isLoading}
        columns={columns}
        dataSource={listQuery.data}
        pagination={false}
        scroll={{ x: 1100 }}
        locale={{
          emptyText: (
            <Empty
              description="Chưa có tài khoản nào, bấm Thêm tài khoản để tạo"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ),
        }}
      />

      <Modal
        title={editing ? 'Sửa tài khoản' : 'Thêm tài khoản'}
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
            label="Tên đăng nhập"
            name="username"
            rules={[
              { required: true, message: 'Vui lòng nhập tên đăng nhập' },
              {
                pattern: USERNAME_PATTERN,
                message:
                  'Tên đăng nhập chỉ gồm chữ, số, dấu chấm, gạch nối, từ 3 đến 100 ký tự',
              },
            ]}
          >
            <Input placeholder="nguoidung01" disabled={editing != null} autoFocus />
          </Form.Item>
          <Form.Item
            label="Mật khẩu"
            name="password"
            extra={editing ? 'Để trống nếu không đổi mật khẩu' : undefined}
            rules={
              editing
                ? [{ min: 6, max: 100, message: 'Mật khẩu từ 6 đến 100 ký tự' }]
                : [
                    { required: true, message: 'Vui lòng nhập mật khẩu' },
                    { min: 6, max: 100, message: 'Mật khẩu từ 6 đến 100 ký tự' },
                  ]
            }
          >
            <Input.Password placeholder="Tối thiểu 6 ký tự" />
          </Form.Item>
          <Form.Item label="Vai trò" name="role" rules={[{ required: true, message: 'Vui lòng chọn vai trò' }]}>
            <Select
              placeholder="Chọn vai trò"
              disabled={roleLocked}
              options={[
                ...(editing?.role === 'ADMIN' ? [{ value: 'ADMIN', label: 'Quản trị viên' }] : []),
                { value: 'MANAGER', label: 'Quản lý' },
                { value: 'USER', label: 'Người dùng' },
              ]}
            />
          </Form.Item>
          <Form.Item
            label="Hồ sơ liên kết"
            name="personId"
            extra={
              needPerson
                ? 'Bắt buộc với vai trò quản lý'
                : 'Có thể bỏ trống với vai trò quản trị viên và người dùng'
            }
            rules={[{ required: needPerson, message: 'Vui lòng chọn hồ sơ cá nhân' }]}
          >
            <Select
              showSearch
              allowClear={!needPerson}
              optionFilterProp="label"
              placeholder="Chọn hồ sơ cá nhân"
              loading={personsQuery.isLoading}
              options={personOptions}
              notFoundContent={
                personsQuery.isLoading ? 'Đang tải...' : 'Chưa có người, hãy thêm ở trang Người'
              }
            />
          </Form.Item>
          <Form.Item
            label="Ngày bắt đầu quản lý"
            name="managerStartDate"
            rules={[{ required: isManager, message: 'Vui lòng chọn ngày bắt đầu quản lý' }]}
          >
            <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" placeholder="Chọn ngày" />
          </Form.Item>
          <Form.Item
            label="Ngày kết thúc quản lý"
            name="managerEndDate"
            extra="Để trống nếu không giới hạn"
            dependencies={['managerStartDate']}
            rules={[
              ({ getFieldValue }) => ({
                validator(_, value) {
                  const start = getFieldValue('managerStartDate')
                  if (value && start && value.isBefore(start)) {
                    return Promise.reject(
                      new Error('Ngày kết thúc phải sau ngày bắt đầu'),
                    )
                  }
                  return Promise.resolve()
                },
              }),
            ]}
          >
            <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" placeholder="Chọn ngày" />
          </Form.Item>
          <Form.Item
            label="Hoạt động"
            name="enabled"
            valuePropName="checked"
            extra={isSelfEdit ? 'Không thể tự tắt tài khoản của chính mình' : undefined}
          >
            <Switch checkedChildren="Bật" unCheckedChildren="Tắt" disabled={isSelfEdit} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
