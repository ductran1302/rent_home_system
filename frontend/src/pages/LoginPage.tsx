import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../api/client'
import { useAuth } from '../auth/context'

interface LoginFormValues {
  username: string
  password: string
}

export default function LoginPage() {
  const { login, me, loading } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!loading && me) {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }

  const onFinish = async (values: LoginFormValues) => {
    setError(null)
    setSubmitting(true)
    try {
      await login(values.username, values.password)
      const from = (location.state as { from?: string } | null)?.from ?? '/'
      navigate(from, { replace: true })
    } catch (err) {
      setError(getErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  const registered = Boolean((location.state as { registered?: boolean } | null)?.registered)

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 400 }}>
        <Typography.Title level={2} style={{ textAlign: 'center', marginTop: 0 }}>
          RuinHome
        </Typography.Title>
        <Typography.Paragraph style={{ textAlign: 'center' }} type="secondary">
          Đăng nhập để quản lý nhà cho thuê
        </Typography.Paragraph>
        {registered && (
          <Alert
            type="success"
            message="Đã tạo tài khoản chủ cho thuê. Đăng nhập để tiếp tục."
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}
        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={onFinish} initialValues={{ username: '', password: '' }}>
          <Form.Item
            label="Tên đăng nhập"
            name="username"
            rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}
          >
            <Input autoComplete="username" autoFocus placeholder="admin" />
          </Form.Item>
          <Form.Item
            label="Mật khẩu"
            name="password"
            rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}
          >
            <Input.Password autoComplete="current-password" placeholder="Mật khẩu" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            Đăng nhập
          </Button>
        </Form>
        <Typography.Paragraph style={{ textAlign: 'center', marginTop: 16, marginBottom: 0 }}>
          <Link to="/register">Chưa có tài khoản? Đăng ký</Link>
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
