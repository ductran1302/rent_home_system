import {
  ApartmentOutlined,
  FileTextOutlined,
  KeyOutlined,
  LogoutOutlined,
  PayCircleOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Dropdown, Layout, Menu, Space, Typography } from 'antd'
import { Suspense } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import PageSkeleton from './PageSkeleton'

const { Sider, Header, Content } = Layout

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý',
  USER: 'Người dùng',
}

export default function AppLayout() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = '/' + (location.pathname.split('/')[1] ?? '')
  const role = me?.role
  const menuItems = [
    { key: '/', icon: <ApartmentOutlined />, label: 'Tổng quan' },
    ...(role === 'USER'
      ? []
      : [
          { key: '/houses', icon: <ApartmentOutlined />, label: 'Nhà & phòng' },
          { key: '/persons', icon: <TeamOutlined />, label: 'Người' },
        ]),
    { key: '/contracts', icon: <FileTextOutlined />, label: 'Hợp đồng' },
    { key: '/billing', icon: <PayCircleOutlined />, label: 'Hóa đơn' },
    ...(role === 'ADMIN'
      ? [{ key: '/accounts', icon: <KeyOutlined />, label: 'Tài khoản' }]
      : []),
  ]

  const userMenu = {
    items: [
      {
        key: 'role',
        label: `Vai trò: ${ROLE_LABELS[me?.role ?? ''] ?? me?.role ?? ''}`,
        disabled: true,
      },
      { type: 'divider' as const },
      {
        key: 'logout',
        icon: <LogoutOutlined />,
        label: 'Đăng xuất',
        onClick: logout,
      },
    ],
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth={64} theme="dark">
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 600,
            fontSize: 18,
          }}
        >
          RuinHome
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <Dropdown menu={userMenu} placement="bottomRight">
            <Space style={{ cursor: 'pointer' }}>
              <Avatar size="small" icon={<UserOutlined />} />
              <Typography.Text>{me?.fullName || me?.username}</Typography.Text>
            </Space>
          </Dropdown>
        </Header>
        <Content style={{ margin: 24 }}>
          <Suspense fallback={<PageSkeleton />}>
            <Outlet />
          </Suspense>
        </Content>
      </Layout>
    </Layout>
  )
}
