export type AssetCondition = 'GOOD' | 'USED' | 'NEEDS_REPAIR' | 'BROKEN'

export type AssetCategory =
  | 'GIUONG'
  | 'TU'
  | 'DIEU_HOA'
  | 'BINH_NONG_LANH'
  | 'TV'
  | 'TU_LANH'
  | 'BAN_GHE'
  | 'KHAC'

export type RepairStatus = 'PENDING' | 'DONE' | 'CANCELLED'

export const CONDITION_META: Record<AssetCondition, { label: string; color: string }> = {
  GOOD: { label: 'Tốt', color: 'green' },
  USED: { label: 'Đã dùng', color: 'blue' },
  NEEDS_REPAIR: { label: 'Cần sửa', color: 'orange' },
  BROKEN: { label: 'Hỏng', color: 'red' },
}

export const CATEGORY_LABELS: Record<AssetCategory, string> = {
  GIUONG: 'Giường',
  TU: 'Tủ',
  DIEU_HOA: 'Điều hòa',
  BINH_NONG_LANH: 'Bình nóng lạnh',
  TV: 'Tivi',
  TU_LANH: 'Tủ lạnh',
  BAN_GHE: 'Bàn ghế',
  KHAC: 'Khác',
}

export const REPAIR_STATUS_META: Record<RepairStatus, { label: string; color: string }> = {
  PENDING: { label: 'Đang xử lý', color: 'orange' },
  DONE: { label: 'Hoàn thành', color: 'green' },
  CANCELLED: { label: 'Đã huỷ', color: 'default' },
}

export type AssetPhotoStage = 'TRUOC' | 'SAU'

export const PHOTO_STAGE_LABELS: Record<AssetPhotoStage, string> = {
  TRUOC: 'Ảnh trước khi sửa',
  SAU: 'Ảnh sau khi sửa',
}

export const PHOTO_MAX = 5

export interface AssetPhoto {
  id: number
  originalName: string | null
  uploadedAt: string
  stage: AssetPhotoStage | null
  contentUrl: string
}
