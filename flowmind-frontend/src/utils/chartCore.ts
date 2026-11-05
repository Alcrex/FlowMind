import { BarChart, HeatmapChart, LineChart, PieChart, RadarChart, SankeyChart } from 'echarts/charts'
import { GridComponent, LegendComponent, RadarComponent, TooltipComponent, VisualMapComponent, GraphicComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { init, use, type EChartsType } from 'echarts/core'

use([BarChart, HeatmapChart, LineChart, PieChart, RadarChart, SankeyChart, GridComponent, LegendComponent, RadarComponent, TooltipComponent, VisualMapComponent, GraphicComponent, CanvasRenderer])

export function initPie(element: HTMLElement): EChartsType { return init(element) }
export function initBar(element: HTMLElement): EChartsType { return init(element) }
export { init }
