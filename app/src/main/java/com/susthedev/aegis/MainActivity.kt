package com.susthedev.aegis

import android.animation.LayoutTransition
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.susthedev.aegis.hook.Discovery

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge-to-edge layout so content draws under status & navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply Monet dynamic colors from system wallpaper
        DynamicColors.applyToActivitiesIfAvailable(application)
        super.onCreate(savedInstanceState)

        if (XposedPrefs.getOrNull() == null) {
            XposedPrefs.addListener {
                runOnUiThread { recreate() }
            }
        }

        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val rootScroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutTransition = LayoutTransition()
        }
        rootScroll.addView(container)

        // Adjust padding for status bar and navigation bar insets
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            container.setPadding(
                pad + insets.left,
                pad + insets.top,
                pad + insets.right,
                pad + insets.bottom
            )
            windowInsets
        }

        // Header Title
        val headerTitle = TextView(this).apply {
            text = "Aegis Policy Manager"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
        }
        container.addView(headerTitle)

        val headerSub = TextView(this).apply {
            text = "Consented development-permission grants for system_server"
            textSize = 13f
            setPadding(0, (4 * density).toInt(), 0, (20 * density).toInt())
        }
        container.addView(headerSub)

        // Load installed packages declaring aegis.devgrant
        val eligibleApps = getEligibleApps()

        if (eligibleApps.isEmpty()) {
            val emptyCard = MaterialCardView(this).apply {
                radius = 28 * density
                strokeWidth = 0
                setCardBackgroundColor(Color.parseColor("#1F2430"))
                val inner = LinearLayout(this@MainActivity).apply {
                    setPadding(pad * 2, pad * 2, pad * 2, pad * 2)
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }
                val emptyTv = TextView(this@MainActivity).apply {
                    text = "No installed applications found declaring the 'aegis.devgrant' manifest meta-data.\n\nAdd <meta-data android:name=\"aegis.devgrant\" android:value=\"...\" /> to your target app's manifest."
                    textSize = 14f
                    gravity = Gravity.CENTER
                }
                inner.addView(emptyTv)
                addView(inner)
            }
            container.addView(emptyCard)
        } else {
            val countTv = TextView(this).apply {
                text = "Eligible Applications (${eligibleApps.size}):"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, (12 * density).toInt())
            }
            container.addView(countTv)

            eligibleApps.forEach { (pkgInfo, perms) ->
                val card = createAppCard(pkgInfo, perms, pad, density)
                container.addView(card)
            }
        }

        setContentView(rootScroll)
    }

    private fun getEligibleApps(): List<Pair<PackageInfo, List<String>>> {
        val pm = packageManager
        val packages = runCatching { pm.getInstalledPackages(PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        val result = mutableListOf<Pair<PackageInfo, List<String>>>()

        for (pkg in packages) {
            val ai = pkg.applicationInfo?.let { existingAi ->
                if (existingAi.metaData == null) {
                    runCatching { pm.getApplicationInfo(pkg.packageName, PackageManager.GET_META_DATA) }.getOrDefault(existingAi)
                } else existingAi
            } ?: continue

            val perms = Discovery.getDeclaredPermissions(ai, pm)
            if (perms.isNotEmpty()) {
                result.add(pkg to perms)
            }
        }
        return result
    }

    private fun createAppCard(
        pkgInfo: PackageInfo,
        perms: List<String>,
        pad: Int,
        density: Float
    ): View {
        val pm = packageManager
        val ai = pkgInfo.applicationInfo

        val card = MaterialCardView(this).apply {
            radius = 28 * density
            strokeWidth = 0
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, (12 * density).toInt())
            }
            layoutParams = params
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            layoutTransition = LayoutTransition()
        }

        // Top Header Row: App Icon + Name / Pkg + Chevron Expand Indicator
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val appIcon = ImageView(this).apply {
            val iconDrawable = ai?.loadIcon(pm)
            setImageDrawable(iconDrawable)
            val iconSize = (44 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                setMargins(0, 0, (14 * density).toInt(), 0)
            }
        }
        topRow.addView(appIcon)

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val appNameTv = TextView(this).apply {
            text = ai?.loadLabel(pm) ?: pkgInfo.packageName
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
        textLayout.addView(appNameTv)

        val pkgNameTv = TextView(this).apply {
            text = pkgInfo.packageName
            textSize = 12f
        }
        textLayout.addView(pkgNameTv)

        topRow.addView(textLayout)

        val permCountTv = TextView(this).apply {
            text = "${perms.size} perms ▾"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setPadding((8 * density).toInt(), 0, 0, 0)
        }
        topRow.addView(permCountTv)

        cardContent.addView(topRow)

        // Collapsible permission rows container
        val permsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
            setPadding(0, (14 * density).toInt(), 0, 0)
            layoutTransition = LayoutTransition()
        }

        perms.forEach { perm ->
            val permRow = createPermissionRow(pkgInfo.packageName, perm, density)
            permsContainer.addView(permRow)
        }

        cardContent.addView(permsContainer)
        card.addView(cardContent)

        // Clicking anywhere on the app entry card smoothly expands/collapses the permissions downward
        card.setOnClickListener {
            if (permsContainer.visibility == View.VISIBLE) {
                permsContainer.visibility = View.GONE
                permCountTv.text = "${perms.size} perms ▸"
            } else {
                permsContainer.visibility = View.VISIBLE
                permCountTv.text = "${perms.size} perms ▾"
            }
        }

        return card
    }

    private fun createPermissionRow(pkg: String, perm: String, density: Float): View {
        val pm = packageManager
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
        }

        var labelText = perm
        try {
            val pInfo = pm.getPermissionInfo(perm, 0)
            val l = pInfo.loadLabel(pm)
            if (!l.isNullOrBlank()) labelText = l.toString()
        } catch (_: Throwable) {
            // PermissionInfo not declared in OS
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val labelTv = TextView(this).apply {
            text = labelText
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
        }
        textLayout.addView(labelTv)

        val rawTv = TextView(this).apply {
            text = perm
            textSize = 11f
            setTypeface(Typeface.MONOSPACE)
        }
        textLayout.addView(rawTv)

        row.addView(textLayout)

        // Switch on the right side
        val switchMat = MaterialSwitch(this).apply {
            val prefs = XposedPrefs.getOrNull()
            val currentRule = prefs?.getString("rule:$pkg:$perm", null)
            isChecked = (currentRule == "APPROVED")

            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    AegisPolicy.setRule(pkg, perm, AegisPolicy.Decision.APPROVED)
                    runRootCommand("pm grant $pkg $perm")
                } else {
                    AegisPolicy.setRule(pkg, perm, AegisPolicy.Decision.DENIED)
                    runRootCommand("pm revoke $pkg $perm")
                }
            }
        }
        row.addView(switchMat)

        // Long-press on the entry pops up an in-app dialog with Familiar Name, Full Identifier, and Explanation
        val longPressListener = View.OnLongClickListener {
            val finalDesc = PermissionDatabase.getDescription(perm)
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(labelText)
                .setMessage(
                    "Familiar Name:\n$labelText\n\n" +
                    "Full Identifier:\n$perm\n\n" +
                    "Explanation:\n$finalDesc"
                )
                .setPositiveButton("Close", null)
                .show()
            true
        }

        row.setOnLongClickListener(longPressListener)
        textLayout.setOnLongClickListener(longPressListener)

        return row
    }

    private fun runRootCommand(command: String) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = java.io.DataOutputStream(process.outputStream)
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
                os.close()
                process.waitFor()
            } catch (t: Throwable) {
                android.util.Log.e("Aegis", "Root command failed: $command", t)
            }
        }.start()
    }
}
