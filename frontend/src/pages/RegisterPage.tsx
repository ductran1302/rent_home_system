import { Alert, Button, Card, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api, getErrorMessage, getErrorStatus } from '../api/client'
import { useAuth } from '../auth/context'
import { USERNAME_PATTERN } from '../utils/validation'

interface RegisterFormValues {
  username: string
  password: string
  confirmPassword: string
}

export default function RegisterPage() {
  const { me, loading } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [form] = Form.useForm<RegisterFormValues>()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (!loading && me) {
    const from = (location.state as { from?: string } | null)?.from ?? '/'
    return <Navigate to={from} replace />
  }

  const onFinish = async (values: RegisterFormValues) => {
    setError(null)
    setSubmitting(true)
    try {
      await api.post('/auth/register', {
        username: values.username,
        password: values.password,
      })
      navigate('/login', { state: { registered: true } })
    } catch (err) {
      if (getErrorStatus(err) === 409) {
        form.setFields([
          { name: 'username', errors: [getErrorMessage(err)] },
        ])
      } else {
        setError(getErrorMessage(err))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
      }}
    >
      <Card style={{ width: '100%', maxWidth: 400 }}>
        <Typography.Title level={2} style={{ textAlign: 'center', marginTop: 0 }}>
          RuinHome
        </Typography.Title>
        <Typography.Paragraph style={{ textAlign: 'center' }} type="secondary">
          Đăng ký tài khoản chủ cho thuê
        </Typography.Paragraph>
        {error && (
          <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />
        )}
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ username: '', password: '', confirmPassword: '' }}
        >
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
            <Input autoComplete="username" autoFocus placeholder="nguoidung01" />
          </Form.Item>
          <Form.Item
            label="Mật khẩu"
            name="password"
            rules={[
              { required: true, message: 'Vui lòng nhập mật khẩu' },
              { min: 8, max: 32, message: 'Mật khẩu phải từ 8 đến 32 ký tự' },
            ]}
          >
            <Input.Password autoComplete="new-password" placeholder="Tối thiểu 8 ký tự" />
          </Form.Item>
          <Form.Item
            label="Nhập lại mật khẩu"
            name="confirmPassword"
            dependencies={['password']}
            rules={[
              { required: true, message: 'Vui lòng nhập lại mật khẩu' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('password') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('Mật khẩu nhập lại không khớp'))
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" placeholder="Nhập lại mật khẩu" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={submitting}>
            Đăng ký
          </Button>
        </Form>
        <Typography.Paragraph style={{ textAlign: 'center', marginTop: 16, marginBottom: 0 }}>
          <Link to="/login">Đã có tài khoản? Đăng nhập</Link>
        </Typography.Paragraph>
      </Card>
    </div>
  )
}
