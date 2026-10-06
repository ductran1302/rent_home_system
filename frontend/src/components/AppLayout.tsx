import {
  AccountBookOutlined,
  ApartmentOutlined,
  FileTextOutlined,
  DollarOutlined,
  HomeOutlined,
  KeyOutlined,
  LogoutOutlined,
  MoonOutlined,
  NotificationOutlined,
  SettingOutlined,
  SunOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Dropdown, Layout, Menu, Space, theme, Typography } from 'antd'
import { Suspense, useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/context'
import { useThemeMode } from '../theme-context'
import logoHouse from '../assets/logo-house.svg'
import logoMark from '../assets/logo-mark.svg'
import PageSkeleton from './PageSkeleton'
import NotificationBell from './NotificationBell'
import NoticeTicker from './NoticeTicker'

const { Sider, Header, Content } = Layout

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  MANAGER: 'Quản lý',
  USER: 'Người dùng',
}

export default function AppLayout() {
  const { me, logout } = useAuth()
  const { token } = theme.useToken()
  const { mode, toggleMode } = useThemeMode()
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = '/' + (location.pathname.split('/')[1] ?? '')
  const [siderCollapsed, setSiderCollapsed] = useState(false)
  const [openKeys, setOpenKeys] = useState<string[]>([])
  const role = me?.role
  const canManage = role === 'ADMIN' || role === 'MANAGER'

  useEffect(() => {
    if (location.pathname.startsWith('/accounts') || location.pathname.startsWith('/notices')) {
      setOpenKeys(['manage'])
    }
  }, [location.pathname])

  const menuItems = [
    { key: '/', icon: <ApartmentOutlined />, label: 'Tổng quan' },
    ...(role === 'USER'
      ? []
      : [
          { key: '/houses', icon: <HomeOutlined />, label: 'Nhà & phòng' },
          { key: '/persons', icon: <TeamOutlined />, label: 'Người' },
          { key: '/assets', icon: <ToolOutlined />, label: 'Tài sản' },
        ]),
    { key: '/contracts', icon: <FileTextOutlined />, label: 'Hợp đồng' },
    { key: '/billing', icon: <DollarOutlined />, label: 'Hóa đơn' },
    ...(canManage
      ? [{ key: '/debts', icon: <AccountBookOutlined />, label: 'Công nợ' }]
      : []),
    ...(canManage
      ? [
          {
            key: 'manage',
            icon: <SettingOutlined />,
            label: 'Quản lý',
            children: [
              ...(role === 'ADMIN'
                ? [{ key: '/accounts', icon: <KeyOutlined />, label: 'Tài khoản' }]
                : []),
              { key: '/notices', icon: <NotificationOutlined />, label: 'Thông báo' },
            ],
          },
        ]
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
      <Sider
        breakpoint="lg"
        collapsedWidth={64}
        theme="dark"
        onBreakpoint={setSiderCollapsed}
      >
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <img
            src={siderCollapsed ? logoMark : logoHouse}
            alt="Logo HOUSE"
            style={{
              height: 36,
              maxHeight: 40,
              maxWidth: 168,
              objectFit: 'contain',
            }}
          />
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          openKeys={openKeys}
          onOpenChange={setOpenKeys}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: token.colorBgContainer,
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            gap: 16,
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
          }}
        >
          <NoticeTicker />
          <NotificationBell />
          <Button
            type="text"
            aria-label={
              mode === 'dark' ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'
            }
            icon={mode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
            onClick={toggleMode}
          />
          <Dropdown menu={userMenu} placement="bottomRight" trigger={['click']}>
            <button
              type="button"
              aria-label="Tài khoản"
              style={{
                border: 'none',
                background: 'transparent',
                padding: 6,
                cursor: 'pointer',
              }}
            >
              <Space>
                <Avatar size="small" icon={<UserOutlined />} />
                <Typography.Text>{me?.fullName || me?.username}</Typography.Text>
              </Space>
            </button>
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
