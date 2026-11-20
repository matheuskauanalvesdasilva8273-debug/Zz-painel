package com.zz.painel

import android.app.*
import android.content.*
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.widget.*
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class MainActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            Toast.makeText(this, "Ative a permissão e reabra", Toast.LENGTH_LONG).show()
            finish(); return
        }
        startForegroundService(Intent(this, HackService::class.java))
        Toast.makeText(this, "ZZ Painel iniciado", Toast.LENGTH_SHORT).show()
        finish()
    }
}

class HackService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var panel: View
    private lateinit var espView: EspOverlay
    private val mem = MemoryManager()
    private val scanner = EntityScanner(mem)
    private val aimbot = AimbotEngine(mem)

    var speedOn = false; var jumpOn = false; var recoilOn = false
    var espOn = false; var aimbotOn = false; var flyOn = false
    var fovSize = 180f; var aimBone = "Cabeça"; var aimMode = "Ao Atirar"

    private val tabContents = arrayOfNulls<View>(3)

    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildEspOverlay()
        buildPanel()
        startLoops()
    }

    private fun buildEspOverlay() {
        espView = EspOverlay(this)
        espView.screenW = resources.displayMetrics.widthPixels.toFloat()
        espView.screenH = resources.displayMetrics.heightPixels.toFloat()
        val p = LayoutParams(MATCH_PARENT, MATCH_PARENT,
            TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT)
        wm.addView(espView, p)
    }

    private fun buildPanel() {
        val RED = Color.parseColor("#E53935")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            minimumWidth = dp(220)
        }
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(RED)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = Gravity.CENTER_VERTICAL
        }
        title.addView(TextView(this).apply {
            text = "⚡ ZZ PAINEL"
            setTextColor(Color.WHITE); textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        })
        title.addView(TextView(this).apply {
            text = "✕"; setTextColor(Color.WHITE); textSize = 16f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { stopSelf() }
        })
        root.addView(title)

        val flipper = FrameLayout(this)
        tabContents[0] = buildAimbotTab(RED)
        tabContents[1] = buildEspTab()
        tabContents[2] = buildMiscTab(RED)
        tabContents.forEach { flipper.addView(it!!.apply { visibility = View.GONE }) }
        tabContents[0]!!.visibility = View.VISIBLE
        root.addView(flipper)

        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = Gravity.CENTER
        }
        listOf("🎯", "👁", "⚙️").forEachIndexed { i, icon ->
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(RED)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                val lp = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                lp.setMargins(dp(3), 0, dp(3), 0)
                layoutParams = lp
            }
            btn.addView(TextView(this).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
            val idx = i
            btn.setOnClickListener {
                tabContents.forEach { it?.visibility = View.GONE }
                tabContents[idx]?.visibility = View.VISIBLE
            }
            tabBar.addView(btn)
        }
        root.addView(tabBar)
        panel = root
        setupDrag(title)
        val params = LayoutParams(WRAP_CONTENT, WRAP_CONTENT,
            TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = 30; y = 100
        }
        wm.addView(panel, params)
    }

    private fun buildAimbotTab(red: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        layout.addView(rowCheck("Ativar Aimbot", "Puxar Paredes",
            { aimbotOn = it }, {}))
        layout.addView(rowCheck("Exibir FOV", "Exibir Linha",
            { espView.showFov = it }, { espView.showLine = it }))
        layout.addView(TextView(this).apply {
            text = "Regular FOV"; setTextColor(Color.WHITE); textSize = 11f
        })
        val sliderVal = TextView(this).apply {
            text = "${fovSize.toInt()}.0"; setTextColor(Color.WHITE)
            setBackgroundColor(red); setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        layout.addView(sliderVal)
        layout.addView(SeekBar(this).apply {
            max = 360; progress = fovSize.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                    fovSize = v.toFloat(); sliderVal.text = "$v.0"; espView.fovRadius = v.toFloat()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        val bones = arrayOf("Cabeça", "Pescoço", "Peito", "Abdômen")
        layout.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@HackService,
                android.R.layout.simple_spinner_dropdown_item, bones)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    aimBone = bones[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })
        layout.addView(TextView(this).apply {
            text = "Tipo de Aimbot:"; setTextColor(Color.WHITE); textSize = 11f
        })
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Ao Atirar", "Ao Olhar").forEach { label ->
            rg.addView(RadioButton(this).apply {
                text = label; setTextColor(Color.WHITE); isChecked = label == "Ao Atirar"
                setOnCheckedChangeListener { _, v -> if (v) aimMode = label }
            })
        }
        layout.addView(rg)
        return layout
    }

    private fun buildEspTab(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        layout.addView(rowCheck("Ativar ESP", "Esconder Painel",
            { espOn = it },
            { panel.visibility = if (it) View.GONE else View.VISIBLE }))
        fun espCheck(label: String, onChange: (Boolean) -> Unit) {
            layout.addView(CheckBox(this).apply {
                text = label; setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, v -> onChange(v) }
            })
        }
        espCheck("ESP Linha")     { espView.showLine = it }
        espCheck("ESP Caixa")     { espView.showBox = it }
        espCheck("ESP Esqueleto") { espView.showSkel = it }
        espCheck("ESP Nome")      { espView.showName = it }
        espCheck("ESP Vida")      { espView.showHp = it }
        return layout
    }

    private fun buildMiscTab(red: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        layout.addView(rowCheck("Speed", "No Recoil",
            { speedOn = it }, { recoilOn = it }))
        layout.addView(rowCheck("Super Jump", "Voar",
            { jumpOn = it }, { flyOn = it }))
        fun redBtn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; setTextColor(Color.WHITE)
            setBackgroundColor(red); textSize = 11f
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(redBtn("Salvar") { saveConfig() })
        row.addView(redBtn("Carregar") { loadConfig() })
        layout.addView(row)
        layout.addView(redBtn("Destruir Hack (BYPASS)") { stopSelf() })
        return layout
    }

    private fun rowCheck(l1: String, l2: String,
                          c1: (Boolean) -> Unit, c2: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(l1 to c1, l2 to c2).forEach { (label, cb) ->
            row.addView(CheckBox(this).apply {
                text = label; setTextColor(Color.WHITE); textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, v -> cb(v) }
            })
        }
        return row
    }

    private fun setupDrag(handle: View) {
        var ix = 0f; var iy = 0f
        handle.setOnTouchListener { _, e ->
            val p = panel.layoutParams as LayoutParams
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = e.rawX - p.x; iy = e.rawY - p.y; true }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (e.rawX - ix).toInt(); p.y = (e.rawY - iy).toInt()
                    wm.updateViewLayout(panel, p); true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun saveConfig() {
        getSharedPreferences("zz", Context.MODE_PRIVATE).edit().apply {
            putBoolean("speed", speedOn); putBoolean("esp", espOn)
            putBoolean("aim", aimbotOn); putFloat("fov", fovSize); apply()
        }
        Toast.makeText(this, "Config salva", Toast.LENGTH_SHORT).show()
    }

    private fun loadConfig() {
        val p = getSharedPreferences("zz", Context.MODE_PRIVATE)
        speedOn = p.getBoolean("speed", false); espOn = p.getBoolean("esp", false)
        aimbotOn = p.getBoolean("aim", false); fovSize = p.getFloat("fov", 180f)
        Toast.makeText(this, "Config carregada", Toast.LENGTH_SHORT).show()
    }

    private fun startLoops() {
        Thread {
            while (true) {
                try {
                    val p = mem.getFFPid(); if (p == -1) { Thread.sleep(2000); continue }
                    if (speedOn)  mem.writeFloat(p, mem.speedAddr,  20.0f)
                    if (jumpOn)   mem.writeFloat(p, mem.jumpAddr,   50.0f)
                    if (recoilOn) mem.writeFloat(p, mem.recoilAddr,  0.0f)
                    if (flyOn)    mem.writeFloat(p, mem.gravityAddr, 0.0f)
                    Thread.sleep(80)
                } catch (e: Exception) { Thread.sleep(500) }
            }
        }.also { it.isDaemon = true }.start()

        Thread {
            while (true) {
                try {
                    val p = mem.getFFPid(); if (p == -1) { Thread.sleep(2000); continue }
                    if (espOn) {
                        val entities = scanner.scan(p)
                        espView.post { espView.enemies = entities; espView.invalidate() }
                        if (aimbotOn && entities.isNotEmpty()) {
                            val nearest = entities.minByOrNull { it.dist }!!
                            aimbot.snap(p, nearest)
                        }
                    }
                    Thread.sleep(50)
                } catch (e: Exception) { Thread.sleep(300) }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun buildNotification(): Notification {
        val ch = "zz"
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel(ch, "ZZ Painel",
                NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, ch)
            .setContentTitle("ZZ Painel ativo")
            .setSmallIcon(android.R.drawable.ic_menu_compass).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { wm.removeView(panel) }
        runCatching { wm.removeView(espView) }
    }
}

class EspOverlay(context: Context) : View(context) {
    var enemies: List<EspEntity> = emptyList()
    var showBox = true; var showLine = false; var showSkel = false
    var showName = false; var showHp = true; var showFov = false
    var fovRadius = 180f; var screenW = 1080f; var screenH = 1920f

    private val boxP  = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val lineP = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    private val hpP   = Paint().apply { style = Paint.Style.FILL }
    private val txtP  = Paint().apply { color = Color.WHITE; textSize = 26f; isAntiAlias = true }
    private val fovP  = Paint().apply { color = Color.parseColor("#88FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 2f }

    override fun onDraw(canvas: Canvas) {
        if (showFov) canvas.drawCircle(screenW / 2, screenH / 2, fovRadius, fovP)
        for (e in enemies) {
            val x = e.sx; val y = e.sy; val h = e.boxH; val w = h * 0.4f
            if (showBox)  canvas.drawRect(x - w, y - h, x + w, y, boxP)
            if (showLine) canvas.drawLine(screenW / 2, screenH, x, y, lineP)
            if (showHp) {
                hpP.color = Color.rgb(((1f - e.hp / 100f) * 255).toInt(), ((e.hp / 100f) * 255).toInt(), 0)
                canvas.drawRect(x - w - 8f, y - h * (e.hp / 100f), x - w - 4f, y, hpP)
            }
            if (showName) canvas.drawText("${e.dist.toInt()}m", x - w, y - h - 6f, txtP)
        }
    }
}

data class EspEntity(val sx: Float, val sy: Float, val boxH: Float,
                     val hp: Float, val dist: Float,
                     val wx: Float, val wy: Float, val wz: Float)

class EntityScanner(private val mem: MemoryManager) {
    private val ENTITY_BASE = 0x1234560L
    private val OFF_X = 0x90L; private val OFF_Y = 0x94L; private val OFF_Z = 0x98L
    private val OFF_HP = 0x1A0L; private val VP_ADDR = 0x2345670L; private val MAX_ENT = 60

    fun scan(pid: Int): List<EspEntity> {
        val sw = mem.screenW; val sh = mem.screenH
        val vp = FloatArray(16) { i -> mem.readFloat(pid, VP_ADDR + i * 4) }
        val cx = mem.readFloat(pid, mem.camXAddr)
        val cy = mem.readFloat(pid, mem.camYAddr)
        val cz = mem.readFloat(pid, mem.camZAddr)
        val result = mutableListOf<EspEntity>()
        for (i in 0 until MAX_ENT) {
            val obj = mem.readLong(pid, ENTITY_BASE + i * 8); if (obj == 0L) continue
            val px = mem.readFloat(pid, obj + OFF_X)
            val py = mem.readFloat(pid, obj + OFF_Y)
            val pz = mem.readFloat(pid, obj + OFF_Z)
            val hp = mem.readFloat(pid, obj + OFF_HP)
            if (hp <= 0f || hp > 220f) continue
            val dx = px - cx; val dy = py - cy; val dz = pz - cz
            val dist = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
            if (dist > 400f) continue
            val screen = worldToScreen(px, py, pz, vp, sw, sh) ?: continue
            result.add(EspEntity(screen.first, screen.second,
                (1f / dist * sh * 60f).coerceIn(20f, 400f), hp, dist, px, py, pz))
        }
        return result
    }

    private fun worldToScreen(wx: Float, wy: Float, wz: Float,
                               m: FloatArray, sw: Float, sh: Float): Pair<Float, Float>? {
        val cx = wx * m[0] + wy * m[4] + wz * m[8]  + m[12]
        val cy = wx * m[1] + wy * m[5] + wz * m[9]  + m[13]
        val cw = wx * m[3] + wy * m[7] + wz * m[11] + m[15]
        if (cw <= 0.1f) return null
        return Pair((cx / cw + 1f) * 0.5f * sw, (1f - cy / cw) * 0.5f * sh)
    }
}

class AimbotEngine(private val mem: MemoryManager) {
    private val YAW_ADDR   = 0x3456780L
    private val PITCH_ADDR = 0x3456784L

    fun snap(pid: Int, target: EspEntity) {
        val cx = mem.readFloat(pid, mem.camXAddr)
        val cy = mem.readFloat(pid, mem.camYAddr)
        val cz = mem.readFloat(pid, mem.camZAddr)
        val dx = target.wx - cx; val dy = target.wy - cy; val dz = target.wz - cz
        val yaw   = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
        val pitch = Math.toDegrees(atan2((-dy).toDouble(),
            sqrt((dx * dx + dz * dz).toDouble()))).toFloat()
        mem.writeFloat(pid, YAW_ADDR, yaw)
        mem.writeFloat(pid, PITCH_ADDR, pitch)
    }
}

class MemoryManager {
    var speedAddr   = 0L; var jumpAddr    = 0L
    var recoilAddr  = 0L; var gravityAddr = 0L
    var camXAddr    = 0L; var camYAddr    = 0L; var camZAddr = 0L
    var screenW     = 1080f; var screenH  = 1920f

    fun getFFPid(): Int {
        for (pkg in listOf("com.dts.freefireth", "com.dts.freefiremax")) {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "pidof $pkg"))
            val r = p.inputStream.bufferedReader().readLine()?.trim()?.toIntOrNull()
            if (r != null && r > 0) return r
        }
        return -1
    }

    fun readFloat(pid: Int, addr: Long): Float {
        if (addr == 0L) return 0f
        val p = Runtime.getRuntime().exec(
            arrayOf("su", "-c", "dd if=/proc/$pid/mem bs=4 skip=${addr / 4} count=1 2>/dev/null"))
        val b = p.inputStream.readBytes().take(4).toByteArray()
        return if (b.size == 4) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).float else 0f
    }

    fun readLong(pid: Int, addr: Long): Long {
        if (addr == 0L) return 0L
        val p = Runtime.getRuntime().exec(
            arrayOf("su", "-c", "dd if=/proc/$pid/mem bs=8 skip=${addr / 8} count=1 2>/dev/null"))
        val b = p.inputStream.readBytes().take(8).toByteArray()
        return if (b.size == 8) ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long else 0L
    }

    fun writeFloat(pid: Int, addr: Long, value: Float) {
        if (addr == 0L) return
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        Runtime.getRuntime().exec(arrayOf("su", "-c",
            "printf '\\x${hex.chunked(2).joinToString("\\x")}'" +
            " | dd of=/proc/$pid/mem bs=4 seek=${addr / 4} conv=notrunc 2>/dev/null"))
    }
}
