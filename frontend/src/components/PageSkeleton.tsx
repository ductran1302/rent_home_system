import { Skeleton } from 'antd'

export default function PageSkeleton() {
  return (
    <div>
      <Skeleton.Input active size="small" style={{ width: 200 }} />
      <div style={{ marginTop: 24 }}>
        <Skeleton active paragraph={{ rows: 10 }} />
      </div>
    </div>
  )
}
