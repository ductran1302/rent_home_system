import {
  App as AntApp,
  Button,
  Empty,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { api, getErrorMessage } from '../../api/client'

interface HouseOption {
  id: number
  name: string
  code: string
}

interface RoomRow {
  id: number
  roomNumber: string
}

interface FeeType {
  id: number
  code: string
  name: string
}

interface MeterRow {
  id: number
  roomId: number
  feeTypeId: number
  feeCode: string
  period: string
  reading: number
}

interface MeterCell {
  meterId: number | null
  value: number | null
  dirty?: boolean
}

export default function MeterModal({
  open,
  onClose,
  period,
}: {
  open: boolean
  onClose: () => void
  period: string
}) {
  const { message } = AntApp.useApp()
  const queryClient = useQueryClient()
  const [houseId, setHouseId] = useState<number | null>(null)
  const [cells, setCells] = useState<Record<string, MeterCell>>({})

  const housesQuery = useQuery({
    enabled: open,
    queryKey: ['houses'],
    queryFn: async () => (await api.get<HouseOption[]>('/houses')).data,
  })

  const feeTypesQuery = useQuery({
    enabled: open,
    queryKey: ['fee-types'],
    queryFn: async () => (await api.get<FeeType[]>('/billing/fee-types')).data,
  })

  const roomsQuery = useQuery({
    enabled: open && houseId != null,
    queryKey: ['rooms', houseId],
    queryFn: async () => (await api.get<RoomRow[]>(`/rooms/by-house/${houseId}`)).data,
  })

  const metersQuery = useQuery({
    enabled: open,
    queryKey: ['meters', period],
    queryFn: async () => (await api.get<MeterRow[]>('/billing/meters', { params: { period } })).data,
  })

  const usageTypes = useMemo(
    () => (feeTypesQuery.data ?? []).filter((type) => type.code === 'DIEN' || type.code === 'NUOC'),
    [feeTypesQuery.data],
  )

  useEffect(() => {
    if (!roomsQuery.data || !metersQuery.data || usageTypes.length === 0) {
      return
    }
    const rooms = roomsQuery.data
    const meters = metersQuery.data
    setCells((prev) => {
      const next: Record<string, MeterCell> = {}
      for (const room of rooms) {
        for (const type of usageTypes) {
          const key = `${room.id}-${type.id}`
          const meter = meters.find(
            (item) => item.roomId === room.id && item.feeTypeId === type.id,
          )
          const serverCell: MeterCell = {
            meterId: meter ? meter.id : null,
            value: meter ? meter.reading : null,
          }
          const existing = prev[key]
          next[key] =
            existing?.dirty && existing.value !== serverCell.value
              ? { ...serverCell, value: existing.value, dirty: true }
              : serverCell
        }
      }
      return next
    })
  }, [roomsQuery.data, metersQuery.data, usageTypes])

  const saveMutation = useMutation({
    mutationFn: async ({
      roomId,
      feeTypeId,
    }: {
      roomId: number
      feeTypeId: number
    }): Promise<MeterRow> => {
      const cell = cells[`${roomId}-${feeTypeId}`]
      if (!cell || cell.value == null) {
        throw new Error('Chưa nhập chỉ số')
      }
      if (cell.meterId) {
        const response = await api.put<MeterRow>(`/billing/meters/${cell.meterId}`, {
          reading: cell.value,
        })
        return response.data
      }
      const response = await api.post<MeterRow>('/billing/meters', {
        roomId,
        feeTypeId,
        period,
        reading: cell.value,
      })
      return response.data
    },
    onSuccess: (data, variables) => {
      const key = `${variables.roomId}-${variables.feeTypeId}`
      setCells((prev) => ({
        ...prev,
        [key]: { meterId: data.id, value: data.reading, dirty: false },
      }))
      message.success('Đã lưu chỉ số')
      queryClient.invalidateQueries({ queryKey: ['meters', period] })
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const columns = [
    { title: 'Phòng', dataIndex: 'roomNumber', key: 'roomNumber', width: 120 },
    ...usageTypes.map((type) => ({
      title: `Chỉ số ${type.name}`,
      key: type.code,
      render: (_: unknown, room: RoomRow) => {
        const cell = cells[`${room.id}-${type.id}`]
        return (
          <InputNumber
            min={0}
            style={{ width: 140 }}
            value={cell?.value ?? null}
            placeholder={cell?.meterId ? 'Đã ghi' : 'Chưa ghi'}
            onChange={(value) =>
              setCells((prev) => ({
                ...prev,
                [`${room.id}-${type.id}`]: {
                  meterId: prev[`${room.id}-${type.id}`]?.meterId ?? null,
                  value,
                  dirty: true,
                },
              }))
            }
          />
        )
      },
    })),
    {
      title: 'Thao tác',
      key: 'actions',
      width: 200,
      render: (_: unknown, room: RoomRow) => (
        <Space>
          {usageTypes.map((type) => (
            <Button
              key={type.id}
              size="small"
              loading={saveMutation.isPending}
              onClick={() => saveMutation.mutate({ roomId: room.id, feeTypeId: type.id })}
            >
              Lưu {type.code === 'DIEN' ? 'điện' : 'nước'}
            </Button>
          ))}
        </Space>
      ),
    },
  ]

  return (
    <Modal
      title={`Nhập chỉ số điện nước kỳ ${period}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={720}
    >
      <Space style={{ marginBottom: 16, width: '100%' }} wrap>
        <Select
          allowClear
          placeholder="Chọn nhà"
          style={{ width: 260 }}
          value={houseId}
          onChange={(value) => setHouseId(value ?? null)}
          loading={housesQuery.isLoading}
          options={(housesQuery.data ?? []).map((house) => ({
            value: house.id,
            label: `${house.name} (${house.code})`,
          }))}
        />
        <Typography.Text type="secondary">
          Chỉ số kỳ trước sẽ tự dùng để tính tiền khi tạo hóa đơn
        </Typography.Text>
      </Space>

      {houseId == null ? (
        <Empty description="Hãy chọn nhà để nhập chỉ số" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Table<RoomRow>
          rowKey="id"
          size="small"
          loading={roomsQuery.isLoading || metersQuery.isLoading}
          columns={columns}
          dataSource={roomsQuery.data}
          pagination={false}
          locale={{
            emptyText: (
              <Empty description="Nhà này chưa có phòng" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ),
          }}
        />
      )}
    </Modal>
  )
}
