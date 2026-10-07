import type { SqlQueryResult } from '../types'

/**
 * 从查询结果里推导出可画的图表。
 *
 * <p>P1 的指标还没有"输出契约"，所以这里按列的类型和名字做启发式推断：
 * 时间列当 X 轴、数值列当度量、其余非时间列当分组维度。等 P2 加上契约后，
 * 这份推断可以整体被契约覆盖，前端组件不用改。</p>
 */

export type ChartType = 'line' | 'bar' | 'pie'

export interface ChartSeries {
  name: string
  data: (number | null)[]
}

export interface ChartSpec {
  categories: string[]
  series: ChartSeries[]
  /** 建议的默认图表类型 */
  preferred: ChartType
  categoryLabel: string
  valueLabel: string
  /** 数据点太多时，X 轴只显示部分标签 */
  dense: boolean
}

type Cell = string | number | boolean | null

const NUMERIC_TYPE = /^(TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT|DECIMAL|NUMERIC|FLOAT|DOUBLE|REAL|NUMBER)/i
const TIME_TYPE = /^(DATE|DATETIME|TIMESTAMP|TIME|YEAR)/i
const TIME_NAME = /(date|time|日期|时间|月份|周|dt$)/i

const MAX_POINTS = 400

export function buildChartSpec(result: SqlQueryResult): ChartSpec | null {
  const { columns, rows } = result
  if (columns.length < 2 || rows.length === 0) return null

  const kinds = columns.map((column, index) => classify(column.typeName, column.name, rows, index))
  const timeIndex = kinds.findIndex((kind) => kind === 'time')
  const measureIndexes = kinds
    .map((kind, index) => (kind === 'measure' ? index : -1))
    .filter((index) => index >= 0)
  const dimensionIndexes = kinds
    .map((kind, index) => (kind === 'dimension' ? index : -1))
    .filter((index) => index >= 0)

  if (measureIndexes.length === 0) return null

  const limitedRows = rows.slice(0, MAX_POINTS)
  const dense = rows.length > MAX_POINTS

  // 有时间列：按时间画趋势；如果还有维度列，就按维度拆成多条线
  if (timeIndex >= 0) {
    const categories = unique(limitedRows.map((row) => formatCell(row[timeIndex])))
    const groupIndex = dimensionIndexes[0]
    const measureIndex = measureIndexes[0]
    const series = groupIndex === undefined
      ? measureIndexes.map((index) => ({
          name: label(columns[index].label, columns[index].name),
          data: alignByCategory(categories, limitedRows, timeIndex, index),
        }))
      : buildGroupedSeries(categories, limitedRows, timeIndex, groupIndex, measureIndex)

    return {
      categories,
      series,
      preferred: 'line',
      categoryLabel: label(columns[timeIndex].label, columns[timeIndex].name),
      valueLabel: label(columns[measureIndex].label, columns[measureIndex].name),
      dense,
    }
  }

  // 没有时间列：按维度做对比
  const categoryIndex = dimensionIndexes[0] ?? 0
  const categories = limitedRows.map((row) => formatCell(row[categoryIndex]))
  const series = measureIndexes.map((index) => ({
    name: label(columns[index].label, columns[index].name),
    data: limitedRows.map((row) => toNumber(row[index])),
  }))

  return {
    categories,
    series,
    preferred: 'bar',
    categoryLabel: label(columns[categoryIndex].label, columns[categoryIndex].name),
    valueLabel: label(columns[measureIndexes[0]].label, columns[measureIndexes[0]].name),
    dense,
  }
}

type ColumnKind = 'time' | 'measure' | 'dimension'

function classify(typeName: string, name: string, rows: Cell[][], index: number): ColumnKind {
  if (TIME_TYPE.test(typeName ?? '') || TIME_NAME.test(name ?? '')) return 'time'
  if (NUMERIC_TYPE.test(typeName ?? '')) return 'measure'
  // 类型名不可靠时（比如驱动没给出准确类型）按值兜底判断
  const sampled = rows.slice(0, 20).map((row) => row[index]).filter((value) => value !== null)
  if (sampled.length > 0 && sampled.every((value) => typeof value === 'number')) return 'measure'
  return 'dimension'
}

/** 一个维度在时间轴上的取值（缺失补 null，保证长度与 categories 对齐）。 */
function buildGroupedSeries(
  categories: string[],
  rows: Cell[][],
  timeIndex: number,
  groupIndex: number,
  measureIndex: number,
): ChartSeries[] {
  const groups = [...new Set(rows.map((row) => formatCell(row[groupIndex])))].slice(0, 12)
  return groups.map((group) => {
    const data: (number | null)[] = new Array(categories.length).fill(null)
    for (const row of rows) {
      if (formatCell(row[groupIndex]) !== group) continue
      const position = categories.indexOf(formatCell(row[timeIndex]))
      if (position >= 0) data[position] = toNumber(row[measureIndex])
    }
    return { name: group, data }
  })
}

function alignByCategory(
  categories: string[],
  rows: Cell[][],
  categoryIndex: number,
  valueIndex: number,
): (number | null)[] {
  const data: (number | null)[] = new Array(categories.length).fill(null)
  for (const row of rows) {
    const position = categories.indexOf(formatCell(row[categoryIndex]))
    if (position >= 0) data[position] = toNumber(row[valueIndex])
  }
  return data
}

function unique(values: string[]): string[] {
  return [...new Set(values)]
}

function label(columnLabel: string, columnName: string): string {
  return columnLabel || columnName
}

function formatCell(value: Cell): string {
  if (value === null || value === undefined) return '—'
  const text = String(value)
  // 日期时间统一收敛到日期，避免 X 轴标签过长
  return text.length > 19 ? text.slice(0, 10) : text
}

function toNumber(value: Cell): number | null {
  if (value === null || value === undefined || value === '') return null
  const parsed = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(parsed) ? parsed : null
}
