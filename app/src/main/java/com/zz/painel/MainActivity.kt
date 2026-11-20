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
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

// ─── MainActivity ─────────────────────────────────────────────────────────────

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

// ─── HackService ──────────────────────────────────────────────────────────────

class HackService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var panel: View
    private lateinit var espView: EspOverlay
    private val mem = MemoryManager()
    private val scanner = EntityScanner(mem)
    private val aimbot = AimbotEngine(mem)

    // Estados
    var speedOn    = false
    var jumpOn     = false
    var recoilOn   = false
    var espOn      = false
    var aimbotOn   = false
    var aimkillOn  = false
    var flyOn      = false
    var teleportOn = false
    var fovSize    = 180f
    var aimBone    = "Cabeça"
    var aimMode    = "Ao Atirar"

    // Tabs
    private var currentTab = 0
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

    // ── ESP Overlay ───────────────────────────────────────────────────────────

    private fun buildEspOverlay() {
        espView = EspOverlay(this)
        espView.screenW = resources.displayMetrics.widthPixels.toFloat()
        espView.screenH = resources.displayMetrics.heightPixels.toFloat()
        val p = LayoutParams(MATCH_PARENT, MATCH_PARENT,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT)
        wm.addView(espView, p)
    }

    // ── Panel UI ──────────────────────────────────────────────────────────────

    private fun buildPanel() {
        val RED   = Color.parseColor("#E53935")
        val BLACK = Color.parseColor("#CC000000")
        val WHITE = Color.WHITE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BLACK)
            minimumWidth = dp(220)
        }

        // ── Título ────────────────────────────────────────────────────────────
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(RED)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(this).apply {
            text = "⚡ ZZ PAINEL"
            setTextColor(WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            gravity = android.view.Gravity.CENTER
        }
        val btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(WHITE)
            textSize = 16f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { stopSelf() }
        }
        title.addView(tvTitle)
        title.addView(btnClose)
        root.addView(title)

        // ── Conteúdo das abas ─────────────────────────────────────────────────
        val flipper = FrameLayout(this)
        tabContents[0] = buildAimbotTab(RED)
        tabContents[1] = buildEspTab(RED)
        tabContents[2] = buildMiscTab(RED)
        tabContents.forEach { flipper.addView(it!!.apply { visibility = View.GONE }) }
        tabContents[0]!!.visibility = View.VISIBLE
        root.addView(flipper)

        // ── Barra de abas ─────────────────────────────────────────────────────
        val tabBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = android.view.Gravity.CENTER
        }

        val tabIcons = listOf("🎯", "👁", "⚙️", "📋")
        val tabLabels = listOf("Aimbot", "ESP", "Misc", "Info")
        tabIcons.forEachIndexed { i, icon ->
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(RED)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                val lp = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                lp.setMargins(dp(3), 0, dp(3), 0)
                layoutParams = lp
            }
            btn.addView(TextView(this).apply {
                text = icon
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
            val idx = if (i < 3) i else 0
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
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            x = 30; y = 100
        }
        wm.addView(panel, params)
    }

    // ── Aba Aimbot ────────────────────────────────────────────────────────────

    private fun buildAimbotTab(red: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }

        // Row 1
        layout.addView(rowCheck("Ativar Aimbot", "Puxar em Paredes",
            { aimbotOn = it }, { }))

        // Row 2
        layout.addView(rowCheck("Exibir FOV", "Exibir Linha",
            { espView.showFov = it }, { espView.showLine = it }))

        // Slider FOV
        layout.addView(TextView(this).apply {
            text = "Regular FOV"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, dp(6), 0, 0)
        })
        val sliderVal = TextView(this).apply {
            text = "${fovSize.toInt()}.0"
            setTextColor(Color.WHITE)
            textSize = 11f
            setBackgroundColor(red)
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        val slider = SeekBar(this).apply {
            max = 360; progress = fovSize.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, u: Boolean) {
                    fovSize = v.toFloat()
                    sliderVal.text = "$v.0"
                    espView.fovRadius = v.toFloat()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(sliderVal)
        layout.addView(slider)

        // Dropdown osso
        layout.addView(TextView(this).apply {
            text = "Puxada"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, dp(4), 0, 0)
        })
        val bones = arrayOf("Cabeça", "Pescoço", "Peito", "Abdômen")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@HackService,
                android.R.layout.simple_spinner_dropdown_item, bones)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    aimBone = bones[pos]
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        layout.addView(spinner)

        // Radio tipo
        layout.addView(TextView(this).apply {
            text = "Tipo de Aimbot:"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(0, dp(6), 0, 0)
        })
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Ao Atirar", "Ao Olhar").forEach { label ->
            rg.addView(RadioButton(this).apply {
                text = label
                setTextColor(Color.WHITE)
                isChecked = label == "Ao Atirar"
                setOnCheckedChangeListener { _, v -> if (v) aimMode = label }
            })
        }
        layout.addView(rg)

        return layout
    }

    // ── Aba ESP ───────────────────────────────────────────────────────────────

    private fun buildEspTab(red: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        layout.addView(rowCheck("Ativar ESP", "Esconder Painel",
            { espOn = it }, { panel.visibility = if (it) View.GONE else View.VISIBLE }))
        fun espCheck(label: String, onChange: (Boolean) -> Unit) =
            CheckBox(this).apply {
                text = label; setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, v -> onChange(v) }
                layout.addView(this)
            }
        espCheck("ESP Linha")   { espView.showLine   = it }
        espCheck("ESP Caixa")   { espView.showBox    = it }
        espCheck("ESP Esqueleto") { espView.showSkel = it }
        espCheck("ESP Nome")    { espView.showName   = it }
        espCheck("ESP Vida")    { espView.showHp     = it }
        return layout
    }

    // ── Aba Misc ──────────────────────────────────────────────────────────────

    private fun buildMiscTab(red: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        layout.addView(rowCheck3("AimKill", "Teleport 8m", "Voar",
            { aimkillOn = it }, { teleportOn = it }, { flyOn = it }))

        fun redBtn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label; setTextColor(Color.WHITE)
            setBackgroundColor(red); textSize = 11f
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setOnClickListener { onClick() }
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(redBtn("Speed (Beta)") { speedOn = !speedOn })
        row1.addView(redBtn("No Recoil")   { recoilOn = !recoilOn })
        layout.addView(row1)

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(redBtn("Salvar Config")    { saveConfig() })
        row2.addView(redBtn("Carregar Config")  { loadConfig() })
        layout.addView(row2)

        layout.addView(redBtn("Destruir Hack (BYPASS)") { stopSelf() })
        return layout
    }

    // ── Helpers UI ────────────────────────────────────────────────────────────

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

    private fun rowCheck3(l1: String, l2: String, l3: String,
                           c1: (Boolean) -> Unit, c2: (Boolean) -> Unit,
                           c3: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(l1 to c1, l2 to c2, l3 to c3).forEach { (label, cb) ->
            row.addView(CheckBox(this).apply {
                text = label; setTextColor(Color.WHITE); textSize = 9f
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

    // ── Config ────────────────────────────────────────────────────────────────

    private fun saveConfig() {
        val prefs = getSharedPreferences("zz_cfg", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("speed", speedOn); putBoolean("esp", espOn)
            putBoolean("aimbot", aimbotOn); putFloat("fov", fovSize)
            apply()
        }
        Toast.makeText(this, "Config salva", Toast.LENGTH_SHORT).show()
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("zz_cfg", Context.MODE_PRIVATE)
        speedOn  = prefs.getBoolean("speed", false)
        espOn    = prefs.getBoolean("esp", false)
        aimbotOn = prefs.getBoolean("aimbot", false)
        fovSize  = prefs.getFloat("fov", 180f)
        Toast.makeText(this, "Config carregada", Toast.LENGTH_SHORT).show()
    }

    // ── Loops ─────────────────────────────────────────────────────────────────

    private fun startLoops() {
        val pid by lazy { mem.getFFPid() }

        // Loop de memória — speed, jump, recoil
        Thread {
            while (true) {
                try {
                    val p = mem.getFFPid(); if (p == -1) { Thread.sleep(2000); return@Thread }
                    if (speedOn)   mem.writeFloat(p, mem.speedAddr,   20.0f)
                    if (jumpOn)    mem.writeFloat(p, mem.jumpAddr,    50.0f)
                    if (recoilOn)  mem.writeFloat(p, mem.recoilAddr,   0.0f)
                    if (flyOn)     mem.writeFloat(p, mem.gravityAddr,  0.0f)
                    Thread.sleep(80)
                } catch (e: Exception) { Thread.sleep(500) }
            }
        }.also { it.isDaemon = true }.start()

        // Loop ESP + Aimbot
        Thread {
            while (true) {
                try {
                    val p = mem.getFFPid(); if (p == -1) { Thread.sleep(2000); return@Thread }
                    if (espOn) {
                        val entities = scanner.scan(p)
                        espView.post { espView.enemies = entities; espView.invalidate() }
                        if (aimbotOn && entities.isNotEmpty()) {
                            val nearest = entities.minByOrNull { it.distance }!!
                            aimbot.snap(p, nearest)
                        }
                    }
                    Thread.sleep(50)
                } catch (e: Exception) { Thread.sleep(300) }
            }
        }.also { it.isDaemon = true }.start()
    }

    // ── Notificação ───────────────────────────────────────────────────────────

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

// ─── EspOverlay ───────────────────────────────────────────────────────────────

class EspOverlay(context: Context) : View(context) {
    var enemies: List<EspEntity> = emptyList()
    var showBox  = true;  var showLine  = false
    var showSkel = false; var showName  = false; var showHp = true
    var showFov  = false; var fovRadius = 180f
    var screenW  = 1080f; var screenH   = 1920f

    private val boxP  = Paint().apply { color = Color.RED;   style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val lineP = Paint().apply { color = Color.CYAN;  style = Paint.Style.STROKE; strokeWidth = 2f; isAntiAlias = true }
    private val hpP   = Paint().apply { style = Paint.Style.FILL }
    private val txtP  = Paint().apply { color = Color.WHITE; textSize = 26f; isAntiAlias = true }
    private val fovP  = Paint().apply { color = Color.parseColor("#88FFFFFF"); style = Paint.Style.STROKE; strokeWidth = 2f }

    override fun onDraw(canvas: Canvas) {
        if (showFov) canvas.drawCircle(screenW/2, screenH/2, fovRadius, fovP)
        for (e in enemies) {
            val x = e.sx; val y = e.sy; val h = e.boxH; val w = h * 0.4f
            if (showBox)  canvas.drawRect(x-w, y-h, x+w, y, boxP)
            if (showLine) canvas.drawLine(screenW/2, screenH, x, y, lineP)
            if (showHp) {
                hpP.color = hpColor(e.hp)
                canvas.drawRect(x-w-8f, y - h*(e.hp/100f), x-w-4f, y, hpP)
            }
            if (showName) canvas.drawText("${e.dist.toInt()}m", x-w, y-h-6f, txtP)
        }
    }

    private fun hpColor(hp: Float): Int {
        val r = ((1f - hp/100f)*255).toInt()
        val g = ((hp/100f)*255).toInt()
        return Color.rgb(r, g, 0)
    }
}

data class EspEntity(val sx: Float, val sy: Float, val boxH: Float,
                     val hp: Float, val dist: Float,
                     val wx: Float, val wy: Float, val wz: Float)

// ─── EntityScanner ────────────────────────────────────────────────────────────

class EntityScanner(private val mem: MemoryManager) {
    // Re-derive com GG ou il2cppdumper após cada update
    private val ENTITY_BASE  = 0x1234560L
    private val OFF_X        = 0x90L
    private val OFF_Y        = 0x94L
    private val OFF_Z        = 0x98L
    private val OFF_HP       = 0x1A0L
    private val VP_ADDR      = 0x2345670L
    private val MAX_ENT      = 60

    fun scan(pid: Int): List<EspEntity> {
        val sw = mem.screenW; val sh = mem.screenH
        val vp = FloatArray(16) { i -> mem.readFloat(pid, VP_ADDR + i*4) }
        val cx = mem.readFloat(pid, mem.camXAddr)
        val cy = mem.readFloat(pid, mem.camYAddr)
        val cz = mem.readFloat(pid, mem.camZAddr)
        val result = mutableListOf<EspEntity>()
        for (i in 0 until MAX_ENT) {
            val obj = mem.readLong(pid, ENTITY_BASE + i*8); if (obj == 0L) continue
            val px = mem.readFloat(pid, obj + OFF_X)
            val py = mem.readFloat(pid, obj + OFF_Y)
            val pz = mem.readFloat(pid, obj + OFF_Z)
