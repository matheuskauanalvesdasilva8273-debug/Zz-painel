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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class MainActivity : Activity() {
    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            Toast.makeText(this, "Ative a permissão e reabra", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        startForegroundService(Intent(this, HackService::class.java))
        Toast.makeText(this, "ZZ Painel iniciado", Toast.LENGTH_SHORT).show()
        finish()
    }
}

class HackService : Service() {
    private var wm: WindowManager? = null
    private var panel: View? = null
    private var espView: EspOverlay? = null

    private val mem = MemoryManager()
    private val scanner = EntityScanner(mem)
    private val aimbot = AimbotEngine(mem)
    private val running = AtomicBoolean(false)

    @Volatile var speedOn = false
    @Volatile var jumpOn = false
    @Volatile var recoilOn = false
    @Volatile var espOn = false
    @Volatile var aimbotOn = false
    @Volatile var flyOn = false

    @Volatile var fovSize = 180f
    @Volatile var aimBone = "Cabeça"
    @Volatile var aimMode = "Ao Atirar"

    private val tabContents = arrayOfNulls<View>(3)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        syncScreenMetrics()
        buildEspOverlay()
        buildPanel()

        running.set(true)
        startLoops()
    }

    private fun syncScreenMetrics() {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        mem.screenW = w
        mem.screenH = h
        espView?.apply {
            screenW = w
            screenH = h
            fovRadius = fovSize
            invalidate()
        }
    }

    private fun buildEspOverlay() {
        val overlay = EspOverlay(this).apply {
            screenW = mem.screenW
            screenH = mem.screenH
            fovRadius = fovSize
        }
        val params = LayoutParams(
            MATCH_PARENT,
            MATCH_PARENT,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        wm?.addView(overlay, params)
        espView = overlay
    }

    private fun buildPanel() {
        val red = Color.parseColor("#E53935")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            minimumWidth = dp(220)
        }

        val title = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(red)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            gravity = Gravity.CENTER_VERTICAL
        }
        title.addView(TextView(this).apply {
            text = "⚡ ZZ PAINEL"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER
        })
        title.addView(TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener { stopSelf() }
        })
        root.addView(title)

        val flipper = FrameLayout(this)
        tabContents[0] = buildAimbotTab(red)
        tabContents[1] = buildEspTab()
        tabContents[2] = buildMiscTab(red)
        tabContents.forEachIndexed { idx, v ->
            v?.visibility = if (idx == 0) View.VISIBLE else View.GONE
            flipper.addView(v)
        }
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
                setBackgroundColor(red)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                val lp = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                lp.setMargins(dp(3), 0, dp(3), 0)
                layoutParams = lp
            }
            btn.addView(TextView(this).apply {
                text = icon
                textSize = 18f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            })
            btn.setOnClickListener {
                tabContents.forEachIndexed { idx, view ->
                    view?.visibility = if (idx == i) View.VISIBLE else View.GONE
                }
            }
            tabBar.addView(btn)
        }
        root.addView(tabBar)

        panel = root
        setupDrag(title)

        val params = LayoutParams(
            WRAP_CONTENT,
            WRAP_CONTENT,
            TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 100
        }
        wm?.addView(panel, params)
    }

    private fun buildAimbotTab(red: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        layout.addView(rowCheck(
            "Ativar Aimbot", "Puxar Paredes",
            { aimbotOn = it },
            { /* placeholder para lógica de parede */ }
        ))
        layout.addView(rowCheck(
            "Exibir FOV", "Exibir Linha",
            { espView?.showFov = it; espView?.postInvalidate() },
            { espView?.showLine = it; espView?.postInvalidate() }
        ))
        layout.addView(TextView(this).apply {
            text = "Regular FOV"
            setTextColor(Color.WHITE)
            textSize = 11f
        })
        val sliderVal = TextView(this).apply {
            text = "${fovSize.toInt()}.0"
            setTextColor(Color.WHITE)
            setBackgroundColor(red)
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        layout.addView(sliderVal)
        layout.addView(SeekBar(this).apply {
            max = 360
            progress = fovSize.toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, v: Int, fromUser: Boolean) {
                    val radius = v.toFloat()
                    fovSize = radius
                    sliderVal.text = "$v.0"
                    espView?.fovRadius = radius
                    espView?.postInvalidate()
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })

        val bones = arrayOf("Cabeça", "Pescoço", "Peito", "Abdômen")
        layout.addView(Spinner(this).apply {
            adapter = ArrayAdapter(
                this@HackService,
                android.R.layout.simple_spinner_dropdown_item,
                bones
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    aimBone = bones[pos]
                }

                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })

        layout.addView(TextView(this).apply {
            text = "Tipo de Aimbot:"
            setTextColor(Color.WHITE)
            textSize = 11f
        })
        val rg = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf("Ao Atirar", "Ao Olhar").forEach { label ->
            rg.addView(RadioButton(this).apply {
                text = label
                setTextColor(Color.WHITE)
                isChecked = label == aimMode
                setOnCheckedChangeListener { _, checked ->
                    if (checked) aimMode = label
                }
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
        layout.addView(rowCheck(
            "Ativar ESP", "Esconder Painel",
            { enabled ->
                espOn = enabled
                if (!enabled) {
                    espView?.post {
                        espView?.enemies = emptyList()
                        espView?.invalidate()
                    }
                }
            },
            { hidden -> panel?.visibility = if (hidden) View.GONE else View.VISIBLE }
        ))

        fun espCheck(label: String, onChange: (Boolean) -> Unit) {
            layout.addView(CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setOnCheckedChangeListener { _, checked ->
                    onChange(checked)
                    espView?.postInvalidate()
                }
            })
        }

        espCheck("ESP Linha") { espView?.showLine = it }
        espCheck("ESP Caixa") { espView?.showBox = it }
        espCheck("ESP Esqueleto") { espView?.showSkel = it }
        espCheck("ESP Nome") { espView?.showName = it }
        espCheck("ESP Vida") { espView?.showHp = it }
        return layout
    }

    private fun buildMiscTab(red: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }
        layout.addView(rowCheck("Speed", "No Recoil", { speedOn = it }, { recoilOn = it }))
        layout.addView(rowCheck("Super Jump", "Voar", { jumpOn = it }, { flyOn = it }))

        fun redBtn(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(red)
            textSize = 11f
            setOnClickListener { onClick() }
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(redBtn("Salvar") { saveConfig() })
        row.addView(redBtn("Carregar") { loadConfig() })
        layout.addView(row)
        layout.addView(redBtn("Destruir Hack (BYPASS)") { stopSelf() })
        return layout
    }

    private fun rowCheck(
        l1: String,
        l2: String,
        c1: (Boolean) -> Unit,
        c2: (Boolean) -> Unit
    ): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(l1 to c1, l2 to c2).forEach { (label, cb) ->
            row.addView(CheckBox(this).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, checked -> cb(checked) }
            })
        }
        return row
    }

    private fun setupDrag(handle: View) {
        var ix = 0f
        var iy = 0f
        handle.setOnTouchListener { _, event ->
            val currentPanel = panel ?: return@setOnTouchListener false
            val p = currentPanel.layoutParams as? LayoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = event.rawX - p.x
                    iy = event.rawY - p.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = (event.rawX - ix).toInt()
                    p.y = (event.rawY - iy).toInt()
                    wm?.updateViewLayout(currentPanel, p)
                    true
                }
                else -> false
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun saveConfig() {
        getSharedPreferences("zz", Context.MODE_PRIVATE).edit().apply {
            putBoolean("speed", speedOn)
            putBoolean("jump", jumpOn)
            putBoolean("recoil", recoilOn)
            putBoolean("fly", flyOn)
            putBoolean("esp", espOn)
            putBoolean("aim", aimbotOn)
            putFloat("fov", fovSize)
            putString("bone", aimBone)
            putString("mode", aimMode)
            apply()
        }
        Toast.makeText(this, "Config salva", Toast.LENGTH_SHORT).show()
    }

    private fun loadConfig() {
        val p = getSharedPreferences("zz", Context.MODE_PRIVATE)
        speedOn = p.getBoolean("speed", false)
        jumpOn = p.getBoolean("jump", false)
        recoilOn = p.getBoolean("recoil", false)
        flyOn = p.getBoolean("fly", false)
        espOn = p.getBoolean("esp", false)
        aimbotOn = p.getBoolean("aim", false)
        fovSize = p.getFloat("fov", 180f)
        aimBone = p.getString("bone", "Cabeça") ?: "Cabeça"
        aimMode = p.getString("mode", "Ao Atirar") ?: "Ao Atirar"

        espView?.fovRadius = fovSize
        espView?.postInvalidate()
        Toast.makeText(this, "Config carregada", Toast.LENGTH_SHORT).show()
    }

    private fun startLoops() {
        // Loop 1: modificadores simples de memória
        Thread {
            while (running.get()) {
                try {
                    syncScreenMetrics()
                    val pid = mem.getTargetPid()
                    if (pid == -1) {
                        Thread.sleep(2000)
                        continue
                    }
                    if (speedOn) mem.writeFloat(pid, mem.speedAddr, 20.0f)
                    if (jumpOn) mem.writeFloat(pid, mem.jumpAddr, 50.0f)
                    if (recoilOn) mem.writeFloat(pid, mem.recoilAddr, 0.0f)
                    if (flyOn) mem.writeFloat(pid, mem.gravityAddr, 0.0f)
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    Thread.sleep(500)
                }
            }
        }.also { it.isDaemon = true }.start()

        // Loop 2: leitura de entidades, ESP e Aimbot independentes
        Thread {
            while (running.get()) {
                try {
                    val pid = mem.getTargetPid()
                    if (pid == -1) {
                        espView?.post {
                            espView?.enemies = emptyList()
                            espView?.invalidate()
                        }
                        Thread.sleep(2000)
                        continue
                    }

                    val shouldScan = espOn || aimbotOn
                    val entities = if (shouldScan) scanner.scan(pid) else emptyList()

                    if (espOn) {
                        espView?.post {
                            espView?.enemies = entities
                            espView?.invalidate()
                        }
                    }

                    if (aimbotOn && entities.isNotEmpty()) {
                        aimbot.update(
                            pid = pid,
                            entities = entities,
                            fovRadius = fovSize,
                            bone = aimBone,
                            mode = aimMode
                        )
                    }

                    Thread.sleep(80)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                    Thread.sleep(300)
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun buildNotification(): Notification {
        val channelId = "zz"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                "ZZ Painel",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("ZZ Painel ativo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    override fun onDestroy() {
        running.set(false)
        panel?.let { runCatching { wm?.removeView(it) } }
        espView?.let { runCatching { wm?.removeView(it) } }
        panel = null
        espView = null
        wm = null
        super.onDestroy()
    }
}

class EspOverlay(context: Context) : View(context) {
    @Volatile var enemies: List<EspEntity> = emptyList()
    @Volatile var showBox = true
    @Volatile var showLine = false
    @Volatile var showSkel = false
    @Volatile var showName = false
    @Volatile var showHp = true
    @Volatile var showFov = false
    @Volatile var fovRadius = 180f
    @Volatile var screenW = 1080f
    @Volatile var screenH = 1920f

    private val boxP = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }
    private val lineP = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val hpP = Paint().apply { style = Paint.Style.FILL }
    private val txtP = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        isAntiAlias = true
    }
    private val fovP = Paint().apply {
        color = Color.parseColor("#88FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = screenW / 2f
        val cy = screenH / 2f

        if (showFov) {
            canvas.drawCircle(cx, cy, fovRadius, fovP)
        }

        for (e in enemies) {
            val x = e.sx
            val y = e.sy
            val h = e.boxH
            val w = h * 0.4f

            if (showBox) {
                canvas.drawRect(x - w, y - h, x + w, y, boxP)
            }
            if (showLine) {
                canvas.drawLine(cx, screenH, x, y, lineP)
            }
            if (showHp) {
                val ratio = (e.hp / 100f).coerceIn(0f, 1f)
                hpP.color = Color.rgb(
                    ((1f - ratio) * 255).toInt(),
                    (ratio * 255).toInt(),
                    0
                )
                canvas.drawRect(x - w - 8f, y - h * ratio, x - w - 4f, y, hpP)
            }
            if (showName) {
                canvas.drawText("${e.dist.toInt()}m", x - w, y - h - 6f, txtP)
            }
        }
    }
}

data class EspEntity(
    val sx: Float,
    val sy: Float,
    val boxH: Float,
    val hp: Float,
    val dist: Float,
    val wx: Float,
    val wy: Float,
    val wz: Float
)

class EntityScanner(private val mem: MemoryManager) {
    // Endereços de exemplo: substitua pelos endereços reais resolvidos em execução
   
