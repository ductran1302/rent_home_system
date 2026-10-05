import { Skeleton } from 'antd'

export default function PageSkeleton() {
  return (
    <div style={{ display: 'grid', gap: 24 }}>
      <Skeleton.Input active size="small" style={{ width: 200 }} />
      <div style={{ display: 'grid', gap: 12 }}>
        {Array.from({ length: 8 }, (_, index) => (
          <Skeleton.Input key={index} active block size="small" />
        ))}
      </div>
    </div>
  )
}
