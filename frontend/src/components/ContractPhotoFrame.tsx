import { PictureOutlined } from '@ant-design/icons'
import { theme, Typography } from 'antd'

export default function ContractPhotoFrame() {
  const { token } = theme.useToken()

  return (
    <div
      role="img"
      aria-label="Khung ảnh hợp đồng, chưa có ảnh"
      style={{
        marginTop: token.marginXS,
        height: 180,
        border: `1px dashed ${token.colorBorder}`,
        borderRadius: token.borderRadius,
        background: token.colorBgLayout,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: token.marginXS,
      }}
    >
      <PictureOutlined style={{ fontSize: 28, color: token.colorTextDescription }} />
      <Typography.Text type="secondary">Chưa có ảnh hợp đồng</Typography.Text>
    </div>
  )
}
