package com.fraudguard.demo
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.utils.ColorTemplate
import app.FusionEngine
import app.TouchAgent
import app.TypingAgent
import app.UsageAgent
import android.app.Activity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fraudguard.demo.db.AppDb
import com.fraudguard.demo.db.ScoreLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardActivity : Activity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusionTitle: TextView
    private lateinit var riskLevel: TextView
    private lateinit var touchPieChart: PieChart
    private lateinit var typingPieChart: PieChart
    private lateinit var usagePieChart: PieChart
    private lateinit var fusionHistoryChart: LineChart
   

    private val fusionEngine = FusionEngine()
    private val touchAgent = TouchAgent()
    private val typingAgent = TypingAgent()
    private val usageAgent = UsageAgent()

    private val fusionHistory = ArrayList<Float>()
    private val HISTORY_SIZE = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        fusionTitle = findViewById(R.id.fusionTitle)
        riskLevel = findViewById(R.id.riskLevel)
        touchPieChart = findViewById(R.id.touchPieChart)
        typingPieChart = findViewById(R.id.typingPieChart)
        usagePieChart = findViewById(R.id.usagePieChart)
        fusionHistoryChart = findViewById(R.id.fusionHistoryChart)
       

        touchAgent.start()
        typingAgent.start()
        usageAgent.start()

        setupPieChart(touchPieChart, "TouchAgent", ColorTemplate.COLORFUL_COLORS[0])
        setupPieChart(typingPieChart, "TypingAgent", ColorTemplate.COLORFUL_COLORS[1])
        setupPieChart(usagePieChart, "UsageAgent", ColorTemplate.COLORFUL_COLORS[2])
        setupLineChart(fusionHistoryChart)

        handler.post(updateCharts)

}

    private fun setupPieChart(chart: PieChart, label: String, color: Int) {
        chart.setUsePercentValues(true)
        chart.description.isEnabled = false
        chart.setHoleColor(android.graphics.Color.TRANSPARENT)
        chart.setTransparentCircleAlpha(110)
        chart.setEntryLabelColor(android.graphics.Color.WHITE)
        chart.setEntryLabelTextSize(12f)
        chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
        chart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
        chart.legend.orientation = Legend.LegendOrientation.VERTICAL
        chart.legend.textColor = android.graphics.Color.WHITE
        chart.legend.isEnabled = false
    }

    private fun setupLineChart(chart: LineChart) {
        chart.description.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)
        chart.setBackgroundColor(android.graphics.Color.DKGRAY)
        chart.axisLeft.textColor = android.graphics.Color.WHITE
        chart.axisRight.isEnabled = false
        chart.xAxis.textColor = android.graphics.Color.WHITE
        chart.xAxis.setDrawGridLines(false)
        chart.axisLeft.setDrawGridLines(true)
        chart.axisLeft.gridColor = android.graphics.Color.LTGRAY
        chart.legend.isEnabled = false
    }

    private val updateCharts = object : Runnable {
        override fun run() {
            val touch = touchAgent.getResult().score.toFloat()
            val typing = typingAgent.getResult().score.toFloat()
            val usage = usageAgent.getResult().score.toFloat()
            val fusionResult = fusionEngine.fuseScores(touch.toDouble(), typing.toDouble(), usage.toDouble())
            val fusion = fusionResult.finalScore.toFloat()
            val risk = fusionResult.riskLevel.name

            if (fusionHistory.size >= HISTORY_SIZE) fusionHistory.removeAt(0)
            fusionHistory.add(fusion)

            setPieChartData(touchPieChart, touch, "TouchAgent", ColorTemplate.COLORFUL_COLORS[0])
            setPieChartData(typingPieChart, typing, "TypingAgent", ColorTemplate.COLORFUL_COLORS[1])
            setPieChartData(usagePieChart, usage, "UsageAgent", ColorTemplate.COLORFUL_COLORS[2])
            setLineChartData(fusionHistoryChart, fusionHistory)

            riskLevel.text = "Risk Level: $risk"
            riskLevel.setTextColor(
                when (risk) {
                    "HIGH" -> android.graphics.Color.RED
                    "MEDIUM" -> android.graphics.Color.YELLOW
                    else -> android.graphics.Color.GREEN
                }
            )

            handler.postDelayed(this, 1000)
        }

        private fun setPieChartData(chart: PieChart, score: Float, label: String, color: Int) {
            val entries = ArrayList<PieEntry>()
            entries.add(PieEntry(score * 100, label))
            entries.add(PieEntry(100 - score * 100, "Other"))
            val dataSet = PieDataSet(entries, label)
            dataSet.colors = listOf(color, android.graphics.Color.DKGRAY)
            dataSet.valueTextColor = android.graphics.Color.WHITE
            dataSet.valueTextSize = 16f
            val data = PieData(dataSet)
            chart.data = data
            chart.invalidate()
        }

        private fun setLineChartData(chart: LineChart, history: List<Float>) {
            val entries = ArrayList<Entry>()
            for (i in history.indices) {
                entries.add(Entry(i.toFloat(), history[i]))
            }
            val dataSet = LineDataSet(entries, "Fusion Score")
            dataSet.color = ColorTemplate.getHoloBlue()
            dataSet.setDrawCircles(false)
            dataSet.lineWidth = 3f
            dataSet.setDrawValues(false)
            dataSet.setDrawFilled(true)
            dataSet.fillColor = ColorTemplate.getHoloBlue()
            dataSet.fillAlpha = 80
            val data = LineData(dataSet)
            chart.data = data
            chart.invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateCharts)
        touchAgent.stop()
        typingAgent.stop()
        usageAgent.stop()
    }
}

   

   
   