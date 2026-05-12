package com.example.pushups_locker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class AppAdapter(
    private var allApps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppToggle: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

    private var filteredApps = allApps

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)
        val status: TextView = view.findViewById(R.id.appStatus)
        val switch: SwitchMaterial = view.findViewById(R.id.appSwitch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = filteredApps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.name
        
        updateStatusText(holder.status, app)
        
        holder.switch.setOnCheckedChangeListener(null)
        holder.switch.isChecked = app.isLocked
        
        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            app.isLocked = isChecked
            updateStatusText(holder.status, app)
            onAppToggle(app, isChecked)
        }

        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
    }

    fun filter(query: String) {
        filteredApps = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { FuzzySearch.matches(query, it.name) }
        }
        notifyDataSetChanged()
    }

    fun updateData(newApps: List<AppInfo>) {
        allApps = newApps
        filteredApps = newApps
        notifyDataSetChanged()
    }

    private fun updateStatusText(textView: TextView, app: AppInfo) {
        val context = textView.context
        if (app.isLocked) {
            val h = app.timeLimitSeconds / 3600
            val m = (app.timeLimitSeconds % 3600) / 60
            val s = app.timeLimitSeconds % 60
            
            val timeStr = when {
                h > 0 -> String.format(Locale.getDefault(), "%dh %dm %ds", h, m, s)
                m > 0 -> String.format(Locale.getDefault(), "%dm %ds", m, s)
                else -> String.format(Locale.getDefault(), "%ds", s)
            }
            
            textView.text = "Limit: $timeStr | Pushups: ${app.pushupsRequired}"
            textView.setTextColor(ContextCompat.getColor(context, R.color.accent_red))
        } else {
            textView.text = context.getString(R.string.app_status_not_locked)
            textView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
    }

    override fun getItemCount() = filteredApps.size
}