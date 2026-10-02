const VND_FORMATTER = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

export function formatVnd(value: number | bigint | null | undefined): string {
  if (value == null) {
    return '0 ₫'
  }
  return VND_FORMATTER.format(value)
}

const NUMBER_FORMATTERS = new Map<number, Intl.NumberFormat>()

const DEFAULT_NUMBER_FORMATTER = new Intl.NumberFormat('vi-VN')

function getNumberFormatter(maximumFractionDigits: number): Intl.NumberFormat {
  let formatter = NUMBER_FORMATTERS.get(maximumFractionDigits)
  if (!formatter) {
    formatter = new Intl.NumberFormat('vi-VN', { maximumFractionDigits })
    NUMBER_FORMATTERS.set(maximumFractionDigits, formatter)
  }
  return formatter
}

export function formatNumber(
  value: number | null | undefined,
  maximumFractionDigits?: number,
): string {
  if (value == null) {
    return '-'
  }
  if (maximumFractionDigits === undefined) {
    return DEFAULT_NUMBER_FORMATTER.format(value)
  }
  return getNumberFormatter(maximumFractionDigits).format(value)
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleDateString('vi-VN')
}

const pad2 = (value: number): string => String(value).padStart(2, '0')

export const CODE_CELL = { fontFamily: 'monospace' } as const

export const MONEY_CELL = { fontVariantNumeric: 'tabular-nums' } as const

export function formatPeriod(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const [year, month] = value.split('-')
  if (!year || !month) {
    return value
  }
  return `${month}/${year}`
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${pad2(date.getDate())}/${pad2(date.getMonth() + 1)}/${date.getFullYear()} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}
