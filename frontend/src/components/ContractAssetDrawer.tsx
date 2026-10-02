import {
  App as AntApp,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Modal,
  Select,
  Table,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery } from '@tanstack/react-query'
import dayjs, { type Dayjs } from 'dayjs'
import { useState } from 'react'
import { api, getErrorMessage } from '../api/client'
import { CODE_CELL, formatDate, formatVnd, MONEY_CELL } from '../utils/format'
import {
  CATEGORY_LABELS,
  CONDITION_META,
  type AssetCategory,
  type AssetCondition,
} from '../utils/asset'

interface AssetItem {
  id: number
  assetId: number
  code: string
  name: string
  category: AssetCategory
  price: number
  condition: AssetCondition
  handoverCondition: AssetCondition
  returnCondition: AssetCondition | null
  handoverNote: string | null
  returnedAt: string | null
  repairCount: number
  repairCost: number
}

interface AssetSummary {
  total: number
  brokenCount: number
  needsRepairCount: number
  repairCost: number
}

interface ContractAssetList {
  items: AssetItem[]
  summary: AssetSummary
}

interface ReturnFormValues {
  returnCondition: AssetCondition
  returnedAt?: Dayjs
}

export default function ContractAssetDrawer({
  contractId,
  canManage,
  onClose,
}: {
  contractId: number | null
  canManage: boolean
  onClose: () => void
}) {
  const { message } = AntApp.useApp()
  const [returning, setReturning] = useState<AssetItem | null>(null)
  const [form] = Form.useForm<ReturnFormValues>()

  const listQuery = useQuery({
    enabled: contractId != null,
    queryKey: ['contract-assets', contractId],
    queryFn: async () =>
      (await api.get<ContractAssetList>(`/contracts/${contractId}/assets`)).data,
  })

  const returnMutation = useMutation({
    mutationFn: async (values: ReturnFormValues) => {
      if (!contractId || !returning) {
        throw new Error('Thiếu thông tin thu hồi')
      }
      return api.post<AssetItem>(`/contracts/${contractId}/assets/${returning.assetId}/return`, {
        returnCondition: values.returnCondition,
        returnedAt: values.returnedAt ? values.returnedAt.format('YYYY-MM-DD') : null,
      })
    },
    onSuccess: () => {
      message.success('Đã thu hồi tài sản.')
      setReturning(null)
      form.resetFields()
      listQuery.refetch()
    },
    onError: (error) => message.error(getErrorMessage(error)),
  })

  const openReturn = (row: AssetItem) => {
    setReturning(row)
    form.setFieldsValue({ returnCondition: row.condition, returnedAt: dayjs() })
  }

  const columns = [
    {
      title: 'Mã',
      dataIndex: 'code',
      key: 'code',
      width: 130,
      render: (value: string) => <span style={CODE_CELL}>{value}</span>,
    },
    {
      title: 'Tên tài sản',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: 'Nhóm',
      dataIndex: 'category',
      key: 'category',
      width: 130,
      render: (value: AssetCategory) => CATEGORY_LABELS[value],
    },
    {
      title: 'Khi bàn giao',
      dataIndex: 'handoverCondition',
      key: 'handoverCondition',
      width: 120,
      render: (value: AssetCondition) => (
        <Tag color={CONDITION_META[value].color}>{CONDITION_META[value].label}</Tag>
      ),
    },
    {
      title: 'Hiện tại',
      dataIndex: 'condition',
      key: 'condition',
      width: 110,
      render: (value: AssetCondition) => (
        <Tag color={CONDITION_META[value].color}>{CONDITION_META[value].label}</Tag>
      ),
    },
    {
      title: 'Ngày thu hồi',
      dataIndex: 'returnedAt',
      key: 'returnedAt',
      width: 120,
      render: (value: string | null) => formatDate(value) || '-',
    },
    {
      title: 'Giá mua',
      dataIndex: 'price',
      key: 'price',
      width: 130,
      align: 'right' as const,
      render: (value: number) => <span style={MONEY_CELL}>{formatVnd(value)}</span>,
    },
    ...(canManage
      ? [
          {
            title: 'Thao tác',
            key: 'actions',
            width: 130,
            render: (_: unknown, row: AssetItem) =>
              row.returnedAt ? (
                <Typography.Text type="secondary">Đã thu hồi</Typography.Text>
              ) : (
                <Button size="small" onClick={() => openReturn(row)}>
                  Thu hồi
                </Button>
              ),
          },
        ]
      : []),
  ]

  const summary = listQuery.data?.summary

  return (
    <Drawer
      title="Tài sản bàn giao"
      open={contractId != null}
      onClose={onClose}
      width="min(880px, calc(100vw - 48px))"
      destroyOnClose
    >
      {listQuery.isError && (
        <Empty
          style={{ margin: '48px 0' }}
          description={`Không tải được danh sách tài sản: ${getErrorMessage(listQuery.error)}`}
        >
          <Button onClick={() => listQuery.refetch()}>Thử lại</Button>
        </Empty>
      )}

      {!listQuery.isError && summary && (
        <Descriptions size="small" column={4} style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Bàn giao">{summary.total}</Descriptions.Item>
          <Descriptions.Item label="Hỏng">{summary.brokenCount}</Descriptions.Item>
          <Descriptions.Item label="Cần sửa">{summary.needsRepairCount}</Descriptions.Item>
          <Descriptions.Item label="Chi phí sửa chữa trong kỳ">
            <span style={MONEY_CELL}>{formatVnd(summary.repairCost)}</span>
          </Descriptions.Item>
        </Descriptions>
      )}

      {!listQuery.isError && (
        <Table<AssetItem>
          rowKey="id"
          size="small"
          loading={listQuery.isLoading}
          columns={columns}
          dataSource={listQuery.data?.items}
          scroll={{ x: 900 }}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Hợp đồng chưa bàn giao tài sản nào, chọn Sửa hợp đồng để chọn tài sản"
              />
            ),
          }}
          pagination={false}
        />
      )}

      <Modal
        title="Thu hồi tài sản"
        open={returning != null}
        onCancel={() => setReturning(null)}
        onOk={() => form.submit()}
        confirmLoading={returnMutation.isPending}
        okText="Thu hồi"
        cancelText="Huỷ"
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary" style={{ marginTop: 0 }}>
          {returning ? `${returning.code} - ${returning.name}` : ''}
        </Typography.Paragraph>
        <Form form={form} layout="vertical" onFinish={(values) => returnMutation.mutate(values)}>
          <Form.Item
            label="Tình trạng khi thu hồi"
            name="returnCondition"
            rules={[{ required: true, message: 'Vui lòng chọn tình trạng khi thu hồi' }]}
          >
            <Select
              placeholder="Chọn tình trạng"
              options={Object.entries(CONDITION_META).map(([key, meta]) => ({
                value: key,
                label: meta.label,
              }))}
            />
          </Form.Item>
          <Form.Item label="Ngày thu hồi" name="returnedAt">
            <DatePicker style={{ width: '100%' }} format="DD/MM/YYYY" placeholder="Chọn ngày" />
          </Form.Item>
        </Form>
      </Modal>
    </Drawer>
  )
}
