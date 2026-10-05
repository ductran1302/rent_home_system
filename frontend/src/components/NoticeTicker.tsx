import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { theme } from 'antd'

interface NoticeRow {
  id: number
  title: string
  content: string
}

export default function NoticeTicker() {
  const { token } = theme.useToken()
  const boxRef = useRef<HTMLDivElement>(null)
  const [overflow, setOverflow] = useState(false)
  const [duration, setDuration] = useState(20)

  const activeQuery = useQuery({
    queryKey: ['notices', 'active'],
    queryFn: async () => (await api.get<NoticeRow[]>('/notices/active')).data,
    refetchInterval: 60_000,
  })

  const items = activeQuery.data ?? []
  const text = items.map((item) => `${item.title}: ${item.content}`).join('   ·   ')

  useEffect(() => {
    const box = boxRef.current
    if (!box) {
      return
    }
    const measure = () => {
      const track = box.firstElementChild as HTMLElement | null
      const first = track?.firstElementChild as HTMLElement | null
      if (!track || !first) {
        setOverflow(false)
        return
      }
      const isOverflow = first.offsetWidth > box.clientWidth
      setOverflow(isOverflow)
      if (isOverflow) {
        setDuration(Math.max(8, first.offsetWidth / 60))
      }
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(box)
    return () => observer.disconnect()
  }, [text])

  if (!text) {
    return null
  }

  const textStyle = {
    color: token.colorError,
    fontWeight: 600,
    flexShrink: 0,
  }

  return (
    <div
      ref={boxRef}
      aria-label="Thông báo quan trọng"
      style={{
        flex: 1,
        minWidth: 0,
        marginRight: 16,
        overflow: 'hidden',
        display: 'flex',
        alignItems: 'center',
      }}
    >
      <div
        className={overflow ? 'notice-ticker-track' : undefined}
        style={
          overflow
            ? {
                display: 'flex',
                whiteSpace: 'nowrap',
                width: 'max-content',
                ['--notice-ticker-duration' as string]: `${duration}s`,
              }
            : { display: 'flex', whiteSpace: 'nowrap', width: 'max-content' }
        }
      >
        <span style={{ ...textStyle, paddingRight: overflow ? 64 : 0 }}>{text}</span>
        {overflow && (
          <span aria-hidden="true" style={{ ...textStyle, paddingRight: 64 }}>
            {text}
          </span>
        )}
      </div>
    </div>
  )
}
