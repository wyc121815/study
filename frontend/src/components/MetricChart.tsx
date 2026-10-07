import { useEffect, useMemo, useRef } from 'react'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

import type { ChartSpec, ChartType } from '../charts/chartSpec'

// 按需注册，避免把整个 echarts 打进包里
echarts.use([
  LineChart,
  BarChart,
  PieChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  CanvasRenderer,
])

interface Props {
  spec: ChartSpec
  type: ChartType
  height?: number
}

export default function MetricChart({ spec, type, height = 340 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<echarts.ECharts | null>(null)

  const option = useMemo(() => buildOption(spec, type), [spec, type])

  useEffect(() => {
    if (!containerRef.current) return
    const chart = echarts.init(containerRef.current)
    chartRef.current = chart
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(containerRef.current)
    return () => {
      observer.disconnect()
      chart.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    chartRef.current?.setOption(option, true)
  }, [option])

  return <div ref={containerRef} className="chart" style={{ height }} />
}

function buildOption(spec: ChartSpec, type: ChartType): echarts.EChartsCoreOption {
  const axisLabel = spec.dense ? { rotate: 45, hideOverlap: true } : { hideOverlap: true }

  if (type === 'pie') {
    const values = spec.series[0]?.data ?? []
    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          type: 'pie',
          radius: ['38%', '68%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}\n{d}%' },
          data: spec.categories.map((name, index) => ({ name, value: values[index] ?? 0 })),
        },
      ],
    }
  }

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: type === 'bar' ? 'shadow' : 'line' } },
    legend: { bottom: 0, type: 'scroll' },
    grid: { left: 12, right: 20, top: 24, bottom: 48, containLabel: true },
    xAxis: {
      type: 'category',
      name: spec.categoryLabel,
      nameLocation: 'middle',
      nameGap: 28,
      data: spec.categories,
      axisLabel,
    },
    yAxis: { type: 'value', name: spec.valueLabel, scale: false },
    series: spec.series.map((item) => ({
      name: item.name,
      type,
      data: item.data,
      smooth: type === 'line',
      showSymbol: spec.categories.length <= 40,
      connectNulls: true,
      barMaxWidth: 36,
    })),
  }
}
