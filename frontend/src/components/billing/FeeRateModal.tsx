import {
  App as AntApp,
  Button,
  Empty,
  Form,
  InputNumber,
  Modal,
  Skeleton,
  Table,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, getErrorMessage } from '../../api/client'
import { formatPeriod, formatVnd, MONEY_CELL } from '../../utils/format'

interface FeeType {
  id: number
  code: string
  name: string
  unit: string
}

interface FeeRate {
  id: number
  feeTypeId: number
  feeCode: string
  feeName: string
  unit: string
  period: string
  price: number
}

export default function FeeRateModal({
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
  const [editingType, setEditingType] = useState<FeeType | null>(null)
  const [form] = Form.useForm<{ price: number }>()

  const feeTypesQuery = useQuery({
    enabled: open,
    queryKey: ['fee-types'],
    queryFn: async () => (await api.get<FeeType[]>('/billing/fee-types')).data,
  })

  const ratesQuery = useQuery({
    enabled: open,
    queryKey: ['fee-rates'],
    queryFn: async () => (await api.get<FeeRate[]>('/billing/fee-rates')).data,
  })

  const saveMutation = useMutation({
    mutationFn: async (values: { feeTypeId: number; price: number }) =>
      api.put('/billing/fee-rates', { ...values, period }),
    onSuccess: () => {
      message.success('Đã lưu giá kỳ ' + formatPeriod(period) + '.')
      setEditingType(null)
      form.resetFields()
      queryClient.invalidateQueries({ queryKey: ['fee-rates'] })
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const rows = (feeTypesQuery.data ?? [])
    .filter((type) => type.code !== 'PHONG')
    .map((type) => {
      const rate = (ratesQuery.data ?? []).find(
        (item) => item.feeTypeId === type.id && item.period === period,
      )
      return { type, rate }
    })

  interface RateRow {
    type: FeeType
    rate: FeeRate | undefined
  }

  const columns = [
    {
      title: 'Khoản thu',
      key: 'name',
      render: (_: unknown, row: RateRow) => row.type.name,
    },
    {
      title: 'Đơn vị',
      key: 'unit',
      width: 100,
      render: (_: unknown, row: RateRow) => row.type.unit,
    },
    {
      title: `Giá kỳ ${formatPeriod(period)}`,
      key: 'price',
      width: 180,
      align: 'right' as const,
      render: (_: unknown, row: RateRow) =>
        row.rate ? <span style={MONEY_CELL}>{formatVnd(row.rate.price)}</span> : 'Chưa cấu hình',
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 110,
      render: (_: unknown, row: RateRow) => (
        <Button
          size="small"
          type="primary"
          onClick={() => {
            setEditingType(row.type)
            form.setFieldsValue({ price: row.rate?.price ?? 0 })
          }}
        >
          Sửa giá
        </Button>
      ),
    },
  ]

  return (
    <Modal
      title={`Cấu hình giá kỳ ${formatPeriod(period)}`}
      open={open}
      onCancel={onClose}
      footer={null}
      width={560}
    >
      {ratesQuery.isLoading ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : (
        <Table
          rowKey={(row) => row.type.id}
          size="small"
          columns={columns}
          dataSource={rows as RateRow[]}
          pagination={false}
          locale={{
            emptyText: <Empty description="Không có khoản thu" image={Empty.PRESENTED_IMAGE_SIMPLE} />,
          }}
        />
      )}

      <Modal
        title={editingType ? `Giá ${editingType.name} kỳ ${formatPeriod(period)}` : ''}
        open={editingType != null}
        onCancel={() => setEditingType(null)}
        onOk={() => form.submit()}
        confirmLoading={saveMutation.isPending}
        okText="Lưu"
        cancelText="Huỷ"
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) => {
            if (editingType) {
              saveMutation.mutate({ feeTypeId: editingType.id, price: values.price })
            }
          }}
        >
          <Form.Item
            label="Đơn giá (đồng)"
            name="price"
            rules={[{ required: true, message: 'Vui lòng nhập đơn giá' }]}
          >
            <InputNumber min={0} step={1000} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Modal>
  )
}
