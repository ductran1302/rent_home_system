import { DeleteOutlined, LoadingOutlined, PlusOutlined } from '@ant-design/icons'
import { Button, Image, Popconfirm, Skeleton, theme, Typography, Upload } from 'antd'
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { PHOTO_MAX, PHOTO_STAGE_LABELS, type AssetPhoto } from '../utils/asset'

const THUMB = 84

export function PhotoThumb({ url, alt, size = THUMB }: { url: string; alt: string; size?: number }) {
  const { token } = theme.useToken()
  const [src, setSrc] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let objectUrl: string | null = null
    let cancelled = false
    setSrc(null)
    setFailed(false)
    const path = url.startsWith('/api/') ? url.slice(4) : url
    api
      .get<Blob>(path, { responseType: 'blob' })
      .then((response) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(response.data)
        setSrc(objectUrl)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [url])

  if (failed) {
    return (
      <div
        role="img"
        aria-label={`${alt}, không tải được ảnh`}
        style={{
          width: size,
          height: size,
          border: `1px dashed ${token.colorBorder}`,
          borderRadius: token.borderRadius,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: 4,
          textAlign: 'center',
        }}
      >
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
          Lỗi ảnh
        </Typography.Text>
      </div>
    )
  }

  if (!src) {
    return <Skeleton.Image active style={{ width: size, height: size }} />
  }

  return (
    <Image
      width={size}
      height={size}
      src={src}
      alt={alt}
      style={{ objectFit: 'cover', borderRadius: token.borderRadius }}
    />
  )
}

interface PhotoUploadProps {
  label: string
  photos: AssetPhoto[]
  loading?: boolean
  uploading?: boolean
  disabled?: boolean
  disabledHint?: string
  onUpload: (file: File) => void
  onDelete: (photo: AssetPhoto) => void
}

export default function PhotoUpload({
  label,
  photos,
  loading,
  uploading,
  disabled,
  disabledHint,
  onUpload,
  onDelete,
}: PhotoUploadProps) {
  const { token } = theme.useToken()
  const full = photos.length >= PHOTO_MAX
  const empty = !loading && photos.length === 0

  const altOf = (photo: AssetPhoto) =>
    photo.stage ? PHOTO_STAGE_LABELS[photo.stage] : photo.originalName || label

  return (
    <div style={{ marginBottom: token.margin }}>
      <Typography.Text strong>{label}</Typography.Text>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: token.marginXS,
          marginTop: token.marginXS,
          alignItems: 'flex-start',
        }}
      >
        {loading
          ? null
          : photos.map((photo) => (
              <div key={photo.id} style={{ position: 'relative', width: THUMB }}>
                <PhotoThumb url={photo.contentUrl} alt={altOf(photo)} />
                <Popconfirm
                  title="Xoá ảnh này?"
                  okText="Xoá"
                  cancelText="Huỷ"
                  onConfirm={() => onDelete(photo)}
                >
                  <Button
                    type="text"
                    danger
                    size="small"
                    aria-label="Xoá ảnh"
                    icon={<DeleteOutlined />}
                    style={{
                      position: 'absolute',
                      top: 2,
                      right: 2,
                      width: 24,
                      height: 24,
                      padding: 0,
                      background: token.colorBgElevated,
                    }}
                  />
                </Popconfirm>
              </div>
            ))}
        {loading && <Skeleton.Image active style={{ width: THUMB, height: THUMB }} />}
        {!disabled && !loading && !full && (
          <Upload
            accept="image/jpeg,image/png,image/webp"
            showUploadList={false}
            disabled={uploading}
            beforeUpload={(file) => {
              onUpload(file)
              return false
            }}
          >
            <div
              style={{
                width: THUMB,
                height: THUMB,
                border: `1px dashed ${token.colorBorder}`,
                borderRadius: token.borderRadius,
                background: token.colorBgLayout,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 4,
                cursor: uploading ? 'default' : 'pointer',
              }}
            >
              {uploading ? <LoadingOutlined /> : <PlusOutlined />}
              <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                Thêm ảnh
              </Typography.Text>
            </div>
          </Upload>
        )}
      </div>
      <div style={{ marginTop: 4 }}>
        {disabled && disabledHint ? (
          <Typography.Text type="secondary">{disabledHint}</Typography.Text>
        ) : empty ? (
          <Typography.Text type="secondary">
            Chưa có ảnh, bấm Thêm ảnh để đối chiếu tình trạng.
          </Typography.Text>
        ) : (
          <Typography.Text type="secondary">
            {photos.length}/{PHOTO_MAX} ảnh, mỗi ảnh tối đa 5MB (JPG, PNG hoặc WebP)
          </Typography.Text>
        )}
      </div>
    </div>
  )
}
